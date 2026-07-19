package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.core.identity.HumanUserIdGenerator
import com.example.core.identity.DeviceIdGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        BodyWeight::class,
        TapeMeasurement::class,
        Exercise::class,
        WorkoutTemplate::class,
        WorkoutSession::class,
        LoggedSet::class,
        UserProfile::class,
        WorkoutTemplateExercise::class,
        WorkoutTemplateSet::class,
        CommandQueueEntity::class,
        UserPreferences::class,
        ActiveWorkoutBackup::class
    ],
    version = 9,
    exportSchema = false
)
abstract class StrengthDatabase : RoomDatabase() {

    abstract fun strengthDao(): StrengthDao

    companion object {
        @Volatile
        private var INSTANCE: StrengthDatabase? = null

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE body_weight ADD COLUMN bodyFat REAL")
                db.execSQL("ALTER TABLE body_weight ADD COLUMN leanMass REAL")
                db.execSQL("ALTER TABLE body_weight ADD COLUMN fatMass REAL")
                db.execSQL("ALTER TABLE body_weight ADD COLUMN bmi REAL")
                db.execSQL("ALTER TABLE logged_set ADD COLUMN rpe INTEGER")
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `user_profile` (`id` TEXT NOT NULL, `googleUserId` TEXT, `email` TEXT, `displayName` TEXT, `photoUrl` TEXT, `authProvider` TEXT, `createdAt` INTEGER NOT NULL, `lastLoginAt` INTEGER NOT NULL, `isOfflineUser` INTEGER NOT NULL, `preferredUnits` TEXT NOT NULL, `heightCm` REAL, `dateOfBirth` TEXT, `sex` TEXT, `trainingExperience` TEXT, PRIMARY KEY(`id`))")
                db.execSQL("ALTER TABLE body_weight ADD COLUMN userId TEXT")
                db.execSQL("ALTER TABLE tape_measurement ADD COLUMN userId TEXT")
                db.execSQL("ALTER TABLE workout_template ADD COLUMN userId TEXT")
                db.execSQL("ALTER TABLE workout_session ADD COLUMN userId TEXT")
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create WorkoutTemplateExercise table
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `workout_template_exercise` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`templateId` INTEGER NOT NULL, " +
                        "`exerciseId` TEXT NOT NULL, " +
                        "`position` INTEGER NOT NULL, " +
                        "`restSeconds` INTEGER NOT NULL, " +
                        "`notes` TEXT, " +
                        "`supersetGroupId` TEXT, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL" +
                        ")"
                )

