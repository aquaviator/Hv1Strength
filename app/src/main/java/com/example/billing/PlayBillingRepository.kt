package com.example.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface BillingRepository {
    val subscriptionState: StateFlow<SubscriptionState>
    val productInfo: StateFlow<SubscriptionProductInfo?>
    fun initializeConnection()
    fun launchPurchaseFlow(activity: Activity): Boolean
    fun restorePurchases()
    fun acknowledgePurchaseIfNeeded(purchase: Purchase)
}

class PlayBillingRepository(
    private val context: Context,
    private val externalScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : BillingRepository, PurchasesUpdatedListener {

    private val TAG = "PlayBillingRepository"

    private val _subscriptionState = MutableStateFlow<SubscriptionState>(SubscriptionState.Loading)
    override val subscriptionState: StateFlow<SubscriptionState> = _subscriptionState.asStateFlow()

    private val _productInfo = MutableStateFlow<SubscriptionProductInfo?>(null)
    override val productInfo: StateFlow<SubscriptionProductInfo?> = _productInfo.asStateFlow()

    private var billingClient: BillingClient? = null
    private var cachedProductDetails: ProductDetails? = null

    init {
        initializeConnection()
    }

    override fun initializeConnection() {
        try {
            billingClient = BillingClient.newBuilder(context)
                .setListener(this)
                .enablePendingPurchases()
                .build()

            startConnection()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to instantiate BillingClient", e)
            _subscriptionState.value = SubscriptionState.Unavailable
        }
    }

    private fun startConnection() {
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.i(TAG, "BillingClient setup finished successfully")
                    queryProductDetails()
                    queryActivePurchases()
                } else {
                    Log.w(TAG, "Billing setup failed with code: ${billingResult.responseCode} (${billingResult.debugMessage})")
                    _subscriptionState.value = SubscriptionState.Unavailable
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected")
                _subscriptionState.value = SubscriptionState.Unavailable
            }
        })
    }

    private fun queryProductDetails() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(CommercialConfig.PRODUCT_ID_ANNUAL)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                val details = productDetailsList.first()
                cachedProductDetails = details
                parseAndPublishProductDetails(details)
            } else {
                Log.w(TAG, "queryProductDetailsAsync failed or returned empty: ${billingResult.responseCode}")
            }
        }
    }

    private fun parseAndPublishProductDetails(details: ProductDetails) {
        val offers = details.subscriptionOfferDetails ?: emptyList()
        if (offers.isEmpty()) {
            _productInfo.value = null
            return
        }

        val bestOffer = offers.firstOrNull { offer ->
            offer.pricingPhases.pricingPhaseList.any { phase ->
                phase.priceAmountMicros == 0L
            }
        } ?: offers.first()

        val basePhase = bestOffer.pricingPhases.pricingPhaseList.lastOrNull()
        val trialPhase = bestOffer.pricingPhases.pricingPhaseList.firstOrNull { it.priceAmountMicros == 0L }

        val formattedPrice = basePhase?.formattedPrice ?: CommercialConfig.PLANNED_UK_PRICE
        val currencyCode = basePhase?.priceCurrencyCode ?: "GBP"
        val billingPeriod = basePhase?.billingPeriod ?: "P1Y"

        _productInfo.value = SubscriptionProductInfo(
            productId = details.productId,
            title = details.title,
            description = details.description,
            formattedPrice = formattedPrice,
            priceCurrencyCode = currencyCode,
            billingPeriod = billingPeriod,
            hasFreeTrial = trialPhase != null,
            trialPeriod = trialPhase?.billingPeriod,
            offerToken = bestOffer.offerToken
        )
    }

    private fun queryActivePurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient?.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases)
            } else {
                Log.w(TAG, "queryPurchasesAsync failed: ${billingResult.responseCode}")
                _subscriptionState.value = SubscriptionState.NoSubscription
            }
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        val activePurchase = purchases.firstOrNull { purchase ->
            purchase.products.contains(CommercialConfig.PRODUCT_ID_ANNUAL) &&
                    (purchase.purchaseState == Purchase.PurchaseState.PURCHASED || purchase.purchaseState == Purchase.PurchaseState.PENDING)
        }

        if (activePurchase == null) {
            _subscriptionState.value = SubscriptionState.NoSubscription
            return
        }

        when (activePurchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                if (!activePurchase.isAcknowledged) {
                    acknowledgePurchaseIfNeeded(activePurchase)
                }
                _subscriptionState.value = SubscriptionState.PurchasedUnverified(
                    orderId = activePurchase.orderId,
                    purchaseToken = activePurchase.purchaseToken,
                    productId = activePurchase.products.firstOrNull() ?: CommercialConfig.PRODUCT_ID_ANNUAL,
                    purchaseTime = activePurchase.purchaseTime,
                    isAcknowledged = activePurchase.isAcknowledged
                )
            }
            Purchase.PurchaseState.PENDING -> {
                _subscriptionState.value = SubscriptionState.PurchasePending
            }
            else -> {
                _subscriptionState.value = SubscriptionState.NoSubscription
            }
        }
    }

    override fun launchPurchaseFlow(activity: Activity): Boolean {
        val details = cachedProductDetails
        val info = _productInfo.value

        if (details == null || info == null || billingClient == null) {
            Log.e(TAG, "Cannot launch purchase flow: ProductDetails or BillingClient not ready")
            _subscriptionState.value = SubscriptionState.Error("Google Play Billing is not ready. Please try again.")
            return false
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .setOfferToken(info.offerToken)
                .build()
        )

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val responseCode = billingClient!!.launchBillingFlow(activity, flowParams).responseCode
        return responseCode == BillingClient.BillingResponseCode.OK
    }

    override fun restorePurchases() {
        if (billingClient?.isReady == true) {
            _subscriptionState.value = SubscriptionState.Loading
            queryActivePurchases()
        } else {
            initializeConnection()
        }
    }

    override fun acknowledgePurchaseIfNeeded(purchase: Purchase) {
        if (purchase.isAcknowledged) return

        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient?.acknowledgePurchase(params) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.i(TAG, "Purchase acknowledged successfully")
            } else {
                Log.w(TAG, "Failed to acknowledge purchase: ${billingResult.responseCode}")
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases != null) {
                    processPurchases(purchases)
                } else {
                    _subscriptionState.value = SubscriptionState.NoSubscription
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.i(TAG, "User canceled the billing flow")
                if (_subscriptionState.value is SubscriptionState.Loading || _subscriptionState.value is SubscriptionState.Error) {
                    _subscriptionState.value = SubscriptionState.NoSubscription
                }
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                Log.i(TAG, "Item already owned. Querying active purchases...")
                queryActivePurchases()
            }
            else -> {
                Log.w(TAG, "Billing flow error code: ${billingResult.responseCode} - ${billingResult.debugMessage}")
                _subscriptionState.value = SubscriptionState.Error(
                    message = billingResult.debugMessage.ifBlank { "Billing error occurred." },
                    responseCode = billingResult.responseCode
                )
            }
        }
    }
}
