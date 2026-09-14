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
        
        val requestId = id.toString()
        return try {
            val result = syncEngine.synchronizeAll()
            val status = SyncManager.currentStatus.value
            val (downloaded, uploaded) = SyncManager.currentRunCounts()
            SyncManager.completeManualCheck(applicationContext, requestId,
                downloaded, uploaded,
                attention = status == "ItemsNeedReview" || status.contains("NeedsAttention"),
                offline = status == "WaitingForConnection",
                reason = result.exceptionOrNull()?.localizedMessage ?: SyncManager.lastError.value,
                completedAt = System.currentTimeMillis())
            if (result.isSuccess) {
                if (UnattendedSyncPolicy.workerShouldRetry(true, repository.getAllCommands())) Result.retry()
                else Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            SyncManager.completeManualCheck(applicationContext, requestId, 0, 0, false, false,
                e.localizedMessage ?: "Synchronization failed", System.currentTimeMillis())
            Result.retry()
        }
    }
}
