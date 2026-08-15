package com.example.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

/** DUMP-protected debug-only controller. It can create only the fixed synthetic fixture. */
class V31AcceptanceController : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(ACTION_ARM_MATCHING, ACTION_ARM_CONFLICT, ACTION_RUN_SYNC, ACTION_QUEUE_LOCAL_WEIGHT, ACTION_REPORT, ACTION_DISARM, ACTION_RESET)) return
        if (intent.action == ACTION_DISARM) { DebugAcceptanceIdentity.disarm(context); return }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_ARM_MATCHING -> armMatching(context)
                    ACTION_ARM_CONFLICT -> armConflict(context)
                    ACTION_RUN_SYNC -> runSync(context)
                    ACTION_QUEUE_LOCAL_WEIGHT -> queueLocalWeight(context)
                    ACTION_RESET -> reset(context)
                    else -> report(context)
                }
            } catch (_: Exception) {
                Log.e(TAG, "result=FAILED_SAFE")
            } finally { pending.finish() }
        }
    }

    private suspend fun armMatching(context: Context) {
        if (DebugAcceptanceIdentity.hasValidAcceptanceMarker(context)) reset(context)
        DebugAcceptanceIdentity.arm(context, DebugAcceptanceIdentity.SYNTHETIC_UID, DebugAcceptanceIdentity.SYNTHETIC_HUMAN)
        val deps = requireNotNull(DebugAcceptanceIdentity.dependencies(context))
        val auth = requireNotNull(deps.firebaseAuth)
        Tasks.await(auth.signInWithEmailAndPassword("owner@example.invalid", "local-only-password"))
        require(auth.currentUser?.uid == DebugAcceptanceIdentity.SYNTHETIC_UID)
        val dao = StrengthDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO)).strengthDao()
        val legacyHuman = "human_legacysynthetic0000000000000000"
        // Re-arming the time-limited fixture is idempotent: remove only this controller's
        // fixed synthetic measurement before recreating it.
        dao.legacyBodyWeights(DebugAcceptanceIdentity.SYNTHETIC_UID, legacyHuman)
            .filter { it.globalId == "fixture-weight" }
            .forEach { dao.deleteBodyWeight(it.id) }
        dao.insertUserProfile(UserProfile(DebugAcceptanceIdentity.SYNTHETIC_UID,
            googleUserId = DebugAcceptanceIdentity.SYNTHETIC_UID, authProvider = "google", isOfflineUser = false,
            globalId = "fixture-profile", humanUserId = legacyHuman, firebaseUid = DebugAcceptanceIdentity.SYNTHETIC_UID))
        dao.insertBodyWeight(BodyWeight(weight = 80f, date = 1L, userId = DebugAcceptanceIdentity.SYNTHETIC_UID,
            globalId = "fixture-weight", humanUserId = legacyHuman))
        Log.i(TAG, "result=ARMED profileCount=1 measurementCount=1")
    }

    private suspend fun reset(context: Context) {
        val totals = SyntheticAcceptanceReset.reset(context)
        Log.i(TAG, "result=RESET removedRecords=${totals.removedRecords}")
    }

    private suspend fun armConflict(context: Context) {
        if (!DebugAcceptanceIdentity.hasValidAcceptanceMarker(context)) {
            DebugAcceptanceIdentity.arm(context, DebugAcceptanceIdentity.SYNTHETIC_UID, DebugAcceptanceIdentity.SYNTHETIC_HUMAN)
        }
        val deps = requireNotNull(DebugAcceptanceIdentity.dependencies(context))
        val auth = requireNotNull(deps.firebaseAuth)
        Tasks.await(auth.signInWithEmailAndPassword("owner@example.invalid", "local-only-password"))
        require(auth.currentUser?.uid == DebugAcceptanceIdentity.SYNTHETIC_UID)
        val dao = StrengthDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO)).strengthDao()
        val localTime = System.currentTimeMillis() - 2_000L
        dao.insertUserProfile(UserProfile(
            id = DebugAcceptanceIdentity.SYNTHETIC_UID,
            googleUserId = DebugAcceptanceIdentity.SYNTHETIC_UID,
            authProvider = "google",
            globalId = "fixture-profile",
            humanUserId = DebugAcceptanceIdentity.SYNTHETIC_HUMAN,
            firebaseUid = DebugAcceptanceIdentity.SYNTHETIC_UID,
            syncStatus = "SYNCED"
        ))
        dao.insertExercise(Exercise(
            id = FIXTURE_EXERCISE_ID,
            name = "Acceptance Press - phone",
            category = "Chest",
            isCustom = true,
            globalId = FIXTURE_EXERCISE_ID,
            humanUserId = DebugAcceptanceIdentity.SYNTHETIC_HUMAN,
            updatedAt = localTime,
            revision = 2,
            syncStatus = "PENDING_UPLOAD",
            originDeviceId = "acceptance-phone"
        ))
        dao.enqueueCommand(CommandQueueEntity(
            commandId = "fixture-conflict-command",
            humanUserId = DebugAcceptanceIdentity.SYNTHETIC_HUMAN,
            commandType = "ExerciseUpdated",
            entityType = "CUSTOM_EXERCISE",
            entityGlobalId = FIXTURE_EXERCISE_ID,
            payloadJson = "{}",
            originDeviceId = "acceptance-phone"
        ))
        val remote = requireNotNull(DebugAcceptanceIdentity.firestore(context))
            .collection("users").document(DebugAcceptanceIdentity.SYNTHETIC_HUMAN)
            .collection("customExercises").document(FIXTURE_EXERCISE_ID)
        Tasks.await(remote.set(mapOf(
            "globalId" to FIXTURE_EXERCISE_ID,
            "humanUserId" to DebugAcceptanceIdentity.SYNTHETIC_HUMAN,
            "name" to "Acceptance Press - online",
            "category" to "Chest",
            "isCustom" to true,
            "createdAt" to localTime - 1_000L,
            "updatedAt" to localTime + 1_000L,
            "revision" to 2L,
            "originDeviceId" to "acceptance-online"
        )))
        Log.i(TAG, "result=CONFLICT_ARMED entityType=CUSTOM_EXERCISE")
    }

    private suspend fun report(context: Context) {
        val dao = StrengthDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO)).strengthDao()
        val journal = dao.getMigrationState()?.phase ?: "NONE"
        val legacy = dao.countRemainingLegacyOwnership("human_legacysynthetic0000000000000000")
        val commands = dao.countUploadableLegacyCommands("human_legacysynthetic0000000000000000")
        Log.i(TAG, "result=AGGREGATE journal=$journal legacyRows=$legacy uploadableLegacyCommands=$commands")
        val commandStates = dao.getAllCommands().groupingBy { it.status }.eachCount().entries
            .sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }
        val conflictRecords = dao.getConflictExercisesFlow().first().size + dao.getConflictTemplatesFlow().first().size
        Log.i(TAG, "result=SYNC_STATE commands=$commandStates conflictRecords=$conflictRecords")
    }

    private suspend fun runSync(context: Context) {
        check(DebugAcceptanceIdentity.isValidAcceptanceSession(context))
        val dao = StrengthDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO)).strengthDao()
        val repository = StrengthRepository(dao, context)
        val result = com.example.core.sync.SyncEngineImpl(context, repository).synchronizeAll()
        Log.i(TAG, "result=SYNC_RUN success=${result.isSuccess}")
        report(context)
    }

    private suspend fun queueLocalWeight(context: Context) {
        check(DebugAcceptanceIdentity.isValidAcceptanceSession(context))
        val dao = StrengthDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO)).strengthDao()
        StrengthRepository(dao, context).insertBodyWeight(BodyWeight(
            weight = 81f,
            date = System.currentTimeMillis(),
            userId = DebugAcceptanceIdentity.SYNTHETIC_UID,
            humanUserId = DebugAcceptanceIdentity.SYNTHETIC_HUMAN
        ))
        Log.i(TAG, "result=LOCAL_WEIGHT_QUEUED durable=true")
        report(context)
    }

    companion object {
        const val ACTION_ARM_MATCHING = "com.example.debug.V31_ARM_MATCHING"
        const val ACTION_ARM_CONFLICT = "com.example.debug.V32_1_ARM_CONFLICT"
        const val ACTION_RUN_SYNC = "com.example.debug.V32_1_RUN_SYNC"
        const val ACTION_QUEUE_LOCAL_WEIGHT = "com.example.debug.V32_1_QUEUE_LOCAL_WEIGHT"
        const val ACTION_REPORT = "com.example.debug.V31_REPORT"
        const val ACTION_DISARM = "com.example.debug.V31_DISARM"
        const val ACTION_RESET = "com.example.debug.V31_RESET_SYNTHETIC_SESSION"
        private const val TAG = "V31Acceptance"
        private const val FIXTURE_EXERCISE_ID = "fixture-conflict-exercise"
    }
}
