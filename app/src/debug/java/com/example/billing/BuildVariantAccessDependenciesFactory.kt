package com.example.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.Purchase
import com.example.data.DebugAcceptanceIdentity
import com.example.data.StrengthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal object BuildVariantAccessDependenciesFactory {
    fun create(context: Context, repository: StrengthRepository): AccessDependencies {
        if (!DebugAcceptanceIdentity.isValidAcceptanceSession(context)) {
            val billing = PlayBillingRepository(context)
            return AccessDependencies(billing, PlayEntitlementRepository(context, billing, repository))
        }
        val billing = InertAcceptanceBillingRepository()
        return AccessDependencies(billing, AcceptanceEntitlementRepository(context))
    }
}

private class InertAcceptanceBillingRepository : BillingRepository {
    override val subscriptionState: StateFlow<SubscriptionState> = MutableStateFlow(SubscriptionState.NoSubscription)
    override val productInfo: StateFlow<SubscriptionProductInfo?> = MutableStateFlow(null)
    override fun initializeConnection() = Unit
    override fun launchPurchaseFlow(activity: Activity) = false
    override fun restorePurchases() = Unit
    override fun acknowledgePurchaseIfNeeded(purchase: Purchase) = Unit
}

private class AcceptanceEntitlementRepository(private val context: Context) : EntitlementRepository {
    private val state = MutableStateFlow(accessState())
    override val appAccessState: StateFlow<AppAccessState> = state
    override val cachedEntitlement: StateFlow<VerifiedEntitlement?> = MutableStateFlow(null)
    override fun refreshAccessState() { state.value = accessState() }
    override suspend fun verifyAndProcessPurchase(purchaseToken: String, productId: String, orderId: String?) = false
    private fun accessState(): AppAccessState = if (DebugAcceptanceIdentity.isValidAcceptanceSession(context)) {
        val now = System.currentTimeMillis()
        AppAccessState.TrialActive(daysRemaining = 1, trialEndDateMillis = now + 60 * 60 * 1000L, trialStartedAtMillis = now)
    } else AppAccessState.VerificationUnavailable
}
