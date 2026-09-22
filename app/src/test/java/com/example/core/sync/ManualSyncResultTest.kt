package com.example.core.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ManualSyncResultTest {
    @Test
    fun currentRequestCompletesPersistentlyWithCountsAndAttention() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("sync_check_result", Context.MODE_PRIVATE).edit().clear().commit()
        assertTrue(SyncManager.beginManualCheck(context, "request-1", 100L))
        assertFalse(SyncManager.beginManualCheck(context, "request-2", 101L))
        assertTrue(SyncManager.startManualCheck(context, "request-1", 110L))

        SyncManager.updateCurrentRunCounts(downloaded = 3, uploaded = 2, attentionCount = 1)
        assertEquals(SyncRunCounts(3, 2, 1), SyncManager.currentRunCounts())
        SyncManager.completeManualCheck(context, "request-2", 9, 9, 0, false, null, null, 199L)
        assertEquals("CHECKING", SyncManager.manualResult.value.phase)

        SyncManager.completeManualCheck(context, "request-1", 3, 2, 1, false, null, null, 200L)
        assertEquals(ManualSyncResult(phase = "ATTENTION", requestId = "request-1", requestedAt = 100L,
            startedAt = 110L, completedAt = 200L, lastSuccessfulCompletedAt = 200L,
            downloaded = 3, uploaded = 2, attentionCount = 1, deterministicAttention = true),
            SyncManager.manualResult.value)
        val stored = context.getSharedPreferences("sync_check_result", Context.MODE_PRIVATE)
        assertEquals("ATTENTION", stored.getString("phase", null))
        assertEquals(200L, stored.getLong("completed_at", 0L))
        assertEquals(1, stored.getInt("attention_count", 0))
        assertEquals("request-1", stored.getString("request_id", null))
    }

    @Test
    fun failureDoesNotReplaceLastSuccessfulCompletion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val previous = SyncManager.manualResult.value.lastSuccessfulCompletedAt
        assertTrue(SyncManager.beginManualCheck(context, "request-failure", 300L))
        assertTrue(SyncManager.startManualCheck(context, "request-failure", 310L))
        SyncManager.completeManualCheck(context, "request-failure", 0, 0, 0, false,
            "temporary network failure", "TRANSIENT_FAILURE", 320L)
        val result = SyncManager.manualResult.value
        assertEquals("FAILED", result.phase)
        assertEquals(previous, result.lastSuccessfulCompletedAt)
        assertEquals("TRANSIENT_FAILURE", result.errorClassification)
    }
}
