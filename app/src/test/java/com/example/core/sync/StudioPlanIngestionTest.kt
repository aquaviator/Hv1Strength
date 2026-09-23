package com.example.core.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StudioPlanIngestionTest {
    private lateinit var db: StrengthDatabase
    private val owner = "human_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

    @Before fun setUp() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), StrengthDatabase::class.java).allowMainThreadQueries().build() }
    @After fun tearDown() { if (::db.isInitialized) db.close() }

    private fun workout(version: String, global: String, localId: Int) = StudioWorkoutLink(version, owner, global, 1,
        "1".repeat(64), "fixture", global, "", "STRENGTH", "{}", localId, 1, 1, acknowledgementId = "ack-$version")

    private fun publication(revision: Long, moved: Boolean = false, removed: Boolean = false): Pair<String, Map<String, Any?>> {
        val placements1 = listOf(
            mapOf<String, Any?>("placementId" to "p1", "dayOfWeek" to if (moved) 2L else 1L, "workoutId" to "workout-a", "workoutVersionId" to "workout-a-r1", "preferredMinuteOfDay" to 540L, "reminderEnabled" to true, "notes" to "Optional note"),
            mapOf<String, Any?>("placementId" to "p2", "dayOfWeek" to 3L, "workoutId" to "workout-b", "workoutVersionId" to "workout-b-r1", "preferredMinuteOfDay" to null, "reminderEnabled" to false, "notes" to "")).let { if (removed) it.take(1) else it }
        val payload = mapOf<String, Any?>("schemaVersion" to "humanv1.plan/1", "planId" to "plan-1", "title" to "Two weeks",
            "description" to "", "startDate" to "2026-09-14", "timezone" to "Europe/London",
            "destinationApplication" to "HUMAN_STRENGTH", "workoutVersionIds" to (if (removed) listOf("workout-a-r1") else listOf("workout-a-r1", "workout-b-r1")), "weeks" to listOf(
                mapOf("weekId" to "w1", "weekNumber" to 1L, "label" to "Week 1", "placements" to placements1),
                mapOf("weekId" to "w2", "weekNumber" to 2L, "label" to "Week 2", "placements" to listOf(
                    mapOf<String, Any?>("placementId" to "p3", "dayOfWeek" to 1L, "workoutId" to "workout-a", "workoutVersionId" to "workout-a-r1", "preferredMinuteOfDay" to null, "reminderEnabled" to false, "notes" to "")))))
        val checksum = StudioWorkoutContract.sha256(StudioWorkoutContract.canonicalJson(payload))
        val id = "plan-1_r${revision}_${checksum.take(12)}"
        return id to mapOf("schemaVersion" to "humanv1.plan/1", "globalId" to "plan-1", "versionId" to id,
            "humanUserId" to owner, "revision" to revision, "publicationState" to "PUBLISHED", "tombstoneState" to "ACTIVE",
            "sourceDraftId" to "plan-1", "contentType" to "plan", "contentChecksum" to checksum, "payload" to payload)
    }

    private fun singlePublication(revision: Long, placementId: String, planId: String = "plan-reconcile",
                                  scheduledEpochDay: Long = 20719L): Pair<String, Map<String, Any?>> {
        val payload = mapOf<String, Any?>("schemaVersion" to "humanv1.plan/1", "planId" to planId,
            "title" to "Reconciled plan", "description" to "", "startDate" to "2026-09-23", "timezone" to "Europe/London",
            "destinationApplication" to "HUMAN_STRENGTH", "workoutVersionIds" to listOf("workout-a-r1"),
            "weeks" to listOf(mapOf("weekId" to "w1", "weekNumber" to 1L, "label" to "Week 1", "placements" to listOf(
                mapOf<String, Any?>("placementId" to placementId, "dayOfWeek" to 1L, "scheduledEpochDay" to scheduledEpochDay,
                    "workoutId" to "workout-a", "workoutVersionId" to "workout-a-r1", "preferredMinuteOfDay" to null,
                    "reminderEnabled" to false, "notes" to "")))))
        val checksum = StudioWorkoutContract.sha256(StudioWorkoutContract.canonicalJson(payload))
        val id = "${planId}_r${revision}_${checksum.take(12)}"
        return id to mapOf("schemaVersion" to "humanv1.plan/1", "globalId" to planId, "versionId" to id,
            "humanUserId" to owner, "revision" to revision, "publicationState" to "PUBLISHED", "tombstoneState" to "ACTIVE",
            "sourceDraftId" to planId, "contentType" to "plan", "contentChecksum" to checksum, "payload" to payload)
    }

    @Test fun `newer revision with changed placement supersedes only untouched occurrence and replay is inert`() = runBlocking {
        val dao = db.strengthDao()
        dao.insertStudioWorkoutLink(workout("workout-a-r1", "workout-a", 11))
        val (v1, e1) = singlePublication(1, "old-placement")
        val first = StudioPlanContract.parse(v1, e1, owner, "uid-a", { dao.getStudioWorkoutLink(it) }, 1000)
        assertTrue(dao.applyStudioPlanTransaction(first).applied)
        val (v3, e3) = singlePublication(3, "replacement-placement")
        val replacement = StudioPlanContract.parse(v3, e3, owner, "uid-a", { dao.getStudioWorkoutLink(it) }, 3000)
        assertTrue(dao.applyStudioPlanTransaction(replacement).applied)
        assertEquals(3000L, dao.getPlannedWorkout("plan-reconcile:old-placement")?.deletedAt)
        assertNull(dao.getPlannedWorkout("plan-reconcile:replacement-placement")?.deletedAt)
        val beforeReplay = dao.getAllPlannedWorkoutsForBackup("uid-a")
        assertFalse(dao.applyStudioPlanTransaction(replacement).applied)
        assertEquals(beforeReplay, dao.getAllPlannedWorkoutsForBackup("uid-a"))
        assertFalse(dao.applyStudioPlanTransaction(first).applied)
        assertEquals(beforeReplay, dao.getAllPlannedWorkoutsForBackup("uid-a"))
    }

    @Test fun `legacy acknowledged exact version heals once without duplicate acknowledgement`() = runBlocking {
        val dao = db.strengthDao()
        dao.insertStudioWorkoutLink(workout("workout-a-r1", "workout-a", 11))
        val (v1, e1) = singlePublication(1, "old-placement")
        dao.applyStudioPlanTransaction(StudioPlanContract.parse(v1, e1, owner, "uid-a", { dao.getStudioWorkoutLink(it) }, 1000))
        val (v3, e3) = singlePublication(3, "replacement-placement")
        val replacement = StudioPlanContract.parse(v3, e3, owner, "uid-a", { dao.getStudioWorkoutLink(it) }, 3000)
        dao.applyStudioPlanTransaction(replacement)
        val existing = requireNotNull(dao.getStudioPlanLink(v3))
        dao.updateStudioPlanLink(existing.copy(acknowledgementState = "APPLIED", planReconciliationVersion = 0))
        val obsolete = requireNotNull(dao.getPlannedWorkout("plan-reconcile:old-placement"))
        dao.upsertPlannedWorkout(obsolete.copy(deletedAt = null))

        var acknowledgements = 0
        val repository = StudioPlanIngestionRepository(com.google.firebase.firestore.FirebaseFirestore.getInstance(), dao,
            { 4000L }, { _, _, _ -> acknowledgements++ })
        val first = repository.synchronizeEnvelopes(owner, "uid-a", listOf(StudioPlanEnvelope(v3, e3)))
        assertEquals(1, first.applied)
        assertEquals(4000L, dao.getPlannedWorkout("plan-reconcile:old-placement")?.deletedAt)
        assertNull(dao.getPlannedWorkout("plan-reconcile:replacement-placement")?.deletedAt)
        assertEquals(CURRENT_PLAN_RECONCILIATION_VERSION, dao.getStudioPlanLink(v3)?.planReconciliationVersion)
        assertEquals("APPLIED", dao.getStudioPlanLink(v3)?.acknowledgementState)
        assertEquals(0, acknowledgements)

        val beforeReplay = dao.getAllPlannedWorkoutsForBackup("uid-a")
        assertEquals(0, repository.synchronizeEnvelopes(owner, "uid-a", listOf(StudioPlanEnvelope(v3, e3))).applied)
        assertEquals(beforeReplay, dao.getAllPlannedWorkoutsForBackup("uid-a"))
        assertEquals(0, acknowledgements)
    }

    @Test fun `future reconciliation version fails closed without occurrence changes`() = runBlocking {
        val dao = db.strengthDao()
        dao.insertStudioWorkoutLink(workout("workout-a-r1", "workout-a", 11))
        val (id, envelope) = singlePublication(3, "replacement-placement")
        val parsed = StudioPlanContract.parse(id, envelope, owner, "uid-a", { dao.getStudioWorkoutLink(it) }, 1000)
        dao.applyStudioPlanTransaction(parsed)
        val link = requireNotNull(dao.getStudioPlanLink(id))
        dao.updateStudioPlanLink(link.copy(planReconciliationVersion = CURRENT_PLAN_RECONCILIATION_VERSION + 1))
        val before = dao.getAllPlannedWorkoutsForBackup("uid-a")
        val failure = runCatching { dao.applyStudioPlanTransaction(parsed) }.exceptionOrNull()
        assertEquals("UNSUPPORTED_PLAN_RECONCILIATION_VERSION", failure?.message)
        assertEquals(before, dao.getAllPlannedWorkoutsForBackup("uid-a"))
    }

    @Test fun `concurrent legacy healing is serialized and advances once`() = runBlocking {
        val dao = db.strengthDao()
        dao.insertStudioWorkoutLink(workout("workout-a-r1", "workout-a", 11))
        val (id, envelope) = singlePublication(3, "replacement-placement")
        val parsed = StudioPlanContract.parse(id, envelope, owner, "uid-a", { dao.getStudioWorkoutLink(it) }, 1000)
        dao.applyStudioPlanTransaction(parsed)
        dao.updateStudioPlanLink(requireNotNull(dao.getStudioPlanLink(id)).copy(planReconciliationVersion = 0))
        val results = listOf(async { dao.applyStudioPlanTransaction(parsed) }, async { dao.applyStudioPlanTransaction(parsed) }).awaitAll()
        assertEquals(1, results.count { it.applied })
        assertEquals(1, results.count { !it.applied })
        assertEquals(CURRENT_PLAN_RECONCILIATION_VERSION, dao.getStudioPlanLink(id)?.planReconciliationVersion)
        assertEquals(1, dao.getPlannedWorkoutsForSeries("plan-reconcile", "uid-a").count { it.deletedAt == null })
    }

    @Test fun `history is preserved and local future edits block acknowledgement-ready apply`() = runBlocking {
        val dao = db.strengthDao()
        dao.insertStudioWorkoutLink(workout("workout-a-r1", "workout-a", 11))
        suspend fun scenario(status: String, detached: Boolean, syncStatus: String): StudioPlanApplyResult {
            db.clearAllTables()
            dao.insertStudioWorkoutLink(workout("workout-a-r1", "workout-a", 11))
            val (v1, e1) = singlePublication(1, "old-placement")
            dao.applyStudioPlanTransaction(StudioPlanContract.parse(v1, e1, owner, "uid-a", { dao.getStudioWorkoutLink(it) }, 1000))
            val old = requireNotNull(dao.getPlannedWorkout("plan-reconcile:old-placement"))
            dao.upsertPlannedWorkout(old.copy(status = status, detachedFromSeries = detached, syncStatus = syncStatus,
                completedAt = if (status == "COMPLETED") 1500 else null, linkedSessionId = if (status == "COMPLETED") 7 else null))
            val (v3, e3) = singlePublication(3, "replacement-placement")
            return dao.applyStudioPlanTransaction(StudioPlanContract.parse(v3, e3, owner, "uid-a", { dao.getStudioWorkoutLink(it) }, 3000))
        }
        assertTrue(scenario("COMPLETED", false, "SYNCED").applied)
        assertNull(dao.getPlannedWorkout("plan-reconcile:old-placement")?.deletedAt)
        assertTrue(scenario("SKIPPED", false, "SYNCED").applied)
        assertNull(dao.getPlannedWorkout("plan-reconcile:old-placement")?.deletedAt)
        assertTrue(scenario("PLANNED", true, "SYNCED").applied)
        assertNull(dao.getPlannedWorkout("plan-reconcile:old-placement")?.deletedAt)
        assertTrue(scenario("PLANNED", false, "PENDING_UPLOAD").conflict)
        assertNull(dao.getPlannedWorkout("plan-reconcile:replacement-placement"))
        assertNull(dao.getPlannedWorkout("plan-reconcile:old-placement")?.deletedAt)
    }

    @Test fun `same day placements remain distinct and another plan is untouched`() = runBlocking {
        val dao = db.strengthDao()
        dao.insertStudioWorkoutLink(workout("workout-a-r1", "workout-a", 11))
        val (oneId, oneEnvelope) = singlePublication(1, "one", "plan-one")
        val one = StudioPlanContract.parse(oneId, oneEnvelope, owner, "uid-a", { dao.getStudioWorkoutLink(it) }, 1000)
        val duplicateSlot = one.occurrences.single().copy(id = "plan-one:two", globalId = "plan-one:two")
        assertTrue(dao.applyStudioPlanTransaction(one.copy(occurrences = one.occurrences + duplicateSlot)).applied)
        val (otherId, otherEnvelope) = singlePublication(1, "one", "plan-other")
        assertTrue(dao.applyStudioPlanTransaction(StudioPlanContract.parse(otherId, otherEnvelope, owner, "uid-a", { dao.getStudioWorkoutLink(it) }, 1000)).applied)
        assertEquals(2, dao.getPlannedWorkoutsForSeries("plan-one", "uid-a").count { it.deletedAt == null })
        assertEquals(1, dao.getPlannedWorkoutsForSeries("plan-other", "uid-a").count { it.deletedAt == null })
    }

    @Test fun `two week plan reconstructs exact dependencies and is idempotent`() = runBlocking {
        val dao = db.strengthDao()
        dao.insertStudioWorkoutLink(workout("workout-a-r1", "workout-a", 11))
        dao.insertStudioWorkoutLink(workout("workout-b-r1", "workout-b", 12))
        val (id, envelope) = publication(1)
        val parsed = StudioPlanContract.parse(id, envelope, owner, "uid-a", { dao.getStudioWorkoutLink(it) }, 1000)
        assertEquals(3, parsed.occurrences.size)
        assertEquals(listOf(11, 12, 11), parsed.occurrences.map { it.templateId })
        assertEquals(listOf(20710L, 20712L, 20717L), parsed.occurrences.map { it.scheduledEpochDay })
        assertTrue(dao.applyStudioPlanTransaction(parsed).applied)
        assertFalse(dao.applyStudioPlanTransaction(parsed).applied)
        assertEquals(1, dao.getTrainingPlansForUser("uid-a").size)
        assertEquals(3, dao.getAllPlannedWorkoutsForBackup("uid-a").size)
    }

    @Test fun `parser accepts the canonical immutable plan and exact workout version`() = runBlocking {
        val dependency = workout("workout-a-r1", "workout-a", 11)
        val payload = mapOf<String, Any?>("schemaVersion" to "humanv1.canonical-plan/1", "planGlobalId" to "canonical-plan",
            "revision" to 1L, "checksum" to "", "owner" to mapOf("humanUserId" to owner),
            "provenance" to mapOf("contentClass" to "USER_AUTHORED", "originApplication" to "WORKOUT_STUDIO"),
            "title" to "Canonical plan", "goal" to "", "athleteLevel" to "ALL", "durationWeeks" to 1L,
            "timezone" to "Europe/London", "startDate" to "2026-09-14", "phases" to emptyList<Any>(), "cycles" to emptyList<Any>(),
            "scheduleSchemaVersion" to "1.2", "weeks" to listOf(mapOf("weekId" to "week-1", "order" to 0L, "recoveryWeek" to false,
                "placements" to listOf(mapOf("placementId" to "placement-1", "order" to 0L, "daySlot" to 1L,
                    "workoutGlobalId" to dependency.workoutGlobalId, "workoutVersionId" to dependency.versionId,
                    "workoutRevision" to dependency.sourceRevision, "workoutChecksum" to dependency.contentChecksum,
                    "workoutOwnerHumanUserId" to owner, "destinationApplication" to "HUMAN_STRENGTH",
                    "required" to true, "priority" to false, "adaptation" to "FIXED")), "days" to emptyList<Any>())),
            "publicationEligibility" to "ELIGIBLE", "validationStatus" to "VALID", "createdAt" to "2026-01-01T00:00:00.000Z",
            "updatedAt" to "2026-01-01T00:00:00.000Z", "tombstoneState" to "ACTIVE")
        val checksum = StudioWorkoutContract.sha256(StudioWorkoutContract.canonicalJson(payload))
        val versionId = "canonical-plan_r1_${checksum.take(12)}"
        val envelope = mapOf<String, Any?>("schemaVersion" to "humanv1.canonical-plan/1", "globalId" to "canonical-plan", "versionId" to versionId,
            "humanUserId" to owner, "revision" to 1L, "publicationState" to "PUBLISHED", "tombstoneState" to "ACTIVE",
            "sourceDraftId" to "canonical-plan", "contentType" to "plan", "contentChecksum" to checksum, "payload" to payload)
        val parsed = StudioPlanContract.parse(versionId, envelope, owner, "uid-a", { if (it == dependency.versionId) dependency else null }, 1000)
        assertEquals(1, parsed.occurrences.size)
        assertEquals(dependency.localRoutineId, parsed.occurrences.single().templateId)
    }

    @Test fun `new revision preserves completed history and missing dependency fails closed`() = runBlocking {
        val dao = db.strengthDao()
        dao.insertStudioWorkoutLink(workout("workout-a-r1", "workout-a", 11))
        dao.insertStudioWorkoutLink(workout("workout-b-r1", "workout-b", 12))
        val (id1, envelope1) = publication(1)
        dao.applyStudioPlanTransaction(StudioPlanContract.parse(id1, envelope1, owner, "uid-a", { dao.getStudioWorkoutLink(it) }, 1000))
        val completed = requireNotNull(dao.getPlannedWorkout("plan-1:p1")).copy(status = "COMPLETED", completedAt = 1500, syncStatus = "SYNCED")
        dao.upsertPlannedWorkout(completed)
        val (id2, envelope2) = publication(2, moved = true)
        dao.applyStudioPlanTransaction(StudioPlanContract.parse(id2, envelope2, owner, "uid-a", { dao.getStudioWorkoutLink(it) }, 2000))
        assertEquals(completed, dao.getPlannedWorkout("plan-1:p1"))
        val (id3, envelope3) = publication(3, moved = true, removed = true)
        dao.applyStudioPlanTransaction(StudioPlanContract.parse(id3, envelope3, owner, "uid-a", { dao.getStudioWorkoutLink(it) }, 2500))
        assertEquals(2500L, dao.getPlannedWorkout("plan-1:p2")?.deletedAt)
        dao.clearLatestStudioWorkoutLink(owner, "workout-b")
        // An absent exact version is never substituted with a different workout revision.
        val failure = runCatching { StudioPlanContract.parse(id2, envelope2, owner, "uid-a", { if (it == "workout-b-r1") null else dao.getStudioWorkoutLink(it) }, 3000) }.exceptionOrNull()
        assertEquals("MISSING_WORKOUT_DEPENDENCY", (failure as StudioPlanContractException).reasonCode)
    }

    @Test fun `wrong owner and checksum fail closed`() = runBlocking {
        val (id, envelope) = publication(1)
        val wrongOwner = runCatching { StudioPlanContract.parse(id, envelope, "human_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "uid", { null }) }.exceptionOrNull()
        assertEquals("WRONG_OWNER", (wrongOwner as StudioPlanContractException).reasonCode)
        val badChecksum = runCatching { StudioPlanContract.parse(id, envelope + ("contentChecksum" to "f".repeat(64)), owner, "uid", { null }) }.exceptionOrNull()
        assertEquals("CHECKSUM_MISMATCH", (badChecksum as StudioPlanContractException).reasonCode)
    }

    @Test fun `missing dependency is bounded then quarantined without acknowledgement`() = runBlocking {
        val dao = db.strengthDao()
        val (id, envelope) = publication(1)
        var now = 1_000L
        var acknowledgements = 0
        val repository = StudioPlanIngestionRepository(com.google.firebase.firestore.FirebaseFirestore.getInstance(), dao,
            { now }, { _, _, _ -> acknowledgements++ })
        assertEquals(1, repository.synchronizeEnvelopes(owner, "uid-a", listOf(StudioPlanEnvelope(id, envelope))).waiting)
        now = 31_001
        assertEquals(1, repository.synchronizeEnvelopes(owner, "uid-a", listOf(StudioPlanEnvelope(id, envelope))).waiting)
        now = 151_002
        val third = repository.synchronizeEnvelopes(owner, "uid-a", listOf(StudioPlanEnvelope(id, envelope)))
        assertEquals(1, third.requiresAttention)
        assertEquals(0, acknowledgements)
        assertTrue(dao.getAllPlannedWorkoutsForBackup("uid-a").isEmpty())
        assertEquals("MISSING_WORKOUT_DEPENDENCY", dao.getStudioPlanQuarantine(id)?.reasonCode)
    }

    @Test fun `poisoned plan does not block valid plan and corrected dependency clears quarantine idempotently`() = runBlocking {
        val dao = db.strengthDao()
        val missing = publication(2)
        val valid = publication(1, removed = true)
        dao.insertStudioWorkoutLink(workout("workout-a-r1", "workout-a", 11))
        var acknowledgements = 0
        val repository = StudioPlanIngestionRepository(com.google.firebase.firestore.FirebaseFirestore.getInstance(), dao,
            { 1_000L }, { _, _, _ -> acknowledgements++ })
        val first = repository.synchronizeEnvelopes(owner, "uid-a", listOf(StudioPlanEnvelope(missing.first, missing.second), StudioPlanEnvelope(valid.first, valid.second)))
        assertEquals(1, first.applied)
        assertEquals(1, acknowledgements)
        assertNotNull(dao.getTrainingPlan("plan-1"))
        dao.insertStudioWorkoutLink(workout("workout-b-r1", "workout-b", 12))
        val recovered = StudioPlanIngestionRepository(com.google.firebase.firestore.FirebaseFirestore.getInstance(), dao,
            { 31_001L }, { _, _, _ -> acknowledgements++ })
        assertEquals(1, recovered.synchronizeEnvelopes(owner, "uid-a", listOf(StudioPlanEnvelope(missing.first, missing.second))).applied)
        assertEquals(0, recovered.synchronizeEnvelopes(owner, "uid-a", listOf(StudioPlanEnvelope(missing.first, missing.second))).applied)
        assertNull(dao.getStudioPlanQuarantine(missing.first))
    }

    @Test fun `dependency owner destination archive and immutable checksum fail closed`() = runBlocking {
        val (_, envelope) = publication(1, removed = true)
        val id = envelope["versionId"] as String
        suspend fun reason(link: StudioWorkoutLink) = (runCatching {
            StudioPlanContract.parse(id, envelope, owner, "uid", { link })
        }.exceptionOrNull() as StudioPlanContractException).reasonCode
        assertEquals("WORKOUT_DEPENDENCY_MISMATCH", reason(workout("workout-a-r1", "other", 1)))
        assertEquals("WORKOUT_DEPENDENCY_WRONG_DESTINATION", reason(workout("workout-a-r1", "workout-a", 1).copy(applicationId = "OTHER")))
        assertEquals("WORKOUT_DEPENDENCY_ARCHIVED", reason(workout("workout-a-r1", "workout-a", 1).copy(tombstoneState = "ARCHIVED")))
        val canonicalId = "workout-a_r1_${"2".repeat(12)}"
        val changedPayload = (envelope["payload"] as Map<String, Any?>).toMutableMap().apply {
            this["workoutVersionIds"] = listOf(canonicalId)
            this["weeks"] = listOf(mapOf("weekNumber" to 1L, "placements" to listOf(mapOf(
                "placementId" to "p1", "dayOfWeek" to 1L, "workoutId" to "workout-a", "workoutVersionId" to canonicalId))))
        }
        val checksum = StudioWorkoutContract.sha256(StudioWorkoutContract.canonicalJson(changedPayload))
        val changedId = "plan-1_r1_${checksum.take(12)}"
        val changedEnvelope = envelope + mapOf("versionId" to changedId, "contentChecksum" to checksum, "payload" to changedPayload)
        val failure = runCatching { StudioPlanContract.parse(changedId, changedEnvelope, owner, "uid", { workout(canonicalId, "workout-a", 1) }) }.exceptionOrNull()
        assertEquals("WORKOUT_DEPENDENCY_CHECKSUM_MISMATCH", (failure as StudioPlanContractException).reasonCode)
    }
}
