package com.example.core.sync

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object SyncScheduler {

    fun scheduleImmediate(context: Context) {
        if (!com.example.StrengthApplication.isFirebaseConfigured) {
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<ImmediateSyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "ImmediateSyncWork",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun schedulePeriodic(context: Context) {
        if (!com.example.StrengthApplication.isFirebaseConfigured) {
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<PeriodicSyncWorker>(
            4, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "PeriodicSyncWork",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
