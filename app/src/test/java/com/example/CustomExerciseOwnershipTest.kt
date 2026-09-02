package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.Exercise
import com.example.data.StrengthDatabase
import com.example.data.StrengthRepository
import com.example.core.sync.SyncEngineImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CustomExerciseOwnershipTest {
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
