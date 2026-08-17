package com.example.catalogue

import android.content.Context
import androidx.room.withTransaction
import com.example.BuildConfig
import com.example.data.CatalogueReleaseState
import com.example.data.StrengthDatabase
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

data class GovernedReleaseManifest(
    val releaseId: String, val schemaVersion: Int, val catalogueVersion: String,
    val exerciseCount: Int, val contentSha256: String, val status: String,
    val channel: String, val minimumStrengthVersionCode: Int, val previousReleaseId: String?
)

data class GovernedCataloguePayload(
    val manifest: GovernedReleaseManifest,
    val rawDocuments: List<Map<String, Any?>>,
    val exercises: List<CatalogueExercise>
)

enum class CatalogueSyncStatus {
    REMOTE_ACCEPTED, BUNDLED_CURRENT, UNAVAILABLE, PERMISSION_DENIED, AUTHENTICATION_FAILED,
    APP_CHECK_FAILED, TIMEOUT, INVALID_MANIFEST, INVALID_SCHEMA, COUNT_MISMATCH,
    CHECKSUM_MISMATCH, INVALID_REFERENCE, INVALID_CAPABILITY, MISSING_REQUIRED_ID,
    VERSION_INCOMPATIBLE, UNKNOWN_FAILURE
}

data class CatalogueSyncDecision(val accepted: Boolean, val status: CatalogueSyncStatus, val detail: String? = null)

data class GovernedCatalogueApplyPlan(
    val upserts: List<com.example.data.Exercise>,
    val retainedHistoricalIds: Set<String>,
    val preservedCustomIds: Set<String>,
    val customCollisions: Set<String>
)

internal class CatalogueGatewayException(
    val status: CatalogueSyncStatus
) : IllegalStateException(status.name)

fun planGovernedCatalogueApply(
    incoming: List<CatalogueExercise>, existing: List<com.example.data.Exercise>, acceptedAt: Long
): GovernedCatalogueApplyPlan {
    val custom = existing.filter { it.isCustom }
    val customIds = custom.map { it.id }.toSet()
    val governed = existing.filterNot { it.isCustom }
    val governedById = governed.associateBy { it.id }
    val incomingIds = incoming.map { it.id }.toSet()
    return GovernedCatalogueApplyPlan(
        upserts = incoming.filterNot { it.id in customIds }.map { exercise ->
            val old = governedById[exercise.id]
            val candidate = exercise.toRoom(acceptedAt).copy(createdAt = old?.createdAt ?: acceptedAt)
            if (old != null && old.name == candidate.name && old.category == candidate.category) old
            else candidate.copy(revision = (old?.revision ?: 0) + 1)
        },
        retainedHistoricalIds = governed.map { it.id }.filterNot { it in incomingIds }.toSet(),
        preservedCustomIds = customIds,
        customCollisions = customIds.intersect(incomingIds)
    )
}

object GovernedCatalogueValidator {
    private val supportedCapabilities = MeasurementCapability.entries.map { it.wireName }.toSet()

