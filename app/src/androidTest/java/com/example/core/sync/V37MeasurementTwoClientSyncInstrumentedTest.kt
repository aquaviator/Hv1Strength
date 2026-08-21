package com.example.core.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.*
import com.example.measurement.*
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class V37MeasurementTwoClientSyncInstrumentedTest {
    private val projectId = "demo-hv1-planner-sync"
    private val host = "10.0.2.2"
    private val owner = "human_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    private val uid = "planner-client-owner"
    private lateinit var context: Context
    private lateinit var appA: FirebaseApp
    private lateinit var appB: FirebaseApp
    private lateinit var dbA: StrengthDatabase
    private lateinit var dbB: StrengthDatabase

    @Before fun setUp() = runBlocking {
        check(projectId.startsWith("demo-") && projectId != "hv1-platform")
        context = ApplicationProvider.getApplicationContext()
        appA = app("v37-measurement-a-${System.nanoTime()}")
        appB = app("v37-measurement-b-${System.nanoTime()}")
        signIn(appA); signIn(appB)
        dbA = Room.inMemoryDatabaseBuilder(context, StrengthDatabase::class.java).allowMainThreadQueries().build()
        dbB = Room.inMemoryDatabaseBuilder(context, StrengthDatabase::class.java).allowMainThreadQueries().build()
        seedProfile(dbA); seedProfile(dbB)
    }

    @After fun tearDown() {
        if (::dbA.isInitialized) dbA.close(); if (::dbB.isInitialized) dbB.close()
        if (::appA.isInitialized) appA.delete(); if (::appB.isInitialized) appB.delete()
    }

    private fun app(name: String) = FirebaseApp.initializeApp(context,
        FirebaseOptions.Builder().setApplicationId("1:123456789:android:$name")
            .setApiKey("local-emulator-only-key").setProjectId(projectId).build(), name)!!

    private suspend fun signIn(app: FirebaseApp) {
        FirebaseAuth.getInstance(app).apply { useEmulator(host, 9099) }
            .signInWithEmailAndPassword("owner@example.invalid", "local-only-password").await()
        FirebaseFirestore.getInstance(app).apply {
            useEmulator(host, 8080)
            firestoreSettings = FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build()
        }
    }

    private suspend fun seedProfile(db: StrengthDatabase) = db.strengthDao().insertUserProfile(
        UserProfile(id=uid, firebaseUid=uid, globalId="profile-$uid", humanUserId=owner,
            authProvider="google", isOfflineUser=false, syncStatus="SYNCED"))

    private fun engine(app: FirebaseApp, repo: StrengthRepository, device: String, online: () -> Boolean) =
        SyncEngineImpl(context, repo, FirebaseFirestore.getInstance(app), online, device) {
            SyncIdentityResolution.Ready(AuthenticatedSyncIdentity(uid, owner))
        }

    private suspend fun seedCompletedParent(db: StrengthDatabase, device: String) {
        db.strengthDao().insertSession(WorkoutSession(id=1, templateName="Synthetic", startTime=1, endTime=2,
            userId=uid, globalId="v37-session", humanUserId=owner, syncStatus="PENDING_UPLOAD", originDeviceId=device))
        db.strengthDao().insertLoggedSet(LoggedSet(id=1, sessionId=1, sessionGlobalId="v37-session",
            exerciseId="rowing_machine_cardio", setNumber=1, reps=0, weight=0f, isCompleted=true,
            actualDuration=600, actualDistance=2000f, globalId="v37-set", humanUserId=owner,
            syncStatus="PENDING_UPLOAD", originDeviceId=device))
        db.strengthDao().enqueueCommand(command("WORKOUT_SESSION", "v37-session", device))
        db.strengthDao().enqueueCommand(command("LOGGED_SET", "v37-set", device))
    }

    private fun command(type: String, id: String, device: String) = CommandQueueEntity(
        commandId="cmd-${type.lowercase()}-$id-$device", humanUserId=owner, commandType="V37Acceptance",
        entityType=type, entityGlobalId=id, payloadJson="{}", originDeviceId=device)

    private suspend fun saveGraph(db: StrengthDatabase, device: String, power: Double, timestamp: Long) {
        val profile = ExerciseMetricProfile("governed:rowing_machine_cardio:v1",
            primary=setOf("duration", "distance"), optional=setOf("power"))
        val values = listOf(ObservationInput(MetricInput("power", numericValue=power, unitKey="watt"),
            source=MetricSource.MACHINE, manufacturer="Synthetic", deviceModel="Rower",
            deviceIdentifier="hashed-device", protocol="fixture-v1",
            segments=listOf(SegmentInput(0, 60_000, power, "watt", "work")),
            samples=listOf(SampleInput(5_000, power, "watt"))))
        val observation = MeasurementRepository(db.strengthDao()).saveObservations(
            "v37-set", profile, values, timestamp, owner, device).single()
        db.strengthDao().enqueueCommand(command("MEASUREMENT_RECORD", observation.globalId, device)
            .copy(commandId="cmd-measurement-${observation.revision}-$device"))
    }

    private suspend fun assertEquivalent() {
        val a=dbA.strengthDao().getMetricObservation("v37-set:power")!!
        val b=dbB.strengthDao().getMetricObservation("v37-set:power")!!
        assertEquals(a.copy(syncStatus="SYNCED",lastSyncedAt=null), b.copy(syncStatus="SYNCED",lastSyncedAt=null))
        assertEquals(dbA.strengthDao().getMetricSegments(a.globalId), dbB.strengthDao().getMetricSegments(b.globalId))
        assertEquals(dbA.strengthDao().getMetricSamples(a.globalId), dbB.strengthDao().getMetricSamples(b.globalId))
    }

    @Test fun twoIndependentRoomClientsConvergeWithoutDuplicatesOrLegacyRegression() = runBlocking {
        val repoA=StrengthRepository(dbA.strengthDao(), deviceIdOverride="device-a")
        val repoB=StrengthRepository(dbB.strengthDao(), deviceIdOverride="device-b")
        var onlineA=true
        val syncA=engine(appA,repoA,"device-a",{onlineA}); val syncB=engine(appB,repoB,"device-b",{true})
        seedCompletedParent(dbA,"device-a"); saveGraph(dbA,"device-a",250.0,1000)
        assertTrue(syncA.synchronizeAll().isSuccess); assertTrue(syncB.synchronizeAll().isSuccess)
        assertEquivalent()

        saveGraph(dbB,"device-b",275.0,2000)
        assertTrue(syncB.synchronizeAll().isSuccess); assertTrue(syncA.synchronizeAll().isSuccess)
        assertEquivalent(); assertEquals(275.0, dbA.strengthDao().getMetricObservation("v37-set:power")!!.numericValue!!,0.0)

        onlineA=false; saveGraph(dbA,"device-a",300.0,3000)
        assertTrue(syncA.synchronizeAll().isFailure)
        onlineA=true; assertTrue(syncA.synchronizeAll().isSuccess); assertTrue(syncB.synchronizeAll().isSuccess)
        assertEquivalent()

        repeat(2) { assertTrue(syncA.synchronizeAll().isSuccess); assertTrue(syncB.synchronizeAll().isSuccess) }
        assertEquals(1, dbA.strengthDao().getMetricObservations("v37-set").size)
        assertEquals(1, dbB.strengthDao().getMetricSegments("v37-set:power").size)
        assertEquals(1, FirebaseFirestore.getInstance(appA).collection("users").document(owner)
            .collection("measurementRecords").get().await().size())

        // Simulate a legacy client updating only the parent loggedSet. New
        // measurement records remain intact and completion is monotonic.
        FirebaseFirestore.getInstance(appA).collection("users").document(owner).collection("loggedSets")
            .document("v37-set").update(mapOf("isCompleted" to false, "revision" to 99L, "updatedAt" to 9999L)).await()
        assertTrue(syncA.synchronizeAll().isSuccess)
        assertTrue(dbA.strengthDao().getLoggedSetByGlobalId("v37-set")!!.isCompleted)
        assertEquals(1, dbA.strengthDao().getMetricObservations("v37-set").size)

        val foreign = MetricObservationEntity("foreign:energy","v37-set","energy",numericValue=1.0,
            canonicalUnit="kilocalorie",humanUserId="human_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")
        dbA.strengthDao().replaceMetricObservationGraph(foreign,emptyList(),emptyList())
        dbA.strengthDao().enqueueCommand(command("MEASUREMENT_RECORD",foreign.globalId,"device-a"))
        syncA.synchronizeAll()
        assertTrue(dbA.strengthDao().getPendingMetricObservations().any { it.globalId==foreign.globalId })
    }
}
