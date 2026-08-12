package com.example.service.workout

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class WorkoutNotificationFactory(private val context: Context) {
    fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Active strength workout", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps a workout and rest timer visible while you train"; setSound(null, null); enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
    }
    fun restoring() = base("Strength workout", "Restoring active workout", "restore").build()
    fun build(state: WorkoutExecutionSnapshot, now: Long): Notification {
        val rest = state.restRemainingSeconds(now)
        val elapsed = format(state.elapsedSeconds(now))
        val detail = if (rest != null && rest > 0) "Rest ${format(rest)}" else listOfNotNull(state.currentExercise, "Elapsed $elapsed").joinToString(" • ")
        return base(state.workoutName, detail, state.sessionId)
            .setSubText("${state.completedSets}/${state.totalSets} sets complete")
            .setContentIntent(openIntent(state.sessionId)).build()
    }
    private fun base(title: String, text: String, sessionId: String) = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle(title).setContentText(text)
        .setOnlyAlertOnce(true).setOngoing(true).setSilent(true).setCategory(NotificationCompat.CATEGORY_STOPWATCH)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setContentIntent(openIntent(sessionId))
    private fun openIntent(sessionId: String) = PendingIntent.getActivity(context, sessionId.hashCode(),
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_OPEN_WORKOUT, true), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun format(seconds: Int) = "%02d:%02d".format(seconds / 60, seconds % 60)
    companion object { const val CHANNEL_ID = "strength_active_workout"; const val NOTIFICATION_ID = 1941; const val EXTRA_OPEN_WORKOUT = "open_strength_workout" }
}
