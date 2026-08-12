package com.example.catalogue

import com.example.data.Exercise
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.example.data.StrengthDatabase
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
class ExerciseCatalogueTest {
    private val validJson = """{"contractVersion":1,"catalogueVersion":"1","releaseChannel":"test","releasedAt":"2026-01-01T00:00:00Z","sourceId":"test","exerciseCount":1,"payloadChecksum":"0be478af5b7593035bb1af5da02e53c4319468083afe431570272f1efb0cd701","exercises":[{"id":"bench_press","name":"Bench Press","aliases":["chest press"],"category":"Chest","primaryMuscles":["chest"],"secondaryMuscles":["triceps"],"equipment":["barbell"],"type":"strength","capabilities":["repetitions","load"],"laterality":"bilateral","bodyweight":false,"active":true}]}"""

    @Test fun parsesAndSearchesAliasesAndFilters() {
        val source = PackagedExerciseLibrarySource.fromJson(validJson)
        assertTrue(source.snapshot.validation.valid)
        assertEquals("bench_press", source.search("chest press", equipment = "barbell", capability = MeasurementCapability.LOAD).single().id)
        assertEquals(1, source.search(muscle = "triceps").size)
        assertTrue(source.search(category = "Legs").isEmpty())
    }

    @Test fun rejectsChecksumAndUnsupportedContract() {
        assertFalse(PackagedExerciseLibrarySource.fromJson(validJson.replace("0be478", "1be478")).snapshot.validation.valid)
        assertTrue(PackagedExerciseLibrarySource.fromJson(validJson.replace("contractVersion\":1", "contractVersion\":2")).snapshot.validation.errors.any { it.contains("contract") })
    }

    @Test fun rejectsDuplicateIdsAndInvalidCapabilities() {
        val second = """{"id":"bench_press","name":"Other","aliases":[],"category":"Chest","primaryMuscles":[],"secondaryMuscles":[],"equipment":[],"type":"strength","capabilities":[],"laterality":"bilateral","bodyweight":false,"active":true}"""
        val duplicate = validJson.replace("\"exerciseCount\":1", "\"exerciseCount\":2").dropLast(2) + ",$second]}"
        val result = PackagedExerciseLibrarySource.fromJson(duplicate).snapshot.validation.errors
        assertTrue(result.any { it.contains("Duplicate") })
        assertTrue(result.any { it.contains("capabilities") })
    }

    @Test fun rejectsMalformedEnumsAliasesAndBodyweightCapabilityCombinations() {
        val malformed = validJson.replace("\"aliases\":[\"chest press\"]", "\"aliases\":[\"\",\"\"]")
            .replace("\"type\":\"strength\"", "\"type\":\"unknown\"")
            .replace("\"laterality\":\"bilateral\"", "\"laterality\":\"sometimes\"")
            .replace("\"capabilities\":[\"repetitions\",\"load\"]", "\"capabilities\":[\"repetitions\",\"assisted_load\"]")
        val errors = PackagedExerciseLibrarySource.fromJson(malformed).snapshot.validation.errors
        assertTrue(errors.any { it.contains("aliases") })
        assertTrue(errors.any { it.contains("type") })
        assertTrue(errors.any { it.contains("laterality") })
        assertTrue(errors.any { it.contains("requires bodyweight") })
    }

    @Test fun customOverlayPreservesCustomAndRejectsCollision() {
        val governed = PackagedExerciseLibrarySource.fromJson(validJson).snapshot.exercises
        val overlay = overlayLibrary(governed, listOf(Exercise("custom_1", "My Lift", "Other", true), Exercise("bench_press", "Mine", "Other", true)))
        assertEquals(listOf("bench_press"), overlay.rejectedCustomCollisions)
        assertTrue(overlay.items.any { it.exercise.id == "custom_1" && it.exercise.isCustom })
    }

    @Test fun packagedAssetIsVerifiedAndRoomReconciliationIsIdempotent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = PackagedExerciseLibrarySource.load(context)
        assertTrue(source.snapshot.validation.errors.joinToString(), source.snapshot.validation.valid)
        assertEquals(124, source.snapshot.exercises.size)
        val database = Room.inMemoryDatabaseBuilder(context, StrengthDatabase::class.java).allowMainThreadQueries().build()
        try {
            database.strengthDao().insertExercise(Exercise("custom_1", "My Lift", "Other", true))
            val first = CatalogueReconciler(context, database).reconcile()
            val second = CatalogueReconciler(context, database).reconcile()
            val stored = database.strengthDao().getAllExercisesSync()
            assertEquals(124, first.inserted)
            assertEquals(0, second.inserted)
            assertEquals(125, stored.size)
            assertTrue(stored.single { it.id == "custom_1" }.isCustom)
        } finally { database.close() }
    }
}