    fun validate(payload: GovernedCataloguePayload, requiredIds: Set<String> = emptySet()): CatalogueSyncDecision {
        val manifest = payload.manifest
        if (manifest.status != "published" || manifest.channel != "production")
            return CatalogueSyncDecision(false, CatalogueSyncStatus.INVALID_MANIFEST)
        if (manifest.schemaVersion != 1 || payload.rawDocuments.any { (it["schemaVersion"] as? Number)?.toInt() != 1 })
            return CatalogueSyncDecision(false, CatalogueSyncStatus.INVALID_SCHEMA)
        if (manifest.minimumStrengthVersionCode > BuildConfig.VERSION_CODE)
            return CatalogueSyncDecision(false, CatalogueSyncStatus.VERSION_INCOMPATIBLE)
        if (manifest.exerciseCount != payload.exercises.size || payload.rawDocuments.size != payload.exercises.size)
            return CatalogueSyncDecision(false, CatalogueSyncStatus.COUNT_MISMATCH)
        val ids = payload.exercises.map { it.id }
        val rawIds = payload.rawDocuments.map { it["exerciseId"] as? String }
        if (ids.distinct().size != ids.size || rawIds.any { it.isNullOrBlank() } || rawIds.distinct().size != rawIds.size || rawIds.filterNotNull().toSet() != ids.toSet())
            return CatalogueSyncDecision(false, CatalogueSyncStatus.INVALID_SCHEMA)
        val idSet = ids.toSet()
        if (!idSet.containsAll(requiredIds)) return CatalogueSyncDecision(false, CatalogueSyncStatus.MISSING_REQUIRED_ID)
        if (payload.rawDocuments.any { document ->
                val capabilities = (document["trackingCapabilities"] as? List<*>)?.mapNotNull { it as? String }.orEmpty()
                capabilities.isEmpty() || capabilities.any { it !in supportedCapabilities } ||
                    (("assisted_load" in capabilities || "weighted_bodyweight" in capabilities) && "bodyweight" !in capabilities)
            }) return CatalogueSyncDecision(false, CatalogueSyncStatus.INVALID_CAPABILITY)
        if (payload.exercises.any { item ->
                item.relatedIds.any { it !in idSet || it == item.id } ||
                    item.regressionId?.let { it !in idSet || it == item.id } == true ||
                    item.progressionId?.let { it !in idSet || it == item.id } == true ||
                    item.replacementId?.let { it !in idSet || it == item.id } == true
            }) return CatalogueSyncDecision(false, CatalogueSyncStatus.INVALID_REFERENCE)
        val actual = checksum(payload.rawDocuments)
        if (!manifest.contentSha256.equals(actual, true))
            return CatalogueSyncDecision(false, CatalogueSyncStatus.CHECKSUM_MISMATCH, actual)
        return CatalogueSyncDecision(true, CatalogueSyncStatus.REMOTE_ACCEPTED)
    }

    internal fun canonical(value: Any?): String = when (value) {
        null -> "null"
        is Boolean, is Number -> value.toString()
        is String -> "\"" + value.flatMap { char -> when (char) {
            '\\' -> listOf('\\', '\\'); '"' -> listOf('\\', '"'); '\n' -> listOf('\\', 'n')
            '\r' -> listOf('\\', 'r'); '\t' -> listOf('\\', 't'); else -> listOf(char)
        }}.joinToString("") + "\""
        is List<*> -> value.joinToString(",", "[", "]") { canonical(it) }
        is Map<*, *> -> value.entries.sortedBy { it.key.toString() }.joinToString(",", "{", "}") {
            canonical(it.key.toString()) + ":" + canonical(it.value)
        }
        else -> canonical(value.toString())
    }

    internal fun checksum(documents: List<Map<String, Any?>>) = sha256(canonical(documents.sortedBy { it["exerciseId"].toString() }))

    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}

interface GovernedCatalogueGateway { suspend fun fetch(): GovernedCataloguePayload }

class FirebaseGovernedCatalogueGateway(private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()) : GovernedCatalogueGateway {
    override suspend fun fetch(): GovernedCataloguePayload {
        val current = firestore.collection("exercise_catalogue").document("current").get().awaitTask()
        if (!current.exists()) throw CatalogueGatewayException(CatalogueSyncStatus.UNAVAILABLE)
        val releaseId = current.getString("releaseId")
            ?: throw CatalogueGatewayException(CatalogueSyncStatus.INVALID_MANIFEST)
        val release = firestore.collection("exercise_catalogue_releases").document(releaseId).get().awaitTask()
        if (!release.exists()) throw CatalogueGatewayException(CatalogueSyncStatus.INVALID_MANIFEST)
        val documents = firestore.collection("exercise_catalogue_releases").document(releaseId)
            .collection("exercises").get().awaitTask().documents.map { it.data.orEmpty() }
        val data = release.data.orEmpty()
        return try {
            GovernedCataloguePayload(data.toManifest(), documents, documents.map(::toExercise))
        } catch (_: Exception) {
            throw CatalogueGatewayException(CatalogueSyncStatus.INVALID_MANIFEST)
        }
    }