                // 2. Create WorkoutTemplateSet table
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `workout_template_set` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`templateExerciseId` INTEGER NOT NULL, " +
                        "`position` INTEGER NOT NULL, " +
                        "`setType` TEXT NOT NULL, " +
                        "`targetRepsMin` INTEGER, " +
                        "`targetRepsMax` INTEGER, " +
                        "`targetWeight` REAL, " +
                        "`targetRpe` INTEGER, " +
                        "`targetDurationSeconds` INTEGER, " +
                        "`targetDistance` REAL, " +
                        "`tempo` TEXT, " +
                        "`notes` TEXT, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL" +
                        ")"
                )

                // 3. Add upgraded fields to logged_set
                db.execSQL("ALTER TABLE `logged_set` ADD COLUMN `actualDuration` INTEGER")
                db.execSQL("ALTER TABLE `logged_set` ADD COLUMN `actualDistance` REAL")
                db.execSQL("ALTER TABLE `logged_set` ADD COLUMN `setType` TEXT NOT NULL DEFAULT 'WORKING'")
                db.execSQL("ALTER TABLE `logged_set` ADD COLUMN `targetRepsMin` INTEGER")
                db.execSQL("ALTER TABLE `logged_set` ADD COLUMN `targetRepsMax` INTEGER")
                db.execSQL("ALTER TABLE `logged_set` ADD COLUMN `targetWeight` REAL")
                db.execSQL("ALTER TABLE `logged_set` ADD COLUMN `targetRpe` INTEGER")
                db.execSQL("ALTER TABLE `logged_set` ADD COLUMN `targetDuration` INTEGER")
                db.execSQL("ALTER TABLE `logged_set` ADD COLUMN `targetDistance` REAL")
                db.execSQL("ALTER TABLE `logged_set` ADD COLUMN `notes` TEXT")

                // 4. Migrate existing workout templates and pre-populate tables
                val cursor = db.query("SELECT id, name, exerciseIdsJson, userId FROM workout_template")
                if (cursor != null) {
                    try {
                        val idIndex = cursor.getColumnIndexOrThrow("id")
                        val jsonIndex = cursor.getColumnIndexOrThrow("exerciseIdsJson")
                        while (cursor.moveToNext()) {
                            val templateId = cursor.getInt(idIndex)
                            val exerciseIdsJson = cursor.getString(jsonIndex)
                            if (!exerciseIdsJson.isNullOrEmpty()) {
                                try {
                                    val jsonArray = org.json.JSONArray(exerciseIdsJson)
                                    for (i in 0 until jsonArray.length()) {
                                        val exerciseId = jsonArray.getString(i)
                                        val position = i + 1

                                        // Insert workout_template_exercise
                                        val contentValuesEx = android.content.ContentValues().apply {
                                            put("templateId", templateId)
                                            put("exerciseId", exerciseId)
                                            put("position", position)
                                            put("restSeconds", 90) // default rest
                                            putNull("notes")
                                            putNull("supersetGroupId")
                                            put("createdAt", System.currentTimeMillis())
                                            put("updatedAt", System.currentTimeMillis())
                                        }
                                        val templateExId = db.insert(
                                            "workout_template_exercise",
                                            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
                                            contentValuesEx
                                        )

                                        // Create 3 default WORKING sets of 8-10 reps
                                        for (setNum in 1..3) {
                                            val contentValuesSet = android.content.ContentValues().apply {
                                                put("templateExerciseId", templateExId)
                                                put("position", setNum)
                                                put("setType", "WORKING")
                                                put("targetRepsMin", 8)
                                                put("targetRepsMax", 10)
                                                putNull("targetWeight")
                                                putNull("targetRpe")
                                                putNull("targetDurationSeconds")
                                                putNull("targetDistance")
                                                putNull("tempo")
                                                putNull("notes")
                                                put("createdAt", System.currentTimeMillis())
                                                put("updatedAt", System.currentTimeMillis())
                                            }
                                            db.insert(
                                                "workout_template_set",
                                                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
                                                contentValuesSet
                                            )
                                        }
                                    }
                                } catch (jsonEx: Exception) {
                                    jsonEx.printStackTrace()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        cursor.close()
                    }
                }
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Alter user_profile table
                db.execSQL("ALTER TABLE user_profile ADD COLUMN globalId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE user_profile ADD COLUMN humanUserId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE user_profile ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE user_profile ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE user_profile ADD COLUMN revision INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE user_profile ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
                db.execSQL("ALTER TABLE user_profile ADD COLUMN lastSyncedAt INTEGER")
                db.execSQL("ALTER TABLE user_profile ADD COLUMN conflictState TEXT")
                db.execSQL("ALTER TABLE user_profile ADD COLUMN firebaseUid TEXT")

                // 2. Alter body_weight table
                db.execSQL("ALTER TABLE body_weight ADD COLUMN globalId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE body_weight ADD COLUMN humanUserId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE body_weight ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE body_weight ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE body_weight ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE body_weight ADD COLUMN revision INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE body_weight ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
                db.execSQL("ALTER TABLE body_weight ADD COLUMN lastSyncedAt INTEGER")
                db.execSQL("ALTER TABLE body_weight ADD COLUMN conflictState TEXT")

                // 3. Alter tape_measurement table
                db.execSQL("ALTER TABLE tape_measurement ADD COLUMN globalId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tape_measurement ADD COLUMN humanUserId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tape_measurement ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tape_measurement ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tape_measurement ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE tape_measurement ADD COLUMN revision INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE tape_measurement ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
                db.execSQL("ALTER TABLE tape_measurement ADD COLUMN lastSyncedAt INTEGER")
                db.execSQL("ALTER TABLE tape_measurement ADD COLUMN conflictState TEXT")

                // 4. Alter exercise table
                db.execSQL("ALTER TABLE exercise ADD COLUMN globalId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE exercise ADD COLUMN humanUserId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE exercise ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE exercise ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE exercise ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE exercise ADD COLUMN revision INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE exercise ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
                db.execSQL("ALTER TABLE exercise ADD COLUMN lastSyncedAt INTEGER")
                db.execSQL("ALTER TABLE exercise ADD COLUMN conflictState TEXT")

                // 5. Alter workout_template table
                db.execSQL("ALTER TABLE workout_template ADD COLUMN globalId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE workout_template ADD COLUMN humanUserId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE workout_template ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE workout_template ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE workout_template ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE workout_template ADD COLUMN revision INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE workout_template ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
                db.execSQL("ALTER TABLE workout_template ADD COLUMN lastSyncedAt INTEGER")
                db.execSQL("ALTER TABLE workout_template ADD COLUMN conflictState TEXT")

                // 6. Alter workout_template_exercise table
                db.execSQL("ALTER TABLE workout_template_exercise ADD COLUMN globalId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE workout_template_exercise ADD COLUMN humanUserId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE workout_template_exercise ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE workout_template_exercise ADD COLUMN revision INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE workout_template_exercise ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
                db.execSQL("ALTER TABLE workout_template_exercise ADD COLUMN lastSyncedAt INTEGER")
                db.execSQL("ALTER TABLE workout_template_exercise ADD COLUMN conflictState TEXT")

                // 7. Alter workout_template_set table
                db.execSQL("ALTER TABLE workout_template_set ADD COLUMN globalId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE workout_template_set ADD COLUMN humanUserId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE workout_template_set ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE workout_template_set ADD COLUMN revision INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE workout_template_set ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
                db.execSQL("ALTER TABLE workout_template_set ADD COLUMN lastSyncedAt INTEGER")
                db.execSQL("ALTER TABLE workout_template_set ADD COLUMN conflictState TEXT")

                // 8. Alter workout_session table
                db.execSQL("ALTER TABLE workout_session ADD COLUMN globalId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE workout_session ADD COLUMN humanUserId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE workout_session ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE workout_session ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE workout_session ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE workout_session ADD COLUMN revision INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE workout_session ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
                db.execSQL("ALTER TABLE workout_session ADD COLUMN lastSyncedAt INTEGER")
                db.execSQL("ALTER TABLE workout_session ADD COLUMN conflictState TEXT")

                // 9. Alter logged_set table
                db.execSQL("ALTER TABLE logged_set ADD COLUMN globalId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE logged_set ADD COLUMN humanUserId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE logged_set ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE logged_set ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE logged_set ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE logged_set ADD COLUMN revision INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE logged_set ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
                db.execSQL("ALTER TABLE logged_set ADD COLUMN lastSyncedAt INTEGER")
                db.execSQL("ALTER TABLE logged_set ADD COLUMN conflictState TEXT")

                // 10. Create command_queue table
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `command_queue` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`commandId` TEXT NOT NULL, " +
                        "`humanUserId` TEXT NOT NULL, " +
                        "`commandType` TEXT NOT NULL, " +
                        "`entityType` TEXT NOT NULL, " +
                        "`entityGlobalId` TEXT NOT NULL, " +
                        "`payloadJson` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`attempts` INTEGER NOT NULL DEFAULT 0, " +
                        "`lastAttemptAt` INTEGER, " +
                        "`status` TEXT NOT NULL DEFAULT 'PENDING', " +
                        "`errorMessage` TEXT" +
                        ")"
                )

                // 11. Populate / Migrate existing records with globalId and humanUserId
                migrateExistingData(db)
            }
        }

        private fun migrateExistingData(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()

            fun mapUid(uid: String?): String {
                return HumanUserIdGenerator.mapUserIdToHumanUserId(uid)
            }

            fun genUuid(prefix: String): String {
                val uuid = java.util.UUID.randomUUID().toString().replace("-", "").lowercase().take(12)
                return "${prefix}_$uuid"
            }

            // A. user_profile
            var cursor = db.query("SELECT id, createdAt FROM user_profile")
            if (cursor != null) {
                try {
                    val idCol = cursor.getColumnIndex("id")
                    val createCol = cursor.getColumnIndex("createdAt")
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(idCol)
                        val created = if (createCol >= 0) cursor.getLong(createCol) else now
                        val hId = mapUid(id)
                        val gId = genUuid("profile")
                        db.execSQL(
                            "UPDATE user_profile SET globalId = ?, humanUserId = ?, updatedAt = ? WHERE id = ?",
                            arrayOf(gId, hId, created, id)
                        )
                    }
                } finally {
                    cursor.close()
                }
            }

            // B. body_weight
            cursor = db.query("SELECT id, userId, date FROM body_weight")
            if (cursor != null) {
                try {
                    val idCol = cursor.getColumnIndex("id")
                    val userCol = cursor.getColumnIndex("userId")
                    val dateCol = cursor.getColumnIndex("date")
                    while (cursor.moveToNext()) {
                        val id = cursor.getInt(idCol)
                        val userId = if (userCol >= 0) cursor.getString(userCol) else null
                        val date = if (dateCol >= 0) cursor.getLong(dateCol) else now
                        val hId = mapUid(userId)
                        val gId = genUuid("measurement")
                        db.execSQL(
                            "UPDATE body_weight SET globalId = ?, humanUserId = ?, createdAt = ?, updatedAt = ? WHERE id = ?",
                            arrayOf(gId, hId, date, date, id)
                        )
                    }
                } finally {
                    cursor.close()
                }
            }

            // C. tape_measurement
            cursor = db.query("SELECT id, userId, date FROM tape_measurement")
            if (cursor != null) {
                try {
                    val idCol = cursor.getColumnIndex("id")
                    val userCol = cursor.getColumnIndex("userId")
                    val dateCol = cursor.getColumnIndex("date")
                    while (cursor.moveToNext()) {
                        val id = cursor.getInt(idCol)
                        val userId = if (userCol >= 0) cursor.getString(userCol) else null
                        val date = if (dateCol >= 0) cursor.getLong(dateCol) else now
                        val hId = mapUid(userId)
                        val gId = genUuid("measurement")
                        db.execSQL(
                            "UPDATE tape_measurement SET globalId = ?, humanUserId = ?, createdAt = ?, updatedAt = ? WHERE id = ?",
                            arrayOf(gId, hId, date, date, id)
                        )
                    }
                } finally {
                    cursor.close()
                }
            }

            // D. exercise
            cursor = db.query("SELECT id, isCustom FROM exercise")
            if (cursor != null) {
                try {
                    val idCol = cursor.getColumnIndex("id")
                    val customCol = cursor.getColumnIndex("isCustom")
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(idCol)
                        val isCustom = if (customCol >= 0) cursor.getInt(customCol) == 1 else false
                        val hId = if (isCustom) "human_offlineusr" else "global"
                        val gId = if (isCustom) genUuid("exercise") else id
                        db.execSQL(
                            "UPDATE exercise SET globalId = ?, humanUserId = ?, createdAt = ?, updatedAt = ? WHERE id = ?",
                            arrayOf(gId, hId, now, now, id)
                        )
                    }
                } finally {
                    cursor.close()
                }
            }

            // E. workout_template
            cursor = db.query("SELECT id, userId FROM workout_template")
            if (cursor != null) {
                try {
                    val idCol = cursor.getColumnIndex("id")
                    val userCol = cursor.getColumnIndex("userId")
                    while (cursor.moveToNext()) {
                        val id = cursor.getInt(idCol)
                        val userId = if (userCol >= 0) cursor.getString(userCol) else null
                        val hId = mapUid(userId)
                        val gId = genUuid("template")
                        db.execSQL(
                            "UPDATE workout_template SET globalId = ?, humanUserId = ?, createdAt = ?, updatedAt = ? WHERE id = ?",
                            arrayOf(gId, hId, now, now, id)
                        )
                    }
                } finally {
                    cursor.close()
                }
            }

            // F. workout_template_exercise
            cursor = db.query("SELECT id, templateId, createdAt FROM workout_template_exercise")
            if (cursor != null) {
                try {
                    val idCol = cursor.getColumnIndex("id")
                    val tempCol = cursor.getColumnIndex("templateId")
                    val createCol = cursor.getColumnIndex("createdAt")
                    while (cursor.moveToNext()) {
                        val id = cursor.getInt(idCol)
                        val templateId = cursor.getInt(tempCol)
                        val created = if (createCol >= 0) cursor.getLong(createCol) else now

                        var parentHumanId = "human_offlineusr"
                        val parentCursor = db.query("SELECT humanUserId FROM workout_template WHERE id = $templateId")
                        if (parentCursor != null) {
                            try {
                                if (parentCursor.moveToFirst()) {
                                    parentHumanId = parentCursor.getString(0) ?: "human_offlineusr"
                                }
                            } finally {
                                parentCursor.close()
                            }
                        }

                        val gId = genUuid("template_exercise")
                        db.execSQL(
                            "UPDATE workout_template_exercise SET globalId = ?, humanUserId = ? WHERE id = ?",
                            arrayOf(gId, parentHumanId, id)
                        )
                    }
                } finally {
                    cursor.close()
                }
            }

            // G. workout_template_set
            cursor = db.query("SELECT id, templateExerciseId, createdAt FROM workout_template_set")
            if (cursor != null) {
                try {
                    val idCol = cursor.getColumnIndex("id")
                    val tempExCol = cursor.getColumnIndex("templateExerciseId")
                    val createCol = cursor.getColumnIndex("createdAt")
                    while (cursor.moveToNext()) {
                        val id = cursor.getInt(idCol)
                        val templateExerciseId = cursor.getInt(tempExCol)
                        val created = if (createCol >= 0) cursor.getLong(createCol) else now

                        var parentHumanId = "human_offlineusr"
                        val parentCursor = db.query("SELECT humanUserId FROM workout_template_exercise WHERE id = $templateExerciseId")
                        if (parentCursor != null) {
                            try {
                                if (parentCursor.moveToFirst()) {
                                    parentHumanId = parentCursor.getString(0) ?: "human_offlineusr"
                                }
                            } finally {
                                parentCursor.close()
                            }
                        }

                        val gId = genUuid("template_set")
                        db.execSQL(
                            "UPDATE workout_template_set SET globalId = ?, humanUserId = ? WHERE id = ?",
                            arrayOf(gId, parentHumanId, id)
                        )
                    }
                } finally {
                    cursor.close()
                }
            }

            // H. workout_session
            cursor = db.query("SELECT id, userId, startTime FROM workout_session")
            if (cursor != null) {
                try {
                    val idCol = cursor.getColumnIndex("id")
                    val userCol = cursor.getColumnIndex("userId")
                    val startCol = cursor.getColumnIndex("startTime")
                    while (cursor.moveToNext()) {
                        val id = cursor.getInt(idCol)
                        val userId = if (userCol >= 0) cursor.getString(userCol) else null
                        val startTime = if (startCol >= 0) cursor.getLong(startCol) else now
                        val hId = mapUid(userId)
                        val gId = genUuid("session")
                        db.execSQL(
                            "UPDATE workout_session SET globalId = ?, humanUserId = ?, createdAt = ?, updatedAt = ? WHERE id = ?",
                            arrayOf(gId, hId, startTime, startTime, id)
                        )
                    }
                } finally {
                    cursor.close()
                }
            }

            // I. logged_set
            cursor = db.query("SELECT id, sessionId FROM logged_set")
            if (cursor != null) {
                try {
                    val idCol = cursor.getColumnIndex("id")
                    val sessCol = cursor.getColumnIndex("sessionId")
                    while (cursor.moveToNext()) {
                        val id = cursor.getInt(idCol)
                        val sessionId = cursor.getInt(sessCol)

                        var parentHumanId = "human_offlineusr"
                        var parentTime = now
                        val parentCursor = db.query("SELECT humanUserId, startTime FROM workout_session WHERE id = $sessionId")
                        if (parentCursor != null) {
                            try {
                                if (parentCursor.moveToFirst()) {
                                    parentHumanId = parentCursor.getString(0) ?: "human_offlineusr"
                                    parentTime = parentCursor.getLong(1)
                                }
                            } finally {
                                parentCursor.close()
                            }
                        }

                        val gId = genUuid("logged_set")
                        db.execSQL(
                            "UPDATE logged_set SET globalId = ?, humanUserId = ?, createdAt = ?, updatedAt = ? WHERE id = ?",
                            arrayOf(gId, parentHumanId, parentTime, parentTime, id)
                        )
                    }
                } finally {
                    cursor.close()
                }
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                android.util.Log.i("StrengthDatabase", "Executing MIGRATION_5_6...")
                try {
                    // 1. Add originDeviceId column to all tables
                    db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `originDeviceId` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE `body_weight` ADD COLUMN `originDeviceId` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE `tape_measurement` ADD COLUMN `originDeviceId` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE `exercise` ADD COLUMN `originDeviceId` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE `workout_template` ADD COLUMN `originDeviceId` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE `workout_template_exercise` ADD COLUMN `originDeviceId` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE `workout_template_set` ADD COLUMN `originDeviceId` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE `workout_session` ADD COLUMN `originDeviceId` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE `logged_set` ADD COLUMN `originDeviceId` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE `command_queue` ADD COLUMN `originDeviceId` TEXT NOT NULL DEFAULT ''")

                    // 2. Add global reference columns
                    db.execSQL("ALTER TABLE `workout_template_exercise` ADD COLUMN `templateGlobalId` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE `workout_template_set` ADD COLUMN `templateExerciseGlobalId` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE `workout_session` ADD COLUMN `templateGlobalId` TEXT")
                    db.execSQL("ALTER TABLE `logged_set` ADD COLUMN `sessionGlobalId` TEXT NOT NULL DEFAULT ''")

                    // 3. Populate originDeviceId for all existing records
                    val deviceId = DeviceIdGenerator.getOrGenerateDeviceId()
                    db.execSQL("UPDATE `user_profile` SET `originDeviceId` = '$deviceId' WHERE `originDeviceId` = ''")
                    db.execSQL("UPDATE `body_weight` SET `originDeviceId` = '$deviceId' WHERE `originDeviceId` = ''")
                    db.execSQL("UPDATE `tape_measurement` SET `originDeviceId` = '$deviceId' WHERE `originDeviceId` = ''")
                    db.execSQL("UPDATE `exercise` SET `originDeviceId` = '$deviceId' WHERE `originDeviceId` = ''")
                    db.execSQL("UPDATE `workout_template` SET `originDeviceId` = '$deviceId' WHERE `originDeviceId` = ''")
                    db.execSQL("UPDATE `workout_template_exercise` SET `originDeviceId` = '$deviceId' WHERE `originDeviceId` = ''")
                    db.execSQL("UPDATE `workout_template_set` SET `originDeviceId` = '$deviceId' WHERE `originDeviceId` = ''")
                    db.execSQL("UPDATE `workout_session` SET `originDeviceId` = '$deviceId' WHERE `originDeviceId` = ''")
                    db.execSQL("UPDATE `logged_set` SET `originDeviceId` = '$deviceId' WHERE `originDeviceId` = ''")
                    db.execSQL("UPDATE `command_queue` SET `originDeviceId` = '$deviceId' WHERE `originDeviceId` = ''")

                    // 4. Populate child-parent global references
                    db.execSQL(
                        "UPDATE `workout_template_exercise` " +
                        "SET `templateGlobalId` = COALESCE((SELECT `globalId` FROM `workout_template` WHERE `workout_template`.`id` = `workout_template_exercise`.`templateId`), '') " +
                        "WHERE `templateGlobalId` = ''"
                    )
                    db.execSQL(
                        "UPDATE `workout_template_set` " +
                        "SET `templateExerciseGlobalId` = COALESCE((SELECT `globalId` FROM `workout_template_exercise` WHERE `workout_template_exercise`.`id` = `workout_template_set`.`templateExerciseId`), '') " +
                        "WHERE `templateExerciseGlobalId` = ''"
                    )
                    db.execSQL(
                        "UPDATE `workout_session` " +
                        "SET `templateGlobalId` = (SELECT `globalId` FROM `workout_template` WHERE `workout_template`.`id` = `workout_session`.`templateId`) " +
                        "WHERE `templateId` IS NOT NULL AND `templateGlobalId` IS NULL"
                    )
                    db.execSQL(
                        "UPDATE `logged_set` " +
                        "SET `sessionGlobalId` = COALESCE((SELECT `globalId` FROM `workout_session` WHERE `workout_session`.`id` = `logged_set`.`sessionId`), '') " +
                        "WHERE `sessionGlobalId` = ''"
                    )
                    android.util.Log.i("StrengthDatabase", "MIGRATION_5_6 executed successfully.")
                } catch (e: Throwable) {
                    android.util.Log.e("StrengthDatabase", "FATAL error during MIGRATION_5_6", e)
                    throw e
                }
            }
        }

        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                android.util.Log.i("StrengthDatabase", "Executing MIGRATION_6_7...")
                try {
                    db.execSQL("ALTER TABLE `command_queue` ADD COLUMN `nextRetryAt` INTEGER")
                    android.util.Log.i("StrengthDatabase", "MIGRATION_6_7 executed successfully.")
                } catch (e: Throwable) {
                    android.util.Log.e("StrengthDatabase", "FATAL error during MIGRATION_6_7", e)
                    throw e
                }
            }
        }

        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                android.util.Log.i("StrengthDatabase", "Executing MIGRATION_7_8...")
                try {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `user_preferences` (
                            `id` TEXT NOT NULL, 
                            `isMetric` INTEGER NOT NULL, 
                            `theme` TEXT NOT NULL, 
                            `keepScreenAwake` INTEGER NOT NULL, 
                            `defaultRestTimerDuration` INTEGER NOT NULL, 
                            `soundOn` INTEGER NOT NULL, 
                            `vibrationOn` INTEGER NOT NULL, 
                            `defaultWarmupSets` INTEGER NOT NULL, 
                            `autoCompleteBehavior` INTEGER NOT NULL, 
                            `autoScroll` INTEGER NOT NULL, 
                            `timerPreferences` TEXT NOT NULL, 
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())
                    db.execSQL("""
                        INSERT OR IGNORE INTO `user_preferences` (
                            id, isMetric, theme, keepScreenAwake, defaultRestTimerDuration, 
                            soundOn, vibrationOn, defaultWarmupSets, autoCompleteBehavior, 
                            autoScroll, timerPreferences
                        ) VALUES (
                            'default', 1, 'system', 0, 90, 1, 1, 0, 1, 1, 'standard'
                        )
                    """.trimIndent())
                    android.util.Log.i("StrengthDatabase", "MIGRATION_7_8 executed successfully.")
                } catch (e: Throwable) {
                    android.util.Log.e("StrengthDatabase", "FATAL error during MIGRATION_7_8", e)
                    throw e
                }
            }
        }

        val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                android.util.Log.i("StrengthDatabase", "Executing MIGRATION_8_9...")
                try {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `active_workout_backup` (
                            `id` INTEGER NOT NULL,
                            `templateId` INTEGER,
                            `templateName` TEXT NOT NULL,
                            `startTime` INTEGER NOT NULL,
                            `exercisesJson` TEXT NOT NULL,
                            `setsJson` TEXT NOT NULL,
                            `exerciseMetadataJson` TEXT NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())
                    android.util.Log.i("StrengthDatabase", "MIGRATION_8_9 executed successfully.")
                } catch (e: Throwable) {
                    android.util.Log.e("StrengthDatabase", "FATAL error during MIGRATION_8_9", e)
                    throw e
                }
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): StrengthDatabase {
            val appCtx = context.applicationContext
            android.util.Log.i("StrengthDatabase", "getDatabase called. Setting appContext references.")
            HumanUserIdGenerator.appContext = appCtx
            DeviceIdGenerator.appContext = appCtx
            
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    android.util.Log.i("StrengthDatabase", "Building database 'strength_database'...")
                    val instance = Room.databaseBuilder(
                        appCtx,
                        StrengthDatabase::class.java,
                        "strength_database"
                    )
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                        .addCallback(StrengthDatabaseCallback(scope))
                        .build()
                    INSTANCE = instance
                    android.util.Log.i("StrengthDatabase", "Database building completed.")
                    instance
                }
            }
        }
    }

    private class StrengthDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            android.util.Log.i("StrengthDatabase", "onCreate callback triggered.")
            INSTANCE?.let { database ->
                // Use a non-cancelable Application-level CoroutineScope to prevent cancellation if the caller's scope (e.g. activity lifecycleScope) gets destroyed.
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        android.util.Log.i("StrengthDatabase", "Populating database with default data...")
                        populateDatabase(database.strengthDao())
                        android.util.Log.i("StrengthDatabase", "Database population completed successfully.")
                    } catch (e: Throwable) {
                        android.util.Log.e("StrengthDatabase", "Error populating database with default data on database creation", e)
                    }
                }
            }
        }

        suspend fun populateDatabase(dao: StrengthDao) {
            val now = System.currentTimeMillis()
            val defaultExercises = listOf(
                Exercise("bench_press", "Bench Press", "Chest", isCustom = false, globalId = "bench_press", humanUserId = "global", createdAt = now, updatedAt = now),
                Exercise("incline_db_press", "Incline Dumbbell Press", "Chest", isCustom = false, globalId = "incline_db_press", humanUserId = "global", createdAt = now, updatedAt = now),
                Exercise("chest_fly", "Chest Fly", "Chest", isCustom = false, globalId = "chest_fly", humanUserId = "global", createdAt = now, updatedAt = now),
                Exercise("deadlift", "Deadlift", "Back", isCustom = false, globalId = "deadlift", humanUserId = "global", createdAt = now, updatedAt = now),
                Exercise("pull_up", "Pull Up", "Back", isCustom = false, globalId = "pull_up", humanUserId = "global", createdAt = now, updatedAt = now),
                Exercise("barbell_row", "Barbell Row", "Back", isCustom = false, globalId = "barbell_row", humanUserId = "global", createdAt = now, updatedAt = now),
                Exercise("lat_pulldown", "Lat Pulldown", "Back", isCustom = false, globalId = "lat_pulldown", humanUserId = "global", createdAt = now, updatedAt = now),
                Exercise("squat", "Barbell Squat", "Legs", isCustom = false, globalId = "squat", humanUserId = "global", createdAt = now, updatedAt = now),
                Exercise("romanian_deadlift", "Romanian Deadlift", "Legs", isCustom = false, globalId = "romanian_deadlift", humanUserId = "global", createdAt = now, updatedAt = now),
                Exercise("leg_press", "Leg Press", "Legs", isCustom = false, globalId = "leg_press", humanUserId = "global", createdAt = now, updatedAt = now),
                Exercise("calf_raise", "Calf Raise", "Legs", isCustom = false, globalId = "calf_raise", humanUserId = "global", createdAt = now, updatedAt = now),
                Exercise("overhead_press", "Overhead Press", "Shoulders", isCustom = false, globalId = "overhead_press", humanUserId = "global", createdAt = now, updatedAt = now),
                Exercise("lateral_raise", "Lateral Raise", "Shoulders", isCustom = false, globalId = "lateral_raise", humanUserId = "global", createdAt = now, updatedAt = now),
                Exercise("rear_delt_fly", "Rear Delt Fly", "Shoulders", isCustom = false, globalId = "rear_delt_fly", humanUserId = "global", createdAt = now, updatedAt = now),
                Exercise("bicep_curl", "Bicep Curl", "Arms", isCustom = false, globalId = "bicep_curl", humanUserId = "global", createdAt = now, updatedAt = now),
                Exercise("tricep_pushdown", "Tricep Pushdown", "Arms", isCustom = false, globalId = "tricep_pushdown", humanUserId = "global", createdAt = now, updatedAt = now),
                Exercise("hammer_curl", "Hammer Curl", "Arms", isCustom = false, globalId = "hammer_curl", humanUserId = "global", createdAt = now, updatedAt = now),
                Exercise("skull_crusher", "Skull Crusher", "Arms", isCustom = false, globalId = "skull_crusher", humanUserId = "global", createdAt = now, updatedAt = now),
                Exercise("hanging_leg_raise", "Hanging Leg Raise", "Abs", isCustom = false, globalId = "hanging_leg_raise", humanUserId = "global", createdAt = now, updatedAt = now),
                Exercise("plank", "Plank", "Abs", isCustom = false, globalId = "plank", humanUserId = "global", createdAt = now, updatedAt = now),
                Exercise("crunch", "Abdominal Crunch", "Abs", isCustom = false, globalId = "crunch", humanUserId = "global", createdAt = now, updatedAt = now)
            )
            dao.insertExercises(defaultExercises)

            val pushTemplate = WorkoutTemplate(
                name = "Push Day",
                exerciseIdsJson = """["bench_press","overhead_press","incline_db_press","lateral_raise","tricep_pushdown"]""",
                globalId = "template_push",
                humanUserId = "human_offlineusr",
                createdAt = now,
                updatedAt = now
            )
            val pullTemplate = WorkoutTemplate(
                name = "Pull Day",
                exerciseIdsJson = """["deadlift","pull_up","barbell_row","rear_delt_fly","bicep_curl","hammer_curl"]""",
                globalId = "template_pull",
                humanUserId = "human_offlineusr",
                createdAt = now,
                updatedAt = now
            )
            val legsAbsTemplate = WorkoutTemplate(
                name = "Legs & Abs",
                exerciseIdsJson = """["squat","romanian_deadlift","calf_raise","plank","crunch"]""",
                globalId = "template_legs",
                humanUserId = "human_offlineusr",
                createdAt = now,
                updatedAt = now
            )

            dao.insertTemplate(pushTemplate)
            dao.insertTemplate(pullTemplate)
            dao.insertTemplate(legsAbsTemplate)

            // Insert default user preferences
            dao.insertUserPreferences(UserPreferences())
        }
    }
}
