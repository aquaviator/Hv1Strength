package com.example.billing

internal data class BillingOfferCandidate(
    val basePlanId: String,
    val offerId: String?,
    val offerToken: String,
    val hasFreePhase: Boolean
)

internal enum class LocalPurchaseDisposition { PURCHASED_UNVERIFIED, PENDING, SUSPENDED, REJECTED }
internal enum class BillingResponseDisposition { SUCCESS, CANCELLED, REQUERY_OWNED, UNAVAILABLE, RETRYABLE_ERROR, ERROR }

internal object BillingPolicy {
    fun selectOffer(offers: List<BillingOfferCandidate>): BillingOfferCandidate? = offers
        .filter { it.offerToken.isNotBlank() }
        .sortedWith(
            compareByDescending<BillingOfferCandidate> { it.hasFreePhase }
                .thenBy { it.basePlanId }
                .thenBy { it.offerId.orEmpty() }
                .thenBy { it.offerToken }
        )
        .firstOrNull()

    fun classifyPurchase(
        products: List<String>,
        purchaseState: Int,
        suspended: Boolean
    ): LocalPurchaseDisposition = when {
        CommercialConfig.PRODUCT_ID_ANNUAL !in products -> LocalPurchaseDisposition.REJECTED
        suspended -> LocalPurchaseDisposition.SUSPENDED
        purchaseState == 2 -> LocalPurchaseDisposition.PENDING
        purchaseState == 1 -> LocalPurchaseDisposition.PURCHASED_UNVERIFIED
        else -> LocalPurchaseDisposition.REJECTED
    }

    fun classifyResponse(responseCode: Int): BillingResponseDisposition = when (responseCode) {
        0 -> BillingResponseDisposition.SUCCESS
        1 -> BillingResponseDisposition.CANCELLED
        7 -> BillingResponseDisposition.REQUERY_OWNED
        3 -> BillingResponseDisposition.UNAVAILABLE
        2, 6, 12 -> BillingResponseDisposition.RETRYABLE_ERROR
        else -> BillingResponseDisposition.ERROR
    }
}
