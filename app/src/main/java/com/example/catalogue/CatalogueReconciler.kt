package com.example.catalogue

import android.content.Context
import androidx.room.withTransaction
import com.example.data.StrengthDatabase

data class ReconciliationResult(val applied: Boolean, val inserted: Int, val updated: Int,
    val customPreserved: Int, val collisions: List<String>, val fallbackActive: Boolean)

class CatalogueReconciler(private val context: Context, private val database: StrengthDatabase) {
    suspend fun reconcile(): ReconciliationResult {
        val loaded = ExerciseCatalogueRuntime.load(context)
        val dao = database.strengthDao()
        val existing = dao.getAllExercisesSync()
        val governedExisting = existing.filterNot { it.isCustom }
        val custom = existing.filter { it.isCustom }
        if (!loaded.validation.valid) {
            if (governedExisting.isNotEmpty()) return ReconciliationResult(false, 0, 0, custom.size, emptyList(), false)
            val fallback = ExerciseCatalogueRuntime.fallback(loaded.validation.errors.joinToString())
            database.withTransaction { dao.insertExercises(fallback.exercises.map { it.toRoom(System.currentTimeMillis()) }) }
            return ReconciliationResult(true, fallback.exercises.size, 0, custom.size, emptyList(), true)
        }
        val customIds = custom.map { it.id }.toSet()
        val candidates = loaded.exercises.filterNot { it.id in customIds }
        val existingById = governedExisting.associateBy { it.id }
        val now = System.currentTimeMillis()
        database.withTransaction { dao.insertExercises(candidates.map { item ->
            val old = existingById[item.id]
            item.toRoom(now).copy(createdAt = old?.createdAt ?: now, revision = old?.revision ?: 1)
        }) }
        context.getSharedPreferences("strength_catalogue", Context.MODE_PRIVATE).edit()
            .putString("version", loaded.metadata.catalogueVersion).putLong("last_reconciled", now).putBoolean("fallback", false).apply()
        return ReconciliationResult(true, candidates.count { it.id !in existingById }, candidates.count { it.id in existingById },
            custom.size, loaded.exercises.map { it.id }.filter { it in customIds }, false)
    }
}
