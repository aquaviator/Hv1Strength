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
        val db = StrengthDatabase.getDatabase(
            applicationContext,
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
        )
        val repository = StrengthRepository(db.strengthDao(), applicationContext)
        val syncEngine = SyncEngineImpl(applicationContext, repository)
        
        return try {
            val result = syncEngine.synchronizeAll()
            if (result.isSuccess) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
