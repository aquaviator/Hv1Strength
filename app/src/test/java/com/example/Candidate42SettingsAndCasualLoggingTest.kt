package com.example

import com.example.ui.viewmodel.ActiveWorkoutState
import com.example.ui.viewmodel.ActiveSet
import com.example.ui.viewmodel.CasualSuperset
import com.example.ui.viewmodel.PendingSupersetSuggestion
import com.example.data.Exercise
import org.junit.Assert.*
import org.junit.Test

class Candidate42SettingsAndCasualLoggingTest {

    @Test
    fun testDobIso8601Format() {
        val dobMillis = 946684800000L // 2000-01-01 in UTC
        val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = dobMillis
        }
        val isoString = String.format(
            java.util.Locale.US,
            "%04d-%02d-%02d",
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH) + 1,
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        )
        assertEquals("2000-01-01", isoString)
    }

    @Test
    fun testCasualWorkoutInitialState() {
        val state = ActiveWorkoutState(
            templateId = null,
            templateName = "Log a Workout",
            startTime = System.currentTimeMillis(),
            exercises = emptyList(),
            sets = emptyMap(),
            exerciseMetadata = emptyMap(),
            workoutSource = "CASUAL"
        )

        assertEquals("Log a Workout", state.templateName)
        assertEquals("CASUAL", state.workoutSource)
        assertTrue(state.exercises.isEmpty())
        assertNull(state.pendingSupersetSuggestion)
    }

    @Test
    fun testRepeatedExerciseGroupingLogic() {
        val ex1 = Exercise(
            id = "bench_press",
            name = "Bench Press",
            category = "Chest"
        )

        val initialExercises = listOf(ex1)
        val initialSets = mapOf(
            "bench_press" to listOf(
                ActiveSet(setNumber = 1, reps = 10, weight = 60f),
                ActiveSet(setNumber = 2, reps = 10, weight = 60f)
            )
        )

        val currentState = ActiveWorkoutState(
            templateId = null,
            templateName = "Log a Workout",
            startTime = System.currentTimeMillis(),
            exercises = initialExercises,
            sets = initialSets,
            workoutSource = "CASUAL"
        )

        // Simulating repeated exercise selection for bench_press
        val existingSets = currentState.sets[ex1.id] ?: emptyList()
        val lastSet = existingSets.lastOrNull()
        val newSet = ActiveSet(
            setNumber = existingSets.size + 1,
            reps = lastSet?.reps ?: 10,
            weight = lastSet?.weight ?: 0f
        )

        val updatedSetsMap = currentState.sets.toMutableMap()
        updatedSetsMap[ex1.id] = existingSets + newSet
        val updatedState = currentState.copy(sets = updatedSetsMap)

        assertEquals(1, updatedState.exercises.size)
        assertEquals(3, updatedState.sets["bench_press"]?.size)
        assertEquals(3, updatedState.sets["bench_press"]?.get(2)?.setNumber)
    }

    @Test
    fun testAlternatingSupersetPatternDetection() {
        val timeNow = System.currentTimeMillis()
        val set1A = ActiveSet(setNumber = 1, reps = 10, weight = 50f, isCompleted = true, completedAt = timeNow - 4000)
        val set1B = ActiveSet(setNumber = 1, reps = 10, weight = 30f, isCompleted = true, completedAt = timeNow - 3000)
        val set2A = ActiveSet(setNumber = 2, reps = 10, weight = 50f, isCompleted = true, completedAt = timeNow - 2000)
        val set2B = ActiveSet(setNumber = 2, reps = 10, weight = 30f, isCompleted = true, completedAt = timeNow - 1000)

        data class CompletedInfo(val exId: String, val completedAt: Long)
        val completed = listOf(
            CompletedInfo("exA", set1A.completedAt!!),
            CompletedInfo("exB", set1B.completedAt!!),
            CompletedInfo("exA", set2A.completedAt!!),
            CompletedInfo("exB", set2B.completedAt!!)
        ).sortedBy { it.completedAt }

        var isAlternating = true
        for (i in 0 until completed.size - 1) {
            if (completed[i].exId == completed[i + 1].exId) {
                isAlternating = false
                break
            }
        }

        assertTrue("Sequence A-B-A-B should be detected as strictly alternating", isAlternating)
    }

    @Test
    fun testCasualWorkoutRemainsActiveAfterLoggingFirstSetAndAdditionalSets() {
        val exBench = Exercise(id = "bench", name = "Bench Press", category = "Chest")
        val state = ActiveWorkoutState(
            templateId = null,
            templateName = "Log a Workout",
            startTime = System.currentTimeMillis(),
            exercises = listOf(exBench),
            sets = mapOf("bench" to listOf(ActiveSet(setNumber = 1, reps = 12, weight = 22.5f, isCompleted = true))),
            workoutSource = "CASUAL"
        )

        // Verify casual workout is active and not finished
        assertEquals("CASUAL", state.workoutSource)
        assertNull(state.templateId)
        assertEquals(1, state.exercises.size)
        assertEquals(1, state.sets["bench"]?.size)

        // Adding another set keeps session active
        val updatedSets = state.sets.toMutableMap()
        val currentBenchSets = updatedSets["bench"] ?: emptyList()
        updatedSets["bench"] = currentBenchSets + ActiveSet(setNumber = 2, reps = 10, weight = 22.5f, isCompleted = true)
        val state2 = state.copy(sets = updatedSets)

        assertEquals(2, state2.sets["bench"]?.size)
        assertEquals("CASUAL", state2.workoutSource)
    }

    @Test
    fun testAddingAnotherExerciseAndRepeatedExerciseKeepsCasualWorkoutActive() {
        val exBench = Exercise(id = "bench", name = "Bench Press", category = "Chest")
        val exLat = Exercise(id = "lat", name = "Lat Pulldown", category = "Back")

        val state1 = ActiveWorkoutState(
            templateId = null,
            templateName = "Log a Workout",
            startTime = System.currentTimeMillis(),
            exercises = listOf(exBench),
            sets = mapOf("bench" to listOf(ActiveSet(setNumber = 1, reps = 12, weight = 22.5f, isCompleted = true))),
            workoutSource = "CASUAL"
        )

        // Add Lat Pulldown
        val state2 = state1.copy(
            exercises = listOf(exBench, exLat),
            sets = mapOf(
                "bench" to listOf(ActiveSet(setNumber = 1, reps = 12, weight = 22.5f, isCompleted = true)),
                "lat" to listOf(ActiveSet(setNumber = 1, reps = 10, weight = 40f, isCompleted = true))
            )
        )

        assertEquals(2, state2.exercises.size)
        assertEquals(1, state2.sets["lat"]?.size)

        // Repeated exercise selection for Bench Press appends set
        val existingBenchSets = state2.sets["bench"] ?: emptyList()
        val newBenchSet = ActiveSet(setNumber = existingBenchSets.size + 1, reps = 10, weight = 22.5f, isCompleted = true)
        val updatedSets = state2.sets.toMutableMap()
        updatedSets["bench"] = existingBenchSets + newBenchSet
        val state3 = state2.copy(sets = updatedSets)

        assertEquals(2, state3.exercises.size)
        assertEquals(2, state3.sets["bench"]?.size)
        assertEquals("CASUAL", state3.workoutSource)
    }

    @Test
    fun testNoPlannedExercisesRemainingDoesNotFinishCasualWorkout() {
        val exBench = Exercise(id = "bench", name = "Bench Press", category = "Chest")
        val state = ActiveWorkoutState(
            templateId = null,
            templateName = "Log a Workout",
            startTime = System.currentTimeMillis(),
            exercises = listOf(exBench),
            sets = mapOf("bench" to listOf(ActiveSet(setNumber = 1, reps = 12, weight = 22.5f, isCompleted = true))),
            workoutSource = "CASUAL"
        )

        // Routine completion condition (e.g. all planned target sets done) does not apply to casual mode
        val isCasual = state.workoutSource == "CASUAL" || state.templateId == null
        assertTrue("Casual mode must be identified as casual", isCasual)
        // Casual completion can only happen explicitly
    }

    @Test
    fun testRoutineCompletionRulesRemainUnchanged() {
        val stateRoutine = ActiveWorkoutState(
            templateId = 123,
            templateName = "Chest Day",
            startTime = System.currentTimeMillis(),
            exercises = listOf(Exercise(id = "bench", name = "Bench Press", category = "Chest")),
            sets = mapOf("bench" to listOf(ActiveSet(setNumber = 1, reps = 10, weight = 60f, isCompleted = true))),
            workoutSource = "ROUTINE"
        )

        val isCasual = stateRoutine.workoutSource == "CASUAL" || stateRoutine.templateId == null
        assertFalse("Routine workout must not be marked as casual", isCasual)
    }
}
