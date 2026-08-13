package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LegacyOwnershipMigrationTest {
    private lateinit var db: StrengthDatabase
    private lateinit var dao: StrengthDao
    private val uid = "firebase-user"
    private val legacy = "human_legacylegacylegacylegacylegacy12"
    private val target = "human_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), StrengthDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.strengthDao()
    }
    @After fun close() { if (::db.isInitialized) db.close() }

    private suspend fun seed(): UserProfile {
        val profile = UserProfile(uid, googleUserId = uid, authProvider = "google", isOfflineUser = false,
            globalId = "profile-global", humanUserId = legacy, firebaseUid = uid)
        dao.insertUserProfile(profile)
        dao.insertBodyWeight(BodyWeight(weight = 80f, date = 1, userId = uid, globalId = "weight-global", humanUserId = legacy))
        val templateId = dao.insertTemplate(WorkoutTemplate(name = "Legacy", exerciseIdsJson = "[]", userId = uid,
            globalId = "template-global", humanUserId = legacy)).toInt()
        val sessionId = dao.insertSession(WorkoutSession(templateId = templateId, templateName = "Legacy", startTime = 1,
            endTime = 2, userId = uid, globalId = "session-global", humanUserId = legacy)).toInt()
        dao.insertLoggedSet(LoggedSet(sessionId = sessionId, exerciseId = "bench_press", setNumber = 1, reps = 5,
            weight = 60f, globalId = "set-global", humanUserId = legacy))
        return profile
    }

    @Test fun matchingUidMigratesGraphAtomically() = runBlocking {
        val profile = seed()
        val plan = dao.prepareVerifiedLegacyMigration(uid, uid, target, profile.copy(humanUserId = target))
        dao.commitVerifiedLegacyMigration(plan)
        assertEquals(target, dao.getUserProfile(uid)?.humanUserId)
        assertEquals(0, dao.countRemainingLegacyOwnership(legacy))
        assertEquals("ROOM_COMMITTED", dao.getMigrationState()?.phase)
        assertEquals(0, dao.countUploadableLegacyCommands(legacy))
    }

    @Test fun injectedFailureRollsBackEveryRoomWrite() = runBlocking {
        val profile = seed()
        val plan = dao.prepareVerifiedLegacyMigration(uid, uid, target, profile.copy(humanUserId = target))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER abort_session_owner_update BEFORE UPDATE OF humanUserId ON workout_session BEGIN SELECT RAISE(ABORT, 'test rollback'); END")
        assertThrows(android.database.sqlite.SQLiteException::class.java) { runBlocking { dao.commitVerifiedLegacyMigration(plan) } }
        assertEquals(legacy, dao.getUserProfile(uid)?.humanUserId)
        assertTrue(dao.countMeaningfulOwnedRecords(uid, legacy) >= 3)
        assertNull(dao.getMigrationState())
    }

    @Test fun differentUidIsDeniedBeforeMutation() = runBlocking {
        val profile = seed()
        assertThrows(IllegalArgumentException::class.java) { runBlocking {
            dao.prepareVerifiedLegacyMigration(uid, "different-user", target, profile.copy(humanUserId = target))
        } }
        assertEquals(legacy, dao.getUserProfile(uid)?.humanUserId)
    }

    @Test fun activeBackupOwnershipUsesTypedJson() {
        val backup = ActiveWorkoutBackup(templateName = "Legacy", startTime = 1,
            exercisesJson = """[{"id":"custom","name":"Custom","category":"Other","isCustom":true,"userId":"$legacy"}]""",
            setsJson = """{"custom":[]}""",
            exerciseMetadataJson = """{"custom":{},"__global_recovery__":{"workoutOwnerUserId":"$uid","workoutOwnerHumanUserId":"$legacy"}}""")
        val rewritten = rewriteActiveWorkoutOwnership(backup, uid, legacy, target)
        assertEquals(target, org.json.JSONArray(rewritten.exercisesJson).getJSONObject(0).getString("userId"))
        assertEquals(target, org.json.JSONObject(rewritten.exerciseMetadataJson)
            .getJSONObject("__global_recovery__").getString("workoutOwnerHumanUserId"))
    }
}
