package com.example.data

import android.content.Context
import androidx.room.withTransaction

/** Fixed-scope reset for the debug acceptance dataset. It accepts no caller-controlled IDs. */
internal object SyntheticAcceptanceReset {
    private const val LEGACY = "human_legacysynthetic0000000000000000"

    suspend fun reset(context: Context): ResetTotals {
        check(DebugAcceptanceIdentity.hasValidAcceptanceMarker(context))
        val database = StrengthDatabase.getDatabase(context, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO))
        val sql = database.openHelper.writableDatabase
        var removed = 0
        database.withTransaction {
            val allowedHumans = arrayOf(LEGACY, DebugAcceptanceIdentity.SYNTHETIC_HUMAN)
            check(count(sql, "SELECT COUNT(*) FROM user_profile WHERE id != ? OR humanUserId NOT IN (?, ?)",
                arrayOf(DebugAcceptanceIdentity.SYNTHETIC_UID, *allowedHumans)) == 0)
            val journal = database.strengthDao().getMigrationState()
            check(journal == null || (journal.sourceProfileId == DebugAcceptanceIdentity.SYNTHETIC_UID &&
                journal.sourceHumanUserId in allowedHumans && journal.targetHumanUserId == DebugAcceptanceIdentity.SYNTHETIC_HUMAN))
            val ownershipTables = listOf("body_weight", "tape_measurement", "workout_template", "workout_session",
                "training_plan", "planned_workout")
            ownershipTables.forEach { table ->
                check(count(sql, "SELECT COUNT(*) FROM $table WHERE (userId IS NOT NULL AND userId NOT IN ('', ?, 'global')) OR (humanUserId NOT IN ('', ?, ?, 'global'))",
                    arrayOf(DebugAcceptanceIdentity.SYNTHETIC_UID, *allowedHumans)) == 0)
            }
            listOf("logged_set", "workout_template_exercise", "workout_template_set", "command_queue").forEach { table ->
                check(count(sql, "SELECT COUNT(*) FROM $table WHERE humanUserId NOT IN ('', ?, ?, 'global')", allowedHumans) == 0)
            }
            check(count(sql, "SELECT COUNT(*) FROM exercise WHERE isCustom = 1 AND humanUserId NOT IN (?, ?)", allowedHumans) == 0)

            val humanOnly = listOf("command_queue", "logged_set", "workout_template_set", "workout_template_exercise")
            humanOnly.forEach { table ->
                removed += count(sql, "SELECT COUNT(*) FROM $table WHERE humanUserId IN (?, ?)", allowedHumans)
                sql.execSQL("DELETE FROM $table WHERE humanUserId IN (?, ?)", allowedHumans)
            }
            val owned = listOf("planned_workout", "training_plan", "workout_session", "workout_template",
                "tape_measurement", "body_weight")
            owned.forEach { table ->
                val args = arrayOf(DebugAcceptanceIdentity.SYNTHETIC_UID, *allowedHumans)
                removed += count(sql, "SELECT COUNT(*) FROM $table WHERE userId = ? OR humanUserId IN (?, ?)", args)
                sql.execSQL("DELETE FROM $table WHERE userId = ? OR humanUserId IN (?, ?)", args)
            }
            check(count(sql, "SELECT COUNT(*) FROM active_workout_backup") == 0)
            listOf("user_preferences", "legacy_ownership_migration").forEach { table ->
                removed += count(sql, "SELECT COUNT(*) FROM $table"); sql.execSQL("DELETE FROM $table")
            }
            removed += count(sql, "SELECT COUNT(*) FROM user_profile WHERE id = ?", arrayOf(DebugAcceptanceIdentity.SYNTHETIC_UID))
            sql.execSQL("DELETE FROM user_profile WHERE id = ?", arrayOf(DebugAcceptanceIdentity.SYNTHETIC_UID))
            removed += count(sql, "SELECT COUNT(*) FROM exercise WHERE isCustom = 1")
            sql.execSQL("DELETE FROM exercise WHERE isCustom = 1")
        }
        DebugAcceptanceIdentity.disarm(context)
        return ResetTotals(removed)
    }

    private fun count(db: androidx.sqlite.db.SupportSQLiteDatabase, query: String, args: Array<out Any?> = emptyArray()): Int =
        db.query(query, args).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    data class ResetTotals(val removedRecords: Int)
}
