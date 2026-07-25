package com.example.billing

import android.content.Context
import android.util.Log
import com.example.data.StrengthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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
            combine(
                billingRepository.subscriptionState,
                repository.getUserProfileFlow("offline"),
                _cachedEntitlement
            ) { subState, userProfile, cached ->
                resolveAccessState(subState, userProfile?.createdAt, cached)
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
        profileCreatedAt: Long?,
        cached: VerifiedEntitlement?
    ): AppAccessState {
        val now = System.currentTimeMillis()

        // 1. Check if cached verified entitlement is valid
        if (cached != null && cached.isValidAt(now)) {
            return when (cached.status) {
                "ACTIVE" -> AppAccessState.Subscribed(cached.expiryTimestampMillis)
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
                        return AppAccessState.Subscribed(updatedCached.expiryTimestampMillis)
                    }
                }
            }
            is SubscriptionState.PurchasePending -> {
                // If trial is still active, trial applies. Otherwise PaymentPending.
                val trialState = checkTrialState(profileCreatedAt, now)
                if (trialState is AppAccessState.TrialActive) {
                    return trialState
                }
                return AppAccessState.PaymentPending
            }
            else -> {}
        }

        // 3. Fallback to 30-day introductory trial
        return checkTrialState(profileCreatedAt, now)
    }

    private fun checkTrialState(profileCreatedAt: Long?, nowMillis: Long): AppAccessState {
        val createdAt = profileCreatedAt ?: prefs.getLong("first_launch_time", 0L).let {
            if (it == 0L) {
                val currentTime = System.currentTimeMillis()
                prefs.edit().putLong("first_launch_time", currentTime).apply()
                currentTime
            } else {
                it
            }
        }

        val thirtyDaysInMillis = 30L * 24L * 60L * 60L * 1000L
        val trialEndDateMillis = createdAt + thirtyDaysInMillis

        return if (nowMillis < trialEndDateMillis) {
            val millisRemaining = trialEndDateMillis - nowMillis
            val daysRemaining = (millisRemaining / (24L * 60L * 60L * 1000L)).toInt() + 1
            AppAccessState.TrialActive(daysRemaining, trialEndDateMillis)
        } else {
            AppAccessState.Expired
        }
    }

    override fun refreshAccessState() {
        externalScope.launch {
            val userProfile = repository.getUserProfile("offline")
            val subState = billingRepository.subscriptionState.value
            val cached = _cachedEntitlement.value
            _appAccessState.value = resolveAccessState(subState, userProfile?.createdAt, cached)
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
