package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.PlannedWorkout
import com.example.data.StrengthRepository
import com.example.data.TrainingPlan
import com.example.data.WorkoutTemplate
import com.example.planner.AdherenceSummary
import com.example.planner.TrainingPlanner
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

class PlannerViewModel(
    private val repository: StrengthRepository,
    private val auth: AuthViewModel,
    private val activeWorkout: ActiveWorkoutViewModel,
    private val context: android.content.Context
) : ViewModel() {
    suspend fun getPlan(seriesId: String): TrainingPlan? = repository.getTrainingPlan(seriesId)
    val occurrences: StateFlow<List<PlannedWorkout>> = auth.activeUserId.flatMapLatest { userId ->
        if (userId.isNullOrBlank()) flowOf(emptyList()) else repository.getPlannedWorkoutsForUser(userId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val adherence: StateFlow<AdherenceSummary> = occurrences.map { TrainingPlanner.adherence(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrainingPlanner.adherence(emptyList()))

    fun schedule(template: WorkoutTemplate, date: LocalDate, minuteOfDay: Int?, weekdays: Set<DayOfWeek>, end: LocalDate?, reminder: Boolean = false) {
        viewModelScope.launch {
            val userId = auth.activeUserId.value ?: return@launch
            val profile = repository.getUserProfile(userId) ?: return@launch
            val seriesId = "plan_${UUID.randomUUID().toString().replace("-", "")}"
            val plan = TrainingPlan(
                id = seriesId, userId = userId, humanUserId = profile.humanUserId,
                templateId = template.id, templateGlobalId = template.globalId,
                routineName = template.name, firstEpochDay = date.toEpochDay(),
                preferredMinuteOfDay = minuteOfDay,
                weekdaysMask = TrainingPlanner.weekdayMask(weekdays),
                recurrenceEndEpochDay = end?.toEpochDay()
            )
            val generated = TrainingPlanner.generate(plan).map { it.copy(reminderEnabled = reminder) }
            repository.createTrainingPlan(plan, generated)
            generated.forEach { com.example.planner.PlannerReminderScheduler.schedule(context, it) }
        }
    }

    fun start(item: PlannedWorkout, template: WorkoutTemplate?) {
        if (template != null) activeWorkout.startWorkout(template, item.id)
    }

    fun skip(item: PlannedWorkout) { com.example.planner.PlannerReminderScheduler.cancel(context, item.id); update(item.copy(status = "SKIPPED", reminderEnabled = false)) }

    fun reschedule(item: PlannedWorkout, date: LocalDate) = update(item.copy(
        scheduledEpochDay = date.toEpochDay(), detachedFromSeries = true, status = "PLANNED",
        completedAt = null, linkedSessionId = null
    ))

    fun rescheduleFuture(item: PlannedWorkout, newDate: LocalDate) {
        val delta = newDate.toEpochDay() - item.scheduledEpochDay
        val future = occurrences.value.filter { it.seriesId == item.seriesId && it.scheduledEpochDay >= item.scheduledEpochDay && it.status == "PLANNED" && !it.detachedFromSeries }
            .let { if (delta > 0) it.sortedByDescending(PlannedWorkout::scheduledEpochDay) else it.sortedBy(PlannedWorkout::scheduledEpochDay) }
        future.forEach { update(it.copy(scheduledEpochDay = it.scheduledEpochDay + delta)) }
    }

    fun editFuture(item: PlannedWorkout, template: WorkoutTemplate, newDate: LocalDate, minuteOfDay: Int?,
        weekdays: Set<DayOfWeek>, end: LocalDate?, reminder: Boolean) {
        require(weekdays.isNotEmpty())
        require(end == null || !end.isBefore(newDate))
        val userId = auth.activeUserId.value ?: return
        viewModelScope.launch {
            val oldPlan = repository.getTrainingPlan(item.seriesId) ?: return@launch
            if (oldPlan.userId != userId || oldPlan.humanUserId != item.humanUserId) return@launch
            val affected = occurrences.value.filter { it.seriesId == item.seriesId && it.scheduledEpochDay >= item.scheduledEpochDay }
            affected.forEach { com.example.planner.PlannerReminderScheduler.cancel(context, it.id) }
            val updatedPlan = oldPlan.copy(
                templateId = template.id, templateGlobalId = template.globalId, routineName = template.name,
                firstEpochDay = newDate.toEpochDay(), preferredMinuteOfDay = minuteOfDay,
                weekdaysMask = TrainingPlanner.weekdayMask(weekdays), recurrenceEndEpochDay = end?.toEpochDay()
            )
            val protectedIds = affected.filter { it.status != "PLANNED" || it.detachedFromSeries || it.linkedSessionId != null }.mapTo(hashSetOf()) { it.id }
            val replacements = TrainingPlanner.generate(updatedPlan).filter { it.id !in protectedIds }
                .map { it.copy(reminderEnabled = reminder && minuteOfDay != null) }
            repository.replaceFutureTrainingPlan(updatedPlan, item.scheduledEpochDay, affected, replacements)
            replacements.forEach { com.example.planner.PlannerReminderScheduler.schedule(context, it) }
        }
    }

    fun restoreSkipped(item: PlannedWorkout) {
        val userId = auth.activeUserId.value ?: return
        if (item.userId != userId || item.status != "SKIPPED" || item.linkedSessionId != null || item.deletedAt != null) return
        update(item.copy(status = "PLANNED", reminderEnabled = item.preferredMinuteOfDay != null))
    }

    fun update(item: PlannedWorkout) {
        val userId = auth.activeUserId.value ?: return
        viewModelScope.launch { repository.updatePlannedWorkout(item, userId); com.example.planner.PlannerReminderScheduler.schedule(context, item) }
    }

    fun deleteOne(item: PlannedWorkout) {
        val userId = auth.activeUserId.value ?: return
        com.example.planner.PlannerReminderScheduler.cancel(context, item.id)
        viewModelScope.launch { repository.deletePlannedWorkout(item.id, userId) }
    }

    fun deleteFuture(item: PlannedWorkout) {
        val userId = auth.activeUserId.value ?: return
        occurrences.value.filter { it.seriesId == item.seriesId && it.scheduledEpochDay >= item.scheduledEpochDay && it.status == "PLANNED" }
            .forEach { com.example.planner.PlannerReminderScheduler.cancel(context, it.id) }
        viewModelScope.launch { repository.deleteFuturePlannedWorkouts(item.seriesId, userId, item.scheduledEpochDay) }
    }
}
