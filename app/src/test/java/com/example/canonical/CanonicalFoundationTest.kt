package com.example.canonical

import org.junit.Assert.*
import org.junit.Test

class CanonicalFoundationTest {
    private val owner = "human_fixture_owner"
    private fun legacy(overrides: LegacyRecord.() -> LegacyRecord = { this }) = LegacyRecord("workout", "workout_1", owner, 1, "humanv1.workout/1", ContentClass.LEGACY_NORMALIZABLE, mapOf("workoutId" to "workout_1", "description" to "")).let(overrides)

    @Test fun contractParityAndStructures() {
        assertEquals("humanv1.canonical-workout/1", CanonicalContract.WORKOUT_SCHEMA)
        assertEquals("humanv1.canonical-plan/1", CanonicalContract.PLAN_SCHEMA)
        assertTrue(CanonicalContract.executionStructures.containsAll(setOf("STRAIGHT_SETS", "SUPERSET", "CIRCUIT", "INTERVAL", "AMRAP", "EMOM", "TIME_CAP", "DISTANCE", "RUN_WALK", "CYCLING_INTERVAL", "SWIM_INTERVAL", "BRICK", "TRANSITION", "RECOVERY")))
    }
    @Test fun deterministicChecksumAndOccurrenceIdentity() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", CanonicalContract.sha256("abc"))
        assertEquals(CanonicalContract.occurrenceId("p1", "x1", 21000), CanonicalContract.occurrenceId("p1", "x1", 21000))
        assertNotEquals(CanonicalContract.occurrenceId("p1", "x1", 21000), CanonicalContract.occurrenceId("p1", "x1", 21001))
    }
    @Test fun validatesWorkoutWithoutForcingStrengthRepetitions() {
        val swim = CanonicalWorkout(CanonicalContract.WORKOUT_SCHEMA, "w1", "w1_v1", 1, "hash", null, ContentClass.GOVERNED_LIBRARY, "SWIMMING", "CSS", listOf(WorkoutBlock("b1", 1, "SWIM_INTERVAL", listOf(ExercisePlacement("p1", 1, "swim_css", "GOVERNED", listOf(SetPrescription("s1", 1, distanceMetres = 400)))))))
        assertTrue(CanonicalValidator.workout(swim).isEmpty())
        assertEquals(listOf("EXECUTABLE_BLOCK_REQUIRED"), CanonicalValidator.workout(swim.copy(blocks = emptyList())))
    }
    @Test fun validatesPlanAndExactAcknowledgementField() {
        val plan = CanonicalPlan(CanonicalContract.PLAN_SCHEMA, "p1", "p1_v1", 1, "hash", null, ContentClass.GOVERNED_LIBRARY, "Plan", "Europe/London", "2026-09-14", listOf(PlanWeek("week1", 1, false, listOf(PlanPlacement("place1", 1, 1, "w1_v1", true, true)))), "APPLIED:w1_v1:hash")
        assertTrue(CanonicalValidator.plan(plan).isEmpty()); assertEquals("APPLIED:w1_v1:hash", plan.acknowledgement)
    }
    @Test fun safeNormalizationIsDeterministicAndDoesNotInventPrescriptions() {
        val first = NormalizationPlanner.plan(owner, listOf(legacy())); val second = NormalizationPlanner.plan(owner, listOf(legacy()))
        assertEquals(first, second); assertEquals(NormalizationClassification.SAFE_NORMALIZATION_AVAILABLE, first.commands.single().classification)
        assertEquals(setOf("workoutGlobalId", "description"), first.commands.single().changes.map { it.field }.toSet())
        assertFalse(first.commands.single().after!!.fields.containsKey("sets"))
    }
    @Test fun parentOnlyAndConflictsFailClosed() {
        val empty = legacy { copy(globalId = "template_push", contentClass = ContentClass.LEGACY_INCOMPLETE, hasExecutableBlocks = false) }
        val other = legacy { copy(globalId = "other", humanUserId = "human_other") }
        val revision = legacy { copy(globalId = "newer", remoteRevision = 2) }
        assertEquals(mapOf("other" to NormalizationClassification.OWNERSHIP_CONFLICT, "newer" to NormalizationClassification.REVISION_CONFLICT, "template_push" to NormalizationClassification.USER_REVIEW_REQUIRED), NormalizationPlanner.plan(owner, listOf(empty, other, revision)).commands.associate { it.before.globalId to it.classification })
        assertNull(NormalizationPlanner.plan(owner, listOf(empty)).commands.single().after)
    }
    @Test fun emulatorApplyMatchesPreviewAndSecondRunIsIdempotent() {
        val source = legacy(); val plan = NormalizationPlanner.plan(owner, listOf(source)); val first = NormalizationPlanner.applyInEmulator(plan, listOf(source), true); val second = NormalizationPlanner.applyInEmulator(plan, first.records, true)
        assertEquals(plan.commands.single().after, first.records.single()); assertEquals(1, first.auditCommandIds.size); assertTrue(second.auditCommandIds.isEmpty())
        assertThrows(IllegalStateException::class.java) { NormalizationPlanner.applyInEmulator(plan, listOf(source), false) }
    }
    @Test fun completedSkippedAndDetachedHistoryIsImmutable() {
        val before = listOf(Occurrence("COMPLETED", "v1"), Occurrence("SKIPPED", "v1"), Occurrence("PLANNED", "v1", true), Occurrence("PLANNED", "v1"))
        assertEquals(listOf("v1", "v1", "v1", "v2"), replaceFutureOnly(before, "v2").map { it.workoutVersionId }); assertTrue(before.all { it.workoutVersionId == "v1" })
    }
    @Test fun customerLabelsAreExact() { assertEquals(listOf("Up to date", "Safe format upgrade available", "Needs your input", "Conflict needs review", "Historical record preserved"), FormatStatus.entries.map { it.customerLabel }) }
}
