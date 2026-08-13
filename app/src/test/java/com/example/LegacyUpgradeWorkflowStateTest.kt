package com.example

import com.example.billing.AppAccessState
import com.example.data.AuthState
import com.example.data.LegacyOwnershipTotals
import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyUpgradeWorkflowStateTest {
    private val totals = LegacyOwnershipTotals(1, 1, 1, 1, 1, 1, 1, 1)

    @Test fun upgradeDecisionStaysOnWelcomeWorkflow() {
        assertEquals(StartupDestination.Welcome,
            resolveStartupDestination(AuthState.LegacyUpgradeRequired(totals), AppAccessState.Initializing))
    }

    @Test fun migrationProgressCannotNavigateToDashboard() {
        assertEquals(StartupDestination.Welcome,
            resolveStartupDestination(AuthState.LegacyUpgradeRunning("Updating"), AppAccessState.Initializing))
    }

    @Test fun retryableHandoffCannotNavigateToDashboard() {
        assertEquals(StartupDestination.Welcome,
            resolveStartupDestination(AuthState.LegacyUpgradeHandoffRequired("Retry"), AppAccessState.Initializing))
    }
}
