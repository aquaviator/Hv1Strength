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
class Sprint5WorkoutGroupingAndExecutionTest {

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
    fun testDomainModels() {
        // Create TemplateExerciseStates
        val bp = StrengthViewModel.TemplateExerciseState(
            id = 1,
            exerciseId = "ex_bp",
            restSeconds = 90,
            supersetGroupId = "group_1"
        )
        val row = StrengthViewModel.TemplateExerciseState(
            id = 2,
            exerciseId = "ex_row",
            restSeconds = 90,
            supersetGroupId = "group_1"
        )
        val squat = StrengthViewModel.TemplateExerciseState(
            id = 3,
            exerciseId = "ex_squat",
            restSeconds = 120,
            supersetGroupId = null // Ungrouped
        )

        val templateExercises = listOf(bp, row, squat)
        val groups = WorkoutExecutionQueue.mapToExerciseGroups(templateExercises)

        assertEquals(2, groups.size)
        
        // Group 1: SUPERSET
        val group1 = groups[0]
        assertEquals("group_1", group1.id)
        assertEquals(WorkoutGroupType.SUPERSET, group1.type)
        assertEquals(2, group1.exercises.size)
        assertEquals("ex_bp", group1.exercises[0].exerciseId)
        assertEquals("ex_row", group1.exercises[1].exerciseId)

        // Group 2: SINGLE (Squat)
        val group2 = groups[1]
        assertEquals(WorkoutGroupType.SINGLE, group2.type)
        assertEquals(1, group2.exercises.size)
        assertEquals("ex_squat", group2.exercises[0].exerciseId)
    }

    @Test
    fun testExecutionQueueInterleaving() {
        val exBp = Exercise("ex_bp", "Bench Press", "Chest")
        val exRow = Exercise("ex_row", "Bent-Over Row", "Back")
        val exSquat = Exercise("ex_squat", "Squat", "Legs")

        val exercises = listOf(exBp, exRow, exSquat)
        
        val sets = mapOf(
            "ex_bp" to listOf(
                ActiveSet(setNumber = 1, isCompleted = false),
                ActiveSet(setNumber = 2, isCompleted = false)
            ),
            "ex_row" to listOf(
                ActiveSet(setNumber = 1, isCompleted = false),
                ActiveSet(setNumber = 2, isCompleted = false)
            ),
            "ex_squat" to listOf(
                ActiveSet(setNumber = 1, isCompleted = false)
            )
        )

        val metadata = mapOf(
            "ex_bp" to ActiveExerciseMetadata(restSeconds = 90, supersetGroupId = "group_1"),
            "ex_row" to ActiveExerciseMetadata(restSeconds = 90, supersetGroupId = "group_1"),
            "ex_squat" to ActiveExerciseMetadata(restSeconds = 120, supersetGroupId = null)
        )

        val queue = WorkoutExecutionQueue.generateQueue(exercises, sets, metadata)

        // Interleaving should be:
        // 1. group_1, ex_bp, round 1 (setIdx 0)
        // 2. group_1, ex_row, round 1 (setIdx 0)
        // 3. group_1, ex_bp, round 2 (setIdx 1)
        // 4. group_1, ex_row, round 2 (setIdx 1)
        // 5. single, ex_squat, round 1 (setIdx 0)
        assertEquals(5, queue.size)

        // Step 1
        assertEquals("group_group_1_ex_bp_0", queue[0].id)
        assertEquals("ex_bp", queue[0].exercise.id)
        assertEquals(1, queue[0].roundNumber)
        assertEquals(WorkoutGroupType.SUPERSET, queue[0].groupType)

        // Step 2
        assertEquals("group_group_1_ex_row_0", queue[1].id)
        assertEquals("ex_row", queue[1].exercise.id)
        assertEquals(1, queue[1].roundNumber)

        // Step 3
        assertEquals("group_group_1_ex_bp_1", queue[2].id)
        assertEquals("ex_bp", queue[2].exercise.id)
        assertEquals(2, queue[2].roundNumber)

        // Step 4
        assertEquals("group_group_1_ex_row_1", queue[3].id)
        assertEquals("ex_row", queue[3].exercise.id)
        assertEquals(2, queue[3].roundNumber)

        // Step 5
        assertEquals("single_ex_squat_0", queue[4].id)
        assertEquals("ex_squat", queue[4].exercise.id)
        assertEquals(WorkoutGroupType.SINGLE, queue[4].groupType)
    }

    @Test
    fun testRecoveryWithActiveStepPrecision() {
        val jsonMetadata = """
            {
                "ex_bp": {
                    "restSeconds": 90,
                    "notes": null,
                    "supersetGroupId": "group_1"
                },
                "ex_row": {
                    "restSeconds": 90,
                    "notes": null,
                    "supersetGroupId": "group_1"
                },
                "__global_recovery__": {
                    "activeSessionId": "test-group-session",
                    "workoutNotes": "",
                    "isMetric": true,
                    "stateVersion": 2,
                    "isRestTimerPaused": false,
                    "restTimerEndTimestamp": null,
                    "restTimerDuration": null,
                    "currentExerciseId": "ex_row"
                }
            }
        """.trimIndent()

        val backup = ActiveWorkoutBackup(
            id = 1,
            templateId = null,
            templateName = "Grouped Workout Recovery",
            startTime = 987654321L,
            exercisesJson = """
                [
                    {"id":"ex_bp","name":"Bench Press","category":"Chest"},
                    {"id":"ex_row","name":"Bent-Over Row","category":"Back"}
                ]
            """.trimIndent(),
            setsJson = """
                {
                    "ex_bp": [
                        {"id":1,"setNumber":1,"reps":10,"weight":60.0,"isCompleted":true,"setType":"WORKING"},
                        {"id":2,"setNumber":2,"reps":10,"weight":60.0,"isCompleted":false,"setType":"WORKING"}
                    ],
                    "ex_row": [
                        {"id":3,"setNumber":1,"reps":10,"weight":50.0,"isCompleted":false,"setType":"WORKING"},
                        {"id":4,"setNumber":2,"reps":10,"weight":50.0,"isCompleted":false,"setType":"WORKING"}
                    ]
                }
            """.trimIndent(),
            exerciseMetadataJson = jsonMetadata
        )

        runBlocking {
            repository.saveActiveWorkoutBackup(backup)
        }

        // Check recovery available
        activeWorkoutViewModel.checkForActiveWorkoutBackup()
        waitUntil {
            activeWorkoutViewModel.workoutRecoveryState.value is WorkoutRecoveryState.Available
        }

        // Resume workout
        activeWorkoutViewModel.resumeWorkout()
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value != null }

