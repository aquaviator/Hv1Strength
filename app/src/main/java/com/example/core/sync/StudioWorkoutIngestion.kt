package com.example.core.sync

import com.example.data.*
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import java.security.MessageDigest

class StudioWorkoutContractException(val reasonCode: String) : IllegalArgumentException(reasonCode)

object StudioWorkoutContract {
    private val supportedMetrics = setOf("repetitions", "external_load", "assistance", "duration", "distance",
        "rpe", "tempo", "rir", "intervals", "side", "energy")

    private fun javascriptQuote(value: String): String = buildString {
        append('"')
        value.forEach { character -> when (character) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000c' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (character.code <= 0x1f) append("\\u%04x".format(character.code)) else append(character)
        } }
        append('"')
    }

    fun canonicalJson(value: Any?): String = when (value) {
        null -> "null"
        is Boolean, is Number -> value.toString()
        is String -> javascriptQuote(value)
        is List<*> -> value.joinToString(prefix = "[", postfix = "]", separator = ",") { canonicalJson(it) }
        is Map<*, *> -> value.entries.filter { it.key is String }.sortedBy { it.key as String }
            .joinToString(prefix = "{", postfix = "}", separator = ",") {
                "${javascriptQuote(it.key as String)}:${canonicalJson(it.value)}"
            }
        else -> throw StudioWorkoutContractException("UNSUPPORTED_PAYLOAD_VALUE")
    }

    fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    @Suppress("UNCHECKED_CAST")
    fun parse(versionId: String, value: Map<String, Any?>, expectedOwner: String, firebaseUid: String,
              appliedAt: Long = System.currentTimeMillis()): StudioWorkoutImport {
        fun requiredString(map: Map<String, Any?>, key: String) = (map[key] as? String)?.takeIf { it.isNotBlank() }
            ?: throw StudioWorkoutContractException("MISSING_${key.uppercase()}")
        val owner = requiredString(value, "humanUserId")
        if (owner != expectedOwner) throw StudioWorkoutContractException("WRONG_OWNER")
        if (requiredString(value, "versionId") != versionId) throw StudioWorkoutContractException("VERSION_ID_MISMATCH")
        if (value["contentType"] != "workout" || value["publicationState"] != "PUBLISHED" || value["tombstoneState"] != "ACTIVE")
            throw StudioWorkoutContractException("INVALID_PUBLICATION_STATE")
        if (value["schemaVersion"] != "humanv1.workout/1") throw StudioWorkoutContractException("UNSUPPORTED_SCHEMA")
        val globalId = requiredString(value, "globalId")
        if (value["sourceDraftId"] != globalId) throw StudioWorkoutContractException("SOURCE_ID_MISMATCH")
        val revision = (value["revision"] as? Number)?.toLong()?.takeIf { it >= 1 }
            ?: throw StudioWorkoutContractException("INVALID_REVISION")
        val checksum = requiredString(value, "contentChecksum")
        if (!checksum.matches(Regex("^[0-9a-f]{64}$"))) throw StudioWorkoutContractException("INVALID_CHECKSUM")
        val payload = value["payload"] as? Map<String, Any?>
            ?: throw StudioWorkoutContractException("MISSING_PAYLOAD")
        if (requiredString(payload, "workoutId") != globalId) throw StudioWorkoutContractException("WORKOUT_ID_MISMATCH")
        if (sha256(canonicalJson(payload)) != checksum) throw StudioWorkoutContractException("CHECKSUM_MISMATCH")
        val title = requiredString(payload, "title")
        val discipline = requiredString(payload, "discipline")
        val catalogueReleaseId = requiredString(payload, "catalogueReleaseId")
        val blocks = payload["blocks"] as? List<*> ?: throw StudioWorkoutContractException("MISSING_BLOCKS")
        val imported = mutableListOf<StudioImportedExercise>()
        val exerciseIds = mutableListOf<String>()

        fun parseExercise(block: Map<String, Any?>, position: Int, group: String? = null, suffix: String = "") {
            val blockId = requiredString(block, "blockId") + suffix
            val exerciseId = requiredString(block, "exerciseId")
            requiredString(block, "exerciseNameSnapshot")
            val efforts = block["efforts"] as? List<*> ?: throw StudioWorkoutContractException("MISSING_EFFORTS")
            exerciseIds += exerciseId
            val childGlobalId = "$versionId:$blockId"
            val child = WorkoutTemplateExercise(exerciseId = exerciseId, templateId = 0, position = position,
                restSeconds = 0, notes = block["notes"] as? String, supersetGroupId = group,
                globalId = childGlobalId, humanUserId = owner, revision = revision,
                syncStatus = "SYNCED", lastSyncedAt = appliedAt, originDeviceId = "studio:HUMAN_STRENGTH",
                templateGlobalId = globalId)
            val parsedEfforts = efforts.mapIndexed { effortIndex, raw ->
                val effort = raw as? Map<String, Any?> ?: throw StudioWorkoutContractException("MALFORMED_EFFORT")
                val effortId = requiredString(effort, "effortId") + suffix
                val setGlobalId = "$versionId:$effortId"
                val prescriptions = effort["prescriptions"] as? List<*>
                    ?: throw StudioWorkoutContractException("MISSING_PRESCRIPTIONS")
                val parsed = prescriptions.mapIndexed { metricIndex, rawMetric ->
                    val metric = rawMetric as? Map<String, Any?> ?: throw StudioWorkoutContractException("MALFORMED_METRIC")
                    val key = requiredString(metric, "metricKey")
                    if (key !in supportedMetrics) throw StudioWorkoutContractException("UNSUPPORTED_MANDATORY_METRIC")
                    MetricPrescriptionEntity(globalId = "$versionId:${requiredString(metric, "prescriptionId")}$suffix",
                        templateSetGlobalId = setGlobalId, metricKey = key,
                        minimumValue = (metric["minimumValue"] as? Number)?.toDouble(),
                        targetValue = (metric["targetValue"] as? Number)?.toDouble(),
                        maximumValue = (metric["maximumValue"] as? Number)?.toDouble(),
                        textValue = metric["textValue"] as? String, canonicalUnit = metric["canonicalUnit"] as? String,
                        position = (metric["position"] as? Number)?.toInt() ?: metricIndex,
                        createdAt = appliedAt, updatedAt = appliedAt)
                }
                fun target(key: String) = parsed.firstOrNull { it.metricKey == key }?.targetValue
                StudioImportedEffort(WorkoutTemplateSet(templateExerciseId = 0, position = effortIndex + 1,
                    setType = requiredString(effort, "effortType"),
                    targetRepsMin = target("repetitions")?.toInt(), targetRepsMax = target("repetitions")?.toInt(),
                    targetWeight = target("external_load")?.toFloat() ?: target("assistance")?.toFloat(),
                    targetRpe = target("rpe")?.toInt(), targetDurationSeconds = target("duration")?.toInt(),
                    targetDistance = target("distance")?.toFloat(), tempo = parsed.firstOrNull { it.metricKey == "tempo" }?.textValue,
                    notes = effort["notes"] as? String, globalId = setGlobalId, humanUserId = owner,
                    revision = revision, syncStatus = "SYNCED", lastSyncedAt = appliedAt,
                    originDeviceId = "studio:HUMAN_STRENGTH", templateExerciseGlobalId = childGlobalId), parsed)
            }
            imported += StudioImportedExercise(child, parsedEfforts)
        }

        var position = 0
        blocks.forEach { raw ->
            val block = raw as? Map<String, Any?> ?: throw StudioWorkoutContractException("MALFORMED_BLOCK")
            when (requiredString(block, "type")) {
                "EXERCISE" -> parseExercise(block, ++position)
                "SUPERSET", "CIRCUIT" -> {
                    val groupId = requiredString(block, "blockId")
                    val nested = block["exercises"] as? List<*> ?: throw StudioWorkoutContractException("MISSING_GROUP_EXERCISES")
                    val rounds = if (block["type"] == "CIRCUIT") (block["rounds"] as? Number)?.toInt()?.takeIf { it > 0 } ?: 1 else 1
                    repeat(rounds) { round -> nested.forEach { nestedRaw ->
                        parseExercise(nestedRaw as? Map<String, Any?> ?: throw StudioWorkoutContractException("MALFORMED_GROUP_EXERCISE"),
                            ++position, groupId, if (round == 0) "" else ":round${round + 1}")
                    } }
                }
                "REST", "TRANSITION", "NOTE" -> Unit
                else -> throw StudioWorkoutContractException("UNSUPPORTED_BLOCK_TYPE")
            }
        }
        if (imported.isEmpty()) throw StudioWorkoutContractException("NO_EXERCISES")
        val acknowledgementId = "strength_${sha256("HUMAN_STRENGTH|$versionId").take(32)}"
        val link = StudioWorkoutLink(versionId, owner, globalId, revision, checksum, catalogueReleaseId,
            title, payload["description"] as? String ?: "", discipline, canonicalJson(payload),
            0, revision, appliedAt, acknowledgementId = acknowledgementId)
        val template = WorkoutTemplate(name = title, exerciseIdsJson = JSONArray(exerciseIds).toString(),
            userId = firebaseUid, globalId = globalId, humanUserId = owner, revision = revision,
            syncStatus = "SYNCED", lastSyncedAt = appliedAt, originDeviceId = "studio:HUMAN_STRENGTH")
        return StudioWorkoutImport(link, template, imported)
    }
}

