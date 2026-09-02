package com.example.core.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.*
import com.example.planner.TrainingPlanner
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlannerTwoClientSyncInstrumentedTest {
    private val projectId = "demo-hv1-planner-sync"
    private val emulatorHost = "127.0.0.1"
    private val humanA = "human_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    private val humanB = "human_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    private val uidA = "planner-client-owner"
    private val uidB = "planner-other-owner"
    private lateinit var context: Context
    private lateinit var appA: FirebaseApp
    private lateinit var appB: FirebaseApp
    private lateinit var dbA: StrengthDatabase
    private lateinit var dbB: StrengthDatabase

    @Before fun setUp() = runBlocking {
        check(projectId != "hv1-platform" && projectId.startsWith("demo-"))
        check(emulatorHost == "127.0.0.1") { "Production endpoint guard failed" }
        context = ApplicationProvider.getApplicationContext()
        appA = app("planner-client-a")
        appB = app("planner-client-b")
        signIn(appA, "owner@example.invalid")
        signIn(appB, "owner@example.invalid")
        dbA = Room.inMemoryDatabaseBuilder(context, StrengthDatabase::class.java).allowMainThreadQueries().build()
        dbB = Room.inMemoryDatabaseBuilder(context, StrengthDatabase::class.java).allowMainThreadQueries().build()
        seedProfile(dbA, uidA, humanA)
        seedProfile(dbB, uidA, humanA)
    }

    @After fun tearDown() {
        if (::dbA.isInitialized) dbA.close()
        if (::dbB.isInitialized) dbB.close()
        if (::appA.isInitialized) appA.delete()
        if (::appB.isInitialized) appB.delete()
    }

    private fun app(name: String): FirebaseApp {
        val options = FirebaseOptions.Builder().setApplicationId("1:123456789:android:$name")
            .setApiKey("local-emulator-only-key").setProjectId(projectId).build()
        return FirebaseApp.initializeApp(context, options, name)!!
    }

    private suspend fun signIn(app: FirebaseApp, email: String) {
        FirebaseAuth.getInstance(app).apply { useEmulator(emulatorHost, 9099) }
            .signInWithEmailAndPassword(email, "local-only-password").await()
        FirebaseFirestore.getInstance(app).apply {
            useEmulator(emulatorHost, 8080)
            firestoreSettings = FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build()
        }
    }

    private suspend fun seedProfile(db: StrengthDatabase, uid: String, human: String) {
        db.strengthDao().insertUserProfile(UserProfile(id = uid, firebaseUid = uid, globalId = "profile-$uid",
            humanUserId = human, authProvider = "google", isOfflineUser = false, syncStatus = "SYNCED"))
    }

    private fun engine(app: FirebaseApp, repo: StrengthRepository, device: String, online: () -> Boolean,
        uid: String = uidA, human: String = humanA) = SyncEngineImpl(
        context = context,
        repository = repo,
        firestoreOverride = FirebaseFirestore.getInstance(app),
        connectivityOverride = online,
        deviceIdOverride = device,
        identityResolverOverride = { SyncIdentityResolution.Ready(AuthenticatedSyncIdentity(uid, human)) }
    )

    private data class ComparableOccurrence(
        val id: String, val seriesId: String, val human: String, val templateGlobalId: String,
        val scheduled: Long, val original: Long, val minute: Int?, val status: String,
        val completedAt: Long?, val linkedSessionId: Int?, val revision: Long, val updatedAt: Long,
        val originDeviceId: String, val deletedAt: Long?
    )

    private fun PlannedWorkout.comparable() = ComparableOccurrence(id, seriesId, humanUserId, templateGlobalId,
        scheduledEpochDay, originalEpochDay, preferredMinuteOfDay, status, completedAt, linkedSessionId, revision,
        updatedAt, originDeviceId, deletedAt)

    private suspend fun assertEquivalent(a: StrengthDatabase, b: StrengthDatabase, user: String = uidA) {
        val plansA = a.strengthDao().getAllTrainingPlansForBackup(user).map { listOf(it.id, it.humanUserId, it.templateGlobalId, it.firstEpochDay, it.preferredMinuteOfDay, it.weekdaysMask, it.recurrenceEndEpochDay, it.revision, it.updatedAt, it.originDeviceId, it.deletedAt) }
        val plansB = b.strengthDao().getAllTrainingPlansForBackup(user).map { listOf(it.id, it.humanUserId, it.templateGlobalId, it.firstEpochDay, it.preferredMinuteOfDay, it.weekdaysMask, it.recurrenceEndEpochDay, it.revision, it.updatedAt, it.originDeviceId, it.deletedAt) }
        assertEquals(plansA, plansB)
        assertEquals(a.strengthDao().getAllPlannedWorkoutsForBackup(user).map { it.comparable() },
            b.strengthDao().getAllPlannedWorkoutsForBackup(user).map { it.comparable() })
    }

    @Test fun twoClientsConvergeThroughActualPlannerSyncPath() = runBlocking {
        val repoA = StrengthRepository(dbA.strengthDao(), deviceIdOverride = "device-a")
        val repoB = StrengthRepository(dbB.strengthDao(), deviceIdOverride = "device-b")
        var onlineA = true
        val syncA = engine(appA, repoA, "device-a", { onlineA })
        val syncB = engine(appB, repoB, "device-b", { true })
        val first = LocalDate.of(2026, 8, 17)
        val plan = TrainingPlan("sync-series", uidA, humanA, 7, "routine-global", "Routine",
            first.toEpochDay(), 13 * 60 + 37,
            TrainingPlanner.weekdayMask(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)), first.plusWeeks(2).toEpochDay())
        repoA.createTrainingPlan(plan, TrainingPlanner.generate(plan))
        assertTrue(repoA.getPendingCommands().isNotEmpty())
        assertTrue(syncA.synchronizeAll().isSuccess)
        assertTrue(repoA.getAllCommands().all { it.status == "SUCCEEDED" })
        assertEquals(1, FirebaseFirestore.getInstance(appA).collection("users").document(humanA)
            .collection("trainingPlans").get().await().size())
        assertEquals(TrainingPlanner.generate(plan).size, FirebaseFirestore.getInstance(appB).collection("users").document(humanA)
            .collection("plannedWorkouts").get().await().size())
        assertTrue(syncB.synchronizeAll().isSuccess)
        assertEquivalent(dbA, dbB)
        val originalCount = dbB.strengthDao().getAllPlannedWorkoutsForBackup(uidA).size
        syncB.synchronizeAll(); assertEquals(originalCount, dbB.strengthDao().getAllPlannedWorkoutsForBackup(uidA).size)

        val edited = repoB.getAllPlannedWorkoutsForBackup(uidA).first().copy(scheduledEpochDay = first.plusDays(1).toEpochDay(), detachedFromSeries = true)
        repoB.updatePlannedWorkout(edited, uidA); syncB.synchronizeAll(); syncA.synchronizeAll()
        assertEquivalent(dbA, dbB)

        val currentPlan = requireNotNull(repoA.getTrainingPlan(plan.id))
        val affected = repoA.getAllPlannedWorkoutsForBackup(uidA).filter { it.seriesId == plan.id && it.scheduledEpochDay >= first.plusDays(3).toEpochDay() }
        val regeneratedPlan = currentPlan.copy(firstEpochDay = first.plusDays(3).toEpochDay(),
            weekdaysMask = TrainingPlanner.weekdayMask(setOf(DayOfWeek.THURSDAY)),
            recurrenceEndEpochDay = first.plusWeeks(3).toEpochDay())
        val protected = affected.filter { it.status != "PLANNED" || it.detachedFromSeries }.mapTo(hashSetOf()) { it.id }
        val replacements = TrainingPlanner.generate(regeneratedPlan).filter { it.id !in protected }
        repoA.replaceFutureTrainingPlan(regeneratedPlan, first.plusDays(3).toEpochDay(), affected, replacements)
        syncA.synchronizeAll(); syncB.synchronizeAll()
        assertTrue(repoA.getAllPlannedWorkoutsForBackup(uidA).any { it.deletedAt != null })
        assertEquivalent(dbA, dbB)

        onlineA = false
        val offlineEdit = repoA.getAllPlannedWorkoutsForBackup(uidA).last().copy(preferredMinuteOfDay = 777)
        repoA.updatePlannedWorkout(offlineEdit, uidA)
        assertTrue(syncA.synchronizeAll().isFailure)
        assertTrue(repoA.getPendingCommands().any { it.entityGlobalId == offlineEdit.globalId })
        onlineA = true; assertTrue(syncA.synchronizeAll().isSuccess); syncB.synchronizeAll()
        assertEquivalent(dbA, dbB)

        val completionTarget = repoA.getAllPlannedWorkoutsForBackup(uidA).first { it.deletedAt == null }
        val stale = requireNotNull(repoB.getPlannedWorkout(completionTarget.id))
        repoA.completePlannedWorkout(completionTarget.id, uidA, 123456789L, 42); syncA.synchronizeAll()
        repoB.updatePlannedWorkout(stale.copy(scheduledEpochDay = stale.scheduledEpochDay + 1), uidA)
        syncB.synchronizeAll(); syncA.synchronizeAll(); syncB.synchronizeAll()
        assertEquals("COMPLETED", repoA.getPlannedWorkout(completionTarget.id)!!.status)
        assertEquals(42, repoB.getPlannedWorkout(completionTarget.id)!!.linkedSessionId)
        assertEquivalent(dbA, dbB)

        val deleteTarget = repoA.getAllPlannedWorkoutsForBackup(uidA).first { it.status == "PLANNED" && it.deletedAt == null }
        val staleDeleteEdit = requireNotNull(repoB.getPlannedWorkout(deleteTarget.id))
        repoA.deletePlannedWorkout(deleteTarget.id, uidA); syncA.synchronizeAll()
        repoB.updatePlannedWorkout(staleDeleteEdit.copy(preferredMinuteOfDay = 600), uidA); syncB.synchronizeAll()
        syncA.synchronizeAll(); syncB.synchronizeAll()
        assertNotNull(repoA.getPlannedWorkout(deleteTarget.id)!!.deletedAt)
        assertEquivalent(dbA, dbB)

        val processedBefore = FirebaseFirestore.getInstance(appA).collection("users").document(humanA)
            .collection("processedCommands").get().await().size()
        syncA.synchronizeAll()
        val processedAfter = FirebaseFirestore.getInstance(appA).collection("users").document(humanA)
            .collection("processedCommands").get().await().size()
        assertEquals(processedBefore, processedAfter)

        val oneTimeDate = LocalDate.of(2026, 9, 12)
        val oneTimePlan = TrainingPlan("one-time-series", uidA, humanA, 7, "routine-global", "Routine",
            oneTimeDate.toEpochDay(), 9 * 60 + 15, 0, null)
        val oneTimeOccurrences = TrainingPlanner.generate(oneTimePlan)
        assertEquals(1, oneTimeOccurrences.size)
        repoA.createTrainingPlan(oneTimePlan, oneTimeOccurrences)
        assertTrue(syncA.synchronizeAll().isSuccess)
        assertTrue(syncB.synchronizeAll().isSuccess)
        assertNotNull(repoB.getTrainingPlan(oneTimePlan.id))
        assertNotNull(repoB.getPlannedWorkout(oneTimeOccurrences.single().id))
        assertEquivalent(dbA, dbB)

        val conflictTargetA = repoA.getAllPlannedWorkoutsForBackup(uidA).first { it.status == "PLANNED" && it.deletedAt == null }
        val conflictTargetB = requireNotNull(repoB.getPlannedWorkout(conflictTargetA.id))
        repoA.updatePlannedWorkout(conflictTargetA.copy(preferredMinuteOfDay = 601), uidA)
        repoB.updatePlannedWorkout(conflictTargetB.copy(preferredMinuteOfDay = 602), uidA)
        syncA.synchronizeAll()
        syncB.synchronizeAll()
        assertEquals("CONFLICT", repoB.getPlannedWorkout(conflictTargetB.id)!!.syncStatus)
        assertTrue(repoB.getAllCommands().any { it.entityGlobalId == conflictTargetB.id && it.status == "FAILED" })

        val otherApp = app("planner-other-client")
        try {
            signIn(otherApp, "other@example.invalid")
            val otherDb = Room.inMemoryDatabaseBuilder(context, StrengthDatabase::class.java).allowMainThreadQueries().build()
            try {
                seedProfile(otherDb, uidB, humanB)
                val otherRepo = StrengthRepository(otherDb.strengthDao(), deviceIdOverride = "device-other")
                val otherSync = engine(otherApp, otherRepo, "device-other", { true }, uidB, humanB)
                assertTrue(otherSync.synchronizeAll().isSuccess)
                assertTrue(otherDb.strengthDao().getAllTrainingPlansForBackup(uidB).isEmpty())
                val forged = plan.copy(id = "forged", userId = uidB)
                otherRepo.createTrainingPlan(forged, TrainingPlanner.generate(forged))
                otherSync.synchronizeAll()
                assertTrue(otherRepo.getAllCommands().any { it.status == "FAILED" || it.status == "POISONED" })
            } finally { otherDb.close() }
        } finally { otherApp.delete() }
    }
}
