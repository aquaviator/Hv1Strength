package com.example.billing

sealed class SubscriptionState {
    object Unavailable : SubscriptionState()
    object Loading : SubscriptionState()
    object NoSubscription : SubscriptionState()
    object PurchasePending : SubscriptionState()
    
    /**
     * Locally observed Google Play purchase that has not yet undergone
     * server-side verification.
     */
    data class PurchasedUnverified(
        val orderId: String?,
        val purchaseToken: String,
        val productId: String,
        val purchaseTime: Long,
        val isAcknowledged: Boolean
    ) : SubscriptionState()
    
    data class Error(
        val message: String,
        val responseCode: Int? = null
    ) : SubscriptionState()
}

data class SubscriptionProductInfo(
    val productId: String,
    val title: String,
    val description: String,
    val formattedPrice: String,
    val priceCurrencyCode: String,
    val billingPeriod: String,
    val hasFreeTrial: Boolean,
    val trialPeriod: String?,
    val offerToken: String
)

/**
 * Domain model representing trusted application entitlement state.
 */
sealed class AppAccessState {
    object Initializing : AppAccessState()
    
    data class TrialActive(
        val daysRemaining: Int,
        val trialEndDateMillis: Long
    ) : AppAccessState()
    
    data class Subscribed(
        val expiryDateMillis: Long? = null
    ) : AppAccessState()
    
    data class SubscriptionActiveUntilExpiry(
        val expiryDateMillis: Long
    ) : AppAccessState()
    
    object GracePeriod : AppAccessState()
    object PaymentPending : AppAccessState()
    object Expired : AppAccessState()
    object VerificationUnavailable : AppAccessState()
    
    data class Error(
        val message: String
    ) : AppAccessState()

    /**
     * Authoritative single entitlement check for application usage.
     */
    val hasAppAccess: Boolean
        get() = this is TrialActive || this is Subscribed || this is SubscriptionActiveUntilExpiry || this is GracePeriod
}

/**
 * Verified entitlement record backed by trusted backend or cached verification.
 */
data class VerifiedEntitlement(
    val productId: String,
    val status: String, // "ACTIVE", "CANCELLED_ACTIVE", "GRACE_PERIOD", "EXPIRED", "PENDING"
    val expiryTimestampMillis: Long,
    val autoRenewEnabled: Boolean,
    val verificationTimestampMillis: Long,
    val source: String // "GOOGLE_PLAY_BACKEND", "LOCAL_TRIAL", "CACHED"
) {
    fun isValidAt(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return expiryTimestampMillis > nowMillis && status != "EXPIRED"
    }
}

