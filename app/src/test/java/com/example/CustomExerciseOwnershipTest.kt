package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.Exercise
import com.example.data.StrengthDatabase
import com.example.data.StrengthRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CustomExerciseOwnershipTest {
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
