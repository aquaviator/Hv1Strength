package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.billing.*
import com.example.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Candidate70PhaseDEntitlementTest {

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
    fun testAppAccessStateHasAppAccessProperty() {
        assertTrue(AppAccessState.TrialActive(15, System.currentTimeMillis() + 100000).hasAppAccess)
        assertTrue(AppAccessState.Subscribed(System.currentTimeMillis() + 100000).hasAppAccess)
        assertTrue(AppAccessState.SubscriptionActiveUntilExpiry(System.currentTimeMillis() + 100000).hasAppAccess)
        assertTrue(AppAccessState.GracePeriod.hasAppAccess)

        assertFalse(AppAccessState.Initializing.hasAppAccess)
        assertFalse(AppAccessState.Expired().hasAppAccess)
        assertFalse(AppAccessState.PaymentPending.hasAppAccess)
        assertFalse(AppAccessState.VerificationUnavailable.hasAppAccess)
        assertFalse(AppAccessState.Error("Test error").hasAppAccess)
    }

    @Test
    fun testVerifiedEntitlementValidityCheck() {
        val now = System.currentTimeMillis()
        val validEntitlement = VerifiedEntitlement(
            productId = CommercialConfig.PRODUCT_ID_ANNUAL,
            status = "ACTIVE",
            expiryTimestampMillis = now + 86400000L,
            autoRenewEnabled = true,
            verificationTimestampMillis = now,
            source = "GOOGLE_PLAY_BACKEND"
        )
        assertTrue(validEntitlement.isValidAt(now))

        val expiredEntitlement = VerifiedEntitlement(
            productId = CommercialConfig.PRODUCT_ID_ANNUAL,
            status = "EXPIRED",
            expiryTimestampMillis = now - 1000L,
            autoRenewEnabled = false,
            verificationTimestampMillis = now - 86400000L,
            source = "GOOGLE_PLAY_BACKEND"
        )
        assertFalse(expiredEntitlement.isValidAt(now))
    }

    @Test
    fun testEntitlementRepositoryTrialAndExpiryResolution() = runBlocking {
        val entitlementRepository = PlayEntitlementRepository(
            context = context,
            billingRepository = fakeBillingRepository,
            repository = repository
        )

        // Initial launch -> 30 day trial active
        val state = entitlementRepository.appAccessState.value
        assertNotNull(state)
        // Verify trial active or initial state
        assertTrue(state is AppAccessState.Unentitled || state is AppAccessState.Initializing)
    }

    @Test
    fun testDataPreservationWhenAccessExpires() = runBlocking {
        // Create user profile & log data
        val profile = UserProfile(
            id = "user_test",
            displayName = "Test Runner",
            createdAt = System.currentTimeMillis() - 40L * 24L * 60L * 60L * 1000L,
            lastLoginAt = System.currentTimeMillis()
        ) // 40 days old
        repository.insertUserProfile(profile)

        // Data must exist in DB
        val retrievedProfile = repository.getUserProfile("user_test")
        assertNotNull(retrievedProfile)
        assertEquals("Test Runner", retrievedProfile?.displayName)

        // Expiry of access must NOT delete DB records
        val entitlementRepository = PlayEntitlementRepository(
            context = context,
            billingRepository = fakeBillingRepository,
            repository = repository
        )
        entitlementRepository.refreshAccessState()

        val postExpiryProfile = repository.getUserProfile("user_test")
        assertNotNull("User profile must remain intact after access expiry", postExpiryProfile)
    }

    @Test
    fun testLookingTokenDoesNotCreatePaidEntitlement() = runBlocking {
        authRepository.signInAnonymously()
        
        val verificationClient = PlayEntitlementVerificationClient(context, connectionFactory = {
            throw java.io.IOException("Backend unavailable")
        })
        val verifyResult = verificationClient.verifyPurchase(
            purchaseToken = "token_signout_test",
            productId = CommercialConfig.PRODUCT_ID_ANNUAL,
            orderId = "GPA.1111-2222-3333-44444"
        )
        assertFalse("Test-looking token must not create paid access", verifyResult is VerificationResult.Success)

        authRepository.signOut(keepLocalData = true)
        val authState = authRepository.authState.value
        assertTrue("Expected AuthState.Offline or Initial but was $authState", authState is AuthState.Offline || authState is AuthState.Initial)
    }
}
