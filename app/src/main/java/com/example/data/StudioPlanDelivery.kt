package com.example.data

data class StudioPlanImport(
    val link: StudioPlanLink,
    val plan: TrainingPlan,
    val occurrences: List<PlannedWorkout>
)

const val CURRENT_PLAN_RECONCILIATION_VERSION = 1

data class StudioPlanApplyResult(
    val link: StudioPlanLink,
    val applied: Boolean,
    val conflict: Boolean,
    val acknowledgementRequired: Boolean = true
)

internal suspend fun StrengthDao.applyStudioPlanGraph(value: StudioPlanImport): StudioPlanApplyResult {
    val exactExisting = getStudioPlanLink(value.link.planVersionId)
    exactExisting?.let { existing ->
        require(existing.humanUserId == value.link.humanUserId &&
            existing.planGlobalId == value.link.planGlobalId &&
            existing.planChecksum == value.link.planChecksum) { "CONFLICTING_PLAN_VERSION_ID" }
        require(existing.planReconciliationVersion <= CURRENT_PLAN_RECONCILIATION_VERSION) {
            "UNSUPPORTED_PLAN_RECONCILIATION_VERSION"
        }
        if (existing.planReconciliationVersion == CURRENT_PLAN_RECONCILIATION_VERSION)
            return StudioPlanApplyResult(existing, applied = false, conflict = existing.conflictState != null,
                acknowledgementRequired = false)
    }
    val latest = getLatestStudioPlanLink(value.link.humanUserId, value.link.planGlobalId)
    if (exactExisting == null && latest != null && value.link.sourceRevision <= latest.sourceRevision) {
        val historical = value.link.copy(isLatest = false, acknowledgementState = "RECEIVED")
        insertStudioPlanLink(historical)
        return StudioPlanApplyResult(historical, applied = false, conflict = false)
    }

    val existingPlan = getTrainingPlan(value.plan.id)
    val planConflict = existingPlan != null && existingPlan.humanUserId != value.link.humanUserId
    var occurrenceConflict = false
    val incomingIds = value.occurrences.mapTo(hashSetOf()) { it.id }
    val existingSeries = getPlannedWorkoutsForSeries(value.plan.id, value.plan.userId)
    existingSeries.filter { it.id !in incomingIds && it.status == "PLANNED" && !it.detachedFromSeries && it.deletedAt == null }
        .forEach { obsolete ->
            if (obsolete.humanUserId != value.link.humanUserId || obsolete.syncStatus != "SYNCED") occurrenceConflict = true
        }
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
        existingSeries
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
    val stored = if (exactExisting != null) {
        val healed = exactExisting.copy(
            conflictState = if (conflict) "LOCAL_PLAN_EDIT_PRESERVED" else null,
            planReconciliationVersion = if (conflict) exactExisting.planReconciliationVersion else CURRENT_PLAN_RECONCILIATION_VERSION
        )
        updateStudioPlanLink(healed)
        healed
    } else {
        clearLatestStudioPlanLink(value.link.humanUserId, value.link.planGlobalId)
        val inserted = value.link.copy(acknowledgementState = if (conflict) "CONFLICT" else "PENDING",
            conflictState = if (conflict) "LOCAL_PLAN_EDIT_PRESERVED" else null, isLatest = true,
            planReconciliationVersion = CURRENT_PLAN_RECONCILIATION_VERSION)
        insertStudioPlanLink(inserted)
        inserted
    }
    return StudioPlanApplyResult(stored, applied = !conflict, conflict = conflict,
        acknowledgementRequired = exactExisting == null)
}
