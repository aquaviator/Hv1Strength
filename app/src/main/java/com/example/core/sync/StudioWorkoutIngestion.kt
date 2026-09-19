package com.example.core.sync

import com.example.data.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
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
        is Map<*, *> -> value.entries.filter { it.key is String && it.key != "checksum" }.sortedBy { it.key as String }
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
        val canonicalContract = value["schemaVersion"] == "humanv1.canonical-workout/1"
        if (!canonicalContract && value["schemaVersion"] != "humanv1.workout/1") throw StudioWorkoutContractException("UNSUPPORTED_SCHEMA")
        val globalId = requiredString(value, "globalId")
        if (value["sourceDraftId"] != globalId) throw StudioWorkoutContractException("SOURCE_ID_MISMATCH")
        val revision = (value["revision"] as? Number)?.toLong()?.takeIf { it >= 1 }
            ?: throw StudioWorkoutContractException("INVALID_REVISION")
        val checksum = requiredString(value, "contentChecksum")
        if (!checksum.matches(Regex("^[0-9a-f]{64}$"))) throw StudioWorkoutContractException("INVALID_CHECKSUM")
        val sourcePayload = value["payload"] as? Map<String, Any?>
            ?: throw StudioWorkoutContractException("MISSING_PAYLOAD")
        if (canonicalContract && sourcePayload["schemaVersion"] != "humanv1.canonical-workout/1") throw StudioWorkoutContractException("UNSUPPORTED_PAYLOAD_SCHEMA")
        if (canonicalContract && requiredString(sourcePayload, "workoutGlobalId") != globalId) throw StudioWorkoutContractException("WORKOUT_ID_MISMATCH")
        if (sha256(canonicalJson(sourcePayload)) != checksum) throw StudioWorkoutContractException("CHECKSUM_MISMATCH")
        val payload = if (canonicalContract) canonicalToLegacy(sourcePayload) else sourcePayload
        if (requiredString(payload, "workoutId") != globalId) throw StudioWorkoutContractException("WORKOUT_ID_MISMATCH")
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
                fun distanceInKilometres(): Float? = parsed.firstOrNull { it.metricKey == "distance" }?.let { metric ->
                    val value = metric.targetValue ?: return@let null
                    when (metric.canonicalUnit) {
                        "m", "metre", "metres" -> (value / 1000.0).toFloat()
                        "km", "kilometre", "kilometres", null -> value.toFloat()
                        "mi", "mile", "miles" -> (value * 1.609344).toFloat()
                        else -> throw StudioWorkoutContractException("UNSUPPORTED_DISTANCE_UNIT")
                    }
                }
                StudioImportedEffort(WorkoutTemplateSet(templateExerciseId = 0, position = effortIndex + 1,
                    setType = requiredString(effort, "effortType"),
                    targetRepsMin = target("repetitions")?.toInt(), targetRepsMax = target("repetitions")?.toInt(),
                    targetWeight = target("external_load")?.toFloat() ?: target("assistance")?.toFloat(),
                    targetRpe = target("rpe")?.toInt(), targetDurationSeconds = target("duration")?.toInt(),
                    targetDistance = distanceInKilometres(), tempo = parsed.firstOrNull { it.metricKey == "tempo" }?.textValue,
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

    @Suppress("UNCHECKED_CAST")
    private fun canonicalToLegacy(payload: Map<String, Any?>): Map<String, Any?> {
        fun metrics(set: Map<String, Any?>): List<Map<String, Any?>> = buildList {
            val repetitions = set["repetitions"] as? Map<String, Any?>
            repetitions?.get("target")?.let { add(mapOf("prescriptionId" to "${set["setId"]}:reps", "metricKey" to "repetitions", "targetValue" to it, "canonicalUnit" to "count")) }
            set["durationSeconds"]?.let { add(mapOf("prescriptionId" to "${set["setId"]}:duration", "metricKey" to "duration", "targetValue" to it, "canonicalUnit" to "s")) }
            set["distanceMetres"]?.let { add(mapOf("prescriptionId" to "${set["setId"]}:distance", "metricKey" to "distance", "targetValue" to it, "canonicalUnit" to "m")) }
            (set["load"] as? Map<String, Any?>)?.let { add(mapOf("prescriptionId" to "${set["setId"]}:load", "metricKey" to "external_load", "targetValue" to it["value"], "canonicalUnit" to it["unit"])) }
            (set["intensity"] as? Map<String, Any?>)?.let { intensity -> add(mapOf("prescriptionId" to "${set["setId"]}:intensity", "metricKey" to intensity["scale"].toString().lowercase(), "textValue" to intensity["target"])) }
            set["tempo"]?.let { add(mapOf("prescriptionId" to "${set["setId"]}:tempo", "metricKey" to "tempo", "textValue" to it)) }
        }
        val blocks: List<Map<String, Any?>> = (payload["blocks"] as? List<*>)?.flatMap { raw ->
            val block = raw as Map<String, Any?>
            if (block["structure"] == "TRANSITION") emptyList<Map<String, Any?>>() else (block["placements"] as? List<*>)?.map { placementRaw ->
                val placement = placementRaw as Map<String, Any?>
                val reference = placement["exerciseReference"] as? Map<String, Any?> ?: emptyMap()
                mapOf("blockId" to placement["placementId"], "type" to "EXERCISE", "exerciseId" to reference["exerciseId"],
                    "exerciseNameSnapshot" to reference["exerciseId"], "notes" to placement["instructions"],
                    "efforts" to ((placement["sets"] as? List<*>)?.map { setRaw -> val set = setRaw as Map<String, Any?>
                        mapOf("effortId" to set["setId"], "effortType" to "WORKING", "restAfterSeconds" to set["restAfterSeconds"], "prescriptions" to metrics(set)) } ?: emptyList()))
            } ?: emptyList()
        } ?: emptyList()
        return mapOf("schemaVersion" to "humanv1.workout/1", "workoutId" to payload["workoutGlobalId"], "title" to payload["title"],
            "description" to payload["description"], "discipline" to payload["discipline"], "catalogueReleaseId" to "canonical", "blocks" to blocks)
    }
}

data class StudioWorkoutSyncSummary(val applied: Int, val requiresAttention: Int)
internal data class ParsedStudioWorkoutBatch(val imports: List<StudioWorkoutImport>, val requiresAttention: Int)

internal fun parseStudioWorkoutBatch(documents: List<Pair<String, Map<String, Any?>>>, owner: String,
                                     firebaseUid: String): ParsedStudioWorkoutBatch {
    val imports = mutableListOf<StudioWorkoutImport>()
    var requiresAttention = 0
    documents.sortedBy { (_, data) -> (data["revision"] as? Number)?.toLong() ?: Long.MAX_VALUE }
        .forEach { (id, data) ->
            try { imports += StudioWorkoutContract.parse(id, data, owner, firebaseUid) }
            catch (_: StudioWorkoutContractException) { requiresAttention++ }
        }
    return ParsedStudioWorkoutBatch(imports, requiresAttention)
}

class StudioWorkoutIngestionRepository(private val firestore: FirebaseFirestore, private val dao: StrengthDao,
    private val acknowledgementOverride: (suspend (StudioWorkoutLink, String, String?) -> Unit)? = null) {
    suspend fun synchronize(owner: String, firebaseUid: String): StudioWorkoutSyncSummary {
        val snapshots = firestore.collection("users").document(owner).collection("publishedWorkouts").get().await()
        var applied = 0
        val batch = parseStudioWorkoutBatch(snapshots.documents.map { it.id to (it.data ?: emptyMap()) }, owner, firebaseUid)
        batch.imports.forEach { value ->
            val result = dao.applyStudioWorkoutTransaction(value)
            if (result.applied) applied++
            acknowledge(result.link, if (result.conflict) "CONFLICT" else "APPLIED",
                if (result.conflict) "LOCAL_EDIT_PRESERVED_NEW_VERSION_CREATED" else null)
        }
        return StudioWorkoutSyncSummary(applied, batch.requiresAttention)
    }

    private suspend fun acknowledge(link: StudioWorkoutLink, state: String, reasonCode: String?) {
        acknowledgementOverride?.let { it(link, state, reasonCode); dao.markStudioAcknowledgement(link.versionId, state); return }
        FirebaseFunctions.getInstance("europe-west1").getHttpsCallable("acknowledgeStudioDelivery").call(mapOf(
            "entityType" to "workout", "acknowledgementId" to link.acknowledgementId, "globalId" to link.workoutGlobalId,
            "versionId" to link.versionId, "checksum" to link.contentChecksum, "sourceRevision" to link.sourceRevision,
            "state" to state, "reasonCode" to reasonCode, "clientAppliedAtMillis" to link.appliedAt)).await()
        dao.markStudioAcknowledgement(link.versionId, state)
    }
}
