package com.example.data.catalogue

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.ColumnInfo

@Entity(tableName = "catalogue_staging_release", primaryKeys = ["channel"])
data class CatalogueStagingReleaseEntity(
    val channel: String,
    val catalogueVersion: String,
    val runtimeContractVersion: Int,
    val schemaVersion: String,
    val checksum: String,
    val recordCount: Int,
    val sourceCatalogueCommit: String,
    val distributionScope: String
)

@Entity(tableName = "catalogue_staging_exercise", primaryKeys = ["canonicalId"])
data class CatalogueStagingExerciseEntity(
    val canonicalId: String,
    val displayName: String,
    val family: String,
    val movementPattern: String,
    val laterality: String,
    val compoundOrIsolation: String,
    val difficulty: String,
    @ColumnInfo(name = "classificationJson")
    val semanticsJson: String,
    val anatomyJson: String,
    val equipmentJson: String,
    val coachingJson: String,
    val keywordsJson: String,
    val relationshipsJson: String
)

@Entity(
    tableName = "catalogue_staging_alias",
    primaryKeys = ["canonicalId", "normalised", "type"],
    foreignKeys = [ForeignKey(
        entity = CatalogueStagingExerciseEntity::class,
        parentColumns = ["canonicalId"],
        childColumns = ["canonicalId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("canonicalId"), Index("normalised")]
)
data class CatalogueStagingAliasEntity(
    val canonicalId: String,
    val value: String,
    val normalised: String,
    val type: String,
    val locale: String?
)

@Entity(
    tableName = "catalogue_staging_search_token",
    primaryKeys = ["canonicalId", "normalised", "source"],
    foreignKeys = [ForeignKey(
        entity = CatalogueStagingExerciseEntity::class,
        parentColumns = ["canonicalId"],
        childColumns = ["canonicalId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("canonicalId"), Index("normalised")]
)
data class CatalogueStagingSearchTokenEntity(
    val canonicalId: String,
    val normalised: String,
    val source: String
)

@Entity(
    tableName = "catalogue_staging_relationship",
    primaryKeys = ["sourceCanonicalId", "targetCanonicalId", "type"],
    foreignKeys = [
        ForeignKey(
            entity = CatalogueStagingExerciseEntity::class,
            parentColumns = ["canonicalId"],
            childColumns = ["sourceCanonicalId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CatalogueStagingExerciseEntity::class,
            parentColumns = ["canonicalId"],
            childColumns = ["targetCanonicalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sourceCanonicalId"), Index("targetCanonicalId")]
)
data class CatalogueStagingRelationshipEntity(
    val sourceCanonicalId: String,
    val targetCanonicalId: String,
    val type: String
)
