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

    @Test fun richIntelligenceIsOptionalAndCustomFallbackRemainsTruthful() {
        val rich = snapshot.exercises.first().copy(intelligence = ExerciseIntelligence(
            purpose = "A reviewed training purpose.", jointActions = listOf("elbow extension"),
            evidence = listOf(EvidenceClaim("Reviewed claim", "Example citation", "https://example.test"))))
        val governedDetails = exerciseDetails(rich.toRoom(0), rich)
        assertTrue(governedDetails.intelligence.hasAdvancedContent)
        assertEquals("A reviewed training purpose.", governedDetails.intelligence.purpose)
        val custom = exerciseDetails(Exercise("custom", "Custom", "Other", true), null)
        assertFalse(custom.intelligence.hasAdvancedContent)
        assertTrue(custom.intelligence.evidence.isEmpty())
    }

    @Test fun expandedPackageIsVerifiedDeterministicAndOriginalIdsRemain() {
        val first = PackagedExerciseLibrarySource.load(context).snapshot
        val second = PackagedExerciseLibrarySource.load(context).snapshot
        assertTrue(first.validation.errors.joinToString(), first.validation.valid)
        assertEquals("2026.08.32", first.metadata.catalogueVersion)
        assertEquals(264, first.exercises.size)
        assertEquals(first.metadata.payloadChecksum, second.metadata.payloadChecksum)
        val original = setOf("bench_press","incline_db_press","chest_fly","deadlift","pull_up","barbell_row","lat_pulldown","squat","romanian_deadlift","leg_press","calf_raise","overhead_press","lateral_raise","rear_delt_fly","bicep_curl","tricep_pushdown","hammer_curl","skull_crusher","hanging_leg_raise","plank","crunch","push_up","goblet_squat","bulgarian_split_squat","hip_thrust","seated_leg_curl","face_pull","kettlebell_swing","farmers_carry","treadmill_run","decline_bench_press","dumbbell_bench_press","incline_bench_press","machine_chest_press","plate_loaded_chest_press","cable_chest_fly","pec_deck","close_grip_push_up","dip","single_arm_cable_press","chin_up","assisted_pull_up","neutral_grip_pulldown","single_arm_pulldown","seated_cable_row","chest_supported_db_row","machine_row","single_arm_db_row","t_bar_row","landmine_row","inverted_row","straight_arm_pulldown","barbell_shrug","back_extension","good_morning","seated_db_shoulder_press","machine_shoulder_press","arnold_press","single_arm_landmine_press","cable_lateral_raise","machine_lateral_raise","reverse_pec_deck","band_pull_apart","upright_row","barbell_curl","ez_bar_curl","incline_db_curl","preacher_curl","cable_curl","concentration_curl","reverse_curl","overhead_triceps_extension","cable_overhead_triceps_extension","close_grip_bench_press","machine_triceps_extension","wrist_curl","reverse_wrist_curl","plate_pinch","dead_hang","front_squat","box_squat","smith_machine_squat","hack_squat","belt_squat","split_squat","walking_lunge","reverse_lunge","step_up","leg_extension","lying_leg_curl","standing_leg_curl","stiff_leg_deadlift","single_leg_rdl","trap_bar_deadlift","sumo_deadlift","glute_bridge","single_leg_glute_bridge","cable_pull_through","cable_hip_abduction","hip_abduction_machine","hip_adduction_machine","seated_calf_raise","leg_press_calf_raise","single_leg_calf_raise","ab_wheel_rollout","cable_crunch","reverse_crunch","russian_twist","pallof_press","side_plank","bird_dog","dead_bug","power_clean","hang_high_pull","push_press","dumbbell_thruster","turkish_get_up","suitcase_carry","front_rack_carry","sled_push","rowing_machine","stationary_bike","stair_climber","battle_ropes")
        assertEquals(124, original.size)
        assertTrue(first.exercises.map { it.id }.containsAll(original))
        assertEquals(first.exercises.size, first.exercises.map { it.id }.distinct().size)
        assertTrue(first.exercises.all { it.movementPattern.isNotBlank() && it.setup.isNotBlank() && it.steps.isNotEmpty() && it.cues.isNotEmpty() && it.mistakes.isNotEmpty() && it.safety.isNotBlank() })
        val ids = first.exercises.map { it.id }.toSet()
        assertTrue(first.exercises.all { item -> item.relatedIds.all { it in ids && it != item.id } })
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

    @Test fun punctuationCaseAndWhitespaceNormalizeDeterministically() {
        val catalogue = snapshot.exercises.associateBy { it.id }
        val room = snapshot.exercises.map { it.toRoom(0) }
        assertEquals(listOf("single_arm_pulldown"), discoverExercises(room, catalogue, emptySet(), emptyList(),
            ExerciseDiscoveryFilters(query = "  SINGLE--arm   lat__pulldown ")).map { it.id })
        assertEquals(normalize(listOf("Single Arm Pulldown")), normalize(listOf("single-arm_pulldown")))
    }

    @Test fun choicesAreOrWithinGroupsAndAndAcrossGroups() {
        val catalogue = snapshot.exercises.associateBy { it.id }
        val room = snapshot.exercises.map { it.toRoom(0) }
        val result = discoverExercises(room, catalogue, emptySet(), emptyList(), ExerciseDiscoveryFilters(
            categories = setOf("Chest"), muscles = setOf("chest"),
            equipmentSelections = setOf("barbell", "dumbbell"),
            capabilities = setOf(MeasurementCapability.LOAD)))
        assertTrue(result.isNotEmpty())
        assertTrue(result.all { it.category == "Chest" })
        assertTrue(result.all { item -> catalogue.getValue(item.id).equipment.any { it in setOf("barbell", "dumbbell") } })
    }

    @Test fun sectionsSourceAndFiltersComposeWithoutDuplicates() {
        val catalogue = snapshot.exercises.associateBy { it.id }
        val custom = Exercise("custom_lift", "My Chest Lift", "Chest", true)
        val room = snapshot.exercises.map { it.toRoom(0) } + custom
        val governed = discoverExercises(room, catalogue, setOf("bench_press", custom.id), emptyList(),
            ExerciseDiscoveryFilters(query = "chest", section = LibrarySection.FAVOURITES, source = ExerciseSource.GOVERNED))
        assertEquals(listOf("bench_press"), governed.map { it.id })
        val customOnly = discoverExercises(room, catalogue, setOf(custom.id), emptyList(),
            ExerciseDiscoveryFilters(query = "chest", section = LibrarySection.FAVOURITES, source = ExerciseSource.CUSTOM))
        assertEquals(listOf(custom.id), customOnly.map { it.id })
        assertEquals(customOnly.size, customOnly.distinctBy { it.id }.size)
    }

    @Test fun detailsCoverGovernedAndCustomWithoutRawDtos() {
        val governed = snapshot.exercises.single { it.id == "single_arm_pulldown" }
        val detail = exerciseDetails(governed.toRoom(0), governed)
        assertEquals("Human V1 governed library", detail.source)
        assertEquals("unilateral", detail.laterality)
        assertTrue("load" in detail.measurements)
        assertEquals("vertical pull", detail.movementPattern)
        assertTrue(detail.steps.isNotEmpty())
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
