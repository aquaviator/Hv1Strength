package com.example.canonical

import java.security.MessageDigest

object CanonicalContract {
    const val WORKOUT_SCHEMA = "humanv1.canonical-workout/1"
    const val PLAN_SCHEMA = "humanv1.canonical-plan/1"
    val executionStructures = setOf("STRAIGHT_SETS", "SUPERSET", "CIRCUIT", "INTERVAL", "AMRAP", "EMOM", "TIME_CAP", "DISTANCE", "RUN_WALK", "CYCLING_INTERVAL", "SWIM_INTERVAL", "BRICK", "TRANSITION", "RECOVERY", "WARM_UP", "COOL_DOWN")
    fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    fun occurrenceId(planVersionId: String, placementId: String, epochDay: Long) = "occ_${sha256("$planVersionId|$placementId|$epochDay").take(24)}"
}

enum class ContentClass { GOVERNED_LIBRARY, USER_AUTHORED, LEGACY_NORMALIZABLE, LEGACY_INCOMPLETE, HISTORICAL_EXECUTION, CONFLICTED }
enum class NormalizationClassification { ALREADY_CANONICAL, SAFE_NORMALIZATION_AVAILABLE, USER_REVIEW_REQUIRED, OWNERSHIP_CONFLICT, REVISION_CONFLICT, UNSUPPORTED_SCHEMA, HISTORICAL_PRESERVE_ONLY }
enum class FormatStatus(val customerLabel: String) {
    UP_TO_DATE("Up to date"), SAFE_UPGRADE("Safe format upgrade available"), NEEDS_INPUT("Needs your input"),
    CONFLICT("Conflict needs review"), HISTORICAL("Historical record preserved")
}

data class CanonicalOwner(val humanUserId: String, val firebaseUid: String? = null)
data class SetPrescription(val setId: String, val order: Int, val repetitions: Int? = null, val durationSeconds: Int? = null, val distanceMetres: Int? = null, val load: String? = null, val restSeconds: Int? = null, val tempo: String? = null)
data class ExercisePlacement(val placementId: String, val order: Int, val exerciseId: String, val exerciseKind: String, val sets: List<SetPrescription>)
data class WorkoutBlock(val blockId: String, val order: Int, val structure: String, val placements: List<ExercisePlacement>)
data class CanonicalWorkout(val schemaVersion: String, val workoutGlobalId: String, val publicationVersionId: String?, val revision: Int, val checksum: String, val owner: CanonicalOwner?, val contentClass: ContentClass, val discipline: String, val title: String, val blocks: List<WorkoutBlock>, val tombstoned: Boolean = false)
data class PlanPlacement(val placementId: String, val order: Int, val daySlot: Int, val workoutVersionId: String, val required: Boolean, val priority: Boolean)
data class PlanWeek(val weekId: String, val order: Int, val recoveryWeek: Boolean, val placements: List<PlanPlacement>)
data class CanonicalPlan(val schemaVersion: String, val planGlobalId: String, val publicationVersionId: String?, val revision: Int, val checksum: String, val owner: CanonicalOwner?, val contentClass: ContentClass, val title: String, val timezone: String, val startDate: String, val weeks: List<PlanWeek>, val acknowledgement: String?)

object CanonicalValidator {
    fun workout(value: CanonicalWorkout): List<String> = buildList {
        if (value.schemaVersion != CanonicalContract.WORKOUT_SCHEMA) add("SUPPORTED_SCHEMA")
        if (value.workoutGlobalId.isBlank() || value.title.isBlank()) add("REQUIRED_FIELD")
        if (value.blocks.isEmpty()) add("EXECUTABLE_BLOCK_REQUIRED")
        value.blocks.forEach { block ->
            if (block.structure !in CanonicalContract.executionStructures) add("EXECUTION_STRUCTURE_SUPPORTED")
            if (block.structure != "TRANSITION" && block.placements.isEmpty()) add("PLACEMENT_REQUIRED")
            block.placements.forEach { placement ->
                if (placement.exerciseId.isBlank()) add("EXERCISE_REFERENCE_REQUIRED")
                if (placement.sets.isEmpty()) add("PRESCRIPTION_REQUIRED")
                placement.sets.forEach { set -> if (set.repetitions == null && set.durationSeconds == null && set.distanceMetres == null && set.load == null) add("EXECUTION_TARGET_REQUIRED") }
            }
        }
    }
    fun plan(value: CanonicalPlan): List<String> = buildList {
        if (value.schemaVersion != CanonicalContract.PLAN_SCHEMA) add("SUPPORTED_SCHEMA")
        if (value.timezone.isBlank()) add("TIMEZONE_REQUIRED")
        if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(value.startDate)) add("EXPLICIT_DATE_REQUIRED")
        if (value.weeks.flatMap { it.placements }.any { it.workoutVersionId.isBlank() }) add("IMMUTABLE_DEPENDENCY_REQUIRED")
    }
}

