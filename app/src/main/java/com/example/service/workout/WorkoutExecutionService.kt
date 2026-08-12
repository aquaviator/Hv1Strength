package com.example.service.workout

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.example.data.StrengthDatabase
import kotlinx.coroutines.*

class WorkoutExecutionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notifications: WorkoutNotificationFactory
    private var sessionId: String? = null
    private var refreshJob: Job? = null
    private var restWasActive = false
    private val sessionGate = WorkoutServiceSessionGate()

    override fun onCreate() {
        super.onCreate(); notifications = WorkoutNotificationFactory(this).also { it.createChannel() }
        ServiceCompat.startForeground(this, WorkoutNotificationFactory.NOTIFICATION_ID, notifications.restoring(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSafely()
            return START_NOT_STICKY
        }
        intent?.getStringExtra(EXTRA_SESSION_ID)?.takeIf { it.isNotBlank() }?.let { sessionId = it }
        if (refreshJob?.isActive != true) refreshJob = scope.launch { refresh() }
        return START_STICKY
    }
    private suspend fun refresh() {
        val database = StrengthDatabase.getDatabase(applicationContext, scope)
        while (currentCoroutineContext().isActive) {
            val backup = runCatching { database.strengthDao().getActiveWorkoutBackup() }.getOrNull()
            val snapshot = backup?.let { runCatching { WorkoutExecutionParser.parse(it) }.getOrNull() }
            if (!sessionGate.accept(sessionId, snapshot)) { stopSafely(); return }
            val active = snapshot ?: run { stopSafely(); return }
            sessionId = active.sessionId
            val restRemaining = active.restRemainingSeconds(System.currentTimeMillis())
            if (restWasActive && restRemaining == 0) deliverRestCompleteCue(database)
            restWasActive = restRemaining != null && restRemaining > 0
            ServiceCompat.startForeground(this, WorkoutNotificationFactory.NOTIFICATION_ID, notifications.build(active, System.currentTimeMillis()), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            delay(1_000L)
        }
    }
    private suspend fun deliverRestCompleteCue(database: StrengthDatabase) {
        val prefs = runCatching { database.strengthDao().getUserPreferences() }.getOrNull() ?: com.example.data.UserPreferences()
        if (prefs.soundOn) runCatching {
            android.media.RingtoneManager.getRingtone(this, android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION))?.play()
        }
        if (prefs.vibrationOn) runCatching {
            val vibrator = getSystemService(android.os.Vibrator::class.java)
            if (android.os.Build.VERSION.SDK_INT >= 26) vibrator.vibrate(android.os.VibrationEffect.createOneShot(350L, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(350L)
            }
        }
    }
    private fun stopSafely() { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    override fun onDestroy() { sessionGate.clear(); refreshJob?.cancel(); scope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        const val EXTRA_SESSION_ID = "strength_session"
        const val ACTION_START = "com.example.action.START_STRENGTH_WORKOUT"
        const val ACTION_STOP = "com.example.action.STOP_STRENGTH_WORKOUT"
    }
}