        val activeState = activeWorkoutViewModel.activeWorkoutState.value!!
        assertEquals("Grouped Workout Recovery", activeState.templateName)
        assertEquals(987654321L, activeState.startTime)
        assertEquals("test-group-session", activeState.activeSessionId)

        // Generate and verify queue
        val queue = activeWorkoutViewModel.executionQueue.value
        assertEquals(4, queue.size)

        // Completed steps should have isCompleted = true
        assertTrue(queue[0].set.isCompleted) // ex_bp round 1
        assertFalse(queue[1].set.isCompleted) // ex_row round 1

        // The first incomplete step in the interleaving order must be ex_row round 1
        val activeStep = queue.firstOrNull { !it.set.isCompleted }
        assertNotNull(activeStep)
        assertEquals("ex_row", activeStep!!.exercise.id)
        assertEquals(1, activeStep.roundNumber)
    }

    @Test
    fun testLegacySprint4BackupGracefulHandling() {
        // No group information or supersetGroupId exists in exerciseMetadataJson
        val jsonMetadata = """
            {
                "__global_recovery__": {
                    "activeSessionId": "legacy-session",
                    "workoutNotes": "Legacy notes",
                    "isMetric": true,
                    "stateVersion": 1,
                    "isRestTimerPaused": false
                }
            }
        """.trimIndent()

        val legacyBackup = ActiveWorkoutBackup(
            id = 1,
            templateId = null,
            templateName = "Legacy Workout",
            startTime = 1111111L,
            exercisesJson = """
                [
                    {"id":"ex_bp","name":"Bench Press","category":"Chest"}
                ]
            """.trimIndent(),
            setsJson = """
                {
                    "ex_bp": [
                        {"id":1,"setNumber":1,"reps":10,"weight":60.0,"isCompleted":false,"setType":"WORKING"}
                    ]
                }
            """.trimIndent(),
            exerciseMetadataJson = jsonMetadata
        )

        runBlocking {
            repository.saveActiveWorkoutBackup(legacyBackup)
        }

        activeWorkoutViewModel.checkForActiveWorkoutBackup()
        waitUntil {
            activeWorkoutViewModel.workoutRecoveryState.value is WorkoutRecoveryState.Available
        }

        activeWorkoutViewModel.resumeWorkout()
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value != null }

        val activeState = activeWorkoutViewModel.activeWorkoutState.value!!
        assertEquals("Legacy Workout", activeState.templateName)

        val queue = activeWorkoutViewModel.executionQueue.value
        assertEquals(1, queue.size)
        assertEquals(WorkoutGroupType.SINGLE, queue[0].groupType)
        assertFalse(queue[0].set.isCompleted)
    }

    @Test
    fun testCompletionAndAccurateLoggedSets() {
        activeWorkoutViewModel.startWorkout(null)
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value != null }

        val exBp = Exercise("ex_bp", "Bench Press", "Chest")
        val exRow = Exercise("ex_row", "Bent-Over Row", "Back")

        activeWorkoutViewModel.addExerciseToActiveWorkout(exBp)
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value?.exercises?.size == 1 }

        activeWorkoutViewModel.addExerciseToActiveWorkout(exRow)
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value?.exercises?.size == 2 }

        // Mock some completed sets
        activeWorkoutViewModel.updateSet(
            exerciseId = "ex_bp",
            setIndex = 0,
            reps = 10,
            weight = 60f,
            isCompleted = true
        )
        activeWorkoutViewModel.updateSet(
            exerciseId = "ex_row",
            setIndex = 0,
            reps = 10,
            weight = 50f,
            isCompleted = true
        )

        // Complete workout
        activeWorkoutViewModel.finishActiveWorkout()
        waitUntil { activeWorkoutViewModel.activeWorkoutState.value == null }

        // Verify session was logged in Room
        val sessionsList = runBlocking { database.strengthDao().getSessionsForUser("offline").first() }
        assertTrue(sessionsList.isNotEmpty())
        val loggedSession = sessionsList.first()

        val loggedSets = runBlocking { database.strengthDao().getSetsForSession(loggedSession.id).first() }
        // We logged 2 completed sets (1 for Bench Press, 1 for Bent-Over Row)
        assertEquals(2, loggedSets.size)
        assertTrue(loggedSets.any { it.exerciseId == "ex_bp" && it.weight == 60f })
        assertTrue(loggedSets.any { it.exerciseId == "ex_row" && it.weight == 50f })
    }
}