    private fun Map<String, Any?>.toManifest() = GovernedReleaseManifest(
        string("releaseId"), int("schemaVersion"), string("catalogueVersion"), int("exerciseCount"),
        string("contentSha256"), string("status"), string("channel"), int("minimumStrengthVersionCode"),
        this["previousReleaseId"] as? String
    )

    private fun toExercise(data: Map<String, Any?>) = CatalogueExercise(
        id = data.string("exerciseId"), name = data.string("displayName"), aliases = data.strings("aliases"),
        category = data.string("category"), primaryMuscles = data.strings("primaryMuscles"),
        secondaryMuscles = data.strings("secondaryMuscles"), equipment = data.strings("equipment"), type = data.string("exerciseType"),
        capabilities = data.strings("trackingCapabilities").mapNotNull { wire -> MeasurementCapability.entries.find { it.wireName == wire } }.toSet(),
        laterality = data.string("laterality"), bodyweight = "bodyweight" in data.strings("trackingCapabilities"),
        active = data["deprecated"] != true, replacementId = data["replacementExerciseId"] as? String,
        movementPattern = data.strings("movementPatterns").firstOrNull().orEmpty(), setup = data.string("setupInstructions"),
        steps = data.strings("executionInstructions"), breathing = data.string("breathingGuidance"),
        cues = data.strings("techniqueCues"), mistakes = data.strings("commonMistakes"), safety = data.string("safetyGuidance"),
        regressionId = data.strings("regressionIds").firstOrNull(), progressionId = data.strings("progressionIds").firstOrNull(),
        relatedIds = data.strings("relatedExerciseIds"), intelligence = ExerciseIntelligence(
            purpose = data.optionalString("purpose"), secondaryCategories = data.strings("secondaryCategories"),
            modalities = data.strings("modalities"), stabilizers = data.strings("stabilizers"), jointActions = data.strings("jointActions"),
            movementPlane = data.optionalString("movementPlane"), kineticChain = data.optionalString("kineticChain"),
            contractionEmphasis = data.optionalString("contractionEmphasis"), rangeOfMotionNotes = data.optionalString("rangeOfMotionNotes"),
            forceVector = data.optionalString("forceVector"), biomechanicalRationale = data.optionalString("biomechanicalRationale"),
            optionalEquipment = data.strings("optionalEquipment"), substitutableEquipment = data.strings("substitutableEquipment"),
            environmentSuitability = data.strings("environmentSuitability"), skillLevel = data.optionalString("skillLevel"),
            compoundClassification = data.optionalString("compoundClassification"), programmingGuidance = data.strings("programmingGuidance"),
            typicalUseCases = data.strings("typicalUseCases"), contraindications = data.strings("contraindications"),
            cautions = data.strings("cautions"), stopConditions = data.strings("stopConditions"), clinicalSupervision = data.strings("clinicalSupervision"),
            evidence = data.maps("evidence").map { EvidenceClaim(it.optionalString("claim"), it.optionalString("citation"), it.optionalString("sourceUrl"), it.optionalString("reviewedAt"), it.optionalString("reviewer")) }
        )
    )
}

