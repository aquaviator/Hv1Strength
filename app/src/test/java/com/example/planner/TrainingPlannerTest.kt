package com.example.planner

import com.example.data.PlannedWorkout
import com.example.data.TrainingPlan
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TrainingPlannerTest {
    private val monday = LocalDate.of(2026, 3, 23)
    private fun plan(mask: Int = 0, end: LocalDate? = null) = TrainingPlan("series", "user-a", "human_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 7, "template-global", "Push", monday.toEpochDay(), 540, mask, end?.toEpochDay())
    private fun item(day: LocalDate = monday, status: String = "PLANNED", completedAt: Long? = null) = PlannedWorkout("series:${day.toEpochDay()}", "series", "user-a", "human_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 7, "template-global", "Push", day.toEpochDay(), monday.toEpochDay(), 540, status, completedAt)

    @Test fun oneTimeScheduleCreatesExactlyOneStableOccurrence() {
        val generated = TrainingPlanner.generate(plan(), monday.plusDays(30).toEpochDay())
        assertEquals(1, generated.size); assertEquals("series:${monday.toEpochDay()}", generated.single().id)
    }
    @Test fun weeklyRecurrenceUsesSelectedWeekdaysDeterministically() {
        val mask = TrainingPlanner.weekdayMask(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        val generated = TrainingPlanner.generate(plan(mask, monday.plusWeeks(2)), monday.plusWeeks(2).toEpochDay())
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY, DayOfWeek.MONDAY, DayOfWeek.THURSDAY, DayOfWeek.MONDAY), generated.map { LocalDate.ofEpochDay(it.scheduledEpochDay).dayOfWeek })
        assertEquals(generated.map { it.id }.distinct().size, generated.size)
    }
    @Test fun multipleWorkoutsOnOneDayHaveIndependentStableIds() {
        val a = TrainingPlanner.generate(plan()).single(); val b = TrainingPlanner.generate(plan().copy(id = "series-b")).single()
        assertEquals(a.scheduledEpochDay, b.scheduledEpochDay); assertNotEquals(a.id, b.id)
    }
    @Test fun pastIncompleteBecomesMissedWithoutMutation() {
        val original = item(); assertEquals(PlannerStatus.MISSED, TrainingPlanner.visibleStatus(original, monday.plusDays(1).toEpochDay())); assertEquals("PLANNED", original.status)
    }
    @Test fun skippedAndCompletedOverrideMissed() {
        assertEquals(PlannerStatus.SKIPPED, TrainingPlanner.visibleStatus(item(status="SKIPPED"), monday.plusDays(2).toEpochDay()))
        assertEquals(PlannerStatus.COMPLETED, TrainingPlanner.visibleStatus(item(status="COMPLETED"), monday.plusDays(2).toEpochDay()))
    }
    @Test fun completionTimingReportsEarlyOnTimeAndLate() {
        val zone = ZoneId.of("Europe/London")
        fun at(day: LocalDate) = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(CompletionTiming.EARLY, TrainingPlanner.completionTiming(item(completedAt=at(monday.minusDays(1))), zone))
        assertEquals(CompletionTiming.AS_PLANNED, TrainingPlanner.completionTiming(item(completedAt=at(monday)), zone))
        assertEquals(CompletionTiming.LATE, TrainingPlanner.completionTiming(item(completedAt=at(monday.plusDays(1))), zone))
    }
    @Test fun adherenceIsTruthfulAndNonPunitive() {
        val items = listOf(item(status="COMPLETED"), item(monday.plusDays(1), "SKIPPED"), item(monday.plusDays(2)))
        val result = TrainingPlanner.adherence(items, monday.plusDays(3)); assertEquals(3, result.planned); assertEquals(1, result.completed); assertEquals(1, result.skipped); assertEquals(1, result.missed); assertEquals(33, result.completionPercent)
        assertFalse(result.recentTrend.contains("fail", true))
    }
    @Test fun insufficientAdherenceDataIsExplicit() {
        assertNull(TrainingPlanner.adherence(emptyList(), monday).completionPercent)
        assertTrue(TrainingPlanner.adherence(emptyList(), monday).recentTrend.contains("More sessions"))
    }
    @Test fun dstBoundaryDoesNotDuplicateEpochDayOccurrences() {
        val start = LocalDate.of(2026, 3, 23)
        val p = plan(TrainingPlanner.weekdayMask(setOf(DayOfWeek.SUNDAY)), LocalDate.of(2026, 4, 5)).copy(firstEpochDay=start.toEpochDay())
        val generated = TrainingPlanner.generate(p); assertEquals(2, generated.size); assertEquals(2, generated.map { it.scheduledEpochDay }.distinct().size)
    }
    @Test fun occurrenceRelationshipSurvivesReschedule() {
        val original = item(); val moved = original.copy(scheduledEpochDay=monday.plusDays(2).toEpochDay(), detachedFromSeries=true)
        assertEquals(original.id, moved.id); assertEquals(original.originalEpochDay, moved.originalEpochDay); assertTrue(moved.detachedFromSeries)
    }
    @Test fun currentRoutinePolicyKeepsReferenceNotSnapshotPayload() {
        val occurrence = item(); assertEquals(7, occurrence.templateId); assertEquals("template-global", occurrence.templateGlobalId)
    }
    @Test fun epochDayIsTimezoneIndependentStorage() {
        val instant = Instant.parse("2026-10-25T00:30:00Z")
        val london = instant.atZone(ZoneId.of("Europe/London")).toLocalDate().toEpochDay()
        assertEquals(LocalDate.of(2026,10,25).toEpochDay(), london)
    }
    @Test fun arbitraryPreferredMinuteIsPreservedWithoutLocaleEncoding() {
        val generated = TrainingPlanner.generate(plan().copy(preferredMinuteOfDay = 13 * 60 + 37)).single()
        assertEquals(817, generated.preferredMinuteOfDay)
    }
    @Test fun futureRegenerationIsDeterministicForChangedWeekdaysAndEndDate() {
        val edited = plan(TrainingPlanner.weekdayMask(setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY)), monday.plusWeeks(2))
            .copy(firstEpochDay = monday.plusDays(1).toEpochDay())
        val first = TrainingPlanner.generate(edited)
        val replay = TrainingPlanner.generate(edited)
        assertEquals(first, replay)
        assertEquals(setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY), first.map { LocalDate.ofEpochDay(it.scheduledEpochDay).dayOfWeek }.toSet())
    }
    @Test fun protectedHistoricalOccurrenceCanBeExcludedFromRegeneration() {
        val generated = TrainingPlanner.generate(plan(TrainingPlanner.weekdayMask(setOf(DayOfWeek.MONDAY)), monday.plusWeeks(2)))
        val completedId = generated.first().id
        val replacements = generated.filterNot { it.id == completedId }
        assertFalse(replacements.any { it.id == completedId })
        assertEquals(2, replacements.size)
    }
}
