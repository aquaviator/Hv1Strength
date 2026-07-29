package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.billing.AccountTrialClient
import com.example.billing.AccountTrialResult
import com.example.billing.AppAccessState
import com.example.billing.PlayEntitlementRepository
import com.example.billing.EntitlementVerificationClient
import com.example.billing.VerificationResult
import com.example.billing.VerifiedEntitlement
import com.example.billing.SubscriptionState
import com.example.billing.CommercialConfig
import com.example.data.StrengthDatabase
import com.example.data.StrengthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SecureAccountTrialTest {
    private lateinit var context: Context
    private lateinit var repository: StrengthRepository
    private lateinit var billingRepository: FakeBillingRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("human_strength_entitlements", Context.MODE_PRIVATE)
            .edit().clear().commit()
        val database = StrengthDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO))
        repository = StrengthRepository(database.strengthDao())
        billingRepository = FakeBillingRepository()
    }

    @Test
    fun activeTrialIsCachedAndRepeatedRefreshDoesNotReinitialize() = runBlocking {
        val now = System.currentTimeMillis()
        val client = FakeAccountTrialClient(
            AccountTrialResult.Active("uid-a", now, now + 30L * DAY, now)
        )
        val entitlementRepository = createRepository("uid-a", client)

        waitUntil { entitlementRepository.appAccessState.value is AppAccessState.TrialActive }
        entitlementRepository.refreshAccessState()
        waitUntil { entitlementRepository.appAccessState.value is AppAccessState.TrialActive }

        assertEquals(1, client.calls)
        assertTrue(entitlementRepository.appAccessState.value.hasAppAccess)
    }

    @Test
    fun consumedTrialMapsToExpired() = runBlocking {
        val now = System.currentTimeMillis()
        val client = FakeAccountTrialClient(
            AccountTrialResult.Expired("uid-b", now - 31L * DAY, now - DAY, now)
        )
        val entitlementRepository = createRepository("uid-b", client)

        waitUntil { entitlementRepository.appAccessState.value is AppAccessState.Expired }
        assertEquals(1, client.calls)
    }

    @Test
    fun backendFailureDoesNotGrantOrMarkTrialExpired() = runBlocking {
        val client = FakeAccountTrialClient(AccountTrialResult.Unavailable)
        val entitlementRepository = createRepository("uid-c", client)

        waitUntil { entitlementRepository.appAccessState.value is AppAccessState.VerificationUnavailable }
        assertTrue(!entitlementRepository.appAccessState.value.hasAppAccess)
    }

    @Test
    fun accountTrialRemainsUsableWhenPaidVerificationIsUnavailable() = runBlocking {
        val now = System.currentTimeMillis()
        val trialClient = FakeAccountTrialClient(
            AccountTrialResult.Active("uid-d", now, now + 30L * DAY, now)
        )
        billingRepository.setFakeState(
            SubscriptionState.PurchasedUnverified(null, "real-token", CommercialConfig.PRODUCT_ID_ANNUAL, now, true)
        )
        val entitlementRepository = createRepository(
            "uid-d",
            trialClient,
            FakeVerificationClient(VerificationResult.NetworkError)
        )

        waitUntil { entitlementRepository.appAccessState.value is AppAccessState.TrialActive }
        assertTrue(entitlementRepository.appAccessState.value.hasAppAccess)
    }

    @Test
    fun backendVerifiedPaidEntitlementRetainsPriorityOverTrial() = runBlocking {
        val now = System.currentTimeMillis()
        val trialClient = FakeAccountTrialClient(
            AccountTrialResult.Active("uid-e", now, now + 30L * DAY, now)
        )
        billingRepository.setFakeState(
            SubscriptionState.PurchasedUnverified(null, "real-token", CommercialConfig.PRODUCT_ID_ANNUAL, now, true)
        )
        val paid = VerifiedEntitlement(
            CommercialConfig.PRODUCT_ID_ANNUAL,
            "ACTIVE",
            now + 365L * DAY,
            true,
            now,
            "GOOGLE_PLAY_BACKEND"
        )
        val entitlementRepository = createRepository(
            "uid-e",
            trialClient,
            FakeVerificationClient(VerificationResult.Success(paid))
        )

        waitUntil { entitlementRepository.appAccessState.value is AppAccessState.Subscribed }
        assertEquals(0, trialClient.calls)
    }

    @Test
    fun nonAccessPaidStatusDoesNotOverrideActiveAccountTrial() = runBlocking {
        val now = System.currentTimeMillis()
        val trialClient = FakeAccountTrialClient(
            AccountTrialResult.Active("uid-f", now, now + 30L * DAY, now)
        )
        billingRepository.setFakeState(
            SubscriptionState.PurchasedUnverified(null, "real-token", CommercialConfig.PRODUCT_ID_ANNUAL, now, true)
        )
        val held = VerifiedEntitlement(
            CommercialConfig.PRODUCT_ID_ANNUAL,
            "ACCOUNT_HOLD",
            now + DAY,
            false,
            now,
            "GOOGLE_PLAY_BACKEND"
        )
        val entitlementRepository = createRepository(
            "uid-f",
            trialClient,
            FakeVerificationClient(VerificationResult.Success(held))
        )

        waitUntil { entitlementRepository.appAccessState.value is AppAccessState.TrialActive }
        assertEquals(1, trialClient.calls)
    }

    private fun createRepository(
        uid: String,
        client: FakeAccountTrialClient,
        verificationClient: EntitlementVerificationClient? = null
    ) =
        PlayEntitlementRepository(
            context = context,
            billingRepository = billingRepository,
            repository = repository,
            verificationClient = verificationClient
                ?: FakeVerificationClient(VerificationResult.NetworkError),
            accountTrialClient = client,
            currentUidProvider = { uid }
        )

    private suspend fun waitUntil(condition: () -> Boolean) {
        repeat(100) {
            if (condition()) return
            delay(20)
        }
        throw AssertionError("Timed out waiting for entitlement state")
    }

    private class FakeAccountTrialClient(
        private val result: AccountTrialResult
    ) : AccountTrialClient {
        var calls = 0
        override suspend fun initializeOrGetTrial(): AccountTrialResult {
            calls += 1
            return result
        }
    }

    private class FakeVerificationClient(
        private val result: VerificationResult
    ) : EntitlementVerificationClient {
        override suspend fun verifyPurchase(
            purchaseToken: String,
            productId: String,
            orderId: String?
        ) = result
    }

    companion object {
        private const val DAY = 24L * 60L * 60L * 1000L
    }
}