class StudioWorkoutIngestionRepository(private val firestore: FirebaseFirestore, private val dao: StrengthDao) {
    suspend fun synchronize(owner: String, firebaseUid: String) {
        val snapshots = firestore.collection("users").document(owner).collection("publishedWorkouts").get().await()
        val imports = snapshots.documents.map { document ->
            StudioWorkoutContract.parse(document.id, document.data ?: emptyMap(), owner, firebaseUid)
        }.sortedBy { it.link.sourceRevision }
        imports.forEach { value ->
            val result = dao.applyStudioWorkoutTransaction(value)
            acknowledge(result.link, if (result.conflict) "CONFLICT" else "APPLIED",
                if (result.conflict) "LOCAL_EDIT_PRESERVED_NEW_VERSION_CREATED" else null)
        }
    }

    private suspend fun acknowledge(link: StudioWorkoutLink, state: String, reasonCode: String?) {
        val ref = firestore.collection("users").document(link.humanUserId)
            .collection("workoutDeliveryAcks").document(link.acknowledgementId)
        firestore.runTransaction { tx ->
            val existing = tx.get(ref)
            if (existing.exists()) {
                val data = existing.data ?: emptyMap()
                require(data["humanUserId"] == link.humanUserId && data["versionId"] == link.versionId &&
                    data["appliedChecksum"] == link.contentChecksum && data["state"] == state) { "ACKNOWLEDGEMENT_CONFLICT" }
            } else {
                tx.set(ref, mapOf("schemaVersion" to 1, "acknowledgementId" to link.acknowledgementId,
                    "humanUserId" to link.humanUserId, "workoutGlobalId" to link.workoutGlobalId,
                    "versionId" to link.versionId, "applicationId" to "HUMAN_STRENGTH",
                    "appliedChecksum" to link.contentChecksum, "sourceRevision" to link.sourceRevision,
                    "state" to state, "reasonCode" to reasonCode, "clientAppliedAtMillis" to link.appliedAt,
                    "createdAt" to FieldValue.serverTimestamp()))
            }
        }.await()
        dao.markStudioAcknowledgement(link.versionId, state)
    }
}
