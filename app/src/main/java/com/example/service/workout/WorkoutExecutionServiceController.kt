package com.example.service.workout

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object WorkoutExecutionServiceController {
    private val navigation = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
    val navigationRequests = navigation.asSharedFlow()

    fun start(context: Context, sessionId: String) {
        ContextCompat.startForegroundService(context, Intent(context, WorkoutExecutionService::class.java)
            .setAction(WorkoutExecutionService.ACTION_START).putExtra(WorkoutExecutionService.EXTRA_SESSION_ID, sessionId))
    }
    fun stop(context: Context) {
        // Deliver a stop command so a pending foreground-service start always reaches
        // onCreate/startForeground before shutdown. Calling stopService directly can
        // race Android's foreground-service timeout after a very short workout.
        context.startService(Intent(context, WorkoutExecutionService::class.java)
            .setAction(WorkoutExecutionService.ACTION_STOP))
    }
    fun requestNavigation() { navigation.tryEmit(Unit) }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun consumeNavigation() { navigation.resetReplayCache() }
}
