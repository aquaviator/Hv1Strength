package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.Exercise
import com.example.data.StrengthDatabase
import com.example.data.StrengthRepository
import com.example.data.CommandQueueEntity
import com.example.core.sync.SyncEngineImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CustomExerciseOwnershipTest {
    @Test fun governedRetryPreservesPoisonedCommandIdentityAndAuditFields() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, StrengthDatabase::class.java)
            .allowMainThreadQueries().build()
        val dao = database.strengthDao()
        val human = "human_12345678901234567890123456789012"
        val globalId = "exercise_retry123"
        dao.insertExercise(Exercise(
            id = "custom_retry123", name = "Retry private", category = "Chest", isCustom = true,
            globalId = globalId, humanUserId = human, syncStatus = "PENDING_UPLOAD",
            originDeviceId = "device_android_1"
        ))
        dao.enqueueCommand(CommandQueueEntity(
            commandId = "cmd_retry123", humanUserId = human, commandType = "ExerciseCreated",
            entityType = "EXERCISE", entityGlobalId = globalId, payloadJson = "{\"globalId\":\"$globalId\"}",
            createdAt = 10, attempts = 5, lastAttemptAt = 20, status = "POISONED",
            errorMessage = "Poisoned after 5 failed attempts: PERMISSION_DENIED",
            originDeviceId = "device_android_1"
        ))
        val repository = StrengthRepository(dao, context, "device_android_1")

        assertEquals(1, repository.retryPermissionDeniedCustomExerciseCommands(human))
        val recovered = dao.getAllCommands().single()
        assertEquals("cmd_retry123", recovered.commandId)
        assertEquals(globalId, recovered.entityGlobalId)
        assertEquals("PENDING", recovered.status)
        assertEquals(5, recovered.attempts)
        assertEquals(20L, recovered.lastAttemptAt)
        assertTrue(recovered.errorMessage!!.contains("PERMISSION_DENIED"))
        assertEquals(0, repository.retryPermissionDeniedCustomExerciseCommands(human))
        database.close()
    }

    @Test fun androidCustomExerciseWireDocumentMatchesTheSharedContract() {
        val exercise = Exercise(
            id = "custom_3deb463a-eda2-416b-86ac-931463496ec9",
            name = "Private exercise",
            category = "Chest",
            isCustom = true,
            globalId = "exercise_6cc2f8f0d0a5",
            humanUserId = "human_12345678901234567890123456789012",
            createdAt = 10,
            updatedAt = 10,
            revision = 1,
            originDeviceId = "device_android_1"
        )
        val document = SyncEngineImpl.customExerciseDocument(exercise, 11)

        assertEquals(setOf("globalId", "id", "name", "category", "isCustom", "humanUserId",
            "createdAt", "updatedAt", "revision", "deletedAt", "originDeviceId", "lastSyncedAt"), document.keys)
        assertEquals(exercise.globalId, document["globalId"])
        assertEquals(exercise.id, document["id"])
        assertEquals(exercise.humanUserId, document["humanUserId"])
        assertEquals(true, document["isCustom"])
        assertEquals(1L, document["revision"])
        assertEquals(11L, document["lastSyncedAt"])
    }

    @Test fun authenticatedCustomExerciseKeepsTrustedHumanOwnerAndQueuesOnce() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, StrengthDatabase::class.java)
            .allowMainThreadQueries().build()
        val repository = StrengthRepository(database.strengthDao(), context)
        val human = "human_12345678901234567890123456789012"

        repository.insertExercise(Exercise(
            id = "custom-owned",
            name = "Owned Custom",
            category = "Chest",
            isCustom = true,
            humanUserId = human
        ))

        assertEquals(human, database.strengthDao().getExerciseById("custom-owned")?.humanUserId)
        val commands = database.strengthDao().getAllCommands()
        assertEquals(1, commands.size)
        assertEquals(human, commands.single().humanUserId)
        assertTrue(commands.single().commandType == "ExerciseCreated")
        database.close()
    }
}
