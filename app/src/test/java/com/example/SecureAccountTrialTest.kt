package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.billing.AccountTrialClient
import com.example.billing.AccountTrialResult
import com.example.billing.AppAccessState
import com.example.billing.PlayEntitlementRepository
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

    private fun createRepository(uid: String, client: FakeAccountTrialClient) =
        PlayEntitlementRepository(
            context = context,
            billingRepository = billingRepository,
            repository = repository,
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

    companion object {
        private const val DAY = 24L * 60L * 60L * 1000L
    }
}
