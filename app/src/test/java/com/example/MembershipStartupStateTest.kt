package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.billing.*
import com.example.data.AuthState
import com.example.data.StrengthDatabase
import com.example.data.StrengthRepository
import com.example.data.UserProfile
import com.example.ui.screens.subscriptionAccessContent
import com.example.ui.viewmodel.accessStateForAuthenticatedUser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MembershipStartupStateTest {
    private lateinit var context: Context
    private lateinit var repository: StrengthRepository
    private lateinit var billing: FakeBillingRepository

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("human_strength_entitlements", Context.MODE_PRIVATE).edit().clear().commit()
        val database = StrengthDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO))
        repository = StrengthRepository(database.strengthDao())
        billing = FakeBillingRepository()
    }

    @Test fun missingCacheAndBillingDelayNeverEmitExpired() = runBlocking {
        val trial = DeferredTrialClient()
        val entitlement = create("uid-a", trial)
        entitlement.prepareForUser("uid-a")
        entitlement.refreshAccessState()
        delay(50)
        assertTrue(entitlement.appAccessState.value is AppAccessState.Initializing)
        assertFalse(rendered(entitlement.appAccessState.value).contains("expired", true))
    }

    @Test fun expiredCacheIsCheckingUntilAuthoritativeExpiration() = runBlocking {
        val now = System.currentTimeMillis()
        saveTrial("uid-b", now - 31 * DAY, now - DAY)
        val trial = DeferredTrialClient()
        val entitlement = create("uid-b", trial)
        entitlement.prepareForUser("uid-b")
        entitlement.refreshAccessState()
        delay(50)
        assertTrue(entitlement.appAccessState.value is AppAccessState.Initializing)
        trial.result.complete(AccountTrialResult.Expired("uid-b", now - 31 * DAY, now - DAY, now))
        waitUntil { entitlement.appAccessState.value is AppAccessState.Expired }
    }

    @Test fun activeCacheRemainsNonExpiredWhileRefreshing() = runBlocking {
        val now = System.currentTimeMillis()
        saveTrial("uid-c", now - DAY, now + 10 * DAY)
        val trial = DeferredTrialClient()
        val entitlement = create("uid-c", trial)
        entitlement.prepareForUser("uid-c")
        entitlement.refreshAccessState()
        waitUntil { entitlement.appAccessState.value is AppAccessState.TrialActive }
        assertFalse(rendered(entitlement.appAccessState.value).contains("expired", true))
    }

    @Test fun unavailablePendingExpiredAndLaterActiveRemainTruthful() = runBlocking {
        val unavailable = create("uid-d", ImmediateTrialClient(AccountTrialResult.Unavailable))
        unavailable.refreshAccessState()
        waitUntil { unavailable.appAccessState.value is AppAccessState.VerificationUnavailable }

        val pendingBilling = FakeBillingRepository().also { it.setFakeState(SubscriptionState.PurchasePending) }
        val pending = create("uid-e", DeferredTrialClient(), pendingBilling)
        pending.refreshAccessState()
        waitUntil { pending.appAccessState.value is AppAccessState.PaymentPending }

        val now = System.currentTimeMillis()
        val activeClient = DeferredTrialClient()
        val laterActive = create("uid-f", activeClient, FakeBillingRepository())
        laterActive.refreshAccessState()
        assertTrue(laterActive.appAccessState.value is AppAccessState.Initializing)
        activeClient.result.complete(AccountTrialResult.Active("uid-f", now, now + 30 * DAY, now))
        waitUntil { laterActive.appAccessState.value is AppAccessState.TrialActive }
        assertEquals(StartupDestination.FullApp, resolveStartupDestination(auth("uid-f"), laterActive.appAccessState.value))
    }

    @Test fun accountSwitchCannotRenderPreviousUidExpiration() {
        val expired = AppAccessState.Expired(2L, 1L)
        assertSame(expired, accessStateForAuthenticatedUser(auth("uid-old"), "uid-old", expired))
        val switched = accessStateForAuthenticatedUser(auth("uid-new"), "uid-old", expired)
        assertTrue(switched is AppAccessState.Initializing)
        assertFalse(rendered(switched).contains("expired", true))
    }

    @Test fun expirationForAnotherUidIsUnavailableNotExpired() = runBlocking {
        val now = System.currentTimeMillis()
        val entitlement = create(
            "uid-current",
            ImmediateTrialClient(AccountTrialResult.Expired("uid-other", now - 31 * DAY, now - DAY, now))
        )

        entitlement.refreshAccessState()
        waitUntil { entitlement.appAccessState.value is AppAccessState.VerificationUnavailable }
        assertFalse(rendered(entitlement.appAccessState.value).contains("expired", true))
    }

    private fun create(uid: String, trial: AccountTrialClient, billingRepository: BillingRepository = billing) = PlayEntitlementRepository(
        context, billingRepository, repository,
        verificationClient = object : EntitlementVerificationClient {
            override suspend fun verifyPurchase(purchaseToken: String, productId: String, orderId: String?) = VerificationResult.NetworkError
        },
        accountTrialClient = trial,
        currentUidProvider = { uid }
    )

    private fun saveTrial(uid: String, started: Long, ends: Long) {
        context.getSharedPreferences("human_strength_entitlements", Context.MODE_PRIVATE).edit()
            .putString("account_trial_uid", uid)
            .putLong("account_trial_started_millis", started)
            .putLong("account_trial_ends_millis", ends)
            .commit()
    }

    private fun auth(uid: String) = AuthState.Authenticated(UserProfile(id = uid, firebaseUid = uid))
    private fun rendered(state: AppAccessState): String {
        val content = subscriptionAccessContent(state)
        return "${content.title} ${content.body}"
    }
    private suspend fun waitUntil(condition: () -> Boolean) {
        repeat(100) { if (condition()) return; delay(20) }
        throw AssertionError("Timed out waiting for membership state")
    }

    private class DeferredTrialClient : AccountTrialClient {
        val result = CompletableDeferred<AccountTrialResult>()
        override suspend fun initializeOrGetTrial() = result.await()
    }
    private class ImmediateTrialClient(private val value: AccountTrialResult) : AccountTrialClient {
        override suspend fun initializeOrGetTrial() = value
    }
    companion object { private const val DAY = 24L * 60L * 60L * 1000L }
}
