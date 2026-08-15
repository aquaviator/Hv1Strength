package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.sync.UnattendedSyncPolicy
import com.example.data.AuthErrorKind
import com.example.data.AuthState
import com.example.data.CommandQueueEntity
import com.example.data.StrengthDatabase
import com.example.data.UserProfile
import com.example.ui.presentation.signInDialogCopy
import com.example.ui.presentation.syncPresentation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UnattendedSynchronizationHotfixTest {

    @Test fun interruptedAndFailedCommandsRemainRetryableButConflictsDoNotBlockConvergence() {
        val processing = command("PROCESSING", attempts = 1)
        val failed = command("FAILED", attempts = 2)
        val conflict = command("BLOCKED_CONFLICT", attempts = 1)

        assertTrue(UnattendedSyncPolicy.isRetryable(processing))
        assertTrue(UnattendedSyncPolicy.isRetryable(failed))
        assertFalse(UnattendedSyncPolicy.isRetryable(conflict))
        assertEquals(2, UnattendedSyncPolicy.outstandingCount(listOf(processing, failed, conflict)))
        assertTrue(UnattendedSyncPolicy.workerShouldRetry(true, listOf(failed)))
        assertEquals("ItemsNeedReview", UnattendedSyncPolicy.completionStatus(1, 0))
        assertEquals("SavedRetrying", UnattendedSyncPolicy.completionStatus(0, 1))
        assertEquals("Synced", UnattendedSyncPolicy.completionStatus(0, 0))
    }

    @Test fun foregroundSyncIsOnlyRequestedForCompletedTrustedAuthentication() {
        assertTrue(UnattendedSyncPolicy.shouldRequestForegroundSync(true, "google", true))
        assertFalse(UnattendedSyncPolicy.shouldRequestForegroundSync(true, "protected_local", true))
        assertFalse(UnattendedSyncPolicy.shouldRequestForegroundSync(true, "offline", false))
        assertFalse(UnattendedSyncPolicy.shouldRequestForegroundSync(false, "google", true))
    }

    @Test fun interruptedProcessingCommandIsEligibleImmediatelyAfterRelaunch() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, StrengthDatabase::class.java)
            .allowMainThreadQueries().build()
        val dao = database.strengthDao()
        val queued = command("PROCESSING", attempts = 1)
        dao.enqueueCommand(queued)

        assertTrue(dao.getPendingCommands(System.currentTimeMillis()).any { it.commandId == queued.commandId })
        database.close()
    }

    @Test fun authenticatedConnectivityPresentationNeverAsksForOfflineModeOrManualSync() {
        val auth = AuthState.Authenticated(UserProfile(id = "uid", humanUserId = "human_12345678901234567890123456789012"))
        val waiting = syncPresentation(auth, "WaitingForConnection", 1, null)
        val retrying = syncPresentation(auth, "SavedRetrying", 1, "temporary")
        val syncing = syncPresentation(auth, "Synchronizing", 1, null)
        val synced = syncPresentation(auth, "Synced", 0, null)

        assertEquals("Saved on this phone", waiting.title)
        assertTrue(waiting.detail.contains("automatically"))
        assertNull(waiting.actionLabel)
        assertTrue(retrying.detail.contains("try again automatically"))
        assertEquals("Synchronizing", syncing.title)
        assertEquals("Synced", synced.title)
    }

    @Test fun conflictAndDifferentAccountActionsPreserveTheirDistinctMeaning() {
        val conflict = signInDialogCopy(AuthErrorKind.DATA_CONFLICT, "ignored")
        val other = signInDialogCopy(AuthErrorKind.DIFFERENT_ACCOUNT, "ignored")
        val disconnected = signInDialogCopy(AuthErrorKind.NETWORK, "ignored")

        assertEquals(listOf("Review items", "Continue", "Sign out"), conflict.actions)
        assertEquals(listOf("Open the local profile", "Export its data", "Sign out"), other.actions)
        assertEquals("Connection needed to sign in", disconnected.title)
        assertEquals(listOf("Try again", "Continue without an account", "Cancel"), disconnected.actions)
    }

    @Test fun protectedLocalProfileOpensAppWithoutBecomingAuthenticatedCloudOwner() {
        val profile = UserProfile(id = "local-owner", humanUserId = "human_localowner000000000000000000000")
        val state = AuthState.ProtectedLocal(profile)

        assertEquals(StartupDestination.FullApp, resolveStartupDestination(state, com.example.billing.AppAccessState.Initializing))
        val presentation = syncPresentation(state, "Synced", 0, null)
        assertEquals("Saved on this phone", presentation.title)
        assertTrue(presentation.detail.contains("different account"))
    }

    private fun command(status: String, attempts: Int) = CommandQueueEntity(
        commandId = "cmd-${status.lowercase()}-$attempts",
        humanUserId = "human_12345678901234567890123456789012",
        commandType = "BodyWeightUpdated",
        entityType = "BODY_WEIGHT",
        entityGlobalId = "weight-1",
        payloadJson = "{}",
        status = status,
        attempts = attempts
    )
}
