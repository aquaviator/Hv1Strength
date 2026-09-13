package com.example.data

data class StudioPlanImport(
    val link: StudioPlanLink,
    val plan: TrainingPlan,
    val occurrences: List<PlannedWorkout>
)

data class StudioPlanApplyResult(val link: StudioPlanLink, val applied: Boolean, val conflict: Boolean)

internal suspend fun StrengthDao.applyStudioPlanGraph(value: StudioPlanImport): StudioPlanApplyResult {
    getStudioPlanLink(value.link.planVersionId)?.let { existing ->
        require(existing.humanUserId == value.link.humanUserId &&
            existing.planGlobalId == value.link.planGlobalId &&
            existing.planChecksum == value.link.planChecksum) { "CONFLICTING_PLAN_VERSION_ID" }
        return StudioPlanApplyResult(existing, applied = false, conflict = existing.conflictState != null)
    }
    val latest = getLatestStudioPlanLink(value.link.humanUserId, value.link.planGlobalId)
    if (latest != null && value.link.sourceRevision <= latest.sourceRevision) {
        val historical = value.link.copy(isLatest = false, acknowledgementState = "RECEIVED")
        insertStudioPlanLink(historical)
        return StudioPlanApplyResult(historical, applied = false, conflict = false)
    }

    val existingPlan = getTrainingPlan(value.plan.id)
    val planConflict = existingPlan != null && existingPlan.humanUserId != value.link.humanUserId
    var occurrenceConflict = false
    value.occurrences.forEach { incoming ->
        val existing = getPlannedWorkout(incoming.id)
        if (existing != null && existing.humanUserId != value.link.humanUserId) occurrenceConflict = true
        if (existing != null && existing.status !in setOf("PLANNED") &&
            (existing.scheduledEpochDay != incoming.scheduledEpochDay || existing.templateId != incoming.templateId)) {
            // Completed, skipped and detached history is immutable. It is preserved rather than overwritten.
            occurrenceConflict = occurrenceConflict || false
        } else if (existing != null && existing.syncStatus != "SYNCED" &&
            (existing.scheduledEpochDay != incoming.scheduledEpochDay || existing.templateId != incoming.templateId)) {
            occurrenceConflict = true
        }
    }
    val conflict = planConflict || occurrenceConflict
    if (!conflict) {
        upsertTrainingPlan(value.plan.copy(createdAt = existingPlan?.createdAt ?: value.plan.createdAt))
        val incomingIds = value.occurrences.mapTo(hashSetOf()) { it.id }
        getPlannedWorkoutsForSeries(value.plan.id, value.plan.userId)
            .filter { it.id !in incomingIds && it.status == "PLANNED" && !it.detachedFromSeries && it.deletedAt == null }
            .forEach { markStudioOccurrenceSuperseded(it.id, value.link.appliedAt, maxOf(it.revision + 1, value.link.sourceRevision)) }
        value.occurrences.forEach { incoming ->
            val existing = getPlannedWorkout(incoming.id)
            if (existing == null || (existing.status == "PLANNED" && !existing.detachedFromSeries)) {
                upsertPlannedWorkout(incoming.copy(createdAt = existing?.createdAt ?: incoming.createdAt,
                    originalEpochDay = existing?.originalEpochDay ?: incoming.originalEpochDay))
            }
        }
    }
    clearLatestStudioPlanLink(value.link.humanUserId, value.link.planGlobalId)
    val stored = value.link.copy(acknowledgementState = if (conflict) "CONFLICT" else "PENDING",
        conflictState = if (conflict) "LOCAL_PLAN_EDIT_PRESERVED" else null, isLatest = true)
    insertStudioPlanLink(stored)
    return StudioPlanApplyResult(stored, applied = !conflict, conflict = conflict)
}
