package com.example.data.catalogue

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RuntimeCatalogueEnvelope(
    @Json(name = "runtime_contract_version") val runtimeContractVersion: Int,
    @Json(name = "schema_version") val schemaVersion: String,
    @Json(name = "catalogue_version") val catalogueVersion: String,
    val channel: String,
    @Json(name = "distribution_scope") val distributionScope: String,
    @Json(name = "source_catalogue_commit") val sourceCatalogueCommit: String,
    @Json(name = "record_count") val recordCount: Int,
    val checksum: String,
    val exercises: List<RuntimeExerciseDto>
)

@JsonClass(generateAdapter = true)
data class RuntimeExerciseDto(
    @Json(name = "canonical_id") val canonicalId: String,
    @Json(name = "display_name") val displayName: String,
    val classification: RuntimeClassificationDto,
    val search: RuntimeSearchDto,
    val anatomy: RuntimeAnatomyDto,
    val equipment: RuntimeEquipmentDto,
    val coaching: RuntimeCoachingDto,
    val relationships: List<RuntimeRelationshipDto>
)

@JsonClass(generateAdapter = true)
data class RuntimeClassificationDto(
    val family: String,
    @Json(name = "movement_pattern") val movementPattern: String,
    val laterality: String,
    @Json(name = "compound_or_isolation") val compoundOrIsolation: String,
    val difficulty: String
)

@JsonClass(generateAdapter = true)
data class RuntimeSearchDto(
    val aliases: List<RuntimeAliasDto>,
    val keywords: List<String>
)

@JsonClass(generateAdapter = true)
data class RuntimeAliasDto(
    val value: String,
    val normalised: String,
    val type: String,
    val locale: String? = null
)

@JsonClass(generateAdapter = true)
data class RuntimeAnatomyDto(
    @Json(name = "primary_muscles") val primaryMuscles: List<String>,
    @Json(name = "secondary_muscles") val secondaryMuscles: List<String>,
    @Json(name = "stabiliser_muscles") val stabiliserMuscles: List<String>
)

@JsonClass(generateAdapter = true)
data class RuntimeEquipmentDto(
    val required: List<String>,
    val attachments: List<String>
)

@JsonClass(generateAdapter = true)
data class RuntimeCoachingDto(
    val setup: String,
    val execution: String,
    @Json(name = "common_errors") val commonErrors: String,
    val safety: String,
    @Json(name = "range_of_motion") val rangeOfMotion: String,
    @Json(name = "breathing_bracing") val breathingBracing: String
)

@JsonClass(generateAdapter = true)
data class RuntimeRelationshipDto(
    val type: String,
    @Json(name = "target_canonical_id") val targetCanonicalId: String
)
