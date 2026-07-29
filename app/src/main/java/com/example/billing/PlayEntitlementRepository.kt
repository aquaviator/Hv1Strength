package com.example.billing

import android.content.Context
import android.util.Log
import com.example.data.StrengthRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface EntitlementRepository {
    val appAccessState: StateFlow<AppAccessState>
    val cachedEntitlement: StateFlow<VerifiedEntitlement?>
    fun refreshAccessState()
    suspend fun verifyAndProcessPurchase(purchaseToken: String, productId: String, orderId: String?): Boolean
}

class PlayEntitlementRepository(
    private val context: Context,
    private val billingRepository: BillingRepository,
    private val repository: StrengthRepository,
    private val verificationClient: EntitlementVerificationClient = PlayEntitlementVerificationClient(context),
    private val accountTrialClient: AccountTrialClient = FirebaseAccountTrialClient(),
    private val currentUidProvider: () -> String? = {
        runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()
    },
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
    private val KEY_TRIAL_UID = "account_trial_uid"
    private val KEY_TRIAL_STARTED_MILLIS = "account_trial_started_millis"
    private val KEY_TRIAL_ENDS_MILLIS = "account_trial_ends_millis"
    private val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val trialRefreshMutex = Mutex()

    private val _cachedEntitlement = MutableStateFlow<VerifiedEntitlement?>(loadCachedEntitlement())
    override val cachedEntitlement: StateFlow<VerifiedEntitlement?> = _cachedEntitlement.asStateFlow()

    private val _appAccessState = MutableStateFlow<AppAccessState>(AppAccessState.Initializing)
    override val appAccessState: StateFlow<AppAccessState> = _appAccessState.asStateFlow()

    init {
        externalScope.launch {
            combine(
                billingRepository.subscriptionState,
                _cachedEntitlement
            ) { subState, cached ->
                resolveAccessState(subState, cached)
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

    private data class CachedAccountTrial(
        val uid: String,
        val startedAtMillis: Long,
        val endsAtMillis: Long
    )

    private fun loadCachedAccountTrial(uid: String?): CachedAccountTrial? {
        if (uid == null || prefs.getString(KEY_TRIAL_UID, null) != uid) return null
        val startedAt = prefs.getLong(KEY_TRIAL_STARTED_MILLIS, 0L)
        val endsAt = prefs.getLong(KEY_TRIAL_ENDS_MILLIS, 0L)
        if (startedAt <= 0L || endsAt <= startedAt) return null
        return CachedAccountTrial(uid, startedAt, endsAt)
    }

    private fun saveCachedAccountTrial(uid: String, startedAtMillis: Long, endsAtMillis: Long) {
        prefs.edit()
            .putString(KEY_TRIAL_UID, uid)
            .putLong(KEY_TRIAL_STARTED_MILLIS, startedAtMillis)
            .putLong(KEY_TRIAL_ENDS_MILLIS, endsAtMillis)
            .apply()
    }

    private fun accountTrialState(trial: CachedAccountTrial, nowMillis: Long): AppAccessState {
        if (trial.endsAtMillis <= nowMillis) return AppAccessState.Expired
        val remainingMillis = trial.endsAtMillis - nowMillis
        val daysRemaining = ((remainingMillis + MILLIS_PER_DAY - 1L) / MILLIS_PER_DAY).toInt()
        return AppAccessState.TrialActive(daysRemaining, trial.endsAtMillis)
    }

    private suspend fun resolveAccessState(
        subState: SubscriptionState,
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

        // 3. Resolve the backend-owned Human V1 account trial for the signed-in Firebase user.
        val currentUid = currentUidProvider()
        loadCachedAccountTrial(currentUid)?.let { return accountTrialState(it, now) }

        if (currentUid != null) {
            return trialRefreshMutex.withLock {
                loadCachedAccountTrial(currentUid)?.let {
                    return@withLock accountTrialState(it, System.currentTimeMillis())
                }
                when (val result = accountTrialClient.initializeOrGetTrial()) {
                    is AccountTrialResult.Active -> {
                        if (result.uid != currentUid) return@withLock AppAccessState.VerificationUnavailable
                        saveCachedAccountTrial(result.uid, result.trialStartedAtMillis, result.trialEndsAtMillis)
                        accountTrialState(
                            CachedAccountTrial(result.uid, result.trialStartedAtMillis, result.trialEndsAtMillis),
                            result.serverNowMillis
                        )
                    }
                    is AccountTrialResult.Expired -> {
                        if (result.uid != currentUid) return@withLock AppAccessState.VerificationUnavailable
                        saveCachedAccountTrial(result.uid, result.trialStartedAtMillis, result.trialEndsAtMillis)
                        AppAccessState.Expired
                    }
                    AccountTrialResult.Disabled -> AppAccessState.Unentitled
                    AccountTrialResult.Unauthenticated -> AppAccessState.Unentitled
                    AccountTrialResult.Unavailable -> AppAccessState.VerificationUnavailable
                }
            }
        }

        return when {
            subState is SubscriptionState.Loading -> AppAccessState.Initializing
            cached != null && (!cached.isValidAt(now) || cached.status == "EXPIRED") -> AppAccessState.Expired
            else -> AppAccessState.Unentitled
        }
    }

    override fun refreshAccessState() {
        externalScope.launch {
            val subState = billingRepository.subscriptionState.value
            val cached = _cachedEntitlement.value
            _appAccessState.value = resolveAccessState(subState, cached)
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
