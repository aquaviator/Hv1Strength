package com.example.catalogue

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.Exercise
import com.example.data.LoggedSet
import com.example.data.WorkoutSession
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.system.measureTimeMillis

@RunWith(RobolectricTestRunner::class)
class ExerciseDiscoveryTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val snapshot get() = PackagedExerciseLibrarySource.load(context).snapshot

    @Test fun expandedPackageIsVerifiedDeterministicAndOriginalIdsRemain() {
        val first = PackagedExerciseLibrarySource.load(context).snapshot
        val second = PackagedExerciseLibrarySource.load(context).snapshot
        assertTrue(first.validation.errors.joinToString(), first.validation.valid)
        assertEquals("2026.08.2", first.metadata.catalogueVersion)
        assertEquals(124, first.exercises.size)
        assertEquals(first.metadata.payloadChecksum, second.metadata.payloadChecksum)
        val original = setOf("bench_press","incline_db_press","chest_fly","deadlift","pull_up","barbell_row","lat_pulldown","squat","romanian_deadlift","leg_press","calf_raise","overhead_press","lateral_raise","rear_delt_fly","bicep_curl","tricep_pushdown","hammer_curl","skull_crusher","hanging_leg_raise","plank","crunch")
        assertTrue(first.exercises.map { it.id }.containsAll(original))
        assertEquals(first.exercises.size, first.exercises.map { it.id }.distinct().size)
    }

    @Test fun keyAliasesCapabilitiesAndCoverageAreUseful() {
        val source = PackagedExerciseLibrarySource.load(context)
        assertTrue(source.search("RDL").any { it.id == "romanian_deadlift" })
        assertEquals("chin_up", source.search("chinup").single().id)
        assertTrue(source.search(equipment = "landmine").isNotEmpty())
        assertTrue(source.search(muscle = "forearms").size >= 5)
        assertTrue(source.snapshot.exercises.any { it.capabilities.containsAll(setOf(MeasurementCapability.DURATION, MeasurementCapability.DISTANCE)) })
        assertTrue(source.snapshot.exercises.map { it.category }.toSet().containsAll(setOf("Chest","Back","Legs","Shoulders","Arms","Core","Full Body","Cardio")))
    }

    @Test fun favouriteToggleIsIdempotentAndProfileKeysAreIsolatedAndPersistent() {
        assertEquals(setOf("bench_press"), toggledFavourite(emptySet(), "bench_press"))
        assertTrue(toggledFavourite(setOf("bench_press"), "bench_press").isEmpty())
        assertNotEquals(favouritePreferenceKey("account-a"), favouritePreferenceKey("account-b"))
        val prefs = context.getSharedPreferences("s6-favourites-test", Context.MODE_PRIVATE)
        prefs.edit().putStringSet(favouritePreferenceKey("account-a"), setOf("bench_press", "custom_lift")).commit()
        assertEquals(setOf("bench_press", "custom_lift"), prefs.getStringSet(favouritePreferenceKey("account-a"), emptySet()))
        assertTrue(prefs.getStringSet(favouritePreferenceKey("account-b"), emptySet()).isNullOrEmpty())
    }

    @Test fun recentOrderingIsDeterministicUniqueAndSupportsCustomExercises() {
        val sessions = listOf(session(1, "a", 100), session(2, "a", 300), session(3, "b", 500))
        val sets = listOf(set(1,1,"bench_press"), set(2,2,"custom_lift"), set(3,2,"bench_press"), set(4,3,"squat"))
        assertEquals(listOf("bench_press", "custom_lift"), recentExerciseIds(sessions.filter { it.userId == "a" }, sets))
    }

    @Test fun combinedFiltersSectionsAndEmptyStatesWork() {
        val catalogue = snapshot.exercises.associateBy { it.id }
        val room = snapshot.exercises.map { it.toRoom(0) } + Exercise("custom_lift", "My Cable Lift", "Back", true)
        val filtered = discoverExercises(room, catalogue, setOf("single_arm_pulldown", "custom_lift"), listOf("custom_lift"),
            ExerciseDiscoveryFilters(query="unilateral", category="Back", equipment="cable", capability=MeasurementCapability.LOAD, section=LibrarySection.FAVOURITES))
        assertEquals(listOf("single_arm_pulldown"), filtered.map { it.id })
        assertEquals(listOf("custom_lift"), discoverExercises(room, catalogue, emptySet(), listOf("custom_lift"), ExerciseDiscoveryFilters(section=LibrarySection.RECENT)).map { it.id })
        assertTrue(discoverExercises(room, catalogue, emptySet(), emptyList(), ExerciseDiscoveryFilters(query="not-a-real-exercise")).isEmpty())
    }

    @Test fun detailsCoverGovernedAndCustomWithoutRawDtos() {
        val governed = snapshot.exercises.single { it.id == "single_arm_pulldown" }
        val detail = exerciseDetails(governed.toRoom(0), governed)
        assertEquals("Human V1 governed library", detail.source)
        assertEquals("unilateral", detail.laterality)
        assertTrue("load" in detail.measurements)
        val custom = exerciseDetails(Exercise("custom_lift", "Mine", "Other", true), null)
        assertEquals("Custom exercise", custom.source)
        assertTrue(custom.measurements.isNotEmpty())
    }

    @Test fun expandedDiscoveryStaysResponsive() {
        val catalogue = snapshot.exercises.associateBy { it.id }
        val room = snapshot.exercises.map { it.toRoom(0) }
        val elapsed = measureTimeMillis { repeat(200) { discoverExercises(room, catalogue, emptySet(), emptyList(), ExerciseDiscoveryFilters(query="press", equipment="barbell")) } }
        assertTrue("Discovery took ${elapsed}ms", elapsed < 1500)
    }

    private fun session(id: Int, user: String, end: Long) = WorkoutSession(id=id, templateName="Session", startTime=end-10, endTime=end, userId=user)
    private fun set(id: Int, session: Int, exercise: String) = LoggedSet(id=id, sessionId=session, exerciseId=exercise, setNumber=1, reps=8, weight=10f, isCompleted=true)
}
