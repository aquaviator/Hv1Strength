package com.example.service.workout

import com.example.data.ActiveWorkoutBackup
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkoutExecutionModelsTest {
    private fun backup(recovery: String = """{"activeSessionId":"session-1","currentExerciseId":"squat","restTimerEndTimestamp":106000,"isRestTimerPaused":false}""") = ActiveWorkoutBackup(
        templateName = "Leg day", startTime = 10_000L,
        exercisesJson = """[{"id":"squat","name":"Barbell Squat","category":"Legs","isCustom":false}]""",
        setsJson = """{"squat":[{"isCompleted":true},{"isCompleted":false}]}""",
        exerciseMetadataJson = """{"__global_recovery__":$recovery}"""
    )

    @Test fun parsesNotificationStateFromDurableBackup() {
        val state = WorkoutExecutionParser.parse(backup())
        assertEquals("session-1", state.sessionId); assertEquals("Barbell Squat", state.currentExercise)
        assertEquals(1, state.completedSets); assertEquals(2, state.totalSets)
    }

    @Test fun elapsedTimeIsTimestampBasedAndNeverNegative() {
        val state = WorkoutExecutionParser.parse(backup())
        assertEquals(90, state.elapsedSeconds(100_000L)); assertEquals(0, state.elapsedSeconds(1L))
    }

    @Test fun restTimerRestoresAddsAndReducesFromTargetTimestamp() {
        val state = WorkoutExecutionParser.parse(backup())
        assertEquals(6, state.restRemainingSeconds(100_000L))
        assertEquals(21, state.copy(restEndsAt = state.restEndsAt!! + 15_000L).restRemainingSeconds(100_000L))
        assertEquals(1, state.copy(restEndsAt = state.restEndsAt!! - 5_000L).restRemainingSeconds(100_000L))
    }

    @Test fun completedAndPausedRestTimersDoNotTick() {
        val state = WorkoutExecutionParser.parse(backup())
        assertEquals(0, state.restRemainingSeconds(200_000L))
        assertNull(state.copy(restPaused = true).restRemainingSeconds(100_000L))
    }

    @Test fun legacyBackupGetsStableProcessRestorationIdentity() {
        val legacy = WorkoutExecutionParser.parse(backup("{}"))
        assertEquals("legacy-10000", legacy.sessionId); assertEquals("Barbell Squat", legacy.currentExercise)
    }

    @Test(expected = org.json.JSONException::class)
    fun corruptBackupFailsClosed() {
        WorkoutExecutionParser.parse(backup().copy(setsJson = "not-json"))
    }

    @Test fun serviceStartsOnlyWithMatchingActiveBackupAndDuplicateStartIsIdempotent() {
        val snapshot = WorkoutExecutionParser.parse(backup()); val gate = WorkoutServiceSessionGate()
        assertFalse(gate.accept("session-1", null))
        assertFalse(gate.accept("different", snapshot))
        assertTrue(gate.accept("session-1", snapshot))
        assertTrue(gate.accept("session-1", snapshot))
        assertEquals("session-1", gate.activeSessionId)
    }

    @Test fun completionOrDiscardStopsWhenBackupDisappears() {
        val gate = WorkoutServiceSessionGate(); val snapshot = WorkoutExecutionParser.parse(backup())
        assertTrue(gate.accept("session-1", snapshot)); assertFalse(gate.accept("session-1", null))
    }
}
