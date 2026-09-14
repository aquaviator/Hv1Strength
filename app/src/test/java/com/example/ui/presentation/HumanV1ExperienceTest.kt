package com.example.ui.presentation

import com.example.billing.AppAccessState
import com.example.data.AuthState
import com.example.data.UserProfile
import org.junit.Assert.*
import org.junit.Test

class HumanV1ExperienceTest {
    private val profile = UserProfile(id = "account-a", displayName = "Athlete", createdAt = 1L, authProvider = "google")

    @Test fun startupProgressesWithoutContradictoryAccountCopy() {
        assertTrue(startupProgressMessage(AuthState.Initial, AppAccessState.Initializing).contains("local"))
        assertTrue(startupProgressMessage(AuthState.Loading, AppAccessState.Initializing).contains("trusted"))
        assertTrue(startupProgressMessage(AuthState.Authenticated(profile), AppAccessState.Initializing).contains("membership"))
    }

    @Test fun offlineProfileIsTruthful() = assertEquals("Training offline", authenticationPresentation(AuthState.Offline).title)
    @Test fun trustedIdentityPendingIsTruthful() = assertEquals("Verifying your account", authenticationPresentation(AuthState.Loading).title)
    @Test fun trustedIdentityBlockedFailsClosed() = assertEquals(ExperienceTone.BLOCKED, authenticationPresentation(AuthState.Error("binding conflict")).tone)
    @Test fun authenticatedReadyIsPositive() = assertEquals(ExperienceTone.POSITIVE, authenticationPresentation(AuthState.Authenticated(profile)).tone)
    @Test fun trialActiveIsAccountTrial() = assertEquals("Human V1 trial active", membershipStatus(AppAccessState.TrialActive(12, 2L, 1L)).title)
    @Test fun subscribedIsStrengthProduct() = assertEquals("Human Strength Annual", membershipStatus(AppAccessState.Subscribed()).title)
    @Test fun verificationPendingIsNotSubscribed() = assertEquals("Verification pending", membershipStatus(AppAccessState.PaymentPending).title)
    @Test fun expiredAccessIsBlocked() = assertEquals(ExperienceTone.BLOCKED, membershipStatus(AppAccessState.Expired()).tone)
    @Test fun syncWaitingStateUsesPendingCount() = assertTrue(syncPresentation(AuthState.Authenticated(profile), "Idle", 2, null).detail.contains("2"))
    @Test fun syncingStateIsNotSynced() = assertEquals("Synchronizing", syncPresentation(AuthState.Authenticated(profile), "Syncing", 0, null).title)
    @Test fun syncedRequiresNoErrorOrPendingWork() = assertEquals("Synced", syncPresentation(AuthState.Authenticated(profile), "Idle", 0, null).title)
    @Test fun syncErrorKeepsLocalTruth() = assertTrue(syncPresentation(AuthState.Authenticated(profile), "Failed", 0, "network").detail.contains("temporarily unavailable"))
    @Test fun studioPlanContractFailureIsNotPresentedAsNetworkOutage() {
        val result = syncPresentation(AuthState.Authenticated(profile), "StudioPlanNeedsAttention", 0, null)
        assertTrue(result.detail.contains("referenced workout is unavailable"))
        assertFalse(result.detail.contains("synchronization is temporarily unavailable"))
    }
    @Test fun notificationDeniedExplainsRecovery() = assertTrue(backgroundPresentation(false, true).detail.contains("Recovery"))
    @Test fun catalogueFallbackRemainsAvailable() = assertTrue(cataloguePresentation("fallback", 3, 2, false, true).detail.contains("3 core"))
    @Test fun differentOwnerCannotClaimRecovery() = assertFalse(recoveryOwnershipAllowed("account-a", "account-b"))
    @Test fun offlineOwnedRecoveryRemainsLocal() = assertTrue(recoveryOwnershipAllowed("offline", "account-b"))
    @Test fun advancedDiagnosticsAndDeveloperControlsAreDebugOnly() {
        assertTrue(advancedDiagnosticsVisible(true))
        assertFalse(advancedDiagnosticsVisible(false))
    }

    @Test fun ordinaryPresentationContainsNoRawIdentity() {
        val rendered = authenticationPresentation(AuthState.Authenticated(profile)).let { it.title + it.detail }
        assertFalse(rendered.contains(profile.id))
        assertFalse(rendered.contains("Firebase UID", ignoreCase = true))
        assertFalse(rendered.contains("Human ID", ignoreCase = true))
    }
}
