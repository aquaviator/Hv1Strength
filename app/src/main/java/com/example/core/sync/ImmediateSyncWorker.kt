package com.example.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.StrengthDatabase
import com.example.data.StrengthRepository

class ImmediateSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!com.example.HumanStrengthApplication.isFirebaseConfigured) {
            return Result.success()
        }
        val db = StrengthDatabase.getDatabase(
            applicationContext,
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
        )
        val repository = StrengthRepository(db.strengthDao(), applicationContext)
        val syncEngine = SyncEngineImpl(applicationContext, repository)
        
        val manual = inputData.getBoolean(SyncScheduler.INPUT_MANUAL, false)
        val requestId = inputData.getString(SyncScheduler.INPUT_REQUEST_ID) ?: id.toString()
        if (manual && !SyncManager.startManualCheck(applicationContext, requestId, System.currentTimeMillis())) {
            return Result.failure()
        }
        return try {
            val result = syncEngine.synchronizeAll()
            val status = SyncManager.currentStatus.value
            val counts = SyncManager.currentRunCounts()
            val attentionCount = maxOf(counts.attentionCount,
                if (status == "ItemsNeedReview" || status.contains("NeedsAttention")) 1 else 0)
            val reason = result.exceptionOrNull()?.localizedMessage ?: SyncManager.lastError.value
            if (manual) SyncManager.completeManualCheck(applicationContext, requestId,
                counts.downloaded, counts.uploaded, attentionCount,
                offline = status == "WaitingForConnection", reason = reason,
                errorClassification = classify(reason, status), completedAt = System.currentTimeMillis())
            if (result.isSuccess) {
                if (UnattendedSyncPolicy.workerShouldRetry(true, repository.getAllCommands())) Result.retry()
                else Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            if (manual) SyncManager.completeManualCheck(applicationContext, requestId, 0, 0, 0, false,
                e.localizedMessage ?: "Synchronization failed", "TRANSIENT_FAILURE", System.currentTimeMillis())
            Result.retry()
        }
    }

    private fun classify(reason: String?, status: String): String? = when {
        reason == null -> null
        status == "WaitingForConnection" -> "OFFLINE"
        reason.contains("permission", ignoreCase = true) -> "PERMISSION"
        else -> "TRANSIENT_FAILURE"
    }
}
