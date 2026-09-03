package com.example.data

import java.security.MessageDigest

data class ReconciliationCandidate(
    val entityType: String,
    val globalId: String,
    val humanUserId: String,
    val revision: Long,
    val syncStatus: String,
    val deletedAt: Long? = null,
    val parentGlobalId: String? = null
)

data class ReconciliationPlan(
    val owner: String,
    val classifications: Map<String, Int>,
    val commands: List<CommandQueueEntity>,
    val missingParents: List<String>,
    val receiptHash: String
)

/** Pure, deterministic planner. Applying its commands remains a repository/Room operation. */
object AccountReconciliationPlanner {
    private val uploadableStates = setOf("LOCAL_ONLY", "PENDING_UPLOAD", "PENDING_DELETE")

    fun plan(
        owner: String,
        candidates: List<ReconciliationCandidate>,
        existingCommands: List<CommandQueueEntity>,
        existingParents: Set<String>,
        now: Long,
        originDeviceId: String
    ): ReconciliationPlan {
        require(isValidAuthoritativeHumanId(owner)) { "Authoritative owner is invalid" }
        require(candidates.none { it.humanUserId != owner }) { "Reconciliation ownership mismatch" }
        val existingIds = existingCommands.map { it.commandId }.toSet()
        val missingParents = candidates.filter { it.parentGlobalId != null && it.parentGlobalId !in existingParents }
            .map { "${it.entityType}:${it.globalId}->${it.parentGlobalId}" }.sorted()
        val commands = candidates.asSequence()
            .filter { it.syncStatus in uploadableStates }
            .filterNot { it.parentGlobalId != null && it.parentGlobalId !in existingParents }
            .filterNot { it.entityType == "PLANNED_WORKOUT" && it.syncStatus == "COMPLETED" }
            .sortedWith(compareBy({ it.entityType }, { it.globalId }))
            .map { candidate ->
                val id = deterministicCommandId(owner, candidate.entityType, candidate.globalId, candidate.revision)
                CommandQueueEntity(
                    commandId = id, humanUserId = owner,
                    commandType = commandType(candidate), entityType = candidate.entityType,
                    entityGlobalId = candidate.globalId,
                    payloadJson = "{\"globalId\":\"${candidate.globalId}\"}",
                    createdAt = now, originDeviceId = originDeviceId
                )
            }.filterNot { it.commandId in existingIds }.toList()
        val classifications = buildMap {
            put("pending", candidates.count { it.syncStatus in uploadableStates })
            put("missing synchronization command", commands.size)
            put("missing parent", missingParents.size)
            put("archived/tombstoned", candidates.count { it.deletedAt != null })
            put("already reconciled", candidates.count { it.syncStatus == "SYNCED" })
            put("failed", candidates.count { it.syncStatus == "FAILED" })
            put("poisoned", existingCommands.count { it.status == "POISONED" })
            put("conflicting equal/divergent revision", candidates.count { it.syncStatus == "CONFLICT" })
        }
        val receipt = (classifications.toSortedMap().entries.joinToString("|") { "${it.key}=${it.value}" } +
            "|commands=" + commands.joinToString(",") { it.commandId } + "|orphans=" + missingParents.joinToString(","))
        return ReconciliationPlan(owner, classifications, commands, missingParents, sha256(receipt))
    }

    fun deterministicCommandId(owner: String, entityType: String, globalId: String, revision: Long): String =
        "cmd_reconcile_${sha256("$owner|$entityType|$globalId|$revision").take(12)}"

    private fun commandType(candidate: ReconciliationCandidate): String = when (candidate.entityType) {
        "CUSTOM_EXERCISE" -> if (candidate.deletedAt == null) "ExerciseCreated" else "ExerciseDeleted"
        "WORKOUT_TEMPLATE" -> if (candidate.deletedAt == null) "WorkoutTemplateUpdated" else "WorkoutTemplateDeleted"
        "WORKOUT_TEMPLATE_EXERCISE" -> "WorkoutTemplateExerciseUpdated"
        "WORKOUT_TEMPLATE_SET" -> "WorkoutTemplateSetUpdated"
        "TRAINING_PLAN" -> "PlannerSeriesUpdated"
        "PLANNED_WORKOUT" -> if (candidate.deletedAt == null) "PlannerOccurrenceUpdated" else "PlannerOccurrenceDeleted"
        else -> error("Unsupported reconciliation entity: ${candidate.entityType}")
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
