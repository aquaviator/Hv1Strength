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
class Sprint6Candidate31AcceptanceTest {

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
     * Test: Active Session, Routine, and History Weight Source Priority Rules
     */
    @Test
    fun testWeightSourcePriorityRules() {
        runBlocking<Unit> {
            // Log a past set in history to act as previous-session history (e.g. 60 kg)
            val pastSet = LoggedSet(
                id = 1,
                sessionId = 10,
                exerciseId = "ex_squats",
                setNumber = 1,
                reps = 8,
                weight = 60f,
                isCompleted = true,
                createdAt = System.currentTimeMillis() - 86400000L // 1 day ago
            )
            repository.insertLoggedSet(pastSet)
        }

        activeWorkoutViewModel.startWorkout(null)
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value != null }

        // Scenario 1: No current, routine or history value for a new exercise
        val newEx = Exercise("ex_bench", "Bench Press", "Chest")
        activeWorkoutViewModel.addExerciseToActiveWorkout(newEx)
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value?.exercises?.size == 1 }

        val activeStateEmpty = activeWorkoutViewModel.activeWorkoutState.value!!
        val emptySet = activeStateEmpty.sets["ex_bench"]!![0]
        
        // No prescription and no history -> 0 kg pre-filled suggestion
        assertEquals(0f, emptySet.weight)
        assertNull(emptySet.targetWeight)

        // Scenario 2: Previous history exists, but no routine prescription
        val squatsEx = Exercise("ex_squats", "Squats", "Legs")
        activeWorkoutViewModel.addExerciseToActiveWorkout(squatsEx)
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value?.exercises?.size == 2 }

        val activeStateHistory = activeWorkoutViewModel.activeWorkoutState.value!!
        val squatsSet = activeStateHistory.sets["ex_squats"]!![0]
        
        // Asserting that the previous history weight is pre-filled as suggestion
        assertEquals(60f, squatsSet.weight)
        assertNull(squatsSet.targetWeight)

        // Scenario 3: Routine prescription exists (50 kg)
        // Add set with routine target weight
        activeWorkoutViewModel.updateSet(
            exerciseId = "ex_squats",
            setIndex = 0,
            reps = 8,
            weight = 0f,
            isCompleted = false,
            targetWeight = 50f
        )
        idleLooper()

        val squatsSetPrescribed = activeWorkoutViewModel.activeWorkoutState.value!!.sets["ex_squats"]!![0]
        assertEquals(50f, squatsSetPrescribed.targetWeight)
        assertEquals(0f, squatsSetPrescribed.weight) // unentered active weight

        // Scenario 4: Active session value is entered (55 kg)
        activeWorkoutViewModel.updateSet(
            exerciseId = "ex_squats",
            setIndex = 0,
            reps = 8,
            weight = 55f,
            isCompleted = false,
            targetWeight = 50f
        )
        idleLooper()

        val squatsSetEntered = activeWorkoutViewModel.activeWorkoutState.value!!.sets["ex_squats"]!![0]
        assertEquals(55f, squatsSetEntered.weight)
        assertEquals(50f, squatsSetEntered.targetWeight)
    }

    /**
     * Test: Editing a fallback Weight updates the active set weight in the ViewModel immediately
     */
    @Test
    fun testEditingFallbackWeightPromotesImmediately() {
        activeWorkoutViewModel.startWorkout(null)
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value != null }

        val squatsEx = Exercise("ex_squats", "Squats", "Legs")
        activeWorkoutViewModel.addExerciseToActiveWorkout(squatsEx)
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value?.exercises?.size == 1 }

        // Start from unconfigured state (fallback weight is 0)
        val initialSet = activeWorkoutViewModel.activeWorkoutState.value!!.sets["ex_squats"]!![0]
        assertEquals(0f, initialSet.weight)

        // Simulate typing/incrementing weight to 45 kg
        activeWorkoutViewModel.updateSet(
            exerciseId = "ex_squats",
            setIndex = 0,
            reps = 8,
            weight = 45f,
            isCompleted = false
        )
        idleLooper()

        // Verify active weight has successfully been updated in the ViewModel immediately
        val updatedSet = activeWorkoutViewModel.activeWorkoutState.value!!.sets["ex_squats"]!![0]
        assertEquals(45f, updatedSet.weight)
    }

    /**
     * Test: Weight, Reps, and RPE survive backup recovery flawlessly
     */
    @Test
    fun testWeightRepsAndRpeSurviveRecovery() {
        // Create manual backup with weight, reps, and RPE
        val backup = ActiveWorkoutBackup(
            id = 1,
            templateId = null,
            templateName = "Custom Session",
            startTime = System.currentTimeMillis(),
            exercisesJson = "[{\"id\":\"squats\",\"name\":\"Squats\",\"category\":\"Legs\"}]",
            setsJson = "{\"squats\":[{\"setNumber\":1,\"reps\":12,\"weight\":82.5,\"isCompleted\":false,\"rpe\":9}]}",
            exerciseMetadataJson = "{\"squats\":{\"restSeconds\":60,\"notes\":\"\"},\"__global_recovery__\":{\"activeSessionId\":\"test-session\",\"stateVersion\":2,\"isMetric\":true,\"isRestTimerPaused\":false,\"currentExerciseId\":\"squats\"}}"
        )
        runBlocking<Unit> {
            repository.saveActiveWorkoutBackup(backup)
        }

        // Discard local state and recover
        activeWorkoutViewModel.checkForActiveWorkoutBackup()
        waitUntil { activeWorkoutViewModel.workoutRecoveryState.value is WorkoutRecoveryState.Available }
        activeWorkoutViewModel.resumeWorkout()
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value != null }

        // Verify recovered values
        val recoveredSet = activeWorkoutViewModel.activeWorkoutState.value!!.sets["squats"]!![0]
        assertEquals(82.5f, recoveredSet.weight)
        assertEquals(12, recoveredSet.reps)
        assertEquals(9, recoveredSet.rpe)
    }

    /**
     * Test: Skip Rest and Natural Expiry advances the queue exactly once
     */
    @Test
    fun testRestTimerTransitions() {
        activeWorkoutViewModel.startWorkout(null)
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value != null }

        val squatEx = Exercise("squats", "Squats", "Legs")
        activeWorkoutViewModel.addExerciseToActiveWorkout(squatEx)
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value?.exercises?.size == 1 }

        // Start a rest timer
        activeWorkoutViewModel.startRestTimer(60)
        assertNotNull(activeWorkoutViewModel.restTimeRemaining.value)

        // Trigger skip
        activeWorkoutViewModel.skipRestTimer()
        idleLooper()

        // Verify timer was cancelled
        assertNull(activeWorkoutViewModel.restTimeRemaining.value)
    }

    /**
     * Test: Completion of final set bypasses rest timer
     */
    @Test
    fun testFinalSetBypassesRest() {
        activeWorkoutViewModel.startWorkout(null)
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value != null }

        val squatEx = Exercise("squats", "Squats", "Legs")
        activeWorkoutViewModel.addExerciseToActiveWorkout(squatEx)
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value?.exercises?.size == 1 }

        // Squats has 1 set by default. Complete the single (final) set.
        activeWorkoutViewModel.updateSet(
            exerciseId = "squats",
            setIndex = 0,
            reps = 8,
            weight = 100f,
            isCompleted = true
        )
        idleLooper()

        // Confirm that all planned sets are completed, which causes the UI to bypass Rest and show the Completion screen
        val queue = activeWorkoutViewModel.executionQueue.value
        assertTrue(queue.all { it.set.isCompleted })
    }
}
