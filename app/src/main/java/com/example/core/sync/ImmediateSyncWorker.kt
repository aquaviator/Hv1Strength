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
        
        return try {
            val result = syncEngine.synchronizeAll()
            if (result.isSuccess) {
                if (UnattendedSyncPolicy.workerShouldRetry(true, repository.getAllCommands())) Result.retry()
                else Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
