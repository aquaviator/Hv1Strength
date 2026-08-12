package com.example.planner

import com.example.data.PlannedWorkout
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class PlannerReminderPolicyTest {
    private val day = LocalDate.of(2026, 6, 15).toEpochDay()
    private fun item(minute: Int? = 817, status: String = "PLANNED", deleted: Long? = null, user: String = "a") =
        PlannedWorkout("occurrence", "series", user, "human_${"a".repeat(32)}", 1, "template", "Routine",
            day, day, minute, status, reminderEnabled = true, deletedAt = deleted)

    @Test fun arbitraryMinuteProducesExactLocalTime() {
        val zone = ZoneId.of("Europe/London")
        val actual = java.time.Instant.ofEpochMilli(PlannerReminderScheduler.triggerAtMillis(item(), zone)!!).atZone(zone)
        assertEquals(13, actual.hour); assertEquals(37, actual.minute)
    }
    @Test fun noTimeNoReminder() = assertNull(PlannerReminderScheduler.triggerAtMillis(item(null), ZoneId.of("UTC")))
    @Test fun completedSkippedAndDeletedAreIneligible() {
        assertFalse(PlannerReminderScheduler.isEligible(item(status = "COMPLETED")))
        assertFalse(PlannerReminderScheduler.isEligible(item(status = "SKIPPED")))
        assertFalse(PlannerReminderScheduler.isEligible(item(deleted = 1L)))
    }
    @Test fun wrongProfileIsIneligible() = assertFalse(PlannerReminderScheduler.isEligible(item(), "b"))

    @Test fun completedStaleRoutingIsNonActionableAndSideEffectFree() {
        val completed = item(status = "COMPLETED").copy(completedAt = 123L, linkedSessionId = 42)
        val original = completed.copy()

        assertFalse(PlannerReminderScheduler.isEligible(completed))
        PlannerReminderNavigation.request(completed.id)
        PlannerReminderNavigation.consume()

        assertEquals(original, completed)
        assertNull(PlannerReminderNavigation.requestedOccurrence.value)
    }
    @Test fun sameOccurrenceUsesStableUniqueWorkName() {
        assertEquals(PlannerReminderScheduler.workName("a"), PlannerReminderScheduler.workName("a"))
        assertNotEquals(PlannerReminderScheduler.workName("a"), PlannerReminderScheduler.workName("b"))
        assertNotEquals(PlannerReminderScheduler.workName("Aa"), PlannerReminderScheduler.workName("BB"))
        assertNotEquals(PlannerReminderScheduler.notificationId("Aa"), PlannerReminderScheduler.notificationId("BB"))
    }
    @Test fun timezoneChangeRecalculatesSameLocalClockTime() {
        val london = java.time.Instant.ofEpochMilli(PlannerReminderScheduler.triggerAtMillis(item(), ZoneId.of("Europe/London"))!!).atZone(ZoneId.of("Europe/London"))
        val tokyo = java.time.Instant.ofEpochMilli(PlannerReminderScheduler.triggerAtMillis(item(), ZoneId.of("Asia/Tokyo"))!!).atZone(ZoneId.of("Asia/Tokyo"))
        assertEquals(london.toLocalTime(), tokyo.toLocalTime()); assertNotEquals(london.toInstant(), tokyo.toInstant())
    }
    @Test fun daylightSavingGapMovesForwardDeterministically() {
        val gapDay = LocalDate.of(2026, 3, 29).toEpochDay()
        val gap = item(90).copy(scheduledEpochDay = gapDay)
        val actual = java.time.Instant.ofEpochMilli(PlannerReminderScheduler.triggerAtMillis(gap, ZoneId.of("Europe/London"))!!).atZone(ZoneId.of("Europe/London"))
        assertEquals(2, actual.hour); assertEquals(30, actual.minute)
    }
    @Test fun daylightSavingOverlapUsesEarlierOffsetDeterministically() {
        val overlapDay = LocalDate.of(2026, 10, 25).toEpochDay()
        val overlap = item(90).copy(scheduledEpochDay = overlapDay)
        val actual = java.time.Instant.ofEpochMilli(PlannerReminderScheduler.triggerAtMillis(overlap, ZoneId.of("Europe/London"))!!).atZone(ZoneId.of("Europe/London"))
        assertEquals("+01:00", actual.offset.toString())
    }
}
