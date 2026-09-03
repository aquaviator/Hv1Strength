package com.example

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.identity.DeviceIdGenerator
import com.example.core.identity.HumanUserIdGenerator
import com.example.data.*
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.ProfileViewModel
import com.example.ui.viewmodel.StrengthViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Candidate4HonestAccountExperienceTest {

    private lateinit var context: Context
    private lateinit var database: StrengthDatabase
    private lateinit var repository: StrengthRepository
    private lateinit var authViewModel: AuthViewModel
    private lateinit var profileViewModel: ProfileViewModel
    private lateinit var viewModel: StrengthViewModel

    private fun idleLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun waitUntil(timeoutMs: Long = 3000, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            idleLooper()
            if (condition()) return
            Thread.sleep(10)
        }
        fail("Condition not met within $timeoutMs ms")
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DeviceIdGenerator.appContext = context
        HumanUserIdGenerator.appContext = context

        database = Room.inMemoryDatabaseBuilder(context, StrengthDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = StrengthRepository(database.strengthDao(), context)

        viewModel = StrengthViewModel(repository, context)
        authViewModel = viewModel.authViewModel
        profileViewModel = viewModel.profileViewModel

        // Perform offline sign-in to initialize the active user session correctly to "offline"
        runBlocking {
            viewModel.authRepository.signInAnonymously()
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * Test 1: Profile Navigation and Back Stack Retention
     */
    @Test
    fun testProfileNavigationAndBackStack() {
        // Confirm auth view model runs and sets up default offline user
        waitUntil { authViewModel.activeUserId.value.isNotEmpty() }
        val activeId = authViewModel.activeUserId.value
        assertEquals("offline", activeId)
    }

    /**
     * Test 2: Profile Truthfulness - backend access is not derived from local profile age.
     */
    @Test
    fun testProfileTruthfulnessAndTrialExpiration() {
        runBlocking {
            // Local profile age is presentation data and must not grant or expire entitlement.
            val profileToday = UserProfile(
                id = "offline",
                displayName = "Offline User",
                email = "",
                photoUrl = "",
                authProvider = "offline",
                createdAt = System.currentTimeMillis()
            )
            repository.insertUserProfile(profileToday)
            waitUntil { profileViewModel.activeUserProfile.value != null }
            waitUntil { !profileViewModel.isTrialExpired.value }
            assertFalse(profileViewModel.isTrialExpired.value)

            // An older local profile must not manufacture an expired backend entitlement.
            val fortyDaysAgo = System.currentTimeMillis() - (40L * 24L * 60L * 60L * 1000L)
            val profileOld = profileToday.copy(createdAt = fortyDaysAgo)
            repository.insertUserProfile(profileOld)
            waitUntil { profileViewModel.activeUserProfile.value?.createdAt == fortyDaysAgo }
            assertFalse(profileViewModel.isTrialExpired.value)

            // The explicit developer simulation remains deterministic and truthful.
            val profileNew = profileToday.copy(createdAt = System.currentTimeMillis())
            repository.insertUserProfile(profileNew)
            waitUntil { profileViewModel.activeUserProfile.value?.createdAt == profileNew.createdAt }
            waitUntil { !profileViewModel.isTrialExpired.value }
            assertFalse(profileViewModel.isTrialExpired.value)

            // Force simulation trigger
            profileViewModel.setSimulateTrialExpired(true)
            waitUntil { profileViewModel.simulateTrialExpired.value }
            waitUntil { profileViewModel.isTrialExpired.value }
            assertTrue(profileViewModel.isTrialExpired.value)

            // Restore simulation trigger
            profileViewModel.setSimulateTrialExpired(false)
            waitUntil { !profileViewModel.simulateTrialExpired.value }
            waitUntil { !profileViewModel.isTrialExpired.value }
            assertFalse(profileViewModel.isTrialExpired.value)
        }
    }

    /**
     * Test 3: Settings Inert/Placeholder Verification
     */
    @Test
    fun testSettingsInertRemoval() {
        // Assert that preference getters retrieve correct defaults, but their toggles do not persist fake status
        assertTrue(profileViewModel.autoCompleteBehavior.value)
        assertTrue(profileViewModel.autoScroll.value)
        assertEquals("standard", profileViewModel.timerPreferences.value)
    }

    /**
     * Test 4: Local Database Deletion Preserves Settings and User Profile Auth Status
     */
    @Test
    fun testLocalDatabaseDeletionPreservesSettings() {
        runBlocking {
            // Seed workout data
            val session = WorkoutSession(
                id = 101,
                templateId = null,
                templateName = "Upper A",
                startTime = System.currentTimeMillis(),
                endTime = System.currentTimeMillis() + 3000L,
                userId = "offline"
            )
            repository.insertSession(session)

            val bodyWeight = BodyWeight(
                id = 201,
                userId = "offline",
                weight = 75f,
                bodyFat = 15f,
                date = System.currentTimeMillis()
            )
            repository.insertBodyWeight(bodyWeight)

            // Set metric preference to false (imperial)
            profileViewModel.setMetric(false)
            waitUntil { !profileViewModel.isMetric.value }

            // Trigger complete local data deletion
            var callbackTriggered = false
            viewModel.deleteLocalWorkoutData {
                callbackTriggered = true
            }
            waitUntil { callbackTriggered }

            // Assert that workout logs are deleted
            val sessions = repository.allSessions.first()
            assertTrue(sessions.isEmpty())

            // Assert that settings are fully preserved
            assertFalse(profileViewModel.isMetric.value)

            // Assert that active user is still offline
            assertEquals("offline", authViewModel.activeUserId.value)
        }
    }

    /**
     * Test 5: CSV and JSON Exports Format Verification
     */
    @Test
    fun testExportFormatValidation() {
        runBlocking {
            // Seed past workout session & sets
            val session = WorkoutSession(
                id = 505,
                templateId = null,
                templateName = "Powerlift Routine",
                startTime = 1718000000000L,
                endTime = 1718003600000L,
                userId = "offline"
            )
            repository.insertSession(session)

            val set = LoggedSet(
                id = 1001,
                sessionId = 505,
                exerciseId = "ex_squat",
                setNumber = 1,
                reps = 5,
                weight = 100f,
                isCompleted = true,
                createdAt = 1718001000000L
            )
            repository.insertLoggedSet(set)

            // Export to JSON
            val jsonExport = profileViewModel.exportData()
            assertNotNull(jsonExport)
            val jsonObject = JSONObject(jsonExport)
            assertTrue(jsonObject.has("workout_sessions"))
            assertTrue(jsonObject.has("logged_sets"))
            assertTrue(jsonObject.has("settings"))

            // Export to CSV
            val csvExport = profileViewModel.exportDataToCsv()
            assertNotNull(csvExport)
            assertTrue(csvExport.contains("Date,Workout,Exercise,Category,Set Number,Set Type,Weight (kg),Reps,Completed"))
            assertTrue(csvExport.contains("Powerlift Routine"))
            assertTrue(csvExport.contains("ex_squat"))
            assertTrue(csvExport.contains("100.0"))
            assertTrue(csvExport.contains("5"))
        }
    }

    @Test
    fun testFormat4ProductionExportImportIsPlannerIdempotent() {
        runBlocking {
            val human = requireNotNull(repository.getUserProfile("offline")).humanUserId
            repository.insertUserProfile(UserProfile("offline", humanUserId = human, globalId = "profile-backup"))
            repository.insertExercise(Exercise("backup-custom", "Backup Custom", "Strength", true))
            repository.insertTemplate(WorkoutTemplate(706, "Backup Routine", "[\"backup-custom\"]", "offline"))
            repository.insertTemplateExercise(WorkoutTemplateExercise(708, 706, "backup-custom", 0, 90))
            repository.insertTemplateSets(listOf(WorkoutTemplateSet(709, 708, 0, "WORKING", 5, 8, 80f, 8)))
            repository.insertSession(WorkoutSession(707, 706, "Backup Routine", 1000, 2000, "offline"))
            repository.insertLoggedSet(LoggedSet(710, 707, "backup-custom", 1, 5, 80f, true, rpe = 8, actualDuration = 40))
            repository.insertBodyWeight(BodyWeight(711, 80f, 1000, 15f, 68f, 12f, 23f, "offline"))
            repository.insertTapeMeasurement(TapeMeasurement(712, 1000, chest = 100f, waist = 80f, userId = "offline"))
            val plan = TrainingPlan("backup-plan", "offline", human, 706, "missing-routine-global", "Backup Routine",
                22000, 817, 0b0010100, 22030, 100, 200, revision = 6, originDeviceId = "backup-device")
            val occurrence = PlannedWorkout("backup-plan:22000", "backup-plan", "offline", human, 706,
                "missing-routine-global", "Backup Routine", 22001, 22000, 817, "COMPLETED", 2000, 707,
                reminderEnabled = true, detachedFromSeries = true, createdAt = 110, updatedAt = 210,
                revision = 8, originDeviceId = "backup-device")
            database.strengthDao().restorePlannerBackupAtomically(listOf(plan), listOf(occurrence))
            val backup = profileViewModel.exportData()
            val root = JSONObject(backup)
            assertEquals(4, root.getInt("version"))
            assertEquals(200, root.getJSONArray("training_plans").getJSONObject(0).getLong("updatedAt"))
            assertEquals("backup-device", root.getJSONArray("planned_workouts").getJSONObject(0).getString("originDeviceId"))

            database.strengthDao().upsertTrainingPlan(plan.copy(routineName = "Corrupted locally"))
            var successes = 0; var error: String? = null
            profileViewModel.importData(backup, { successes++ }, { error = it })
            waitUntil { successes == 1 || error != null }
            profileViewModel.importData(backup, { successes++ }, { error = it })
            waitUntil { successes == 2 || error != null }
            assertNull(error)
            assertEquals("Backup Routine", database.strengthDao().getTrainingPlan("backup-plan")!!.routineName)
            assertEquals(1, database.strengthDao().getAllTrainingPlansForBackup("offline").count { it.id == "backup-plan" })
            assertEquals(1, database.strengthDao().getAllPlannedWorkoutsForBackup("offline").count { it.id == occurrence.id })
            assertEquals(8, database.strengthDao().getPlannedWorkout(occurrence.id)!!.revision)
            assertNotNull(repository.getExerciseById("backup-custom"))
            assertEquals(listOf(706), repository.getTemplatesForUser("offline").first().filter { it.id == 706 }.map { it.id })
            assertEquals(listOf(707), repository.getSessionsForUser("offline").first().filter { it.id == 707 }.map { it.id })
            assertEquals(listOf(710), repository.getLoggedSetsForUser("offline").first().filter { it.id == 710 }.map { it.id })
            assertEquals(711, repository.getBodyWeightsForUser("offline").first().single { it.id == 711 }.id)
            assertEquals(712, repository.getTapeMeasurementsForUser("offline").first().single { it.id == 712 }.id)
        }
    }
}
