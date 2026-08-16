package com.example

import com.example.core.sync.SyncEngineImpl
import com.example.ui.presentation.toConflictSummary
import com.example.data.Exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncConflictClassificationTest {
    @Test fun equalRevisionConcurrentEditsFromDifferentDevicesConflict() {
        assertTrue(SyncEngineImpl.isProvenConcurrentEdit(3, 100, "phone-a", 3, 200, "phone-b"))
    }

    @Test fun ordinaryNewerRemoteAndSameOriginDoNotConflict() {
        assertFalse(SyncEngineImpl.isProvenConcurrentEdit(2, 100, "phone-a", 3, 200, "phone-b"))
        assertFalse(SyncEngineImpl.isProvenConcurrentEdit(3, 100, "phone-a", 3, 200, "phone-a"))
        assertFalse(SyncEngineImpl.isProvenConcurrentEdit(3, 100, "", 3, 200, "phone-b"))
    }

    @Test fun privacySafeSummaryContainsOnlyRecordMetadata() {
        val item = Exercise(
            id = "local-id", name = "My press", category = "Chest", isCustom = true,
            globalId = "stable-reference", humanUserId = "human_private",
            updatedAt = 100, syncStatus = "CONFLICT",
            conflictState = SyncEngineImpl.conflictMetadata(100, 200)
        ).toConflictSummary()
        assertEquals("Custom exercise", item.entityType)
        assertEquals("stable-reference", item.entityReference)
        assertEquals(200L, item.onlineModifiedAt)
        assertFalse(item.toString().contains("human_private"))
        assertNull(Exercise("x", "X", "Other", true, conflictState = "old diagnostic").toConflictSummary().onlineModifiedAt)
    }
}
