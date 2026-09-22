package com.example.ui.presentation

import com.example.core.sync.ManualSyncResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualSyncPresentationTest {
    private val previous = 1_757_934_661_000L
    private val completed = 1_758_558_972_000L

    @Test fun successWithAttentionUsesLatestCompletionNotAttentionAge() {
        val text = manualSyncDetail(ManualSyncResult(phase="ATTENTION", requestId="work-46",
            requestedAt=completed-2_000, startedAt=completed-1_000, completedAt=completed,
            lastSuccessfulCompletedAt=completed, attentionCount=1, deterministicAttention=true), completed+2_000)
        assertEquals("Some items need attention · Checked just now", text)
        assertFalse(text.contains("15 Sept"))
    }

    @Test fun allPhasesHavePlainLanguageAndFailuresKeepLastSuccess() {
        assertEquals("Check queued…", manualSyncDetail(ManualSyncResult(phase="QUEUED"), completed))
        assertEquals("Checking…", manualSyncDetail(ManualSyncResult(phase="CHECKING"), completed))
        assertTrue(manualSyncDetail(ManualSyncResult(phase="UP_TO_DATE", completedAt=completed), completed).contains("just now"))
        assertTrue(manualSyncDetail(ManualSyncResult(phase="UPDATED", completedAt=completed, downloaded=2, uploaded=1), completed).contains("2 downloaded, 1 uploaded"))
        assertTrue(manualSyncDetail(ManualSyncResult(phase="OFFLINE", lastSuccessfulCompletedAt=previous), completed).contains("Last checked"))
        assertTrue(manualSyncDetail(ManualSyncResult(phase="FAILED", reason="offline", lastSuccessfulCompletedAt=previous), completed).contains("Last successful check"))
    }

    @Test fun timeFormattingIsRelativeLocaleSafeAndNeverClaimsFutureIsNow() {
        val absolute: (java.util.Date) -> String = { "ABS:${it.time}" }
        assertEquals("just now", formatSyncTime(completed, completed+5_000, absolute))
        assertEquals("45s ago", formatSyncTime(completed, completed+45_000, absolute))
        assertEquals("2m ago", formatSyncTime(completed, completed+120_000, absolute))
        assertEquals("ABS:$completed", formatSyncTime(completed, completed+3_600_000, absolute))
        assertEquals("ABS:$completed", formatSyncTime(completed, completed-1, absolute))
        assertEquals("never", formatSyncTime(null, completed, absolute))
    }
}
