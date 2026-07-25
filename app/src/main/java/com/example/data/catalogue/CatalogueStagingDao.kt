package com.example.data.catalogue

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CatalogueStagingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelease(release: CatalogueStagingReleaseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(exercises: List<CatalogueStagingExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAliases(aliases: List<CatalogueStagingAliasEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchTokens(tokens: List<CatalogueStagingSearchTokenEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelationships(relationships: List<CatalogueStagingRelationshipEntity>)

    @Query("DELETE FROM catalogue_staging_release WHERE channel = :channel")
    suspend fun deleteRelease(channel: String)

    @Query("DELETE FROM catalogue_staging_exercise")
    suspend fun deleteExercises()

    @Query("SELECT * FROM catalogue_staging_release WHERE channel = :channel LIMIT 1")
    suspend fun getRelease(channel: String): CatalogueStagingReleaseEntity?

    @Query("SELECT * FROM catalogue_staging_exercise ORDER BY canonicalId")
    suspend fun getExercises(): List<CatalogueStagingExerciseEntity>

    @Query("SELECT * FROM catalogue_staging_alias ORDER BY canonicalId, normalised")
    suspend fun getAliases(): List<CatalogueStagingAliasEntity>

    @Query("SELECT * FROM catalogue_staging_relationship ORDER BY sourceCanonicalId, type")
    suspend fun getRelationships(): List<CatalogueStagingRelationshipEntity>

    @Query("""
        SELECT DISTINCT e.* FROM catalogue_staging_exercise e
        JOIN catalogue_staging_search_token t ON t.canonicalId = e.canonicalId
        WHERE t.normalised = :normalisedQuery
        ORDER BY e.displayName
    """)
    suspend fun searchExact(normalisedQuery: String): List<CatalogueStagingExerciseEntity>
}
