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
    class InvalidMeasurementMode(message: String) : CatalogueImportException(message)
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
    private val semanticsAdapter = moshi.adapter<RuntimeExerciseSemanticsDto>()
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
            validateMeasurementModes(exercise)
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

    private fun validateMeasurementModes(exercise: RuntimeExerciseDto) {
        val modes = exercise.measurementModes
        if (modes.isEmpty() || modes.count { it.isDefault } != 1) {
            throw CatalogueImportException.InvalidMeasurementMode(
                "${exercise.canonicalId} requires exactly one default measurement mode"
            )
        }
        if (modes.map { it.modeId }.any { it.isBlank() } ||
            modes.map { it.modeId }.toSet().size != modes.size
        ) {
            throw CatalogueImportException.InvalidMeasurementMode(
                "${exercise.canonicalId} measurement mode IDs must be non-empty and unique"
            )
        }
        modes.forEach { mode ->
            if (mode.measurementSchemaVersion != MEASUREMENT_SCHEMA_VERSION) {
                throw CatalogueImportException.InvalidMeasurementMode(
                    "${exercise.canonicalId}/${mode.modeId} has unsupported measurement schema"
                )
            }
            if (mode.loadSemantics !in LOAD_SEMANTICS) {
                throw CatalogueImportException.InvalidMeasurementMode(
                    "${exercise.canonicalId}/${mode.modeId} has unknown load semantics"
                )
            }
            val fields = mode.required + mode.optional
            if (fields.isEmpty() || fields.map { it.measurement }.toSet().size != fields.size) {
                throw CatalogueImportException.InvalidMeasurementMode(
                    "${exercise.canonicalId}/${mode.modeId} has empty or duplicate measurements"
                )
            }
            fields.forEach { field ->
                val expectedUnit = CANONICAL_UNITS[field.measurement]
                    ?: throw CatalogueImportException.InvalidMeasurementMode(
                        "${exercise.canonicalId}/${mode.modeId} has unknown measurement ${field.measurement}"
                    )
                if (field.unit != expectedUnit) {
                    throw CatalogueImportException.InvalidMeasurementMode(
                        "${exercise.canonicalId}/${mode.modeId} has invalid unit for ${field.measurement}"
                    )
                }
            }
            val measurements = fields.map { it.measurement }.toSet()
            when (mode.loadSemantics) {
                "external_load", "added_load" -> requireMeasurement(exercise, mode, measurements, "load")
                "assistance" -> {
                    requireMeasurement(exercise, mode, measurements, "assistance")
                    if ("load" in measurements) invalidCombination(exercise, mode)
                }
                "none", "bodyweight" -> if (measurements.any { it == "load" || it == "assistance" }) {
                    invalidCombination(exercise, mode)
                }
            }
            mode.derivedMetrics.forEach { metric ->
                if (metric !in DERIVED_METRICS ||
                    !measurements.containsAll(setOf("distance", "duration"))
                ) {
                    throw CatalogueImportException.InvalidMeasurementMode(
                        "${exercise.canonicalId}/${mode.modeId} has invalid derived metric $metric"
                    )
                }
            }
        }
    }

    private fun requireMeasurement(
        exercise: RuntimeExerciseDto,
        mode: RuntimeMeasurementModeDto,
        measurements: Set<String>,
        required: String
    ) {
        if (required !in measurements) {
            throw CatalogueImportException.InvalidMeasurementMode(
                "${exercise.canonicalId}/${mode.modeId} requires $required"
            )
        }
    }

    private fun invalidCombination(
        exercise: RuntimeExerciseDto,
        mode: RuntimeMeasurementModeDto
    ): Nothing = throw CatalogueImportException.InvalidMeasurementMode(
        "${exercise.canonicalId}/${mode.modeId} has contradictory load semantics"
    )

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
        semanticsAdapter.toJson(
            RuntimeExerciseSemanticsDto(
                classification = exercise.classification,
                measurementModes = exercise.measurementModes
            )
        ),
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
        const val SUPPORTED_CONTRACT = 2
        const val MEASUREMENT_SCHEMA_VERSION = 1
        const val PILOT_CHANNEL = "pilot_staging"
        const val PILOT_ASSET = "pilot-staging-v2.json"

        val CANONICAL_UNITS = mapOf(
            "reps" to "count",
            "load" to "kilograms",
            "duration" to "seconds",
            "distance" to "metres",
            "rpe" to "rpe_scale",
            "assistance" to "kilograms",
            "calories" to "kilocalories",
            "power" to "watts",
            "cadence" to "repetitions_per_minute",
            "heart_rate" to "beats_per_minute",
            "resistance" to "resistance_level",
            "speed" to "metres_per_second",
            "pace" to "seconds_per_metre",
            "count" to "count",
            "vertical_distance" to "metres"
        )
        val LOAD_SEMANTICS = setOf(
            "none", "external_load", "added_load", "assistance", "bodyweight"
        )
        val DERIVED_METRICS = setOf("pace", "speed")

        fun loadFixture(context: Context): String =
            context.assets.open(PILOT_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }

        fun normalise(value: String): String =
            value.lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9]+"), " ")
                .trim()
                .replace(Regex("\\s+"), " ")
    }
}