class GovernedCatalogueCoordinator(
    private val database: StrengthDatabase,
    private val gateway: GovernedCatalogueGateway,
    private val now: () -> Long = System::currentTimeMillis
) {
    suspend fun synchronize(bundled: CatalogueSnapshot): CatalogueSyncDecision {
        val checkedAt = now()
        val payload = try { withTimeout(10_000) { gateway.fetch() } }
        catch (error: Throwable) {
            val status = classify(error)
            recordFailure(bundled, checkedAt, status)
            return CatalogueSyncDecision(false, status)
        }
        val decision = GovernedCatalogueValidator.validate(payload, bundled.exercises.map { it.id }.toSet())
        if (!decision.accepted) { recordFailure(bundled, checkedAt, decision.status); return decision }
        val dao = database.strengthDao()
        val existing = dao.getAllExercisesSync()
        val acceptedAt = now()
        val plan = planGovernedCatalogueApply(payload.exercises, existing, acceptedAt)
        database.withTransaction {
            dao.insertExercises(plan.upserts)
            dao.insertCatalogueReleaseState(CatalogueReleaseState(
                bundledVersion = bundled.metadata.catalogueVersion, acceptedReleaseId = payload.manifest.releaseId,
                acceptedCatalogueVersion = payload.manifest.catalogueVersion, acceptedChecksum = payload.manifest.contentSha256,
                acceptedSchemaVersion = payload.manifest.schemaVersion, source = "REMOTE", status = decision.status.name,
                previousReleaseId = payload.manifest.previousReleaseId, lastCheckAt = checkedAt, lastSuccessAt = acceptedAt
            ))
        }
        ExerciseCatalogueRuntime.accept(CatalogueSnapshot(
            CatalogueMetadata(1, payload.manifest.catalogueVersion, payload.manifest.channel, "", "human-v1-governed-firestore", payload.exercises.size, payload.manifest.contentSha256),
            payload.exercises, CatalogueValidation(true, emptyList())
        ))
        return decision
    }

    private suspend fun recordFailure(bundled: CatalogueSnapshot, checkedAt: Long, status: CatalogueSyncStatus) {
        val dao = database.strengthDao(); val previous = dao.getCatalogueReleaseState()
        dao.insertCatalogueReleaseState((previous ?: CatalogueReleaseState(bundledVersion = bundled.metadata.catalogueVersion)).copy(
            lastCheckAt = checkedAt, status = status.name, lastFailure = status.name
        ))
    }

    private fun classify(error: Throwable): CatalogueSyncStatus {
        if (error is CatalogueGatewayException) return error.status
        val text = generateSequence(error) { it.cause }.joinToString(" ") { it.message.orEmpty() }.lowercase()
        return when {
            error is kotlinx.coroutines.TimeoutCancellationException -> CatalogueSyncStatus.TIMEOUT
            "permission" in text -> CatalogueSyncStatus.PERMISSION_DENIED
            "app check" in text || "appcheck" in text -> CatalogueSyncStatus.APP_CHECK_FAILED
            "auth" in text -> CatalogueSyncStatus.AUTHENTICATION_FAILED
            "network" in text || "unavailable" in text || "offline" in text || "interrupted" in text -> CatalogueSyncStatus.UNAVAILABLE
            else -> CatalogueSyncStatus.UNKNOWN_FAILURE
        }
    }
}

object GovernedCatalogueSync {
    suspend fun start(context: Context, database: StrengthDatabase): CatalogueSyncDecision {
        val bundled = ExerciseCatalogueRuntime.snapshot ?: ExerciseCatalogueRuntime.load(context)
        val decision = GovernedCatalogueCoordinator(database, FirebaseGovernedCatalogueGateway()).synchronize(bundled)
        val snapshot = ExerciseCatalogueRuntime.snapshot ?: bundled
        context.getSharedPreferences("strength_catalogue", Context.MODE_PRIVATE).edit()
            .putString("update_status", decision.status.name)
            .putLong("last_update_check", System.currentTimeMillis())
            .putString("source", if (snapshot.metadata.sourceId == "human-v1-governed-firestore") "GOVERNED" else "BUNDLED")
            .apply {
                if (decision.accepted) putLong("last_update_success", System.currentTimeMillis())
            }.apply()
        return decision
    }
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (!continuation.isActive) return@addOnCompleteListener
        val error = task.exception
        if (error != null) continuation.resumeWith(Result.failure(error)) else continuation.resume(task.result)
    }
}

private fun Map<String, Any?>.string(key: String) = this[key] as? String ?: error("$key missing")
private fun Map<String, Any?>.int(key: String) = (this[key] as? Number)?.toInt() ?: error("$key missing")
private fun Map<String, Any?>.strings(key: String) = (this[key] as? List<*>)?.mapNotNull { it as? String }.orEmpty()
private fun Map<String, Any?>.optionalString(key: String) = this[key] as? String ?: ""
private fun Map<String, Any?>.maps(key: String) = (this[key] as? List<*>)?.mapNotNull { it as? Map<String, Any?> }.orEmpty()
