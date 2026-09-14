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
    fun currentRequestCompletesPersistentlyAndRepeatedTapIsIgnored() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(SyncManager.beginManualCheck(context, "request-1", 100L))
        assertFalse(SyncManager.beginManualCheck(context, "request-2", 101L))

        SyncManager.updateCurrentRunCounts(downloaded = 3, uploaded = 2)
        assertEquals(3 to 2, SyncManager.currentRunCounts())
        SyncManager.completeManualCheck(context, "request-2", 9, 9, false, false, null, 199L)
        assertEquals("CHECKING", SyncManager.manualResult.value.phase)

        SyncManager.completeManualCheck(context, "request-1", 3, 2, false, false, null, 200L)
        assertEquals(ManualSyncResult("UPDATED", "request-1", 100L, 200L, 3, 2, null, 200L),
            SyncManager.manualResult.value)
        val stored = context.getSharedPreferences("sync_check_result", Context.MODE_PRIVATE)
        assertEquals("UPDATED", stored.getString("phase", null))
        assertEquals(200L, stored.getLong("completed_at", 0L))
    }
}
