package com.example

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.identity.DeviceIdGenerator
import com.example.core.identity.HumanUserIdGenerator
import com.example.data.*
import com.example.ui.viewmodel.*
import kotlinx.coroutines.flow.first
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
class Sprint2ViewModelTest {

    private lateinit var context: Context
    private lateinit var database: StrengthDatabase
    private lateinit var repository: StrengthRepository
    private lateinit var authViewModel: AuthViewModel
    private lateinit var activeWorkoutViewModel: ActiveWorkoutViewModel
    private lateinit var profileViewModel: ProfileViewModel
    private lateinit var routineViewModel: RoutineViewModel
    private lateinit var historyViewModel: HistoryViewModel

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
        repository = StrengthRepository(database.strengthDao())
        
        authViewModel = AuthViewModel(repository, context)
        profileViewModel = ProfileViewModel(repository, context, authViewModel)
        routineViewModel = RoutineViewModel(repository, context, authViewModel)
        activeWorkoutViewModel = ActiveWorkoutViewModel(repository, context, authViewModel)
        historyViewModel = HistoryViewModel(repository, context, authViewModel, routineViewModel)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testAuthViewModel_initialStateAndSignIn() {
        assertEquals("offline", authViewModel.activeUserId.value)
        assertEquals(AuthState.Initial, authViewModel.authState.value)

        runBlocking {
            authViewModel.authRepository.signInAnonymously()
        }
        waitUntil { authViewModel.authState.value is AuthState.Offline }
        assertEquals("offline", authViewModel.activeUserId.value)
        assertEquals(AuthState.Offline, authViewModel.authState.value)
    }

    @Test
    fun testActiveWorkoutViewModel_backupRestoreAndStateTransition() {
        // Initially empty state
        assertNull(activeWorkoutViewModel.activeWorkoutState.value)

        // Start workout
        activeWorkoutViewModel.startWorkout(null)
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value != null }
        assertEquals("Custom Workout", activeWorkoutViewModel.activeWorkoutState.value?.templateName)

        // Add exercise
        val benchPress = Exercise("bench_press", "Bench Press", "Chest")
        activeWorkoutViewModel.addExerciseToActiveWorkout(benchPress)
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value?.exercises?.size == 1 }

        // Add a set
        activeWorkoutViewModel.addSetToExercise("bench_press")
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value?.sets?.get("bench_press")?.size == 2 }

        val stateAfterSet = activeWorkoutViewModel.activeWorkoutState.value
        assertNotNull(stateAfterSet)
        val benchPressSets = stateAfterSet?.sets?.get("bench_press")
        assertNotNull(benchPressSets)
        assertEquals(2, benchPressSets!!.size)

        // Cancel workout clears the state
        activeWorkoutViewModel.cancelActiveWorkout()
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value == null }
    }

    @Test
    fun testProfileViewModel_logAndRestoreBodyWeight() {
        var backupWeight: BodyWeight? = null
        val onUndoBackup: (BodyWeight) -> Unit = { backupWeight = it }

        // Initial check
        waitUntil {
            runBlocking { profileViewModel.bodyWeights.first().isEmpty() }
        }

        // Log weight
        profileViewModel.logBodyWeight(80.5f, 15.0f, System.currentTimeMillis(), onUndoBackup)
        waitUntil {
            runBlocking { profileViewModel.bodyWeights.first().size == 1 }
        }
        
        val weights = runBlocking { profileViewModel.bodyWeights.first() }
        assertEquals(80.5f, weights.first().weight)
        assertEquals(15.0f, weights.first().bodyFat)

        // Verify we got the backup item for undo
        idleLooper()
        assertNotNull(backupWeight)
        assertEquals(80.5f, backupWeight!!.weight)

        // Delete body weight
        profileViewModel.deleteBodyWeight(weights.first().id) {
            backupWeight = it
        }
        waitUntil {
            runBlocking { profileViewModel.bodyWeights.first().isEmpty() }
        }
    }

    @Test
    fun testRoutineViewModel_customExerciseLifecycle() {
        waitUntil {
            runBlocking { routineViewModel.exercises.first().none { it.name == "My Extra Exercise" } }
        }

        // Create Exercise
        routineViewModel.createCustomExercise("My Extra Exercise", "Legs")
        waitUntil {
            runBlocking { routineViewModel.exercises.first().any { it.name == "My Extra Exercise" } }
        }
        
        val matching = runBlocking { routineViewModel.exercises.first() }.filter { it.name == "My Extra Exercise" }
        assertEquals(1, matching.size)
        assertEquals("Legs", matching.first().category)
        assertTrue(matching.first().isCustom)

        // Delete Exercise
        routineViewModel.deleteCustomExercise(matching.first().id)
        waitUntil {
            runBlocking { routineViewModel.exercises.first().none { it.name == "My Extra Exercise" } }
        }
    }

    @Test
    fun testHistoryViewModel_enrichedSession() {
        val session = WorkoutSession(
            id = 0,
            templateId = 1,
            templateName = "History Test Workout",
            userId = "offline",
            startTime = System.currentTimeMillis() - 1000000,
            endTime = System.currentTimeMillis()
        )
        val sId = runBlocking { repository.insertSession(session) }

        val set1 = LoggedSet(
            id = 0,
            sessionId = sId.toInt(),
            exerciseId = "bench_press",
            setNumber = 1,
            reps = 10,
            weight = 100f,
            isCompleted = true
        )
        runBlocking { repository.insertLoggedSet(set1) }

        waitUntil {
            runBlocking { historyViewModel.filteredSessions.first().any { it.session.id == sId.toInt() } }
        }

        val enrichedList = runBlocking { historyViewModel.filteredSessions.first() }
        val matchedEnriched = enrichedList.first { it.session.id == sId.toInt() }
        assertEquals("History Test Workout", matchedEnriched.session.templateName)
        assertEquals(1000f, matchedEnriched.totalVolume)
        assertEquals(1, matchedEnriched.sets.size)
    }
}
