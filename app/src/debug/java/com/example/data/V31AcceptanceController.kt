package com.example.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** DUMP-protected debug-only controller. It can create only the fixed synthetic fixture. */
class V31AcceptanceController : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(ACTION_ARM_MATCHING, ACTION_REPORT, ACTION_DISARM, ACTION_RESET)) return
        if (intent.action == ACTION_DISARM) { DebugAcceptanceIdentity.disarm(context); return }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_ARM_MATCHING -> armMatching(context)
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

    private suspend fun report(context: Context) {
        val dao = StrengthDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO)).strengthDao()
        val journal = dao.getMigrationState()?.phase ?: "NONE"
        val legacy = dao.countRemainingLegacyOwnership("human_legacysynthetic0000000000000000")
        val commands = dao.countUploadableLegacyCommands("human_legacysynthetic0000000000000000")
        Log.i(TAG, "result=AGGREGATE journal=$journal legacyRows=$legacy uploadableLegacyCommands=$commands")
    }

    companion object {
        const val ACTION_ARM_MATCHING = "com.example.debug.V31_ARM_MATCHING"
        const val ACTION_REPORT = "com.example.debug.V31_REPORT"
        const val ACTION_DISARM = "com.example.debug.V31_DISARM"
        const val ACTION_RESET = "com.example.debug.V31_RESET_SYNTHETIC_SESSION"
        private const val TAG = "V31Acceptance"
    }
}
