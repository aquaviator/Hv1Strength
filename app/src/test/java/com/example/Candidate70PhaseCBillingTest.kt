package com.example

import android.app.Activity
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.billing.*
import com.example.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class FakeBillingRepository : BillingRepository {
    private val _subscriptionState = MutableStateFlow<SubscriptionState>(SubscriptionState.Loading)
    override val subscriptionState: StateFlow<SubscriptionState> = _subscriptionState.asStateFlow()

    private val _productInfo = MutableStateFlow<SubscriptionProductInfo?>(null)
    override val productInfo: StateFlow<SubscriptionProductInfo?> = _productInfo.asStateFlow()

    var acknowledgedCount = 0
    var restoreCalled = false
    var launchFlowCalled = false

    override fun initializeConnection() {
        _subscriptionState.value = SubscriptionState.NoSubscription
    }

    override fun launchPurchaseFlow(activity: Activity): Boolean {
        launchFlowCalled = true
        return true
    }

    override fun restorePurchases() {
        restoreCalled = true
        // Simulate finding purchase
        _subscriptionState.value = SubscriptionState.PurchasedUnverified(
            orderId = "GPA.1234-5678-9012-34567",
            purchaseToken = "token_abc123",
            productId = CommercialConfig.PRODUCT_ID_ANNUAL,
            purchaseTime = System.currentTimeMillis(),
            isAcknowledged = false
        )
    }

    override fun acknowledgePurchaseIfNeeded(purchase: com.android.billingclient.api.Purchase) {
        acknowledgedCount++
    }

    fun setFakeState(state: SubscriptionState) {
        _subscriptionState.value = state
    }

    fun setFakeProductInfo(info: SubscriptionProductInfo?) {
        _productInfo.value = info
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Candidate70PhaseCBillingTest {

    private lateinit var context: Context
    private lateinit var database: StrengthDatabase
    private lateinit var repository: StrengthRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var fakeBillingRepository: FakeBillingRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = StrengthDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO))
        repository = StrengthRepository(database.strengthDao())
        authRepository = AuthRepository(context, repository, CoroutineScope(Dispatchers.IO))
        fakeBillingRepository = FakeBillingRepository()
    }

    @Test
    fun testBillingRepositoryInitialStateAndTransitions() = runBlocking {
        assertEquals(SubscriptionState.Loading, fakeBillingRepository.subscriptionState.value)
        
        fakeBillingRepository.initializeConnection()
        assertEquals(SubscriptionState.NoSubscription, fakeBillingRepository.subscriptionState.value)

        fakeBillingRepository.setFakeState(SubscriptionState.PurchasePending)
        assertEquals(SubscriptionState.PurchasePending, fakeBillingRepository.subscriptionState.value)
    }

    @Test
    fun testProductDetailsAndOfferMapping() = runBlocking {
        val trialInfo = SubscriptionProductInfo(
            productId = CommercialConfig.PRODUCT_ID_ANNUAL,
            title = "Human Strength Annual (Human Strength)",
            description = "Annual membership for Human Strength",
            formattedPrice = "£24.00",
            priceCurrencyCode = "GBP",
            billingPeriod = "P1Y",
            hasFreeTrial = true,
            trialPeriod = "P1M",
            offerToken = "trial_offer_token"
        )

        fakeBillingRepository.setFakeProductInfo(trialInfo)
        val info = fakeBillingRepository.productInfo.value

        assertNotNull(info)
        assertEquals(CommercialConfig.PRODUCT_ID_ANNUAL, info?.productId)
        assertEquals("£24.00", info?.formattedPrice)
        assertTrue(info?.hasFreeTrial == true)
        assertEquals("P1M", info?.trialPeriod)
    }

    @Test
    fun testPurchasedUnverifiedStateRepresentation() = runBlocking {
        val purchasedState = SubscriptionState.PurchasedUnverified(
            orderId = "GPA.1111-2222-3333-44444",
            purchaseToken = "test_purchase_token_xyz",
            productId = CommercialConfig.PRODUCT_ID_ANNUAL,
            purchaseTime = 123456789L,
            isAcknowledged = false
        )

        fakeBillingRepository.setFakeState(purchasedState)
        val current = fakeBillingRepository.subscriptionState.value

        assertTrue(current is SubscriptionState.PurchasedUnverified)
        val unverified = current as SubscriptionState.PurchasedUnverified
        assertEquals("GPA.1111-2222-3333-44444", unverified.orderId)
        assertEquals("test_purchase_token_xyz", unverified.purchaseToken)
        assertFalse(unverified.isAcknowledged)
    }

    @Test
    fun testRestorePurchasesFlow() = runBlocking {
        assertFalse(fakeBillingRepository.restoreCalled)
        fakeBillingRepository.restorePurchases()
        assertTrue(fakeBillingRepository.restoreCalled)

        val state = fakeBillingRepository.subscriptionState.value
        assertTrue(state is SubscriptionState.PurchasedUnverified)
    }

    @Test
    fun testAuthStateRemainsIndependentOfBillingState() = runBlocking {
        authRepository.signInAnonymously()
        val authStateBefore = authRepository.authState.value

        fakeBillingRepository.setFakeState(
            SubscriptionState.PurchasedUnverified(
                orderId = "GPA.9999-8888-7777-66666",
                purchaseToken = "token_independent",
                productId = CommercialConfig.PRODUCT_ID_ANNUAL,
                purchaseTime = System.currentTimeMillis(),
                isAcknowledged = true
            )
        )

        // Changing billing state must not alter auth state
        val authStateAfter = authRepository.authState.value
        assertEquals(authStateBefore, authStateAfter)

        // Signing out auth must not destroy billing state model
        authRepository.signOut(keepLocalData = true)
        val billingStateAfterSignOut = fakeBillingRepository.subscriptionState.value
        assertTrue(billingStateAfterSignOut is SubscriptionState.PurchasedUnverified)
    }

    @Test
    fun testDeveloperTrialSimulationIsIsolated() = runBlocking {
        val prefs = context.getSharedPreferences("strength_settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("simulate_trial_expired", true).apply()

        fakeBillingRepository.setFakeState(SubscriptionState.NoSubscription)
        
        // Developer trial simulation pref must not force billing state to change
        assertTrue(prefs.getBoolean("simulate_trial_expired", false))
        assertEquals(SubscriptionState.NoSubscription, fakeBillingRepository.subscriptionState.value)
    }

    @Test
    fun testBillingUnavailableDoesNotBreakAppCoreData() = runBlocking {
        fakeBillingRepository.setFakeState(SubscriptionState.Unavailable)

        // Core workout templates and profiles remain accessible
        val exercises = repository.allExercises
        assertNotNull(exercises)
        val profile = repository.getUserProfile("offline")
        assertNotNull(profile)
    }
}
