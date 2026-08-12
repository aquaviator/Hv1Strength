package com.example.planner

import com.example.data.PlannedWorkout
import com.example.data.TrainingPlan
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class PlannerStatus { SCHEDULED, COMPLETED, MISSED, SKIPPED }
enum class CompletionTiming { AS_PLANNED, EARLY, LATE }

data class AdherenceSummary(
    val planned: Int,
    val completed: Int,
    val skipped: Int,
    val missed: Int,
    val completionPercent: Int?,
    val currentWeekCompleted: Int,
    val recentTrend: String
)

object TrainingPlanner {
    const val DEFAULT_HORIZON_DAYS = 84L

    fun weekdayMask(days: Set<DayOfWeek>): Int = days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }
    fun includes(mask: Int, day: DayOfWeek): Boolean = mask and (1 shl (day.value - 1)) != 0

    fun occurrenceId(seriesId: String, epochDay: Long): String = "$seriesId:$epochDay"

    fun generate(plan: TrainingPlan, throughEpochDay: Long = plan.recurrenceEndEpochDay
        ?: plan.firstEpochDay + DEFAULT_HORIZON_DAYS): List<PlannedWorkout> {
        require(plan.weekdaysMask != 0 || plan.firstEpochDay <= throughEpochDay) { "Schedule must contain at least one occurrence" }
        val last = minOf(throughEpochDay, plan.recurrenceEndEpochDay ?: throughEpochDay)
        return (plan.firstEpochDay..last).mapNotNull { epochDay ->
            val date = LocalDate.ofEpochDay(epochDay)
            val selected = if (plan.weekdaysMask == 0) epochDay == plan.firstEpochDay else includes(plan.weekdaysMask, date.dayOfWeek)
            if (!selected) null else PlannedWorkout(
                id = occurrenceId(plan.id, epochDay), seriesId = plan.id,
                userId = plan.userId, humanUserId = plan.humanUserId,
                templateId = plan.templateId, templateGlobalId = plan.templateGlobalId,
                routineName = plan.routineName, scheduledEpochDay = epochDay,
                originalEpochDay = epochDay, preferredMinuteOfDay = plan.preferredMinuteOfDay
            )
        }
    }

    fun visibleStatus(item: PlannedWorkout, todayEpochDay: Long): PlannerStatus = when (item.status) {
        "COMPLETED" -> PlannerStatus.COMPLETED
        "SKIPPED" -> PlannerStatus.SKIPPED
        else -> if (item.scheduledEpochDay < todayEpochDay) PlannerStatus.MISSED else PlannerStatus.SCHEDULED
    }

    fun completionTiming(item: PlannedWorkout, zone: ZoneId = ZoneId.systemDefault()): CompletionTiming? {
        val completed = item.completedAt ?: return null
        val actualDay = Instant.ofEpochMilli(completed).atZone(zone).toLocalDate().toEpochDay()
        return when {
            actualDay < item.scheduledEpochDay -> CompletionTiming.EARLY
            actualDay > item.scheduledEpochDay -> CompletionTiming.LATE
            else -> CompletionTiming.AS_PLANNED
        }
    }

    fun adherence(items: List<PlannedWorkout>, today: LocalDate = LocalDate.now()): AdherenceSummary {
        val eligible = items.filter { it.scheduledEpochDay <= today.toEpochDay() }
        val completed = eligible.count { it.status == "COMPLETED" }
        val skipped = eligible.count { it.status == "SKIPPED" }
        val missed = eligible.count { visibleStatus(it, today.toEpochDay()) == PlannerStatus.MISSED }
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong()).toEpochDay()
        val weekly = items.count { it.status == "COMPLETED" && it.scheduledEpochDay in weekStart..today.toEpochDay() }
        val pct = if (eligible.isEmpty()) null else completed * 100 / eligible.size
        val recent = when {
            eligible.size < 2 -> "More sessions are needed to show a trend"
            completed == eligible.size -> "All recent planned sessions completed"
            missed == 0 -> "Recent training is on track"
            else -> "Some recent sessions remain available to complete or reschedule"
        }
        return AdherenceSummary(eligible.size, completed, skipped, missed, pct, weekly, recent)
    }
}
