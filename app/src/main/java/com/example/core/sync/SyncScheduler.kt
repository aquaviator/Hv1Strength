package com.example.core.sync

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object SyncScheduler {

    private const val IMMEDIATE_WORK = "ImmediateSyncWork"
    private const val PERIODIC_WORK = "PeriodicSyncWork"
    const val INPUT_MANUAL = "manual_check"
    const val INPUT_REQUEST_ID = "manual_request_id"

    fun cancelCloudSync(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(IMMEDIATE_WORK)
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
    }

    fun scheduleImmediate(context: Context, manual: Boolean = false): Boolean {
        if (!com.example.HumanStrengthApplication.isFirebaseConfigured) {
            return false
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val requestId = java.util.UUID.randomUUID()
        val request = OneTimeWorkRequestBuilder<ImmediateSyncWorker>()
            .setId(requestId)
            .setInputData(workDataOf(INPUT_MANUAL to manual, INPUT_REQUEST_ID to requestId.toString()))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        if (manual && !SyncManager.beginManualCheck(context, requestId.toString(), System.currentTimeMillis())) return false

        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK,
            if (manual) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,
            request
        )
        return true
    }

    fun schedulePeriodic(context: Context) {
        if (!com.example.HumanStrengthApplication.isFirebaseConfigured) {
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
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
