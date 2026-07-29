package com.example

import com.example.billing.AppAccessState
import com.example.data.AuthState
import com.example.data.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class StartupDestinationTest {
    private val authenticated = AuthState.Authenticated(
        UserProfile(
            id = "firebase-user",
            firebaseUid = "firebase-user",
            authProvider = "google",
            isOfflineUser = false
        )
    )

    @Test
    fun signedOutShowsWelcomeInsteadOfSubscriptionGate() {
        assertEquals(
            StartupDestination.Welcome,
            resolveStartupDestination(AuthState.Initial, AppAccessState.Unentitled)
        )
    }

    @Test
    fun authenticatingShowsAuthLoading() {
        assertEquals(
            StartupDestination.AuthLoading,
            resolveStartupDestination(AuthState.Loading, AppAccessState.Unentitled)
        )
    }

    @Test
    fun authenticatedAccessInitializationShowsAccessLoading() {
        assertEquals(
            StartupDestination.AccessLoading,
            resolveStartupDestination(authenticated, AppAccessState.Initializing)
        )
    }

    @Test
    fun authenticatedActiveTrialShowsFullApp() {
        assertEquals(
            StartupDestination.FullApp,
            resolveStartupDestination(authenticated, AppAccessState.TrialActive(30, 1L))
        )
    }

    @Test
    fun authenticatedExpiredTrialShowsSubscriptionGate() {
        assertEquals(
            StartupDestination.SubscriptionGate,
            resolveStartupDestination(authenticated, AppAccessState.Expired())
        )
    }

    @Test
    fun authenticatedVerificationUnavailableShowsSubscriptionGate() {
        assertEquals(
            StartupDestination.SubscriptionGate,
            resolveStartupDestination(authenticated, AppAccessState.VerificationUnavailable)
        )
    }
}
