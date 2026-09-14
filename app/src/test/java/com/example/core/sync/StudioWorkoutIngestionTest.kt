package com.example.core.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.StrengthDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.json.JSONArray
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
class StudioWorkoutIngestionTest {
    private lateinit var db: StrengthDatabase
    private val owner = "human_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

    private fun jsonValue(value: Any?): Any? = when (value) {
        JSONObject.NULL -> null
        is JSONObject -> value.keys().asSequence().associateWith { jsonValue(value.get(it)) }
        is JSONArray -> (0 until value.length()).map { jsonValue(value.get(it)) }
        else -> value
    }

    @Test fun `production acceptance payload matches Studio JavaScript checksum`() {
        val json = """{"schemaVersion":"humanv1.workout/1","workoutId":"a9b619f7-4922-4463-890c-fb7778735d52","title":"Studio Production Acceptance","discipline":"STRENGTH","catalogueReleaseId":"fixture_catalogue_v1","tags":[],"blocks":[{"blockId":"c982ed80-2ee3-4846-8561-4ab5d45150b8","type":"EXERCISE","exerciseId":"squat","exerciseNameSnapshot":"Barbell Squat","efforts":[{"effortId":"a75796c5-a21f-4551-95f6-ae923f55c7ed","effortType":"WORKING","prescriptions":[{"prescriptionId":"2be6f006-70ef-4120-8581-e2d010092884","metricKey":"repetitions","targetValue":10,"canonicalUnit":"count","position":0},{"prescriptionId":"95c3a107-21ba-40b7-a9be-d912a7459b47","metricKey":"external_load","targetValue":20,"canonicalUnit":"kg","position":1}]}]},{"blockId":"147ccbe1-92c1-44cb-b799-851102fb627c","type":"EXERCISE","exerciseId":"treadmill_run","exerciseNameSnapshot":"Treadmill Run","efforts":[{"effortId":"e53c4c68-507c-49b4-83a2-1bfc3b3cb9c6","effortType":"WORKING","prescriptions":[{"prescriptionId":"11e4424f-ed40-429e-861c-e8efdfb8d143","metricKey":"duration","targetValue":60,"canonicalUnit":"s","position":0},{"prescriptionId":"9828e987-73df-4dfd-9d06-abe45674f2b9","metricKey":"distance","targetValue":100,"canonicalUnit":"m","position":1}]}]}]}"""
        val payload = jsonValue(JSONObject(json))
        assertEquals("ead95d373d8506515e7b7d52cffcb89f8471ccc2ddb2c1b6944e7ec7d278e0b2",
            StudioWorkoutContract.sha256(StudioWorkoutContract.canonicalJson(payload)))
    }

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), StrengthDatabase::class.java)
            .allowMainThreadQueries().build()
    }
    @After fun tearDown() {
        if (::db.isInitialized) db.close()
    }

    private fun publication(revision: Long, reps: Long = 10, checksumOverride: String? = null): Pair<String, Map<String, Any?>> {
        val globalId = "workout-acceptance"
        val payload = mapOf<String, Any?>(
            "schemaVersion" to "humanv1.workout/1", "workoutId" to globalId,
            "title" to "Studio Production Acceptance", "description" to "Delivery test",
            "discipline" to "STRENGTH", "catalogueReleaseId" to "strength-2026.08.36-v1", "tags" to emptyList<String>(),
            "blocks" to listOf(
                mapOf("blockId" to "squat", "type" to "EXERCISE", "exerciseId" to "barbell_squat",
                    "exerciseNameSnapshot" to "Barbell Squat", "efforts" to listOf(mapOf("effortId" to "set-1",
                        "effortType" to "WORKING", "prescriptions" to listOf(
                            mapOf("prescriptionId" to "reps", "metricKey" to "repetitions", "targetValue" to reps, "canonicalUnit" to "count", "position" to 0L),
                            mapOf("prescriptionId" to "load", "metricKey" to "external_load", "targetValue" to 20L, "canonicalUnit" to "kg", "position" to 1L))))),
                mapOf("blockId" to "run", "type" to "EXERCISE", "exerciseId" to "treadmill_run",
                    "exerciseNameSnapshot" to "Treadmill Run", "efforts" to listOf(mapOf("effortId" to "set-2",
                        "effortType" to "WORKING", "prescriptions" to listOf(
                            mapOf("prescriptionId" to "duration", "metricKey" to "duration", "targetValue" to 60L, "canonicalUnit" to "s", "position" to 0L),
                            mapOf("prescriptionId" to "distance", "metricKey" to "distance", "targetValue" to 100L, "canonicalUnit" to "m", "position" to 1L)))))
            ))
        val checksum = checksumOverride ?: StudioWorkoutContract.sha256(StudioWorkoutContract.canonicalJson(payload))
        val versionId = "${globalId}_r${revision}_${checksum.take(12)}"
        return versionId to mapOf("schemaVersion" to "humanv1.workout/1", "globalId" to globalId,
            "versionId" to versionId, "humanUserId" to owner, "revision" to revision,
            "publicationState" to "PUBLISHED", "tombstoneState" to "ACTIVE", "sourceDraftId" to globalId,
            "contentType" to "workout", "contentChecksum" to checksum, "compatibleTags" to listOf("STRENGTH"),
            "payload" to payload)
    }

    @Test fun `parser preserves governed IDs and mixed metrics`() {
        val (id, envelope) = publication(1)
        val parsed = StudioWorkoutContract.parse(id, envelope, owner, "uid-a", 1000)
        assertEquals(listOf("barbell_squat", "treadmill_run"), parsed.exercises.map { it.exercise.exerciseId })
        assertEquals(listOf("repetitions", "external_load"), parsed.exercises[0].efforts[0].prescriptions.map { it.metricKey })
        assertEquals(listOf("duration", "distance"), parsed.exercises[1].efforts[0].prescriptions.map { it.metricKey })
        assertNull(parsed.exercises[1].efforts[0].set.targetWeight)
        assertNull(parsed.exercises[1].efforts[0].set.targetRepsMin)
        assertEquals(60, parsed.exercises[1].efforts[0].set.targetDurationSeconds)
        assertEquals(0.1f, parsed.exercises[1].efforts[0].set.targetDistance)
    }

    @Test fun `wrong owner checksum and unsupported metric fail closed`() {
        val (id, envelope) = publication(1)
        assertEquals("WRONG_OWNER", assertThrows(StudioWorkoutContractException::class.java) {
            StudioWorkoutContract.parse(id, envelope, "human_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "uid-b")
        }.reasonCode)
        assertEquals("CHECKSUM_MISMATCH", assertThrows(StudioWorkoutContractException::class.java) {
            StudioWorkoutContract.parse(id, envelope + ("contentChecksum" to "a".repeat(64)), owner, "uid-a")
        }.reasonCode)
    }

    @Test fun `malformed publication is isolated while independent valid publication continues`() {
        val (validId, valid) = publication(2)
        val (invalidId, invalid) = publication(1, checksumOverride = "a".repeat(64))
        val batch = parseStudioWorkoutBatch(listOf(invalidId to invalid, validId to valid), owner, "uid-a")
        assertEquals(1, batch.requiresAttention)
        assertEquals(listOf(validId), batch.imports.map { it.link.versionId })
    }

    @Test fun `same version is idempotent and revision two supersedes without losing revision one provenance`() = runBlocking {
        val dao = db.strengthDao()
        val (id1, envelope1) = publication(1, 10)
        val first = StudioWorkoutContract.parse(id1, envelope1, owner, "uid-a", 1000)
        val applied = dao.applyStudioWorkoutTransaction(first)
        val again = dao.applyStudioWorkoutTransaction(first)
        assertTrue(applied.applied)
        assertFalse(again.applied)
        assertEquals(1, dao.getStudioWorkoutLinks(owner).size)

        val (id2, envelope2) = publication(2, 12)
        val second = dao.applyStudioWorkoutTransaction(StudioWorkoutContract.parse(id2, envelope2, owner, "uid-a", 2000))
        assertEquals(applied.link.localRoutineId, second.link.localRoutineId)
        val links = dao.getStudioWorkoutLinks(owner)
        assertEquals(2, links.size)
        assertFalse(links.first { it.sourceRevision == 1L }.isLatest)
        assertTrue(links.first { it.sourceRevision == 2L }.isLatest)
        val exercises = dao.getTemplateExercisesSync(second.link.localRoutineId)
        val prescriptions = dao.getMetricPrescriptions(dao.getTemplateSetsSync(exercises.first().id).first().globalId)
        assertEquals(12.0, prescriptions.first { it.metricKey == "repetitions" }.targetValue)
        assertTrue(dao.getAllCommands().isEmpty())
    }

    @Test fun `locally edited import is preserved and newer Studio revision creates visible conflict copy`() = runBlocking {
        val dao = db.strengthDao()
        val (id1, envelope1) = publication(1)
        val first = dao.applyStudioWorkoutTransaction(StudioWorkoutContract.parse(id1, envelope1, owner, "uid-a", 1000))
        val routine = requireNotNull(dao.getTemplateById(first.link.localRoutineId))
        dao.insertTemplate(routine.copy(name = "My local edit", revision = 2, syncStatus = "PENDING_UPLOAD"))
        val (id2, envelope2) = publication(2, 12)
        val second = dao.applyStudioWorkoutTransaction(StudioWorkoutContract.parse(id2, envelope2, owner, "uid-a", 2000))
        assertTrue(second.conflict)
        assertNotEquals(first.link.localRoutineId, second.link.localRoutineId)
        assertEquals("My local edit", dao.getTemplateById(first.link.localRoutineId)?.name)
    }
}
