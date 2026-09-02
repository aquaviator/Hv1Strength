package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudioWorkoutMigrationInstrumentedTest {
    @Test fun migration15To16PreservesExistingRowsAndCreatesProvenance() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "studio-workout-migration.db"
        context.deleteDatabase(name)
        val created = Room.databaseBuilder(context, StrengthDatabase::class.java, name)
            .allowMainThreadQueries().build()
        try {
            created.strengthDao().insertExercise(Exercise("existing", "Existing", "Other", true, humanUserId = "owner"))
        } finally {
            created.close()
        }
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { raw ->
            raw.execSQL("DROP TABLE studio_workout_link")
            raw.version = 15
        }
        val migrated = Room.databaseBuilder(context, StrengthDatabase::class.java, name)
            .addMigrations(StrengthDatabase.MIGRATION_15_16).allowMainThreadQueries().build()
        try {
            assertNotNull(migrated.strengthDao().getExerciseById("existing"))
            val link = StudioWorkoutLink("version", "owner", "global", 1, "checksum", "release", "Title",
                "Description", "STRENGTH", "{}", 1, 1, 1, acknowledgementId = "strength_ack")
            migrated.strengthDao().insertStudioWorkoutLink(link)
            assertEquals("version", migrated.strengthDao().getStudioWorkoutLink("version")?.versionId)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }
}
