package com.example.planner

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlannerBackupRestoreTest {
    private lateinit var db: StrengthDatabase
    private val user = "backup-owner"
    private val human = "human_dddddddddddddddddddddddddddddddd"

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), StrengthDatabase::class.java)
            .allowMainThreadQueries().build()
    }
    @After fun close() = db.close()

    private fun payload(): PlannerBackupCodec.Payload {
        val planA = TrainingPlan("one", user, human, 9, "routine-global", "Routine", 21000, 817,
            createdAt = 100, updatedAt = 200, revision = 4, originDeviceId = "device-source")
        val planB = TrainingPlan("series", user, human, 9, "routine-global", "Routine", 21001, 731, 0b0010100,
            21030, createdAt = 101, updatedAt = 201, revision = 6, originDeviceId = "device-source")
        val items = listOf(
            PlannedWorkout("one:21000", "one", user, human, 9, "routine-global", "Routine", 21000, 21000, 817,
                "COMPLETED", 300, 42, true, false, 110, 210, revision = 8, originDeviceId = "device-source"),
            PlannedWorkout("series:21004", "series", user, human, 9, "routine-global", "Routine", 21005, 21004, 731,
                "SKIPPED", reminderEnabled = true, detachedFromSeries = true, createdAt = 111, updatedAt = 211,
                revision = 5, originDeviceId = "device-source"),
            PlannedWorkout("series:21011", "series", user, human, 9, "routine-global", "Routine", 21011, 21011, 900,
                reminderEnabled = true, createdAt = 112, updatedAt = 212, revision = 3, originDeviceId = "device-source"),
            PlannedWorkout("series:21018", "series", user, human, 9, "routine-global", "Routine", 21018, 21018, 900,
                deletedAt = 500, revision = 7, originDeviceId = "device-source")
        )
        return PlannerBackupCodec.Payload(listOf(planA, planB), items)
    }

    private fun json(value: PlannerBackupCodec.Payload) = JSONObject().apply {
        put("version", 4); put("training_plans", PlannerBackupCodec.plansJson(value.plans))
        put("planned_workouts", PlannerBackupCodec.occurrencesJson(value.occurrences))
        put("workout_sessions", org.json.JSONArray().put(JSONObject().put("id", 42)))
    }

    @Test fun format4RoundTripPreservesPlannerAndIsIdempotent() = runBlocking {
        val expected = payload(); val restored = PlannerBackupCodec.parse(json(expected), user, human)
        db.strengthDao().restorePlannerBackupAtomically(restored.plans, restored.occurrences)
        db.strengthDao().restorePlannerBackupAtomically(restored.plans, restored.occurrences)
        val plans = db.strengthDao().getAllTrainingPlansForBackup(user)
        val items = db.strengthDao().getAllPlannedWorkoutsForBackup(user)
        assertEquals(expected.plans.map { it.copy(syncStatus="PENDING_UPLOAD", lastSyncedAt=null) }, plans)
        assertEquals(expected.occurrences.map { it.copy(syncStatus="PENDING_UPLOAD", lastSyncedAt=null) }, items)
        assertEquals(2, plans.size); assertEquals(4, items.size)
        assertEquals(listOf("series:21011"), PlannerBackupCodec.eligibleReminders(items).map { it.id })
    }

    @Test fun olderBackupWithoutPlannerIsAccepted() {
        val root = JSONObject("""{"version":3,"workout_sessions":[]}""")
        assertEquals(PlannerBackupCodec.Payload(emptyList(), emptyList()), PlannerBackupCodec.parse(root, user, human))
    }

    @Test fun sameDayPlacementsWithStableIdsRoundTripWithoutCollapsing() = runBlocking {
        val expected = payload()
        val first = expected.occurrences.first { it.seriesId == "series" && it.deletedAt == null }
        val second = first.copy(id = "series:second-placement", globalId = "series:second-placement")
        val expanded = expected.copy(occurrences = expected.occurrences + second)
        val restored = PlannerBackupCodec.parse(json(expanded), user, human)

        db.strengthDao().restorePlannerBackupAtomically(restored.plans, restored.occurrences)

        val sameDay = db.strengthDao().getAllPlannedWorkoutsForBackup(user)
            .filter { it.seriesId == "series" && it.scheduledEpochDay == first.scheduledEpochDay && it.deletedAt == null }
        assertEquals(listOf(first.id, second.id).sorted(), sameDay.map { it.id }.sorted())
    }

    @Test fun malformedOrUntrustedPlannerPayloadsFailBeforePersistence() = runBlocking {
        val mutations = listOf<(JSONObject) -> Unit>(
            { it.getJSONArray("training_plans").put(it.getJSONArray("training_plans").getJSONObject(0)) },
            { it.getJSONArray("planned_workouts").put(it.getJSONArray("planned_workouts").getJSONObject(0)) },
            { it.getJSONArray("planned_workouts").getJSONObject(1).put("status", "UNKNOWN") },
            { it.getJSONArray("planned_workouts").getJSONObject(1).put("scheduledEpochDay", "not-a-date") },
            { it.getJSONArray("planned_workouts").getJSONObject(1).put("preferredMinuteOfDay", 1440) },
            { it.getJSONArray("training_plans").getJSONObject(1).put("weekdaysMask", 256) },
            { it.getJSONArray("training_plans").getJSONObject(1).put("recurrenceEndEpochDay", 1) },
            { it.getJSONArray("planned_workouts").getJSONObject(1).put("seriesId", "missing") },
            { it.getJSONArray("training_plans").getJSONObject(0).put("templateGlobalId", "") },
            { it.getJSONArray("planned_workouts").getJSONObject(0).put("deletedAt", 9) },
            { it.getJSONArray("planned_workouts").getJSONObject(0).put("linkedSessionId", 999) },
            { it.getJSONArray("training_plans").getJSONObject(0).put("revision", 0) },
            { it.getJSONArray("training_plans").getJSONObject(0).put("humanUserId", "human_eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee") }
        )
        for (mutation in mutations) {
            val candidate = json(payload()); mutation(candidate)
            assertThrows(Exception::class.java) { PlannerBackupCodec.parse(candidate, user, human) }
        }
        assertTrue(db.strengthDao().getAllTrainingPlansForBackup(user).isEmpty())
        assertTrue(db.strengthDao().getAllPlannedWorkoutsForBackup(user).isEmpty())
    }

    @Test fun truncatedAndUnsupportedStructuresAreRejected() {
        assertThrows(Exception::class.java) { JSONObject("{") }
        assertThrows(Exception::class.java) { PlannerBackupCodec.parse(JSONObject("""{"version":5}"""), user, human) }
        assertThrows(Exception::class.java) { PlannerBackupCodec.parse(JSONObject("""{"version":4,"training_plans":{}}"""), user, human) }
    }
}
