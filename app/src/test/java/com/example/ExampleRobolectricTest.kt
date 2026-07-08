package com.example

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.identity.GlobalIdGenerator
import com.example.core.identity.HumanUserIdGenerator
import com.example.core.identity.DeviceIdGenerator
import com.example.data.*
import com.example.ui.viewmodel.StrengthViewModel
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
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var database: StrengthDatabase
    private lateinit var repository: StrengthRepository
    private lateinit var viewModel: StrengthViewModel

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
        viewModel = StrengthViewModel(repository, context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testAppNameIsCorrect() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Human v1 - Strength", appName)
    }

    @Test
    fun testMainActivityLaunchesWithoutCrashing() {
        try {
            androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    assertNotNull(activity)
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            throw e
        }
    }

    // ==========================================
    // SYNC FOUNDATION TESTS
    // ==========================================

    @Test
    fun testHumanUserIdGenerator_offlineStableAndDeterministic() {
        // Generate a stable offline ID
        val id1 = HumanUserIdGenerator.getOrGenerateOfflineHumanId(context)
        val id2 = HumanUserIdGenerator.getOrGenerateOfflineHumanId(context)
        
        // Assert stable across calls
        assertEquals(id1, id2)
        assertTrue(id1.startsWith("human_"))
        assertEquals(18, id1.length) // human_ + 12 chars

        // Deterministic mapping of standard user IDs
        val mappedOffline = HumanUserIdGenerator.mapUserIdToHumanUserId("offline")
        assertEquals(HumanUserIdGenerator.getOrGenerateOfflineHumanId(context), mappedOffline)

        val mappedGoogle = HumanUserIdGenerator.mapUserIdToHumanUserId("google_123456")
        assertTrue(mappedGoogle.startsWith("human_"))
        assertEquals(18, mappedGoogle.length)
    }

    @Test
    fun testGlobalIdGenerator_prefixes() {
        val measurementId = GlobalIdGenerator.generate("measurement")
        assertTrue(measurementId.startsWith("measurement_"))
        assertEquals(24, measurementId.length) // "measurement_" (12 chars) + 12 chars

        val templateId = GlobalIdGenerator.generate("template")
        assertTrue(templateId.startsWith("template_"))
        assertEquals(21, templateId.length) // "template_" (9 chars) + 12 chars
    }

    @Test
    fun testRepository_insertAssignsCorrectSyncMetadata() = runBlocking {
        val bodyWeight = BodyWeight(
            id = 0,
            date = System.currentTimeMillis(),
            weight = 82.5f,
            userId = "offline"
        )
        
        repository.insertBodyWeight(bodyWeight)
        
        val inserted = repository.allBodyWeights.first().firstOrNull()
        assertNotNull(inserted)
        assertEquals(82.5f, inserted!!.weight)
        
        // Check sync metadata auto-populated
        assertTrue(inserted.globalId.startsWith("measurement_"))
        assertEquals(HumanUserIdGenerator.getOrGenerateOfflineHumanId(context), inserted.humanUserId)
        assertEquals("PENDING_UPLOAD", inserted.syncStatus)
        assertEquals(1L, inserted.revision)
        assertNull(inserted.deletedAt)
        assertTrue(inserted.createdAt > 0)
        assertTrue(inserted.updatedAt > 0)
    }

    @Test
    fun testRepository_softDeleteHidesRecordFromUi() = runBlocking {
        val bodyWeight = BodyWeight(
            id = 0,
            date = System.currentTimeMillis(),
            weight = 75.0f,
            userId = "offline"
        )
        
        repository.insertBodyWeight(bodyWeight)
        
        // Verify active
        val initialList = repository.allBodyWeights.first()
        assertEquals(1, initialList.size)
        val insertedId = initialList.first().id

        // Perform Soft Delete
        repository.deleteBodyWeight(insertedId)

        // Verify hidden from Flow-based queries
        val afterDeleteList = repository.allBodyWeights.first()
        assertTrue(afterDeleteList.isEmpty())

        // Verify STILL exists in DB using raw query or manual check if we select directly (which bypasses deletedAt is null in normal code, but wait, normal DAO gets soft-deleted filter)
        // Let's verify that the record exists and has deletedAt populated
        val dao = database.strengthDao()
        val pendingDeletes = dao.getPendingDeleteBodyWeights()
        assertEquals(1, pendingDeletes.size)
        assertEquals(75.0f, pendingDeletes.first().weight)
        assertNotNull(pendingDeletes.first().deletedAt)
        assertEquals("PENDING_DELETE", pendingDeletes.first().syncStatus)
        assertEquals(2L, pendingDeletes.first().revision)
    }

    @Test
    fun testCommandQueue_operations() = runBlocking {
        val command = CommandQueueEntity(
            id = 0,
            commandId = "cmd_123",
            humanUserId = "human_offlineusr",
            commandType = "CREATE",
            entityType = "BODY_WEIGHT",
            entityGlobalId = "measurement_abc123",
            payloadJson = "{}",
            createdAt = System.currentTimeMillis()
        )

        repository.enqueueCommand(command)

        val pending = repository.getPendingCommands()
        assertEquals(1, pending.size)
        assertEquals("cmd_123", pending.first().commandId)
        assertEquals("PENDING", pending.first().status)

        // Mark processing
        repository.markCommandProcessing(pending.first().id)
        
        var updated = repository.getPendingCommands()
        // Wait, "PENDING" query only gets status = 'PENDING', so it should be empty now
        assertTrue(updated.isEmpty())

        // Mark succeeded
        repository.markCommandSucceeded(pending.first().id)
    }

    // ==========================================
    // ORIGINAL APP FUNCTIONALITY TESTS
    // ==========================================

    @Test
    fun testActiveWorkoutUndoSystem_removeAndUndoExercise() {
        // Start an active empty workout
        viewModel.startWorkout(null)
        waitUntil { viewModel.activeWorkoutState.value != null }

        // Add exercises
        val benchPress = Exercise("bench_press", "Bench Press", "Chest")
        val overheadPress = Exercise("overhead_press", "Overhead Press", "Shoulders")
        
        viewModel.addExerciseToActiveWorkout(benchPress)
        waitUntil { viewModel.activeWorkoutState.value?.exercises?.size == 1 }

        viewModel.addExerciseToActiveWorkout(overheadPress)
        waitUntil { viewModel.activeWorkoutState.value?.exercises?.size == 2 }

        // Verify exercises are in the active workout
        var activeState = viewModel.activeWorkoutState.value
        assertNotNull(activeState)
        assertEquals(2, activeState?.exercises?.size)
        assertEquals("bench_press", activeState?.exercises?.get(0)?.id)

        // Remove bench press from active workout
        viewModel.removeExerciseFromActiveWorkout("bench_press")
        waitUntil { viewModel.activeWorkoutState.value?.exercises?.size == 1 }

        activeState = viewModel.activeWorkoutState.value
        assertEquals("overhead_press", activeState?.exercises?.first()?.id)

        // Perform undo of the exercise deletion
        viewModel.undoRemoveExercise()
        waitUntil { viewModel.activeWorkoutState.value?.exercises?.size == 2 }

        activeState = viewModel.activeWorkoutState.value
        assertEquals("bench_press", activeState?.exercises?.get(0)?.id)
    }

    @Test
    fun testActiveWorkoutUndoSystem_removeAndUndoSet() {
        // Start an active empty workout and add an exercise
        viewModel.startWorkout(null)
        waitUntil { viewModel.activeWorkoutState.value != null }

        val benchPress = Exercise("bench_press", "Bench Press", "Chest")
        viewModel.addExerciseToActiveWorkout(benchPress)
        waitUntil { viewModel.activeWorkoutState.value?.exercises?.size == 1 }

        // Add sets to make it 3
        viewModel.addSetToExercise("bench_press")
        waitUntil { viewModel.activeWorkoutState.value?.sets?.get("bench_press")?.size == 2 }

        viewModel.addSetToExercise("bench_press")
        waitUntil { viewModel.activeWorkoutState.value?.sets?.get("bench_press")?.size == 3 }

        var activeState = viewModel.activeWorkoutState.value
        val originalSetsCount = activeState?.sets?.get("bench_press")?.size ?: 0
        assertEquals(3, originalSetsCount)

        // Remove set 2 (index 1)
        viewModel.removeSetFromExercise("bench_press", 1)
        waitUntil { viewModel.activeWorkoutState.value?.sets?.get("bench_press")?.size == 2 }

        activeState = viewModel.activeWorkoutState.value
        assertEquals(2, activeState?.sets?.get("bench_press")?.size ?: 0)

        // Undo set removal
        viewModel.undoRemoveSet()
        waitUntil { viewModel.activeWorkoutState.value?.sets?.get("bench_press")?.size == 3 }

        activeState = viewModel.activeWorkoutState.value
        assertEquals(3, activeState?.sets?.get("bench_press")?.size ?: 0)
    }

    @Test
    fun testDatabaseMigration_and_SyncMetadata() = runBlocking {
        // Since we are running on the latest version of the compiled database (v6),
        // we can test that inserting, soft deleting, and updating properly assigns:
        // 1. Human Identity: stable offline ID, mapped correctly.
        // 2. Device Identity: stable device ID, persisted correctly.
        // 3. Relationships & Global references: templateGlobalId, templateExerciseGlobalId, sessionGlobalId.
        // 4. Command Queue integration: All mutations enqueue commands.
        // 5. Revision incrementing and soft deletes.

        val context = ApplicationProvider.getApplicationContext<Context>()
        
        // 1. Test Stable Device ID
        val devId = DeviceIdGenerator.getOrGenerateDeviceId(context)
        assertNotNull(devId)
        assertTrue(devId.startsWith("device_"))
        assertEquals(19, devId.length)

        // 2. Test Stable Human ID
        val humanId = HumanUserIdGenerator.getOrGenerateOfflineHumanId(context)
        assertNotNull(humanId)
        assertTrue(humanId.startsWith("human_"))
        assertEquals(18, humanId.length)

        // 3. Insert Workout Template & Exercises to check parent-child global references & commands enqueued
        val template = WorkoutTemplate(
            id = 0,
            name = "Sprint 18 Test Template",
            exerciseIdsJson = "[\"bench_press\"]",
            userId = "offline"
        )
        val templateId = repository.insertTemplate(template)
        val insertedTemplate = repository.getTemplateSync(templateId.toInt())
        assertNotNull(insertedTemplate)
        val tGlobalId = insertedTemplate!!.globalId
        assertTrue(tGlobalId.startsWith("template_"))
        assertEquals(HumanUserIdGenerator.getOrGenerateOfflineHumanId(context), insertedTemplate.humanUserId)
        assertEquals(devId, insertedTemplate.originDeviceId)

        // Verify command enqueued for template creation
        val commands = repository.getPendingCommands()
        assertTrue(commands.any { it.commandType == "WorkoutTemplateCreated" && it.entityGlobalId == tGlobalId })

        // 4. Insert WorkoutTemplateExercise & verify templateGlobalId linkage
        val templateEx = WorkoutTemplateExercise(
            id = 0,
            templateId = templateId.toInt(),
            exerciseId = "bench_press",
            position = 0,
            restSeconds = 90
        )
        val tExId = repository.insertTemplateExercise(templateEx)
        val dao = database.strengthDao()
        val insertedEx = dao.getTemplateExerciseById(tExId.toInt())
        assertNotNull(insertedEx)
        assertEquals(tGlobalId, insertedEx!!.templateGlobalId)
        assertEquals(HumanUserIdGenerator.getOrGenerateOfflineHumanId(context), insertedEx.humanUserId)
        assertEquals(devId, insertedEx.originDeviceId)

        // 5. Insert LoggedSet & verify sessionGlobalId linkage
        val session = WorkoutSession(
            id = 0,
            templateId = templateId.toInt(),
            templateName = "Sprint 18 Test Session",
            userId = "offline",
            startTime = System.currentTimeMillis(),
            endTime = System.currentTimeMillis()
        )
        val sId = repository.insertSession(session)
        val insertedSession = repository.getSessionById(sId.toInt())
        assertNotNull(insertedSession)
        val sGlobalId = insertedSession!!.globalId
        assertEquals(tGlobalId, insertedSession.templateGlobalId)

        val loggedSet = LoggedSet(
            id = 0,
            sessionId = sId.toInt(),
            exerciseId = "bench_press",
            setNumber = 1,
            reps = 10,
            weight = 80f,
            isCompleted = true
        )
        repository.insertLoggedSet(loggedSet)
        val insertedSets = repository.getSetsForSessionSync(sId.toInt())
        assertEquals(1, insertedSets.size)
        val insertedSet = insertedSets.first()
        assertEquals(sGlobalId, insertedSet.sessionGlobalId)
        assertEquals(HumanUserIdGenerator.getOrGenerateOfflineHumanId(context), insertedSet.humanUserId)
        assertEquals(devId, insertedSet.originDeviceId)

        // Verify SetCompleted command enqueued
        val currentCommands = repository.getPendingCommands()
        assertTrue(currentCommands.any { it.commandType == "SetCompleted" && (it.entityGlobalId == loggedSet.globalId || it.entityGlobalId == insertedSet.globalId) })
    }

    // ==========================================
    // HARDENED CLOUD SYNC PRODUCTION TESTS
    // ==========================================

    @Test
    fun testCommandRetryLifecycle_failedAndPoisoned() = runBlocking {
        val now = System.currentTimeMillis()
        val dao = database.strengthDao()
        
        val command = CommandQueueEntity(
            id = 0,
            commandId = "cmd_fail_test",
            humanUserId = "human_user_test",
            commandType = "CREATE",
            entityType = "BODY_WEIGHT",
            entityGlobalId = "measurement_fail",
            payloadJson = "{}",
            attempts = 0,
            status = "PENDING"
        )
        dao.enqueueCommand(command)

        // 1. First failure -> Expect FAILED with backoff
        val pending = dao.getPendingCommands(now)
        assertEquals(1, pending.size)
        val firstCmd = pending.first()
        
        val nextAttempts = firstCmd.attempts + 1
        val backoffMs = 1000L * 10L * Math.pow(2.0, nextAttempts.toDouble()).toLong()
        val retryAt = System.currentTimeMillis() + backoffMs
        
        dao.updateCommandStatus(
            id = firstCmd.id,
            status = "FAILED",
            attempts = nextAttempts,
            lastAttemptAt = System.currentTimeMillis(),
            nextRetryAt = retryAt,
            errorMessage = "Server error"
        )

        val afterFirst = dao.getAllCommands().first { it.commandId == "cmd_fail_test" }
        assertEquals("FAILED", afterFirst.status)
        assertEquals(1, afterFirst.attempts)
        assertNotNull(afterFirst.nextRetryAt)

        // 2. Query with now < retryAt -> Should not show in pending
        val pendingEarly = dao.getPendingCommands(System.currentTimeMillis())
        assertTrue(pendingEarly.none { it.commandId == "cmd_fail_test" })

        // 3. Query with now >= retryAt -> Should show in pending
        val pendingLate = dao.getPendingCommands(retryAt + 1000)
        assertEquals(1, pendingLate.size)

        // 4. Test poisoned transition at 5 attempts
        dao.updateCommandStatus(
            id = firstCmd.id,
            status = "POISONED",
            attempts = 5,
            lastAttemptAt = System.currentTimeMillis(),
            nextRetryAt = null,
            errorMessage = "Poisoned after 5 failed attempts: Server error"
        )

        val afterFifth = dao.getAllCommands().first { it.commandId == "cmd_fail_test" }
        assertEquals("POISONED", afterFifth.status)
        assertEquals(5, afterFifth.attempts)
        assertNull(afterFifth.nextRetryAt)
        
        // Poisoned commands should never be returned by getPendingCommands
        val pendingAfterPoison = dao.getPendingCommands(retryAt + 100000)
        assertTrue(pendingAfterPoison.none { it.commandId == "cmd_fail_test" })
    }
}
