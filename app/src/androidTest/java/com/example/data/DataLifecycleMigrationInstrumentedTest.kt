package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataLifecycleMigrationInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val user = "migration-owner"
    private val human = "human_cccccccccccccccccccccccccccccccc"

    private fun current(name: String): StrengthDatabase = Room.databaseBuilder(context, StrengthDatabase::class.java, name)
        .allowMainThreadQueries().build()

    private suspend fun seedBase(db: StrengthDatabase) {
        val dao = db.strengthDao()
        dao.insertUserProfile(UserProfile(user, displayName = "Migration Owner", firebaseUid = user,
            humanUserId = human, globalId = "profile-stable", originDeviceId = "device-old"))
        dao.insertExercise(Exercise("custom-stable", "Historical Custom", "Strength", true,
            globalId = "exercise-stable", humanUserId = human, originDeviceId = "device-old"))
        dao.insertExercise(Exercise("catalogue-stable", "Governed Exercise", "Strength", false,
            globalId = "catalogue-stable", humanUserId = "global", originDeviceId = "catalogue"))
        dao.insertBodyWeight(BodyWeight(71, 82.5f, 1_700_000_000_000, 18f, 67.6f, 14.9f, 24f, user,
            "weight-stable", human, originDeviceId = "device-old"))
        dao.insertTapeMeasurement(TapeMeasurement(72, 1_700_000_000_100, chest = 102f, waist = 82f, userId = user,
            globalId = "tape-stable", humanUserId = human, originDeviceId = "device-old"))
        val templateId = dao.insertTemplate(WorkoutTemplate(41, "Stable Routine", "[\"custom-stable\"]", user,
            globalId = "template-stable", humanUserId = human, originDeviceId = "device-old")).toInt()
        val templateExerciseId = dao.insertTemplateExercise(WorkoutTemplateExercise(42, templateId, "custom-stable", 0, 90,
            globalId = "template-exercise-stable", humanUserId = human, originDeviceId = "device-old",
            templateGlobalId = "template-stable")).toInt()
        dao.insertTemplateSet(WorkoutTemplateSet(43, templateExerciseId, 0, "WORKING", 5, 8, 100f, 8,
            globalId = "template-set-stable", humanUserId = human, originDeviceId = "device-old",
            templateExerciseGlobalId = "template-exercise-stable"))
        val sessionId = dao.insertSession(WorkoutSession(51, templateId, "Stable Routine", 1_700_000_000_000, 1_700_000_003_600,
            user, globalId = "session-stable", humanUserId = human, originDeviceId = "device-old")).toInt()
        dao.insertLoggedSet(LoggedSet(61, sessionId, "custom-stable", 1, 5, 100f, true, rpe = 8,
            actualDuration = 45, actualDistance = 12.5f, globalId = "set-stable", humanUserId = human,
            originDeviceId = "device-old", sessionGlobalId = "session-stable"))
        dao.enqueueCommand(CommandQueueEntity(commandId = "pending-stable", humanUserId = human,
            commandType = "WorkoutUpdated", entityType = "WORKOUT_SESSION", entityGlobalId = "session-stable", payloadJson = "{}"))
        dao.enqueueCommand(CommandQueueEntity(commandId = "completed-stable", humanUserId = human,
            commandType = "WorkoutUpdated", entityType = "WORKOUT_SESSION", entityGlobalId = "session-stable", payloadJson = "{}",
            status = "SUCCEEDED"))
        dao.insertActiveWorkoutBackup(ActiveWorkoutBackup(templateName = "Stable Routine", startTime = 1_700_000_004_000,
            exercisesJson = "[]", setsJson = "{}", exerciseMetadataJson = "{}"))
    }

    private suspend fun seedPlanner(db: StrengthDatabase) {
        val first = LocalDate.of(2026, 8, 17).toEpochDay()
        val dao = db.strengthDao()
        dao.upsertTrainingPlan(TrainingPlan("one-time", user, human, 41, "template-stable", "Stable Routine",
            first, 817, revision = 7, updatedAt = 1234567, originDeviceId = "device-old"))
        dao.upsertTrainingPlan(TrainingPlan("recurring", user, human, 41, "template-stable", "Stable Routine",
            first, 731, 0b0010100, first + 28, revision = 3, updatedAt = 1234568, originDeviceId = "device-old"))
        dao.insertPlannedWorkouts(listOf(
            PlannedWorkout("one-time:$first", "one-time", user, human, 41, "template-stable", "Stable Routine",
                first, first, 817, "COMPLETED", 1_700_000_003_600, 51, true, false, revision = 9,
                updatedAt = 1234570, originDeviceId = "device-old"),
            PlannedWorkout("recurring:${first + 3}", "recurring", user, human, 41, "template-stable", "Stable Routine",
                first + 4, first + 3, 731, "SKIPPED", reminderEnabled = false, detachedFromSeries = true,
                revision = 4, updatedAt = 1234571, originDeviceId = "device-old")
        ))
    }

    private fun downgradeTo9(db: StrengthDatabase): SupportSQLiteDatabase {
        val raw = db.openHelper.writableDatabase
        raw.execSQL("DROP TABLE training_plan")
        raw.execSQL("DROP TABLE planned_workout")
        raw.version = 9
        return raw
    }

    private fun downgradeTo10(db: StrengthDatabase): SupportSQLiteDatabase {
        val raw = db.openHelper.writableDatabase
        for (table in listOf("training_plan", "planned_workout")) {
            for (column in listOf("globalId", "revision", "deletedAt", "syncStatus", "lastSyncedAt", "conflictState", "originDeviceId"))
                raw.execSQL("ALTER TABLE `$table` DROP COLUMN `$column`")
        }
        raw.version = 10
        return raw
    }

    private fun scalar(db: SupportSQLiteDatabase, sql: String): Long = db.query(sql).use { it.moveToFirst(); it.getLong(0) }

    @Test fun migration9To10PreservesExistingDataAndCreatesPlannerConstraints() { runBlocking {
        val name = "s8fb-9-10-${System.nanoTime()}.db"
        val database = current(name)
        try {
            seedBase(database)
            val raw = downgradeTo9(database)
            StrengthDatabase.MIGRATION_9_10.migrate(raw)
            assertEquals(1, scalar(raw, "SELECT COUNT(*) FROM user_profile"))
            assertEquals(1, scalar(raw, "SELECT COUNT(*) FROM workout_session"))
            assertEquals(1, scalar(raw, "SELECT COUNT(*) FROM logged_set"))
            assertEquals(1, scalar(raw, "SELECT COUNT(*) FROM exercise WHERE id='custom-stable'"))
            assertEquals(0, scalar(raw, "SELECT COUNT(*) FROM training_plan"))
            assertEquals(0, scalar(raw, "SELECT COUNT(*) FROM pragma_foreign_key_check"))
            raw.execSQL("INSERT INTO training_plan (id,userId,humanUserId,templateId,templateGlobalId,routineName,firstEpochDay,weekdaysMask,createdAt,updatedAt) VALUES ('p','$user','$human',41,'template-stable','Stable',1,0,1,1)")
            raw.execSQL("INSERT INTO planned_workout (id,seriesId,userId,humanUserId,templateId,templateGlobalId,routineName,scheduledEpochDay,originalEpochDay,status,reminderEnabled,detachedFromSeries,createdAt,updatedAt) VALUES ('o','p','$user','$human',41,'template-stable','Stable',1,1,'PLANNED',0,0,1,1)")
            assertThrows(Exception::class.java) { raw.execSQL("INSERT INTO planned_workout (id,seriesId,userId,humanUserId,templateId,templateGlobalId,routineName,scheduledEpochDay,originalEpochDay,status,reminderEnabled,detachedFromSeries,createdAt,updatedAt) VALUES ('o2','p','$user','$human',41,'template-stable','Stable',1,1,'PLANNED',0,0,1,1)") }
        } finally { database.close(); context.deleteDatabase(name) }
    } }

    @Test fun migration10To11PreservesPlannerAndAddsSafeSyncDefaults() { runBlocking {
        val name = "s8fb-10-11-${System.nanoTime()}.db"
        var database = current(name)
        seedBase(database); seedPlanner(database); downgradeTo10(database); database.close()
        try {
            database = Room.databaseBuilder(context, StrengthDatabase::class.java, name).allowMainThreadQueries()
                .addMigrations(StrengthDatabase.MIGRATION_10_11).build()
            val plan = database.strengthDao().getTrainingPlan("recurring")!!
            val completed = database.strengthDao().getPlannedWorkout("one-time:${LocalDate.of(2026, 8, 17).toEpochDay()}")!!
            assertEquals("recurring", plan.globalId); assertEquals(1, plan.revision); assertEquals("", plan.originDeviceId)
            assertEquals("COMPLETED", completed.status); assertEquals(51, completed.linkedSessionId)
            assertEquals(817, completed.preferredMinuteOfDay); assertEquals(0, scalar(database.openHelper.writableDatabase, "SELECT COUNT(*) FROM pragma_foreign_key_check"))
        } finally { database.close(); context.deleteDatabase(name) }
    } }

    @Test fun sequential9To10To11OpensProductionDaoAndCreatesPlan() { runBlocking {
        val name = "s8fb-9-11-${System.nanoTime()}.db"
        var database = current(name)
        seedBase(database); downgradeTo9(database); database.close()
        try {
            database = Room.databaseBuilder(context, StrengthDatabase::class.java, name).allowMainThreadQueries()
                .addMigrations(StrengthDatabase.MIGRATION_9_10, StrengthDatabase.MIGRATION_10_11).build()
            assertNotNull(database.strengthDao().getUserProfile(user))
            val repo = StrengthRepository(database.strengthDao(), deviceIdOverride = "migration-device")
            val plan = TrainingPlan("post-migration", user, human, 41, "template-stable", "Stable Routine", 21000, 600)
            repo.createTrainingPlan(plan, listOf(PlannedWorkout("post-migration:21000", "post-migration", user, human,
                41, "template-stable", "Stable Routine", 21000, 21000, 600)))
            assertEquals("migration-device", database.strengthDao().getTrainingPlan("post-migration")!!.originDeviceId)
            assertEquals(1, database.strengthDao().getAllPlannedWorkoutsForBackup(user).size)
            database.openHelper.writableDatabase.query("PRAGMA integrity_check").use { assertTrue(it.moveToFirst()); assertEquals("ok", it.getString(0)) }
        } finally { database.close(); context.deleteDatabase(name) }
    } }
}
