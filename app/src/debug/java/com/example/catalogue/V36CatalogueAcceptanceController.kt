package com.example.catalogue

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.room.withTransaction
import com.example.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.MessageDigest

/** DUMP-protected, fixed synthetic V36 fixture. It never contacts Firebase. */
class V36CatalogueAcceptanceController : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(ACTION_APPLY, ACTION_REPORT, ACTION_DISARM)) return
        if (intent.action == ACTION_DISARM) {
            ExerciseCatalogueRuntime.load(context)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
            Log.i(TAG, "result=DISARMED")
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { if (intent.action == ACTION_APPLY) apply(context) else validateAndReport(context) }
            catch (error: Throwable) { Log.e(TAG, "result=FAILED_SAFE reason=${error.javaClass.simpleName}") }
            finally { pending.finish() }
        }
    }

    private suspend fun apply(context: Context) {
        val database = StrengthDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO))
        val dao = database.strengthDao()
        val bundled = PackagedExerciseLibrarySource.load(context).snapshot
        require(sha256(FIXTURE_SPEC) == FIXTURE_CHECKSUM)
        require(bundled.exercises.size == GOVERNED_COUNT)
        requireNoUnknownData(database, bundled.exercises.map { it.id }.toSet())
        val rich = bundled.exercises.first { it.id == "calf_raise" }.copy(intelligence = ExerciseIntelligence(
            purpose = "Controlled plantar-flexor strength training.", stabilizers = listOf("intrinsic foot muscles"),
            jointActions = listOf("ankle plantar flexion"), movementPlane = "sagittal", kineticChain = "closed chain",
            rangeOfMotionNotes = "Use a comfortable controlled range.", forceVector = "vertical",
            biomechanicalRationale = "The ankle plantar flexors produce the primary lifting action.",
            programmingGuidance = listOf("Use controlled repetitions and progress load gradually."),
            typicalUseCases = listOf("Calf strength and hypertrophy"), cautions = listOf("Avoid painful range."),
            evidence = listOf(EvidenceClaim("Progressive loading can improve plantar-flexor strength.",
                "Synthetic V36 acceptance citation", "https://doi.org/10.1000/v36-acceptance", "2026-08-17",
                "acceptance-editor", "editorially reviewed", "Synthetic acceptance citation only",
                "10.1000/v36-acceptance", "12345678"))))
        val noEvidence = bundled.exercises.first { it.id == "wall_slide" }.copy(
            intelligence = ExerciseIntelligence(purpose = "Isometric lower-body training.", evidence = emptyList()))
        val accepted = bundled.exercises.map { when (it.id) { rich.id -> rich; noEvidence.id -> noEvidence; else -> it } }
        val now = FIXED_TIME
        database.withTransaction {
            dao.insertExercises(accepted.map { it.toRoom(now) })
            dao.insertUserProfile(UserProfile(PROFILE_ID, googleUserId = PROFILE_ID,
                displayName = "Synthetic Acceptance", authProvider = "google", globalId = PROFILE_GLOBAL,
                humanUserId = HUMAN_ID, firebaseUid = PROFILE_ID, syncStatus = "SYNCED", createdAt = now,
                updatedAt = now, lastLoginAt = now, lastSyncedAt = now, originDeviceId = DEVICE_ID))
            CUSTOM_IDS.forEachIndexed { index, id -> dao.insertExercise(Exercise(id,
                "V36 Synthetic Custom ${index + 1}", "Other", true, globalId = id, humanUserId = HUMAN_ID,
                createdAt = now, updatedAt = now, syncStatus = "SYNCED", lastSyncedAt = now,
                originDeviceId = DEVICE_ID)) }
            ROUTINE_IDS.forEachIndexed { index, id ->
                val global = "v36-fixture-routine-${index + 1}"
                dao.insertTemplate(WorkoutTemplate(id, "V36 Synthetic Routine ${index + 1}",
                    "[\"${CUSTOM_IDS[index]}\"]", PROFILE_ID, globalId = global, humanUserId = HUMAN_ID,
                    createdAt = now, updatedAt = now, syncStatus = "SYNCED", lastSyncedAt = now,
                    originDeviceId = DEVICE_ID))
                val childId = ROUTINE_EXERCISE_IDS[index]
                dao.insertTemplateExercise(WorkoutTemplateExercise(childId, id, CUSTOM_IDS[index], 0, 90,
                    globalId = "v36-fixture-routine-exercise-${index + 1}", humanUserId = HUMAN_ID,
                    createdAt = now, updatedAt = now, syncStatus = "SYNCED", lastSyncedAt = now,
                    originDeviceId = DEVICE_ID, templateGlobalId = global))
                (0..1).forEach { setIndex -> dao.insertTemplateSet(WorkoutTemplateSet(
                    id = ROUTINE_SET_IDS[index * 2 + setIndex], templateExerciseId = childId, position = setIndex,
                    setType = "WORKING", targetRepsMin = 8, targetRepsMax = 12,
                    globalId = "v36-fixture-routine-set-${index * 2 + setIndex + 1}", humanUserId = HUMAN_ID,
                    createdAt = now, updatedAt = now, syncStatus = "SYNCED", lastSyncedAt = now,
                    originDeviceId = DEVICE_ID, templateExerciseGlobalId = "v36-fixture-routine-exercise-${index + 1}")) }
            }
            SESSION_IDS.forEachIndexed { index, id ->
                val sessionGlobal = "v36-fixture-session-${index + 1}"
                dao.insertSession(WorkoutSession(id, ROUTINE_IDS[index], "V36 Synthetic Routine ${index + 1}",
                    now + index * 10_000L, now + index * 10_000L + 3_600_000L, PROFILE_ID,
                    globalId = sessionGlobal, humanUserId = HUMAN_ID, createdAt = now, updatedAt = now,
                    syncStatus = "SYNCED", lastSyncedAt = now, originDeviceId = DEVICE_ID,
                    templateGlobalId = "v36-fixture-routine-${index + 1}"))
                (0..2).forEach { setIndex -> dao.insertLoggedSet(LoggedSet(
                    id = LOGGED_SET_IDS[index * 3 + setIndex], sessionId = id, exerciseId = CUSTOM_IDS[index],
                    setNumber = setIndex + 1, reps = 10, weight = 20f + index, isCompleted = true,
                    globalId = "v36-fixture-logged-set-${index * 3 + setIndex + 1}", humanUserId = HUMAN_ID,
                    createdAt = now, updatedAt = now, syncStatus = "SYNCED", lastSyncedAt = now,
                    originDeviceId = DEVICE_ID, sessionGlobalId = sessionGlobal)) }
            }
            dao.upsertTrainingPlan(TrainingPlan(PLAN_ID, PROFILE_ID, HUMAN_ID, ROUTINE_IDS[0],
                "v36-fixture-routine-1", "V36 Synthetic Routine 1", 30_000L, 540, 62, 30_030L,
                createdAt = now, updatedAt = now, revision = 3, syncStatus = "SYNCED",
                lastSyncedAt = now, originDeviceId = DEVICE_ID))
            (0 until OCCURRENCE_COUNT).forEach { index ->
                val status = when (index) { 0 -> "COMPLETED"; 1 -> "SKIPPED"; else -> "PLANNED" }
                dao.upsertPlannedWorkout(PlannedWorkout("v36-fixture-occurrence-${index + 1}", PLAN_ID,
                    PROFILE_ID, HUMAN_ID, ROUTINE_IDS[0], "v36-fixture-routine-1", "V36 Synthetic Routine 1",
                    30_000L + index, 30_000L + index, 540, status,
                    completedAt = if (index == 0) now else null,
                    linkedSessionId = if (index == 0) SESSION_IDS[0] else null,
                    createdAt = now, updatedAt = now, revision = if (index == 2) 2 else 1,
                    deletedAt = if (index == 2) now else null, syncStatus = "SYNCED",
                    lastSyncedAt = now, originDeviceId = DEVICE_ID))
            }
        }
        ExerciseCatalogueRuntime.accept(bundled.copy(exercises = accepted))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("fixtureVersion", FIXTURE_VERSION).putString("fixtureChecksum", FIXTURE_CHECKSUM).apply()
        validateAndReport(context)
    }

    private fun requireNoUnknownData(database: StrengthDatabase, governedIds: Set<String>) {
        val db = database.openHelper.writableDatabase
        fun count(sql: String, args: Array<Any?> = emptyArray()): Long = db.query(sql, args).use {
            it.moveToFirst(); it.getLong(0)
        }
        require(count("SELECT COUNT(*) FROM user_profile WHERE id != ?", arrayOf(PROFILE_ID)) == 0L)
        require(count("SELECT COUNT(*) FROM exercise WHERE isCustom = 1 AND id NOT LIKE 'v36_fixture_custom_%'") == 0L)
        require(count("SELECT COUNT(*) FROM exercise WHERE isCustom = 0 AND id NOT IN (${governedIds.joinToString { "?" }})", governedIds.toTypedArray()) == 0L)
        require(count("SELECT COUNT(*) FROM workout_template WHERE globalId NOT IN ('template_push','template_pull','template_legs') AND id NOT BETWEEN 3601 AND 3604") == 0L)
        listOf("workout_template_exercise", "workout_template_set", "workout_session", "logged_set",
            "training_plan", "planned_workout", "command_queue", "body_weight", "tape_measurement").forEach { table ->
            val allowed = when (table) {
                "workout_template_exercise" -> "id BETWEEN 3611 AND 3614"
                "workout_template_set" -> "id BETWEEN 3621 AND 3628"
                "workout_session" -> "id BETWEEN 3631 AND 3634"
                "logged_set" -> "id BETWEEN 3641 AND 3652"
                "training_plan" -> "id = '$PLAN_ID'"
                "planned_workout" -> "id LIKE 'v36-fixture-occurrence-%'"
                "body_weight" -> "globalId = 'fixture-weight'"
                else -> "0"
            }
            require(count("SELECT COUNT(*) FROM $table WHERE NOT ($allowed)") == 0L)
        }
    }

    private fun validateAndReport(context: Context) {
        val db = StrengthDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO)).openHelper.readableDatabase
        fun count(table: String, where: String? = null): Long = db.query(
            "SELECT COUNT(*) FROM $table${where?.let { " WHERE $it" } ?: ""}").use {
            it.moveToFirst(); it.getLong(0)
        }
        val receipt = linkedMapOf(
            "profiles" to count("user_profile", "id = '$PROFILE_ID'"),
            "governed" to count("exercise", "isCustom = 0"),
            "custom" to count("exercise", "id LIKE 'v36_fixture_custom_%'"),
            "routines" to count("workout_template", "id BETWEEN 3601 AND 3604"),
            "routineExercises" to count("workout_template_exercise", "id BETWEEN 3611 AND 3614"),
            "routineSets" to count("workout_template_set", "id BETWEEN 3621 AND 3628"),
            "sessions" to count("workout_session", "id BETWEEN 3631 AND 3634"),
            "loggedSets" to count("logged_set", "id BETWEEN 3641 AND 3652"),
            "plans" to count("training_plan", "id = '$PLAN_ID'"),
            "activePlans" to count("training_plan", "id = '$PLAN_ID' AND deletedAt IS NULL"),
            "tombstonedPlans" to count("training_plan", "id = '$PLAN_ID' AND deletedAt IS NOT NULL"),
            "occurrences" to count("planned_workout", "seriesId = '$PLAN_ID'"),
            "scheduled" to count("planned_workout", "seriesId = '$PLAN_ID' AND status = 'PLANNED' AND deletedAt IS NULL"),
            "completed" to count("planned_workout", "seriesId = '$PLAN_ID' AND status = 'COMPLETED'"),
            "skipped" to count("planned_workout", "seriesId = '$PLAN_ID' AND status = 'SKIPPED'"),
            "tombstonedOccurrences" to count("planned_workout", "seriesId = '$PLAN_ID' AND deletedAt IS NOT NULL"),
            "linkedOccurrences" to count("planned_workout", "seriesId = '$PLAN_ID' AND linkedSessionId IS NOT NULL"),
            "pendingCommands" to count("command_queue", "status = 'PENDING'"),
            "failedCommands" to count("command_queue", "status = 'FAILED'"),
            "governedCommands" to count("command_queue", "entityType = 'EXERCISE'"),
            "ownershipMismatches" to count("user_profile", "id = '$PROFILE_ID' AND humanUserId != '$HUMAN_ID'"),
            "duplicateStableIds" to count("(SELECT globalId FROM exercise WHERE globalId != '' GROUP BY globalId HAVING COUNT(*) > 1)"))
        val expected = mapOf("profiles" to 1L, "governed" to 264L, "custom" to 4L, "routines" to 4L,
            "routineExercises" to 4L, "routineSets" to 8L, "sessions" to 4L, "loggedSets" to 12L,
            "plans" to 1L, "activePlans" to 1L, "tombstonedPlans" to 0L, "occurrences" to 24L,
            "scheduled" to 21L, "completed" to 1L, "skipped" to 1L, "tombstonedOccurrences" to 1L,
            "linkedOccurrences" to 1L, "pendingCommands" to 0L, "failedCommands" to 0L,
            "governedCommands" to 0L, "ownershipMismatches" to 0L, "duplicateStableIds" to 0L)
        require(receipt == expected)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        require(sha256(FIXTURE_SPEC) == FIXTURE_CHECKSUM)
        require(prefs.getString("fixtureVersion", null) == FIXTURE_VERSION &&
            prefs.getString("fixtureChecksum", null) == FIXTURE_CHECKSUM)
        Log.i(TAG, "result=VALID fixtureVersion=$FIXTURE_VERSION fixtureChecksum=$FIXTURE_CHECKSUM " +
            receipt.entries.joinToString(" ") { "${it.key}=${it.value}" })
    }

    companion object {
        const val ACTION_APPLY = "com.example.debug.V36_APPLY_CATALOGUE_FIXTURE"
        const val ACTION_REPORT = "com.example.debug.V36_REPORT_CATALOGUE_FIXTURE"
        const val ACTION_DISARM = "com.example.debug.V36_DISARM_CATALOGUE_FIXTURE"
        const val FIXTURE_VERSION = "v36-synthetic-acceptance-2"
        const val FIXTURE_SPEC = "v36-synthetic-acceptance-2|profiles=1|governed=264|custom=4|routines=4|routineExercises=4|routineSets=8|sessions=4|loggedSets=12|plans=1|occurrences=24|scheduled=21|completed=1|skipped=1|tombstoned=1|linked=1|commands=0"
        const val FIXTURE_CHECKSUM = "bafc8874dd3467922b96b6c34a0bbd324d0edf6c1d30cadd5b3ca1360c9fc59b"
        private const val GOVERNED_COUNT = 264
        private const val OCCURRENCE_COUNT = 24
        private const val PROFILE_ID = "planner-client-owner"
        private const val PROFILE_GLOBAL = "v36-fixture-profile"
        private const val HUMAN_ID = "human_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        private const val PLAN_ID = "v36-fixture-plan-1"
        private const val DEVICE_ID = "v36-disposable-avd"
        private const val FIXED_TIME = 1_787_001_600_000L
        private val CUSTOM_IDS = List(4) { "v36_fixture_custom_${it + 1}" }
        private val ROUTINE_IDS = listOf(3601, 3602, 3603, 3604)
        private val ROUTINE_EXERCISE_IDS = listOf(3611, 3612, 3613, 3614)
        private val ROUTINE_SET_IDS = (3621..3628).toList()
        private val SESSION_IDS = listOf(3631, 3632, 3633, 3634)
        private val LOGGED_SET_IDS = (3641..3652).toList()
        private const val PREFS = "v36_catalogue_acceptance"
        private const val TAG = "V36CatalogueAcceptance"
        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
