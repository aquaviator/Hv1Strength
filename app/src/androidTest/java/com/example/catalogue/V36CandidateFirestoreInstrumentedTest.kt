package com.example.catalogue

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.CommandQueueEntity
import com.example.data.Exercise
import com.example.data.StrengthDatabase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class V36CandidateFirestoreInstrumentedTest {
    private lateinit var context: Context
    private lateinit var app: FirebaseApp
    private lateinit var database: StrengthDatabase

    @Before fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        val project = "demo-v36-device"
        check(project.startsWith("demo-") && project != "hv1-platform")
        app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
            .setApplicationId("1:123456789:android:v36-candidate-acceptance")
            .setApiKey("local-emulator-only-key")
            .setProjectId(project).build(), "v36-candidate-acceptance")!!
        FirebaseFirestore.getInstance(app).apply {
            useEmulator("10.0.2.2", 8080)
            firestoreSettings = FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build()
        }
        database = Room.inMemoryDatabaseBuilder(context, StrengthDatabase::class.java).allowMainThreadQueries().build()
        val bundled = PackagedExerciseLibrarySource.load(context).snapshot
        database.strengthDao().insertExercises(bundled.exercises.map { it.toRoom(1) })
        database.strengthDao().insertExercise(Exercise("v36_local_custom", "V36 Local Custom", "Other", true, "v36-local-custom"))
        database.strengthDao().enqueueCommand(CommandQueueEntity(commandId = "v36-user-command", humanUserId = "human_v36acceptance0000000000000000",
            commandType = "BodyWeightUpdated", entityType = "BODY_WEIGHT", entityGlobalId = "weight-v36", payloadJson = "{}"))
    }

    @After fun tearDown() { if (::database.isInitialized) database.close(); if (::app.isInitialized) app.delete() }

    @Test fun approvedCandidateAppliesTransactionallyAndIsIdempotent() = runBlocking {
        val bundled = PackagedExerciseLibrarySource.load(context).snapshot
        assertEquals(264, bundled.exercises.size)
        val coordinator = GovernedCatalogueCoordinator(database, FirebaseGovernedCatalogueGateway(FirebaseFirestore.getInstance(app)))
        val firstStarted = System.currentTimeMillis()
        val first = coordinator.synchronize(bundled)
        val firstElapsed = System.currentTimeMillis() - firstStarted
        assertTrue(first.toString(), first.accepted)
        assertEquals(956, database.strengthDao().getAllExercisesSync().size)
        assertNotNull(database.strengthDao().getExerciseById("bench_press"))
        assertNotNull(database.strengthDao().getExerciseById("bodyweight_air_squat"))
        assertNotNull(database.strengthDao().getExerciseById("v36_local_custom"))
        assertEquals(listOf("v36-user-command"), database.strengthDao().getAllCommands().map { it.commandId })
        assertEquals("strength-2026.08.36-v1", database.strengthDao().getCatalogueReleaseState()?.acceptedReleaseId)
        val secondStarted = System.currentTimeMillis()
        val second = coordinator.synchronize(bundled)
        val secondElapsed = System.currentTimeMillis() - secondStarted
        assertTrue(second.toString(), second.accepted)
        assertEquals(956, database.strengthDao().getAllExercisesSync().size)
        assertEquals(listOf("v36-user-command"), database.strengthDao().getAllCommands().map { it.commandId })
        println("V36_PERFORMANCE first_fetch_apply_ms=$firstElapsed idempotent_fetch_apply_ms=$secondElapsed governed=955 local_custom=1 pending_commands=1")
    }
}