data class LegacyRecord(val entityType: String, val globalId: String, val humanUserId: String, val revision: Int, val schemaVersion: String, val contentClass: ContentClass, val fields: Map<String, String?>, val hasExecutableBlocks: Boolean = true, val remoteRevision: Int? = null)
data class NormalizationChange(val field: String, val before: String?, val after: String?, val reason: String)
data class NormalizationCommand(val commandId: String, val classification: NormalizationClassification, val before: LegacyRecord, val after: LegacyRecord?, val changes: List<NormalizationChange>, val rollback: LegacyRecord, val historyUnaffected: Boolean = true)
data class NormalizationPlan(val owner: String, val commands: List<NormalizationCommand>, val checkpoint: String)
data class NormalizationResult(val records: List<LegacyRecord>, val auditCommandIds: List<String>)

object NormalizationPlanner {
    private val supported = setOf("humanv1.workout/1", "humanv1.plan/1", CanonicalContract.WORKOUT_SCHEMA, CanonicalContract.PLAN_SCHEMA)
    fun plan(owner: String, records: List<LegacyRecord>, limit: Int = 100): NormalizationPlan {
        require(limit in 1..500) { "INVALID_BATCH_LIMIT" }
        val commands = records.sortedBy { "${it.entityType}:${it.globalId}" }.take(limit).map { record ->
            val classification = when {
                record.humanUserId != owner -> NormalizationClassification.OWNERSHIP_CONFLICT
                record.remoteRevision?.let { it > record.revision } == true || record.contentClass == ContentClass.CONFLICTED -> NormalizationClassification.REVISION_CONFLICT
                record.contentClass == ContentClass.HISTORICAL_EXECUTION -> NormalizationClassification.HISTORICAL_PRESERVE_ONLY
                record.schemaVersion !in supported -> NormalizationClassification.UNSUPPORTED_SCHEMA
                record.contentClass == ContentClass.LEGACY_INCOMPLETE || (record.entityType == "workout" && !record.hasExecutableBlocks) -> NormalizationClassification.USER_REVIEW_REQUIRED
                record.schemaVersion.startsWith("humanv1.canonical-") -> NormalizationClassification.ALREADY_CANONICAL
                else -> NormalizationClassification.SAFE_NORMALIZATION_AVAILABLE
            }
            val changes = if (classification == NormalizationClassification.SAFE_NORMALIZATION_AVAILABLE) meaningPreservingChanges(record) else emptyList()
            val after = if (classification == NormalizationClassification.SAFE_NORMALIZATION_AVAILABLE) record.copy(schemaVersion = if (record.entityType == "workout") CanonicalContract.WORKOUT_SCHEMA else CanonicalContract.PLAN_SCHEMA, fields = record.fields.toMutableMap().apply { changes.forEach { put(it.field, it.after) } }) else null
            val id = "normalize_${CanonicalContract.sha256("$owner|${record.entityType}|${record.globalId}|${record.revision}").take(20)}"
            NormalizationCommand(id, classification, record, after, changes, record)
        }
        return NormalizationPlan(owner, commands, CanonicalContract.sha256(commands.joinToString("|") { it.commandId }))
    }
    private fun meaningPreservingChanges(record: LegacyRecord): List<NormalizationChange> = buildList {
        val legacyId = if (record.entityType == "workout") "workoutId" else "planId"
        val canonicalId = if (record.entityType == "workout") "workoutGlobalId" else "planGlobalId"
        if (record.fields[canonicalId] == null && record.fields[legacyId] == record.globalId) add(NormalizationChange(canonicalId, null, record.globalId, "Canonical field-name conversion"))
        if (record.fields["description"] == "") add(NormalizationChange("description", "", null, "Blank/null equivalence"))
    }
    fun applyInEmulator(plan: NormalizationPlan, current: List<LegacyRecord>, emulator: Boolean): NormalizationResult {
        check(emulator) { "PRODUCTION_MIGRATION_DISABLED" }
        val records = current.toMutableList(); val audits = mutableListOf<String>()
        plan.commands.filter { it.classification == NormalizationClassification.SAFE_NORMALIZATION_AVAILABLE }.forEach { command ->
            val index = records.indexOfFirst { it.entityType == command.before.entityType && it.globalId == command.before.globalId }
            check(index >= 0 && records[index].humanUserId == plan.owner) { "OWNERSHIP_CONFLICT" }
            check(records[index].revision == command.before.revision) { "REVISION_CONFLICT" }
            if (records[index] != command.after) { records[index] = requireNotNull(command.after); audits += command.commandId }
        }
        return NormalizationResult(records, audits)
    }
}

data class Occurrence(val status: String, val workoutVersionId: String, val detached: Boolean = false)
fun replaceFutureOnly(items: List<Occurrence>, nextVersionId: String) = items.map { if (it.status == "COMPLETED" || it.status == "SKIPPED" || it.detached) it else it.copy(workoutVersionId = nextVersionId) }
