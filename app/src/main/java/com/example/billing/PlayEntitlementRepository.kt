package com.example.billing
import android.content.Context
import android.util.Log
import com.example.data.StrengthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.data.AuthRepository
import com.example.data.AuthState
import com.example.data.PlatformConfigRepository


interface EntitlementRepository {
    val appAccessState: StateFlow<AppAccessState>
    val cachedEntitlement: StateFlow<VerifiedEntitlement?>
    fun refreshAccessState()
    suspend fun verifyAndProcessPurchase(purchaseToken: String, productId: String, orderId: String?): Boolean
}



@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlayEntitlementRepository(
    private val context: Context,
    private val billingRepository: BillingRepository,
    private val repository: StrengthRepository,
    private val authRepository: AuthRepository,
    private val verificationClient: EntitlementVerificationClient = PlayEntitlementVerificationClient(context),
    private val platformConfigRepository: PlatformConfigRepository = PlatformConfigRepository(),
    private val externalScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : EntitlementRepository {

    private val TAG = "PlayEntitlementRepo"
    private val PREFS_NAME = "human_strength_entitlements"
    private val KEY_PRODUCT_ID = "cached_product_id"
    private val KEY_STATUS = "cached_status"
    private val KEY_EXPIRY_MILLIS = "cached_expiry_millis"
    private val KEY_AUTO_RENEW = "cached_auto_renew"
    private val KEY_VERIFICATION_MILLIS = "cached_verification_millis"
    private val KEY_SOURCE = "cached_source"

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _cachedEntitlement = MutableStateFlow<VerifiedEntitlement?>(loadCachedEntitlement())
    override val cachedEntitlement: StateFlow<VerifiedEntitlement?> = _cachedEntitlement.asStateFlow()

    private val _appAccessState = MutableStateFlow<AppAccessState>(AppAccessState.Initializing)
    override val appAccessState: StateFlow<AppAccessState> = _appAccessState.asStateFlow()

    init {
        externalScope.launch {
            val userProfileFlow = authRepository.authState.flatMapLatest { state ->
                val userId = when (state) {
                    is AuthState.Authenticated -> state.profile.id
                    is AuthState.Offline -> "offline"
                    else -> "offline"
                }
                repository.getUserProfileFlow(userId)
            }

            combine(
                billingRepository.subscriptionState,
                userProfileFlow,
                _cachedEntitlement
            ) { subState, userProfile, cached ->
                resolveAccessState(subState, userProfile, cached)
            }.collect { newState ->
                _appAccessState.value = newState
            }
        }
    }

    private fun loadCachedEntitlement(): VerifiedEntitlement? {
        val productId = prefs.getString(KEY_PRODUCT_ID, null) ?: return null
        val status = prefs.getString(KEY_STATUS, "EXPIRED") ?: "EXPIRED"
        val expiryMillis = prefs.getLong(KEY_EXPIRY_MILLIS, 0L)
        val autoRenew = prefs.getBoolean(KEY_AUTO_RENEW, false)
        val verificationMillis = prefs.getLong(KEY_VERIFICATION_MILLIS, 0L)
        val source = prefs.getString(KEY_SOURCE, "CACHED") ?: "CACHED"

        return VerifiedEntitlement(
            productId = productId,
            status = status,
            expiryTimestampMillis = expiryMillis,
            autoRenewEnabled = autoRenew,
            verificationTimestampMillis = verificationMillis,
            source = source
        )
    }

    private fun saveCachedEntitlement(entitlement: VerifiedEntitlement) {
        prefs.edit()
            .putString(KEY_PRODUCT_ID, entitlement.productId)
            .putString(KEY_STATUS, entitlement.status)
            .putLong(KEY_EXPIRY_MILLIS, entitlement.expiryTimestampMillis)
            .putBoolean(KEY_AUTO_RENEW, entitlement.autoRenewEnabled)
            .putLong(KEY_VERIFICATION_MILLIS, entitlement.verificationTimestampMillis)
            .putString(KEY_SOURCE, entitlement.source)
            .apply()

        _cachedEntitlement.value = entitlement
    }

    private suspend fun resolveAccessState(
        subState: SubscriptionState,
        userProfile: com.example.data.UserProfile?,
        cached: VerifiedEntitlement?
    ): AppAccessState {
        val now = System.currentTimeMillis()

        // 1. Check if cached verified entitlement is valid
        if (cached != null && cached.isValidAt(now)) {
            return when (cached.status) {
                "ACTIVE" -> AppAccessState.Subscribed(cached.expiryTimestampMillis)
                "TRIAL_ACTIVE" -> {
                    val daysRemaining = maxOf(1, ((cached.expiryTimestampMillis - now) / (24L * 60L * 60L * 1000L)).toInt())
                    AppAccessState.TrialActive(daysRemaining, cached.expiryTimestampMillis)
                }
                "CANCELLED_ACTIVE" -> AppAccessState.SubscriptionActiveUntilExpiry(cached.expiryTimestampMillis)
                "GRACE_PERIOD" -> AppAccessState.GracePeriod
                else -> AppAccessState.Subscribed(cached.expiryTimestampMillis)
            }
        }

        // 2. Process Play Billing subState
        when (subState) {
            is SubscriptionState.PurchasedUnverified -> {
                Log.i(TAG, "Observed PurchasedUnverified. Initiating backend verification...")
                val verifySuccess = verifyAndProcessPurchase(
                    subState.purchaseToken,
                    subState.productId,
                    subState.orderId
                )
                if (verifySuccess) {
                    val updatedCached = _cachedEntitlement.value
                    if (updatedCached != null && updatedCached.isValidAt(now)) {
                        return when (updatedCached.status) {
                            "TRIAL_ACTIVE" -> {
                                val daysRemaining = maxOf(1, ((updatedCached.expiryTimestampMillis - now) / (24L * 60L * 60L * 1000L)).toInt())
                                AppAccessState.TrialActive(daysRemaining, updatedCached.expiryTimestampMillis)
                            }
                            else -> AppAccessState.Subscribed(updatedCached.expiryTimestampMillis)
                        }
                    }
                }
            }
            is SubscriptionState.PurchasePending -> {
                return AppAccessState.PaymentPending
            }
            else -> {}
        }

        if (subState is SubscriptionState.Loading) {
            return AppAccessState.Initializing
        }

        // 3. Check Human V1 Account Trial
        if (userProfile != null) {
            if (userProfile.trialStartedAt != null && userProfile.trialEndsAt != null) {
                // Trial has been initialized
                if (now < userProfile.trialEndsAt!!) {
                    val daysRemaining = maxOf(1, ((userProfile.trialEndsAt!! - now) / (24L * 60L * 60L * 1000L)).toInt())
                    return AppAccessState.TrialActive(daysRemaining, userProfile.trialEndsAt!!)
                }
            } else {
                // New eligible account - initialize trial once
                val trialPolicy = platformConfigRepository.getTrialPolicy()
                if (trialPolicy.trialEnabled) {
                    var cloudTrialStartedAt: Long? = null
                    var cloudTrialEndsAt: Long? = null
                    
                    if (com.example.HumanStrengthApplication.isFirebaseConfigured) {
                        try {
                            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            val doc = db.collection("users").document(userProfile.humanUserId).collection("profile").document("main").get().await()
                            if (doc.exists()) {
                                cloudTrialStartedAt = doc.getLong("trialStartedAt")
                                cloudTrialEndsAt = doc.getLong("trialEndsAt")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not fetch cloud profile to verify trial status", e)
                        }
                    }

                    if (cloudTrialStartedAt != null && cloudTrialEndsAt != null) {
                        val updatedProfile = userProfile.copy(
                            trialStartedAt = cloudTrialStartedAt,
                            trialEndsAt = cloudTrialEndsAt
                        )
                        repository.insertUserProfile(updatedProfile)
                        return AppAccessState.Initializing
                    } else {
                        val isExistingAccount = (now - userProfile.createdAt) > (24L * 60L * 60L * 1000L)
                        val startedAt = if (isExistingAccount) userProfile.createdAt else now
                        val trialEndsAt = startedAt + (trialPolicy.trialDurationDays * 24L * 60L * 60L * 1000L)
                        
                        val updatedProfile = userProfile.copy(
                            trialStartedAt = startedAt,
                            trialEndsAt = trialEndsAt,
                            updatedAt = now
                        )
                        repository.insertUserProfile(updatedProfile)
                        return AppAccessState.Initializing
                    }
                }
            }
        }

        // 4. Known Human V1 trial expired or Play Subscription expired
        val hasExpiredPlay = cached != null && (!cached.isValidAt(now) || cached.status == "EXPIRED")
        val hasExpiredTrial = userProfile?.trialEndsAt != null && now >= userProfile.trialEndsAt

        if (hasExpiredPlay || hasExpiredTrial) {
            return AppAccessState.Expired
        }

        return AppAccessState.Unentitled
    }

    override fun refreshAccessState() {
        externalScope.launch {
            val userId = when (val state = authRepository.authState.value) {
                is AuthState.Authenticated -> state.profile.id
                is AuthState.Offline -> "offline"
                else -> "offline"
            }
            val userProfile = repository.getUserProfile(userId)
            val subState = billingRepository.subscriptionState.value
            val cached = _cachedEntitlement.value
            _appAccessState.value = resolveAccessState(subState, userProfile, cached)
        }
    }

    override suspend fun verifyAndProcessPurchase(
        purchaseToken: String,
        productId: String,
        orderId: String?
    ): Boolean {
        return when (val result = verificationClient.verifyPurchase(purchaseToken, productId, orderId)) {
            is VerificationResult.Success -> {
                Log.i(TAG, "Verification succeeded. Updating entitlement...")
                saveCachedEntitlement(result.entitlement)
                true
            }
            is VerificationResult.Failed -> {
                Log.w(TAG, "Verification failed: ${result.reason}")
                false
            }
            is VerificationResult.NetworkError -> {
                Log.w(TAG, "Verification network error. Relying on cached entitlement...")
                false
            }
        }
    }
}
