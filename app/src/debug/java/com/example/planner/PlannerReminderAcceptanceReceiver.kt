package com.example.planner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.StrengthDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Debug-only deterministic bridge which still executes the production WorkManager Worker. */
class PlannerReminderAcceptanceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(ACTION_TRIGGER, ACTION_REPOST_LAST, ACTION_VALIDATE_LAST)) return
        val pending = goAsync()
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val owner = context.getSharedPreferences("strength_settings", Context.MODE_PRIVATE)
                    .getString("auth_active_user_id", "offline") ?: "offline"
                val dao = StrengthDatabase.getDatabase(context, scope).strengthDao()
                if (intent.action == ACTION_VALIDATE_LAST) {
                    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    val id = prefs.getString(KEY_LAST_OCCURRENCE, null) ?: return@launch
                    val item = dao.getPlannedWorkout(id)
                    val linkedSessionExists = item?.linkedSessionId?.let { dao.getSessionById(it) } != null
                    val sessionCount = dao.getAllSessions().first().size
                    Log.i("S8FF", "status=${item?.status ?: "MISSING"} linked=$linkedSessionExists " +
                        "datePreserved=${item?.scheduledEpochDay == item?.originalEpochDay} " +
                        "sessionCountStable=${sessionCount == prefs.getInt(KEY_SESSION_COUNT, -1)}")
                    return@launch
                }
                if (intent.action == ACTION_REPOST_LAST) {
                    val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .getString(KEY_LAST_OCCURRENCE, null) ?: return@launch
                    val item = dao.getPlannedWorkout(id) ?: return@launch
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putInt(KEY_SESSION_COUNT, dao.getAllSessions().first().size).apply()
                    val manager = context.getSystemService(NotificationManager::class.java)
                    manager.createNotificationChannel(NotificationChannel(
                        PlannerReminderWorker.CHANNEL_ID,
                        "Planned workout reminders",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ))
                    manager.notify(PlannerReminderScheduler.notificationId(id),
                        NotificationCompat.Builder(context, PlannerReminderWorker.CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_launcher_foreground)
                            .setContentTitle("Planned workout")
                            .setContentText("${item.routineName} · completed-stale acceptance")
                            .setContentIntent(PlannerReminderWorker.contentIntent(context, id))
                            .setAutoCancel(true)
                            .build())
                    return@launch
                }
                dao.getPlannedWorkoutsForUser(owner).first()
                    .filter { PlannerReminderScheduler.isEligible(it, owner) }
                    .take(2)
                    .also { items -> items.firstOrNull()?.let { item ->
                        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                            .putString(KEY_LAST_OCCURRENCE, item.id).apply()
                    } }
                    .forEach { item ->
                        val request = OneTimeWorkRequestBuilder<PlannerReminderWorker>()
                            .setInputData(Data.Builder()
                                .putString(PlannerReminderWorker.KEY_OCCURRENCE_ID, item.id)
                                .putString(PlannerReminderWorker.KEY_OWNER_ID, owner)
                                .build())
                            .build()
                        WorkManager.getInstance(context).enqueueUniqueWork(
                            "acceptance_${PlannerReminderScheduler.workName(item.id)}",
                            ExistingWorkPolicy.REPLACE,
                            request
                        )
                    }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TRIGGER = "com.example.debug.TRIGGER_PLANNER_REMINDERS"
        const val ACTION_REPOST_LAST = "com.example.debug.REPOST_LAST_PLANNER_REMINDER"
        const val ACTION_VALIDATE_LAST = "com.example.debug.VALIDATE_LAST_PLANNER_REMINDER"
        private const val PREFS = "planner_reminder_acceptance"
        private const val KEY_LAST_OCCURRENCE = "last_occurrence"
        private const val KEY_SESSION_COUNT = "session_count"
    }
}
