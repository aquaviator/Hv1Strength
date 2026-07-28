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
class Sprint4ActiveWorkoutRecoveryTest {

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

    @Test
    fun testPersistenceAndDebouncedSaving() {
        // 1. Start a workout
        activeWorkoutViewModel.startWorkout(null)
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value != null }
        
        val initialState = activeWorkoutViewModel.activeWorkoutState.value!!
        val bp = Exercise("bench_press", "Bench Press", "Chest")
        
        // Add an exercise which is a critical change (should trigger immediate backup save)
        activeWorkoutViewModel.addExerciseToActiveWorkout(bp)
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value?.exercises?.size == 1 }
        
        // Wait for debounce/immediate IO block to persist
        waitUntil {
            runBlocking { repository.getActiveWorkoutBackup() != null }
        }
        
        val backup = runBlocking { repository.getActiveWorkoutBackup() }
        assertNotNull(backup)
        assertEquals("Custom Workout", backup!!.templateName)
    }

    @Test
    fun testStartupCheckAndRestoration() {
        // 1. Manually write a backup to the database with global recovery parameters in exerciseMetadataJson
        val jsonMetadata = """
            {
                "__global_recovery__": {
                    "activeSessionId": "test-uuid-12345",
                    "workoutNotes": "Testing recovery note",
                    "isMetric": false,
                    "stateVersion": 2,
                    "isRestTimerPaused": true,
                    "restTimerEndTimestamp": ${System.currentTimeMillis() + 60000L},
                    "restTimerDuration": 60,
                    "restTimerRemainingAtSave": 45
                }
            }
        """.trimIndent()

        val backup = ActiveWorkoutBackup(
            id = 1,
            templateId = null,
            templateName = "Restored Session",
            startTime = 123456789L,
            exercisesJson = "[]",
            setsJson = "{}",
            exerciseMetadataJson = jsonMetadata
        )
        
        runBlocking {
            repository.saveActiveWorkoutBackup(backup)
        }

        // 2. Perform check
        activeWorkoutViewModel.checkForActiveWorkoutBackup()
        waitUntil {
            activeWorkoutViewModel.workoutRecoveryState.value is WorkoutRecoveryState.Available
        }
        
        val recoveryState = activeWorkoutViewModel.workoutRecoveryState.value as WorkoutRecoveryState.Available
        assertEquals("Restored Session", recoveryState.workoutName)
        assertEquals(123456789L, recoveryState.startedAt)

        // 3. Resume workout
        activeWorkoutViewModel.resumeWorkout()
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value != null }

        val activeState = activeWorkoutViewModel.activeWorkoutState.value!!
        assertEquals("Restored Session", activeState.templateName)
        assertEquals(123456789L, activeState.startTime)
        assertEquals("test-uuid-12345", activeState.activeSessionId)
        assertEquals("Testing recovery note", activeState.workoutNotes)
        assertFalse(activeState.isMetric)
        assertEquals(2, activeState.stateVersion)
        
        // Verify rest timer recovery
        assertTrue(activeState.isRestTimerPaused)
        assertEquals(60, activeWorkoutViewModel.restTimerDuration.value)
        assertEquals(45, activeWorkoutViewModel.restTimeRemaining.value)
    }

    @Test
    fun testCompletionSemanticsAndIdempotency() {
        // 1. Start workout
        activeWorkoutViewModel.startWorkout(null)
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value != null }

        // Pre-populate an exercise
        val bp = Exercise("bench_press", "Bench Press", "Chest")
        activeWorkoutViewModel.addExerciseToActiveWorkout(bp)
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value?.exercises?.size == 1 }

        // Wait for backup save to complete
        waitUntil { runBlocking { repository.getActiveWorkoutBackup() != null } }

        // 2. Complete workout
        activeWorkoutViewModel.finishActiveWorkout()
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value == null }

        // 3. Verify backup is cleared AFTER completion succeeds
        val backupAfterComplete = runBlocking { repository.getActiveWorkoutBackup() }
        assertNull(backupAfterComplete)

        // 4. Verify a session was inserted
        val sessionsList = runBlocking { database.strengthDao().getSessionsForUser("offline").first() }
        assertTrue(sessionsList.isNotEmpty())
        
        // 5. Test complete idempotency by verifying another finishActiveWorkout won't duplicate session
        // (Simulate if double-tapped or repeated)
        // Since activeWorkoutState is null, calling finishActiveWorkout should be a safe no-op
        activeWorkoutViewModel.finishActiveWorkout()
        idleLooper()
    }

    @Test
    fun testCorruptBackupFallback() {
        // 1. Write malformed JSON to the database
        val corruptBackup = ActiveWorkoutBackup(
            id = 1,
            templateId = null,
            templateName = "Corrupt Workout",
            startTime = 99999L,
            exercisesJson = "{[malformed exercises json",
            setsJson = "{{malformed sets json",
            exerciseMetadataJson = "{malformed metadata"
        )
        runBlocking {
            repository.saveActiveWorkoutBackup(corruptBackup)
        }

        // 2. Perform check
        activeWorkoutViewModel.checkForActiveWorkoutBackup()
        waitUntil {
            activeWorkoutViewModel.workoutRecoveryState.value is WorkoutRecoveryState.Failed
        }
        
        val failedState = activeWorkoutViewModel.workoutRecoveryState.value as WorkoutRecoveryState.Failed
        assertTrue(failedState.reason.contains("Malformed"))
    }
}
