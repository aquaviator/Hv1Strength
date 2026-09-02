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
    fun prepareForUser(uid: String?) {}
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
    private val KEY_TRIAL_VERIFIED_SERVER_MILLIS = "account_trial_verified_server_millis"
    private val KEY_ACCESS_KIND = "account_access_kind"
    private val KEY_HISTORICAL_TRIAL_END = "account_historical_trial_end"
    private val KEY_LAST_OBSERVED_WALL_MILLIS = "entitlement_last_observed_wall_millis"
    private val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val trialRefreshMutex = Mutex()
    @Volatile private var preparedUid: String? = currentUidProvider()

    private val _cachedEntitlement = MutableStateFlow<VerifiedEntitlement?>(discardLegacyPaidEntitlementCache())
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

    private fun discardLegacyPaidEntitlementCache(): VerifiedEntitlement? {
        // Old backend-verified and client-manufactured records used the same format/source,
        // so they cannot be distinguished safely. Paid access is re-verified each app session.
        prefs.edit()
            .remove(KEY_PRODUCT_ID)
            .remove(KEY_STATUS)
            .remove(KEY_EXPIRY_MILLIS)
            .remove(KEY_AUTO_RENEW)
            .remove(KEY_VERIFICATION_MILLIS)
            .remove(KEY_SOURCE)
            .apply()
        return null
    }

    private fun saveCachedEntitlement(entitlement: VerifiedEntitlement) {
        // Session-only cache: persisted legacy records were not provably backend-issued.
        _cachedEntitlement.value = entitlement
    }

    private data class CachedAccountTrial(
        val uid: String,
        val startedAtMillis: Long,
        val endsAtMillis: Long,
        val verifiedServerNowMillis: Long,
        val accessKind: String = "TRIAL",
        val historicalTrialEndMillis: Long = endsAtMillis
    )

    private fun loadCachedAccountTrial(uid: String?): CachedAccountTrial? {
        if (uid == null || prefs.getString(KEY_TRIAL_UID, null) != uid) return null
        val startedAt = prefs.getLong(KEY_TRIAL_STARTED_MILLIS, 0L)
        val endsAt = prefs.getLong(KEY_TRIAL_ENDS_MILLIS, 0L)
        val verifiedServerNow = prefs.getLong(KEY_TRIAL_VERIFIED_SERVER_MILLIS, 0L)
        if (startedAt <= 0L || endsAt <= startedAt || verifiedServerNow <= 0L) return null
        return CachedAccountTrial(uid, startedAt, endsAt, verifiedServerNow,
            prefs.getString(KEY_ACCESS_KIND, "TRIAL") ?: "TRIAL",
            prefs.getLong(KEY_HISTORICAL_TRIAL_END, endsAt))
    }

    private fun saveCachedAccountTrial(
        uid: String,
        startedAtMillis: Long,
        endsAtMillis: Long,
        verifiedServerNowMillis: Long,
        accessKind: String = "TRIAL",
        historicalTrialEndMillis: Long = endsAtMillis
    ) {
        val observedWall = maxOf(
            prefs.getLong(KEY_LAST_OBSERVED_WALL_MILLIS, 0L),
            verifiedServerNowMillis,
            System.currentTimeMillis()
        )
        prefs.edit()
            .putString(KEY_TRIAL_UID, uid)
            .putLong(KEY_TRIAL_STARTED_MILLIS, startedAtMillis)
            .putLong(KEY_TRIAL_ENDS_MILLIS, endsAtMillis)
            .putLong(KEY_TRIAL_VERIFIED_SERVER_MILLIS, verifiedServerNowMillis)
            .putString(KEY_ACCESS_KIND, accessKind)
            .putLong(KEY_HISTORICAL_TRIAL_END, historicalTrialEndMillis)
            .putLong(KEY_LAST_OBSERVED_WALL_MILLIS, observedWall)
            .apply()
    }

    private fun accountTrialState(trial: CachedAccountTrial, nowMillis: Long): AppAccessState {
        val trustedNow = trustedOfflineNow(nowMillis, trial.verifiedServerNowMillis)
        if (trial.endsAtMillis <= trustedNow) {
            return AppAccessState.Expired(trial.historicalTrialEndMillis,
                if (trial.accessKind == "TRIAL") trial.startedAtMillis else null)
        }
        if (trial.accessKind == "SUPPORT") return AppAccessState.SupportAccessActive(
            trial.endsAtMillis, trial.historicalTrialEndMillis)
        val remainingMillis = trial.endsAtMillis - trustedNow
        val daysRemaining = ((remainingMillis + MILLIS_PER_DAY - 1L) / MILLIS_PER_DAY).toInt()
        return AppAccessState.TrialActive(daysRemaining, trial.endsAtMillis, trial.startedAtMillis)
    }

    private fun paidAccessState(entitlement: VerifiedEntitlement, nowMillis: Long): AppAccessState? {
        val trustedNow = trustedOfflineNow(nowMillis)
        if (!entitlement.isValidAt(trustedNow)) return null
        return when (entitlement.status) {
            "ACTIVE" -> AppAccessState.Subscribed(entitlement.expiryTimestampMillis)
            "TRIAL_ACTIVE" -> {
                val daysRemaining = maxOf(
                    1,
                    ((entitlement.expiryTimestampMillis - trustedNow) / MILLIS_PER_DAY).toInt()
                )
                AppAccessState.TrialActive(daysRemaining, entitlement.expiryTimestampMillis)
            }
            "CANCELLED_ACTIVE" -> AppAccessState.SubscriptionActiveUntilExpiry(entitlement.expiryTimestampMillis)
            "GRACE_PERIOD" -> AppAccessState.GracePeriod
            "PENDING" -> AppAccessState.PaymentPending
            else -> null
        }
    }

    private fun trustedOfflineNow(nowMillis: Long, serverNowMillis: Long = 0L): Long {
        val previous = prefs.getLong(KEY_LAST_OBSERVED_WALL_MILLIS, 0L)
        val trustedNow = maxOf(nowMillis, serverNowMillis, previous)
        if (trustedNow > previous) {
            prefs.edit().putLong(KEY_LAST_OBSERVED_WALL_MILLIS, trustedNow).apply()
        }
        return trustedNow
    }

    private suspend fun resolveAccessState(
        subState: SubscriptionState,
        cached: VerifiedEntitlement?
    ): AppAccessState {
        val now = System.currentTimeMillis()
        val currentUid = currentUidProvider()
        if (currentUid == null || preparedUid != currentUid) return AppAccessState.Initializing

        // 1. Check if cached verified entitlement is valid
        cached?.let { paidAccessState(it, now) }?.let { return it }

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
                    updatedCached?.let { paidAccessState(it, now) }?.let { return it }
                }
            }
            is SubscriptionState.PurchasePending -> {
                return AppAccessState.PaymentPending
            }
            else -> {}
        }

        // 3. Resolve the backend-owned Human V1 account trial for the signed-in Firebase user.
        return trialRefreshMutex.withLock {
                val cachedTrialAccess = loadCachedAccountTrial(currentUid)
                    ?.let { accountTrialState(it, System.currentTimeMillis()) }
                    ?.takeIf { it.hasAppAccess }
                when (val result = accountTrialClient.initializeOrGetTrial()) {
                    is AccountTrialResult.SupportActive -> {
                        if (result.uid != currentUid) return@withLock AppAccessState.VerificationUnavailable
                        saveCachedAccountTrial(result.uid, result.effectiveAtMillis, result.expiryAtMillis,
                            result.serverNowMillis, "SUPPORT", result.historicalTrialEndMillis)
                        AppAccessState.SupportAccessActive(result.expiryAtMillis, result.historicalTrialEndMillis)
                    }
                    is AccountTrialResult.Active -> {
                        if (result.uid != currentUid) return@withLock AppAccessState.VerificationUnavailable
                        saveCachedAccountTrial(
                            result.uid,
                            result.trialStartedAtMillis,
                            result.trialEndsAtMillis,
                            result.serverNowMillis
                        )
                        accountTrialState(
                            CachedAccountTrial(
                                result.uid,
                                result.trialStartedAtMillis,
                                result.trialEndsAtMillis,
                                result.serverNowMillis
                            ),
                            result.serverNowMillis
                        )
                    }
                    is AccountTrialResult.Expired -> {
                        if (result.uid != currentUid) return@withLock AppAccessState.VerificationUnavailable
                        saveCachedAccountTrial(
                            result.uid,
                            result.trialStartedAtMillis,
                            result.trialEndsAtMillis,
                            result.serverNowMillis
                        )
                        AppAccessState.Expired(result.trialEndsAtMillis, result.trialStartedAtMillis)
                    }
                    AccountTrialResult.Disabled -> AppAccessState.Unentitled
                    AccountTrialResult.Unauthenticated -> AppAccessState.VerificationUnavailable
                    AccountTrialResult.Unavailable -> cachedTrialAccess
                        ?: AppAccessState.VerificationUnavailable
                }
        }
    }

    override fun prepareForUser(uid: String?) {
        if (preparedUid == uid) return
        // Publish a safe state before associating the owner. This prevents a
        // previous account's terminal state from being framed as the new user's.
        _appAccessState.value = AppAccessState.Initializing
        _cachedEntitlement.value = null
        preparedUid = uid
    }

    override fun refreshAccessState() {
        val uid = currentUidProvider()
        prepareForUser(uid)
        if (uid == null) return
        externalScope.launch {
            val cachedTrial = loadCachedAccountTrial(uid)
            if (cachedTrial != null) {
                val cachedState = accountTrialState(cachedTrial, System.currentTimeMillis())
                if (cachedState.hasAppAccess) _appAccessState.value = cachedState
            } else if (!_appAccessState.value.hasAppAccess) {
                _appAccessState.value = AppAccessState.Initializing
            }
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
