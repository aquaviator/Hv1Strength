package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AnonymousAttachmentTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, StrengthDatabase::class.java).allowMainThreadQueries().build()
    private val dao = db.strengthDao()
    private val repo = StrengthRepository(dao, context, "device_local")
    private val owner = "human_1234567890abcdef1234567890abcdef"
    private val offline = "human_0123456789ab"
    private val uid = "synthetic_uid"
    private val session = WorkoutSession(id = 1, templateName = "Synthetic historical workout", startTime = 100, endTime = 200,
        globalId = "session_synthetic1", humanUserId = owner, createdAt = 200, updatedAt = 200,
        revision = 1, syncStatus = "SYNCED", lastSyncedAt = 300, originDeviceId = "device_remote")

    @Before fun setup() = runBlocking {
        context.getSharedPreferences("human_identity_prefs", Context.MODE_PRIVATE).edit()
            .putString("offline_human_user_id", offline).commit()
        dao.insertUserProfile(UserProfile(id = uid, firebaseUid = uid, humanUserId = owner, authProvider = "google"))
    }
    @After fun close() { db.close() }
    private suspend fun hydrate() = repo.linkExistingDataToUser(uid, owner)
    private suspend fun assertUnchanged(record: WorkoutSession) {
        dao.insertSession(record)
        repeat(3) { hydrate() }
        assertEquals(record, dao.getSessionById(record.id))
        assertTrue(repo.getAllCommands().isEmpty())
    }

    @Test fun downloadedSessionPreservesEveryField() = runBlocking { assertUnchanged(session) }
    @Test fun repeatedHydrationIsIdempotent() = runBlocking {
        dao.insertSession(session)
        repeat(10) { hydrate(); assertEquals(session, dao.getSessionById(1)) }
    }
    @Test fun downloadedSessionDoesNotBecomeUploadCandidate() = runBlocking {
        assertUnchanged(session)
        assertTrue(dao.getPendingUploadSessions().isEmpty())
        assertTrue(dao.getPendingCommands(Long.MAX_VALUE).isEmpty())
    }
    @Test fun downloadedLoggedSetsPreserveEveryField() = runBlocking {
        val set = LoggedSet(id = 1, sessionId = 1, exerciseId = "exercise_synthetic", setNumber = 1, reps = 10, weight = 2.5f,
            globalId = "logged_set_synthetic1", sessionGlobalId = session.globalId, humanUserId = owner,
            createdAt = 200, updatedAt = 200, revision = 1, syncStatus = "SYNCED", lastSyncedAt = 300, originDeviceId = "device_remote")
        dao.insertLoggedSet(set)
        assertUnchanged(session)
        assertEquals(listOf(set), dao.getSetsForSessionSync(1))
        assertTrue(dao.getPendingUploadLoggedSets().isEmpty())
    }
    @Test fun trustedHumanOwnerIsNotReplacedByFirebaseUid() = runBlocking {
        assertUnchanged(session.copy(originDeviceId = "device_local"))
        assertEquals(owner, dao.getSessionById(1)?.humanUserId)
    }
    @Test fun explicitAnonymousLocalSessionAttachesExactlyOnce() = runBlocking {
        val local = session.copy(humanUserId = offline, originDeviceId = "device_local", lastSyncedAt = null, syncStatus = "PENDING_UPLOAD")
        dao.insertSession(local)
        repeat(3) { hydrate() }
        assertEquals(local.copy(userId = uid, humanUserId = owner, revision = 2), dao.getSessionById(1))
        assertEquals(1, dao.getPendingUploadSessions().size)
    }
    @Test fun alreadyAttachedLocalDataDoesNotInflateRevision() = runBlocking {
        assertUnchanged(session.copy(userId = uid, syncStatus = "PENDING_UPLOAD", revision = 7, originDeviceId = "device_local"))
    }
    @Test fun pendingTrustedLocalEditSurvives() = runBlocking {
        assertUnchanged(session.copy(templateName = "Pending edit", revision = 8, syncStatus = "PENDING_UPLOAD"))
    }
    @Test fun otherAccountDataCannotBeAttached() = runBlocking {
        assertUnchanged(session.copy(humanUserId = "human_abcdef1234567890abcdef1234567890", syncStatus = "PENDING_UPLOAD"))
    }
    @Test fun ambiguousAndForeignProvenanceFailsClosed() = runBlocking {
        for (record in listOf(
            session.copy(humanUserId = "", lastSyncedAt = null, syncStatus = "LOCAL_ONLY"),
            session.copy(humanUserId = offline, lastSyncedAt = null, syncStatus = "PENDING_UPLOAD"),
            session.copy(humanUserId = offline, originDeviceId = "device_local"),
            session.copy(humanUserId = "human_offlineusr", originDeviceId = "device_local", lastSyncedAt = null, syncStatus = "LOCAL_ONLY")
        )) assertUnchanged(record)
    }
    @Test fun allSharedEntityTypesProtectDownloadedRecordsAndAttachExplicitLocalOnes() = runBlocking {
        val weight = BodyWeight(id = 1, weight = 75f, date = 100, globalId = "measurement_synthetic1", humanUserId = owner,
            createdAt = 100, updatedAt = 100, syncStatus = "SYNCED", lastSyncedAt = 300, originDeviceId = "device_remote")
        val tape = TapeMeasurement(id = 1, date = 100, waist = 80f, globalId = "measurement_synthetic2", humanUserId = owner,
            createdAt = 100, updatedAt = 100, syncStatus = "SYNCED", lastSyncedAt = 300, originDeviceId = "device_remote")
        val template = WorkoutTemplate(id = 1, name = "Synthetic", exerciseIdsJson = "[]", globalId = "template_synthetic1", humanUserId = owner,
            createdAt = 100, updatedAt = 100, syncStatus = "SYNCED", lastSyncedAt = 300, originDeviceId = "device_remote")
        dao.insertBodyWeight(weight); dao.insertTapeMeasurement(tape); dao.insertTemplate(template)
        repeat(3) { hydrate() }
        assertEquals(weight, dao.getBodyWeightById(1)); assertEquals(tape, dao.getTapeMeasurementById(1)); assertEquals(template, dao.getTemplateById(1))
        val localWeight = weight.copy(humanUserId = offline, lastSyncedAt = null, syncStatus = "LOCAL_ONLY", originDeviceId = "device_local")
        val localTape = tape.copy(humanUserId = offline, lastSyncedAt = null, syncStatus = "LOCAL_ONLY", originDeviceId = "device_local")
        val localTemplate = template.copy(humanUserId = offline, lastSyncedAt = null, syncStatus = "LOCAL_ONLY", originDeviceId = "device_local")
        dao.insertBodyWeight(localWeight); dao.insertTapeMeasurement(localTape); dao.insertTemplate(localTemplate)
        repeat(3) { hydrate() }
        assertEquals(localWeight.copy(userId = uid, humanUserId = owner, revision = 2, syncStatus = "PENDING_UPLOAD"), dao.getBodyWeightById(1))
        assertEquals(localTape.copy(userId = uid, humanUserId = owner, revision = 2, syncStatus = "PENDING_UPLOAD"), dao.getTapeMeasurementById(1))
        assertEquals(localTemplate.copy(userId = uid, humanUserId = owner, revision = 2, syncStatus = "PENDING_UPLOAD"), dao.getTemplateById(1))
    }
    @Test fun mismatchedTrustedProfileCannotAuthorizeLinkage() = runBlocking {
        dao.insertSession(session)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.linkExistingDataToUser("another_uid", owner) } }
        assertEquals(session, dao.getSessionById(1))
    }
    @Test fun hydrationRefreshAndCheckNowReconciliationCreateNoChanges() = runBlocking {
        dao.insertSession(session)
        hydrate()
        assertEquals(session, repo.getSessionById(1))
        hydrate()
        repo.reconcileMissingEditableCommands(uid, owner)
        assertEquals(session, dao.getSessionById(1))
        assertTrue(dao.getPendingUploadSessions().isEmpty())
        assertTrue(repo.getAllCommands().isEmpty())
    }
    @Test fun missingInstallationProvenanceDoesNotInventAnOwner() = runBlocking {
        context.getSharedPreferences("human_identity_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        assertUnchanged(session.copy(humanUserId = offline, originDeviceId = "device_local", lastSyncedAt = null, syncStatus = "LOCAL_ONLY"))
        assertFalse(context.getSharedPreferences("human_identity_prefs", Context.MODE_PRIVATE).contains("offline_human_user_id"))
    }
}
