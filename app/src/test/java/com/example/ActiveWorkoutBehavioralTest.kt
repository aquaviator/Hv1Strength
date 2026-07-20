package com.example

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.identity.DeviceIdGenerator
import com.example.core.identity.HumanUserIdGenerator
import com.example.data.*
import com.example.ui.viewmodel.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ActiveWorkoutBehavioralTest {

    private lateinit var context: Context
    private lateinit var database: StrengthDatabase
    private lateinit var repository: StrengthRepository
    private lateinit var authViewModel: AuthViewModel
    private lateinit var activeWorkoutViewModel: ActiveWorkoutViewModel

    private fun idleLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun waitUntil(timeoutMs: Long = 3000, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            idleLooper()
            if (condition()) return
            Thread.sleep(10)
        }
        fail("Condition not met within $timeoutMs ms")
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DeviceIdGenerator.appContext = context
        HumanUserIdGenerator.appContext = context

        database = Room.inMemoryDatabaseBuilder(context, StrengthDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = StrengthRepository(database.strengthDao(), context)

        authViewModel = AuthViewModel(repository, context)
        activeWorkoutViewModel = ActiveWorkoutViewModel(repository, context, authViewModel)
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * Test 1: Complete execution queue traversal (each set transition)
     */
    @Test
    fun testExecutionQueueTraversalAndSetCompletion() {
        // Start workout
        activeWorkoutViewModel.startWorkout(null)
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value != null }

        // Add an exercise with 3 sets
        val ex = Exercise("squats", "Squats", "Legs")
        activeWorkoutViewModel.addExerciseToActiveWorkout(ex)
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value?.exercises?.size == 1 }

        val activeExId = "squats"
        activeWorkoutViewModel.addSetToExercise(activeExId)
        activeWorkoutViewModel.addSetToExercise(activeExId) // total 3 sets

        // Traversal of sets: verify active indices
        val queue1 = activeWorkoutViewModel.executionQueue.value
        assertEquals(3, queue1.size)
        assertFalse(queue1[0].set.isCompleted)
        assertFalse(queue1[1].set.isCompleted)
        assertFalse(queue1[2].set.isCompleted)

        // Complete Set 1
        activeWorkoutViewModel.updateSet(
            exerciseId = activeExId,
            setIndex = 0,
            reps = 10,
            weight = 100f,
            isCompleted = true
        )
        idleLooper()

        val queue2 = activeWorkoutViewModel.executionQueue.value
        assertTrue(queue2[0].set.isCompleted)
        assertFalse(queue2[1].set.isCompleted)
    }

    /**
     * Test 2: Interactive weight and rep selection
     */
    @Test
    fun testWeightAndRepSelection() {
        activeWorkoutViewModel.startWorkout(null)
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value != null }

        val ex = Exercise("deadlift", "Deadlift", "Legs")
        activeWorkoutViewModel.addExerciseToActiveWorkout(ex)
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value?.exercises?.size == 1 }

        // Set weight and reps for the first set
        activeWorkoutViewModel.updateSet(
            exerciseId = "deadlift",
            setIndex = 0,
            reps = 5,
            weight = 140f,
            isCompleted = false
        )
        idleLooper()

        val firstSet = activeWorkoutViewModel.activeWorkoutState.value!!.sets["deadlift"]!![0]
        assertEquals(5, firstSet.reps)
        assertEquals(140f, firstSet.weight)
    }

    /**
     * Test 3: Rest timer triggering and adjustment (+15s, -15s, skip)
     */
    @Test
    fun testRestTimerTriggeringAndAdjustment() {
        activeWorkoutViewModel.startWorkout(null)
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value != null }

        val ex = Exercise("bench", "Bench Press", "Chest")
        activeWorkoutViewModel.addExerciseToActiveWorkout(ex)
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value?.exercises?.size == 1 }

        // Start a rest timer of 60 seconds
        activeWorkoutViewModel.startRestTimer(60)
        assertEquals(60, activeWorkoutViewModel.restTimerDuration.value)
        assertEquals(60, activeWorkoutViewModel.restTimeRemaining.value)

        // Add 15s rest time
        activeWorkoutViewModel.addRestTime(15)
        assertEquals(75, activeWorkoutViewModel.restTimeRemaining.value)

        // Reduce 15s rest time
        activeWorkoutViewModel.reduceRestTime(15)
        assertEquals(60, activeWorkoutViewModel.restTimeRemaining.value)

        // Skip rest timer
        activeWorkoutViewModel.skipRestTimer()
        assertNull(activeWorkoutViewModel.restTimeRemaining.value)
    }

    /**
     * Test 4: Superset round progression (Round-robin routing)
     */
    @Test
    fun testSupersetRoundProgression() = runBlocking {
        // Start workout with template
        val template = WorkoutTemplate(
            id = 100,
            name = "Superset Template",
            exerciseIdsJson = """["bench", "row"]""",
            userId = "offline"
        )
        database.strengthDao().insertTemplate(template)

        val ex1 = Exercise("bench", "Bench Press", "Chest")
        val ex2 = Exercise("row", "Barbell Row", "Back")
        database.strengthDao().insertExercises(listOf(ex1, ex2))

        val te1 = WorkoutTemplateExercise(
            id = 1,
            templateId = 100,
            exerciseId = "bench",
            position = 1,
            restSeconds = 60,
            supersetGroupId = "SG1"
        )
        val te2 = WorkoutTemplateExercise(
            id = 2,
            templateId = 100,
            exerciseId = "row",
            position = 2,
            restSeconds = 60,
            supersetGroupId = "SG1"
        )
        database.strengthDao().insertTemplateExercises(listOf(te1, te2))

        val ts1 = WorkoutTemplateSet(
            id = 1,
            templateExerciseId = 1,
            position = 1,
            setType = "WORKING",
            targetRepsMin = 8,
            targetWeight = 80f
        )
        val ts2 = WorkoutTemplateSet(
            id = 2,
            templateExerciseId = 2,
            position = 1,
            setType = "WORKING",
            targetRepsMin = 8,
            targetWeight = 70f
        )
        database.strengthDao().insertTemplateSets(listOf(ts1, ts2))

        // Start workout from this template
        activeWorkoutViewModel.startWorkout(template)
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value != null }

        // Traversal verification: Bench Press Set 1 -> Row Set 1
        val queue = activeWorkoutViewModel.executionQueue.value
        assertEquals("bench", queue[0].exercise.id)
        assertEquals("row", queue[1].exercise.id)
    }

    /**
     * Test 5: Finish flow (confirmation and save)
     */
    @Test
    fun testFinishWorkoutFlow() {
        activeWorkoutViewModel.startWorkout(null)
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value != null }

        val ex = Exercise("curls", "Bicep Curls", "Arms")
        activeWorkoutViewModel.addExerciseToActiveWorkout(ex)
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value?.exercises?.size == 1 }

        // Save some backup
        waitUntil { runBlocking { repository.getActiveWorkoutBackup() != null } }

        // Finish the active workout
        activeWorkoutViewModel.finishActiveWorkout()
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value == null }

        // Backup must be destroyed on finish success
        val backup = runBlocking { repository.getActiveWorkoutBackup() }
        assertNull(backup)

        // Session must be persisted in database
        val sessions = runBlocking { database.strengthDao().getSessionsForUser("offline").first() }
        assertTrue(sessions.isNotEmpty())
    }

    /**
     * Test 6: Discard flow (destruction)
     */
    @Test
    fun testDiscardWorkoutFlow() {
        activeWorkoutViewModel.startWorkout(null)
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value != null }

        val ex = Exercise("curls", "Bicep Curls", "Arms")
        activeWorkoutViewModel.addExerciseToActiveWorkout(ex)
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value?.exercises?.size == 1 }

        // Save backup
        waitUntil { runBlocking { repository.getActiveWorkoutBackup() != null } }

        // Discard the active workout
        activeWorkoutViewModel.discardWorkoutBackup()
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value == null }

        // Backup must be deleted from database
        val backup = runBlocking { repository.getActiveWorkoutBackup() }
        assertNull(backup)
    }

    /**
     * Test 7: Legacy format compatibility and successful recovery
     */
    @Test
    fun testLegacyFormatCompatibilityAndRecovery() {
        // Create backup representing legacy JSON (no metadata / empty metadata)
        val legacyBackup = ActiveWorkoutBackup(
            id = 1,
            templateId = null,
            templateName = "Legacy Workout",
            startTime = 987654321L,
            exercisesJson = "[]",
            setsJson = "{}",
            exerciseMetadataJson = "{}"
        )

        runBlocking {
            repository.saveActiveWorkoutBackup(legacyBackup)
        }

        // Perform recovery check
        activeWorkoutViewModel.checkForActiveWorkoutBackup()
        waitUntil {
            activeWorkoutViewModel.workoutRecoveryState.value is WorkoutRecoveryState.Available
        }

        val available = activeWorkoutViewModel.workoutRecoveryState.value as WorkoutRecoveryState.Available
        assertEquals("Legacy Workout", available.workoutName)

        // Resume workout
        activeWorkoutViewModel.resumeWorkout()
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value != null }

        val restoredState = activeWorkoutViewModel.activeWorkoutState.value!!
        assertEquals("Legacy Workout", restoredState.templateName)
        assertEquals(987654321L, restoredState.startTime)
        // Check recovered default values safely
        assertTrue(restoredState.isMetric)
        assertEquals(1, restoredState.stateVersion)
        assertFalse(restoredState.isRestTimerPaused)
    }
}
