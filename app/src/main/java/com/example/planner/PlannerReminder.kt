package com.example.planner

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.PlannedWorkout
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow

object PlannerReminderNavigation {
    val requestedOccurrence = MutableStateFlow<String?>(null)
    fun request(id: String?) { if (!id.isNullOrBlank()) requestedOccurrence.value = id }
    fun consume() { requestedOccurrence.value = null }
}

object PlannerReminderScheduler {
    internal fun stableIdentity(id: String): String = MessageDigest.getInstance("SHA-256")
        .digest(id.toByteArray()).joinToString("") { "%02x".format(it) }
    fun workName(id: String) = "planner_reminder_${stableIdentity(id)}"
    fun notificationId(id: String): Int = (stableIdentity(id).take(8).toLong(16) and 0x7fffffff).toInt()

    internal fun cancelIssuedNotification(context: Context, occurrenceId: String) {
        context.getSystemService(NotificationManager::class.java).cancel(notificationId(occurrenceId))
    }

    fun isEligible(item: PlannedWorkout, expectedUserId: String? = null): Boolean =
        item.reminderEnabled && item.status == "PLANNED" && item.preferredMinuteOfDay != null &&
            item.deletedAt == null && (expectedUserId == null || item.userId == expectedUserId)

    /** Date-based plans resolve gaps by moving forward and overlaps to the earlier offset, per java.time. */
    fun triggerAtMillis(item: PlannedWorkout, zone: ZoneId): Long? {
        if (!isEligible(item)) return null
        val minute = item.preferredMinuteOfDay ?: return null
        val localTime = LocalTime.of(minute / 60, minute % 60)
        return LocalDate.ofEpochDay(item.scheduledEpochDay).atTime(localTime).atZone(zone).toInstant().toEpochMilli()
    }

    fun schedule(context: Context, item: PlannedWorkout, zone: ZoneId = ZoneId.systemDefault(), now: Long = System.currentTimeMillis()) {
        if (!isEligible(item)) {
            cancel(context, item.id); return
        }
        cancelIssuedNotification(context, item.id)
        val trigger = triggerAtMillis(item, zone) ?: return
        if (trigger <= now) return
        val request = OneTimeWorkRequestBuilder<PlannerReminderWorker>()
            .setInitialDelay(trigger - now, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString(PlannerReminderWorker.KEY_OCCURRENCE_ID, item.id)
                .putString(PlannerReminderWorker.KEY_OWNER_ID, item.userId).build())
            .addTag(workName(item.id)).build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(item.id), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context, occurrenceId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(occurrenceId))
        cancelIssuedNotification(context, occurrenceId)
    }
}

class PlannerReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val id = inputData.getString(KEY_OCCURRENCE_ID) ?: return Result.success()
        val expectedOwner = inputData.getString(KEY_OWNER_ID) ?: return Result.success()
        val activeOwner = applicationContext.getSharedPreferences("strength_settings", Context.MODE_PRIVATE)
            .getString("auth_active_user_id", "offline") ?: "offline"
        val item = com.example.data.StrengthDatabase.getDatabase(applicationContext,
            kotlinx.coroutines.CoroutineScope(kotlin.coroutines.coroutineContext)).strengthDao().getPlannedWorkout(id)
        if (activeOwner != expectedOwner || item == null || item.userId != expectedOwner || !PlannerReminderScheduler.isEligible(item, expectedOwner)) {
            applicationContext.getSystemService(NotificationManager::class.java)
                .cancel(PlannerReminderScheduler.notificationId(id))
            return Result.success()
        }
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Planned workout reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val pending = contentIntent(applicationContext, id)
        val whenText = LocalDate.ofEpochDay(item.scheduledEpochDay)
            .format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM"))
        val timeText = item.preferredMinuteOfDay?.let {
            LocalTime.of(it / 60, it % 60).format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))
        }
        manager.notify(PlannerReminderScheduler.notificationId(id), NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle("Planned workout")
            .setContentText("${item.routineName} · $whenText${timeText?.let { value -> ", $value" } ?: ""}")
            .setGroup("planner_reminder_${PlannerReminderScheduler.stableIdentity(id)}")
            .setContentIntent(pending).setAutoCancel(true).build())
        return Result.success()
    }

    companion object {
        const val KEY_OCCURRENCE_ID = "occurrence_id"
        const val KEY_OWNER_ID = "owner_id"
        const val EXTRA_PLANNER_OCCURRENCE = "open_planner_occurrence"
        const val CHANNEL_ID = "planned_workouts"
        internal fun contentIntent(context: Context, id: String): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .setData(Uri.parse("humanv1://planner/occurrence/${Uri.encode(id)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_PLANNER_OCCURRENCE, id)
            return PendingIntent.getActivity(context, PlannerReminderScheduler.notificationId(id), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
