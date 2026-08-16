package com.example.catalogue

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.CommandQueueEntity
import com.example.data.Exercise
import com.example.data.LoggedSet
import com.example.data.PlannedWorkout
import com.example.data.StrengthDatabase
import com.example.data.TrainingPlan
import com.example.data.WorkoutSession
import com.example.data.WorkoutTemplate
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GovernedCatalogueJourneyTest {
    private lateinit var context: Context
    private lateinit var database: StrengthDatabase
    private val human = "human_cataloguejourney0000000000000000"
    private val user = "catalogue-user"

    @Before fun setup() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            database = Room.inMemoryDatabaseBuilder(context, StrengthDatabase::class.java)
                .allowMainThreadQueries().build()
            val dao = database.strengthDao()
            dao.insertExercises(listOf(
            item("bench_press", "Bench Press").toRoom(10),
            item("squat", "Barbell Squat").toRoom(10),
            Exercise("my_custom", "My Custom", "Other", true, "custom-global", humanUserId = human)
        ))
        val templateId = dao.insertTemplate(WorkoutTemplate(name = "Preserved Routine", exerciseIdsJson = "[\"bench_press\",\"squat\"]", userId = user, globalId = "routine-1", humanUserId = human)).toInt()
        val sessionId = dao.insertSession(WorkoutSession(templateId = templateId, templateName = "Preserved Routine", startTime = 1, endTime = 2, userId = user, globalId = "session-1", humanUserId = human)).toInt()
        dao.insertLoggedSet(LoggedSet(sessionId = sessionId, exerciseId = "squat", setNumber = 1, reps = 5, weight = 100f, isCompleted = true, globalId = "set-1", humanUserId = human))
        dao.upsertTrainingPlan(TrainingPlan("plan-1", user, human, templateId, "routine-1", "Preserved Routine", 1))
        dao.upsertPlannedWorkout(PlannedWorkout("occurrence-1", "plan-1", user, human, templateId, "routine-1", "Preserved Routine", 2, 2))
        dao.enqueueCommand(CommandQueueEntity(commandId = "user-command", humanUserId = human, commandType = "BodyWeightUpdated", entityType = "BODY_WEIGHT", entityGlobalId = "weight-1", payloadJson = "{}"))
            context.getSharedPreferences("strength_favourites", Context.MODE_PRIVATE).edit()
                .putStringSet(favouritePreferenceKey(user), setOf("bench_press")).commit()
        }
    }

    @After fun close() = database.close()

    @Test fun equivalentNewEditorialAndDeprecationReleasesPreserveUserDataAndStableIds() = runBlocking {
        val baseline = listOf(item("bench_press", "Bench Press"), item("squat", "Barbell Squat"))
        val equivalent = coordinator(payload("equivalent-264", "2026.08.35-demo-1", baseline)).synchronize(bundled())
        assertTrue(equivalent.accepted)
        assertEquals(3, database.strengthDao().getAllExercisesSync().size)

        val repeat = coordinator(payload("equivalent-264", "2026.08.35-demo-1", baseline)).synchronize(bundled())
        assertTrue(repeat.accepted)
        assertEquals(3, database.strengthDao().getAllExercisesSync().size)

        val added = item("v35_demo_sled_march", "Demo Sled March", category = "Cardio",
            capabilities = setOf(MeasurementCapability.DURATION, MeasurementCapability.DISTANCE))
        assertTrue(coordinator(payload("added-265", "2026.08.35-demo-2", baseline + added)).synchronize(bundled()).accepted)
        assertEquals("Demo Sled March", database.strengthDao().getExerciseById(added.id)?.name)
        assertTrue(ExerciseCatalogueRuntime.snapshot!!.exercises.any { it.id == added.id && it.category == "Cardio" })

        val renamed = baseline.map { if (it.id == "bench_press") it.copy(name = "Bench Press — Updated") else it } + added
        assertTrue(coordinator(payload("editorial-265", "2026.08.35-demo-3", renamed)).synchronize(bundled()).accepted)
        assertEquals("Bench Press — Updated", database.strengthDao().getExerciseById("bench_press")?.name)
        assertEquals(setOf("bench_press"), context.getSharedPreferences("strength_favourites", Context.MODE_PRIVATE)
            .getStringSet(favouritePreferenceKey(user), emptySet()))

        val deprecated = renamed.map { if (it.id == "squat") it.copy(active = false, replacementId = "bench_press") else it }
        assertTrue(coordinator(payload("deprecated-265", "2026.08.35-demo-4", deprecated)).synchronize(bundled()).accepted)
        assertFalse(ExerciseCatalogueRuntime.snapshot!!.exercises.single { it.id == "squat" }.active)
        assertNotNull(database.strengthDao().getExerciseById("squat"))

        val dao = database.strengthDao()
        assertNotNull(dao.getExerciseById("my_custom"))
        assertNotNull(dao.getTemplateById(1))
        assertNotNull(dao.getSessionById(1))
        assertEquals("squat", dao.getSetsForSessionSync(1).single().exerciseId)
        assertNotNull(dao.getTrainingPlan("plan-1"))
        assertNotNull(dao.getPlannedWorkout("occurrence-1"))
        assertEquals(listOf("user-command"), dao.getAllCommands().map { it.commandId })
        assertEquals("deprecated-265", dao.getCatalogueReleaseState()?.acceptedReleaseId)
    }

    @Test fun everyInvalidOrUnavailableReleaseRetainsTheAcceptedCatalogueWithoutRetryStorm() = runBlocking {
        val baseline = listOf(item("bench_press", "Bench Press"), item("squat", "Barbell Squat"))
        val valid = payload("accepted", "2026.08.35-demo-accepted", baseline)
        assertTrue(coordinator(valid).synchronize(bundled()).accepted)
        val baselineRows = database.strengthDao().getAllExercisesSync()

        val wrongChecksum = valid.copy(manifest = valid.manifest.copy(releaseId = "bad-checksum", contentSha256 = "0".repeat(64)))
        val wrongCount = valid.copy(manifest = valid.manifest.copy(releaseId = "bad-count", exerciseCount = 99))
        val duplicateDocs = listOf(raw(baseline[0]), raw(baseline[0]))
        val duplicate = payloadFrom("duplicate", listOf(baseline[0], baseline[0].copy(name = "Duplicate")), duplicateDocs)
        val missing = payload("missing", "bad-missing", listOf(baseline[0]))
        val broken = payload("broken", "bad-broken", baseline.map { if (it.id == "bench_press") it.copy(relatedIds = listOf("absent")) else it })
        val self = payload("self", "bad-self", baseline.map { if (it.id == "bench_press") it.copy(relatedIds = listOf("bench_press")) else it })
        val invalidCapabilitiesDocs = valid.rawDocuments.map { if (it["exerciseId"] == "bench_press") it + ("trackingCapabilities" to listOf("assisted_load")) else it }
        val invalidCapabilities = valid.copy(manifest = valid.manifest.copy(releaseId = "bad-capabilities", contentSha256 = GovernedCatalogueValidator.checksum(invalidCapabilitiesDocs)), rawDocuments = invalidCapabilitiesDocs)
        val unsupportedSchemaDocs = valid.rawDocuments.map { it + ("schemaVersion" to 2L) }
        val unsupportedSchema = valid.copy(manifest = valid.manifest.copy(releaseId = "bad-schema", schemaVersion = 2, contentSha256 = GovernedCatalogueValidator.checksum(unsupportedSchemaDocs)), rawDocuments = unsupportedSchemaDocs)
        val tooNew = valid.copy(manifest = valid.manifest.copy(releaseId = "too-new", minimumStrengthVersionCode = 36))
        val draft = valid.copy(manifest = valid.manifest.copy(releaseId = "draft", status = "draft"))

        val cases = listOf(
            wrongChecksum to CatalogueSyncStatus.CHECKSUM_MISMATCH,
            wrongCount to CatalogueSyncStatus.COUNT_MISMATCH,
            duplicate to CatalogueSyncStatus.INVALID_SCHEMA,
            missing to CatalogueSyncStatus.MISSING_REQUIRED_ID,
            broken to CatalogueSyncStatus.INVALID_REFERENCE,
            self to CatalogueSyncStatus.INVALID_REFERENCE,
            invalidCapabilities to CatalogueSyncStatus.INVALID_CAPABILITY,
            unsupportedSchema to CatalogueSyncStatus.INVALID_SCHEMA,
            tooNew to CatalogueSyncStatus.VERSION_INCOMPATIBLE,
            draft to CatalogueSyncStatus.INVALID_MANIFEST
        )
        cases.forEach { (candidate, expected) ->
            val gateway = CountingGateway(candidate)
            val result = GovernedCatalogueCoordinator(database, gateway, { 50 }).synchronize(bundled())
            assertFalse(result.accepted)
            assertEquals(expected, result.status)
            assertEquals(1, gateway.calls)
            assertEquals(baselineRows, database.strengthDao().getAllExercisesSync())
            assertEquals("accepted", database.strengthDao().getCatalogueReleaseState()?.acceptedReleaseId)
        }

        for ((error, expected) in listOf(
            IOException("interrupted download") to CatalogueSyncStatus.UNAVAILABLE,
            SecurityException("permission denied") to CatalogueSyncStatus.PERMISSION_DENIED,
            CatalogueGatewayException(CatalogueSyncStatus.INVALID_MANIFEST) to CatalogueSyncStatus.INVALID_MANIFEST
        )) {
            val gateway = CountingGateway(error = error)
            val result = GovernedCatalogueCoordinator(database, gateway, { 60 }).synchronize(bundled())
            assertEquals(expected, result.status)
            assertEquals(1, gateway.calls)
            assertEquals(baselineRows, database.strengthDao().getAllExercisesSync())
        }
    }

    private fun bundled() = CatalogueSnapshot(
        CatalogueMetadata(1, "2026.08.32", "production", "", "human-v1-strength", 2, "bundled"),
        listOf(item("bench_press", "Bench Press"), item("squat", "Barbell Squat")),
        CatalogueValidation(true, emptyList())
    )

    private fun coordinator(payload: GovernedCataloguePayload) = GovernedCatalogueCoordinator(database, CountingGateway(payload), { 20 })

    private fun payload(releaseId: String, version: String, exercises: List<CatalogueExercise>): GovernedCataloguePayload {
        val documents = exercises.map(::raw)
        return payloadFrom(releaseId, exercises, documents, version)
    }

    private fun payloadFrom(releaseId: String, exercises: List<CatalogueExercise>, documents: List<Map<String, Any?>>, version: String = releaseId) = GovernedCataloguePayload(
        GovernedReleaseManifest(releaseId, 1, version, exercises.size, GovernedCatalogueValidator.checksum(documents), "published", "production", 35, null),
        documents, exercises
    )

    private fun raw(item: CatalogueExercise): Map<String, Any?> = mapOf(
        "exerciseId" to item.id,
        "schemaVersion" to 1L,
        "displayName" to item.name,
        "trackingCapabilities" to item.capabilities.map { it.wireName }.sorted()
    )

    private fun item(
        id: String,
        name: String,
        category: String = "Strength",
        capabilities: Set<MeasurementCapability> = setOf(MeasurementCapability.REPETITIONS, MeasurementCapability.LOAD)
    ) = CatalogueExercise(id, name, emptyList(), category, emptyList(), emptyList(), listOf("barbell"), "strength", capabilities, "bilateral", false, true)

    private class CountingGateway(
        private val payload: GovernedCataloguePayload? = null,
        private val error: Throwable? = null
    ) : GovernedCatalogueGateway {
        var calls = 0
        override suspend fun fetch(): GovernedCataloguePayload {
            calls += 1
            error?.let { throw it }
            return requireNotNull(payload)
        }
    }
}
