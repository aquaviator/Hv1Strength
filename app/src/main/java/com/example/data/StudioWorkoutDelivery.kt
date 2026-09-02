package com.example.data

data class StudioImportedEffort(
    val set: WorkoutTemplateSet,
    val prescriptions: List<MetricPrescriptionEntity>
)

data class StudioImportedExercise(
    val exercise: WorkoutTemplateExercise,
    val efforts: List<StudioImportedEffort>
)

data class StudioWorkoutImport(
    val link: StudioWorkoutLink,
    val template: WorkoutTemplate,
    val exercises: List<StudioImportedExercise>
)

data class StudioWorkoutApplyResult(
    val link: StudioWorkoutLink,
    val applied: Boolean,
    val conflict: Boolean
)

internal suspend fun StrengthDao.applyStudioWorkoutGraph(value: StudioWorkoutImport): StudioWorkoutApplyResult {
    getStudioWorkoutLink(value.link.versionId)?.let { existing ->
        require(existing.humanUserId == value.link.humanUserId &&
            existing.workoutGlobalId == value.link.workoutGlobalId &&
            existing.contentChecksum == value.link.contentChecksum) { "CONFLICTING_VERSION_ID" }
        return StudioWorkoutApplyResult(existing, applied = false, conflict = existing.conflictState != null)
    }

    val latest = getLatestStudioWorkoutLink(value.link.humanUserId, value.link.workoutGlobalId)
    if (latest != null && value.link.sourceRevision <= latest.sourceRevision) {
        val historical = value.link.copy(localRoutineId = latest.localRoutineId,
            localRoutineRevisionAtApply = latest.localRoutineRevisionAtApply, isLatest = false,
            acknowledgementState = "RECEIVED")
        insertStudioWorkoutLink(historical)
        return StudioWorkoutApplyResult(historical, applied = false, conflict = false)
    }

    val latestRoutine = latest?.let { getTemplateById(it.localRoutineId) }
    val canReplace = latest != null && latestRoutine != null &&
        latestRoutine.revision == latest.localRoutineRevisionAtApply &&
        latestRoutine.syncStatus == "SYNCED" && latestRoutine.conflictState == null
    val conflict = latest != null && !canReplace

    val routineId = if (canReplace) latest!!.localRoutineId else insertTemplate(value.template).toInt()
    if (canReplace) {
        deleteMetricPrescriptionsForTemplate(routineId)
        getTemplateExercisesSync(routineId).forEach { deleteTemplateSetsForExercise(it.id) }
        deleteTemplateExercisesForTemplate(routineId)
        insertTemplate(value.template.copy(id = routineId))
    }

    value.exercises.forEach { imported ->
        val exerciseId = insertTemplateExercise(imported.exercise.copy(templateId = routineId)).toInt()
        imported.efforts.forEach { effort ->
            insertTemplateSet(effort.set.copy(templateExerciseId = exerciseId))
            upsertMetricPrescriptions(effort.prescriptions)
        }
    }

    clearLatestStudioWorkoutLink(value.link.humanUserId, value.link.workoutGlobalId)
    val stored = value.link.copy(localRoutineId = routineId,
        localRoutineRevisionAtApply = value.template.revision,
        acknowledgementState = if (conflict) "CONFLICT" else "PENDING",
        conflictState = if (conflict) "LOCAL_EDIT_PRESERVED_NEW_VERSION_CREATED" else null,
        isLatest = true)
    insertStudioWorkoutLink(stored)
    return StudioWorkoutApplyResult(stored, applied = true, conflict = conflict)
}
