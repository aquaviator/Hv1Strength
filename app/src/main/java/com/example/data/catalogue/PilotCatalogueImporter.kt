package com.example.data.catalogue

import android.content.Context
import androidx.room.withTransaction
import com.example.data.StrengthDatabase
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.security.MessageDigest
import java.util.Locale

sealed class CatalogueImportException(message: String) : IllegalArgumentException(message) {
    class Parse(cause: Throwable) : CatalogueImportException("Invalid runtime catalogue JSON: ${cause.message}")
    class UnsupportedRuntimeContractVersion(actual: Int) :
        CatalogueImportException("Unsupported runtime contract version: $actual")
    class WrongChannel(actual: String) : CatalogueImportException("Expected pilot_staging channel, found: $actual")
    class ChecksumMismatch : CatalogueImportException("Runtime catalogue checksum does not match its payload")
    class RecordCountMismatch : CatalogueImportException("Runtime catalogue record_count does not match exercises")
    class InvalidIdentity(message: String) : CatalogueImportException(message)
    class UnknownRelationship(source: String, target: String) :
        CatalogueImportException("Unknown relationship target $target from $source")
}

enum class CatalogueImportOutcome { IMPORTED, UNCHANGED }

@OptIn(ExperimentalStdlibApi::class)
class PilotCatalogueImporter(
    private val database: StrengthDatabase,
    moshi: Moshi = Moshi.Builder().build()
) {
    private val dao = database.catalogueStagingDao()
    private val envelopeAdapter = moshi.adapter<RuntimeCatalogueEnvelope>()
    private val classificationAdapter = moshi.adapter<RuntimeClassificationDto>()
    private val anatomyAdapter = moshi.adapter<RuntimeAnatomyDto>()
    private val equipmentAdapter = moshi.adapter<RuntimeEquipmentDto>()
    private val coachingAdapter = moshi.adapter<RuntimeCoachingDto>()
    private val stringsAdapter = moshi.adapter<List<String>>()
    private val relationshipsAdapter = moshi.adapter<List<RuntimeRelationshipDto>>()

    suspend fun import(json: String): CatalogueImportOutcome {
        val envelope = parseAndVerify(json)
        val existing = dao.getRelease(PILOT_CHANNEL)
        if (existing?.catalogueVersion == envelope.catalogueVersion &&
            existing.checksum == envelope.checksum
        ) return CatalogueImportOutcome.UNCHANGED

        database.withTransaction {
            dao.deleteExercises()
            dao.deleteRelease(PILOT_CHANNEL)
            dao.insertRelease(envelope.toEntity())
            dao.insertExercises(envelope.exercises.map(::toEntity))
            dao.insertAliases(envelope.exercises.flatMap { exercise ->
                exercise.search.aliases.map { alias ->
                    CatalogueStagingAliasEntity(
                        exercise.canonicalId, alias.value, alias.normalised, alias.type, alias.locale
                    )
                }
            })
            dao.insertSearchTokens(envelope.exercises.flatMap(::searchTokens))
            dao.insertRelationships(envelope.exercises.flatMap { exercise ->
                exercise.relationships.map { relationship ->
                    CatalogueStagingRelationshipEntity(
                        exercise.canonicalId, relationship.targetCanonicalId, relationship.type
                    )
                }
            })
        }
        return CatalogueImportOutcome.IMPORTED
    }

    fun parseAndVerify(json: String): RuntimeCatalogueEnvelope {
        val envelope = try {
            envelopeAdapter.fromJson(json) ?: throw IllegalArgumentException("empty document")
        } catch (error: Throwable) {
            throw CatalogueImportException.Parse(error)
        }
        if (envelope.runtimeContractVersion != SUPPORTED_CONTRACT) {
            throw CatalogueImportException.UnsupportedRuntimeContractVersion(envelope.runtimeContractVersion)
        }
        if (envelope.channel != PILOT_CHANNEL) throw CatalogueImportException.WrongChannel(envelope.channel)
        if (!checksumMatches(json, envelope.checksum)) throw CatalogueImportException.ChecksumMismatch()
        if (envelope.recordCount != envelope.exercises.size) throw CatalogueImportException.RecordCountMismatch()

        val ids = envelope.exercises.map { it.canonicalId }
        if (ids.any { it.isBlank() } || ids.toSet().size != ids.size) {
            throw CatalogueImportException.InvalidIdentity("Canonical IDs must be non-empty and unique")
        }
        val idSet = ids.toSet()
        envelope.exercises.forEach { exercise ->
            exercise.relationships.forEach { relationship ->
                if (relationship.targetCanonicalId !in idSet) {
                    throw CatalogueImportException.UnknownRelationship(
                        exercise.canonicalId, relationship.targetCanonicalId
                    )
                }
            }
        }
        return envelope
    }

    private fun checksumMatches(json: String, expected: String): Boolean {
        val root = JSONTokener(json).nextValue() as? JSONObject ?: return false
        root.remove("checksum")
        val bytes = canonicalJson(root).toByteArray(Charsets.UTF_8)
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
        return actual.equals(expected, ignoreCase = true)
    }

    private fun canonicalJson(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence().toList().sorted()
            .joinToString(prefix = "{", postfix = "}", separator = ",") { key ->
                "${JSONObject.quote(key)}:${canonicalJson(value.get(key))}"
            }
        is JSONArray -> (0 until value.length()).joinToString(
            prefix = "[", postfix = "]", separator = ","
        ) { canonicalJson(value.get(it)) }
        is String -> JSONObject.quote(value)
        is Boolean, is Number -> value.toString()
        else -> error("Unsupported JSON value: ${value::class.java.name}")
    }

    private fun RuntimeCatalogueEnvelope.toEntity() = CatalogueStagingReleaseEntity(
        channel, catalogueVersion, runtimeContractVersion, schemaVersion, checksum,
        recordCount, sourceCatalogueCommit, distributionScope
    )

    private fun toEntity(exercise: RuntimeExerciseDto) = CatalogueStagingExerciseEntity(
        exercise.canonicalId,
        exercise.displayName,
        exercise.classification.family,
        exercise.classification.movementPattern,
        exercise.classification.laterality,
        exercise.classification.compoundOrIsolation,
        exercise.classification.difficulty,
        classificationAdapter.toJson(exercise.classification),
        anatomyAdapter.toJson(exercise.anatomy),
        equipmentAdapter.toJson(exercise.equipment),
        coachingAdapter.toJson(exercise.coaching),
        stringsAdapter.toJson(exercise.search.keywords),
        relationshipsAdapter.toJson(exercise.relationships)
    )

    private fun searchTokens(exercise: RuntimeExerciseDto): List<CatalogueStagingSearchTokenEntity> =
        buildList {
            add(CatalogueStagingSearchTokenEntity(exercise.canonicalId, normalise(exercise.displayName), "name"))
            exercise.search.aliases.forEach {
                add(CatalogueStagingSearchTokenEntity(exercise.canonicalId, it.normalised, "alias"))
            }
            exercise.search.keywords.forEach {
                add(CatalogueStagingSearchTokenEntity(exercise.canonicalId, normalise(it), "keyword"))
            }
        }.distinct()

    companion object {
        const val SUPPORTED_CONTRACT = 1
        const val PILOT_CHANNEL = "pilot_staging"
        const val PILOT_ASSET = "pilot-staging-v1.json"

        fun loadFixture(context: Context): String =
            context.assets.open(PILOT_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }

        fun normalise(value: String): String =
            value.lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9]+"), " ")
                .trim()
                .replace(Regex("\\s+"), " ")
    }
}
