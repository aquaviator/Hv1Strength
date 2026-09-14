package com.example.core.sync

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ManualSyncResult(
    val phase: String = "NEVER_CHECKED",
    val requestId: String? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val downloaded: Int = 0,
    val uploaded: Int = 0,
    val reason: String? = null,
    val lastSuccessfulAt: Long? = null
)

object SyncManager {
    private const val RESULT_PREFS = "sync_check_result"
    private val resultLock = Any()
    private var resultContext: Context? = null

    private val _manualResult = MutableStateFlow(ManualSyncResult())
    val manualResult: StateFlow<ManualSyncResult> = _manualResult
    private var currentRunDownloaded = 0
    private var currentRunUploaded = 0

    private val _currentStatus = MutableStateFlow("Idle")
    val currentStatus: StateFlow<String> = _currentStatus

    private val _pendingUploads = MutableStateFlow(0)
    val pendingUploads: StateFlow<Int> = _pendingUploads

    private val _pendingDownloads = MutableStateFlow(0)
    val pendingDownloads: StateFlow<Int> = _pendingDownloads

    private val _queueSize = MutableStateFlow(0)
    val queueSize: StateFlow<Int> = _queueSize

    private val _lastSync = MutableStateFlow<Long?>(null)
    val lastSync: StateFlow<Long?> = _lastSync

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    private val _conflictCount = MutableStateFlow(0)
    val conflictCount: StateFlow<Int> = _conflictCount

    private val _lastSuccessfulUpload = MutableStateFlow<Long?>(null)
    val lastSuccessfulUpload: StateFlow<Long?> = _lastSuccessfulUpload

    private val _lastSuccessfulDownload = MutableStateFlow<Long?>(null)
    val lastSuccessfulDownload: StateFlow<Long?> = _lastSuccessfulDownload

    private val _parentWarnings = MutableStateFlow<List<String>>(emptyList())
    val parentWarnings: StateFlow<List<String>> = _parentWarnings

    fun initialize(context: Context) = synchronized(resultLock) {
        if (resultContext != null) return@synchronized
        resultContext = context.applicationContext
        val prefs = context.getSharedPreferences(RESULT_PREFS, Context.MODE_PRIVATE)
        _manualResult.value = ManualSyncResult(
            phase = prefs.getString("phase", "NEVER_CHECKED") ?: "NEVER_CHECKED",
            requestId = prefs.getString("request_id", null),
            startedAt = prefs.getLong("started_at", 0L).takeIf { it > 0 },
            completedAt = prefs.getLong("completed_at", 0L).takeIf { it > 0 },
            downloaded = prefs.getInt("downloaded", 0),
            uploaded = prefs.getInt("uploaded", 0),
            reason = prefs.getString("reason", null),
            lastSuccessfulAt = prefs.getLong("last_successful_at", 0L).takeIf { it > 0 }
        )
    }

    fun beginManualCheck(context: Context, requestId: String, startedAt: Long): Boolean = synchronized(resultLock) {
        initialize(context)
        if (_manualResult.value.phase == "CHECKING") return@synchronized false
        persist(ManualSyncResult(phase = "CHECKING", requestId = requestId, startedAt = startedAt,
            lastSuccessfulAt = _manualResult.value.lastSuccessfulAt))
        currentRunDownloaded = 0
        currentRunUploaded = 0
        true
    }

    fun updateCurrentRunCounts(downloaded: Int, uploaded: Int) = synchronized(resultLock) {
        currentRunDownloaded = downloaded
        currentRunUploaded = uploaded
    }

    fun currentRunCounts(): Pair<Int, Int> = synchronized(resultLock) { currentRunDownloaded to currentRunUploaded }

    fun completeManualCheck(context: Context, requestId: String, downloaded: Int, uploaded: Int,
                            attention: Boolean, offline: Boolean, reason: String?, completedAt: Long) = synchronized(resultLock) {
        initialize(context)
        val current = _manualResult.value
        if (current.phase != "CHECKING" || current.requestId != requestId) return@synchronized
        val phase = when {
            offline -> "OFFLINE"
            reason != null -> "FAILED"
            attention -> "ATTENTION"
            downloaded > 0 || uploaded > 0 -> "UPDATED"
            else -> "UP_TO_DATE"
        }
        persist(current.copy(phase = phase, completedAt = completedAt, downloaded = downloaded,
            uploaded = uploaded, reason = reason, lastSuccessfulAt = if (reason == null && !offline) completedAt else current.lastSuccessfulAt))
    }

    private fun persist(value: ManualSyncResult) {
        _manualResult.value = value
        resultContext?.getSharedPreferences(RESULT_PREFS, Context.MODE_PRIVATE)?.edit()?.apply {
            putString("phase", value.phase); putString("request_id", value.requestId)
            putLong("started_at", value.startedAt ?: 0L); putLong("completed_at", value.completedAt ?: 0L)
            putInt("downloaded", value.downloaded); putInt("uploaded", value.uploaded)
            putString("reason", value.reason); putLong("last_successful_at", value.lastSuccessfulAt ?: 0L)
        }?.apply()
    }

    fun updateStatus(status: String) {
        _currentStatus.value = status
    }

    fun updatePendingUploads(count: Int) {
        _pendingUploads.value = count
    }

    fun updatePendingDownloads(count: Int) {
        _pendingDownloads.value = count
    }

    fun updateQueueSize(size: Int) {
        _queueSize.value = size
    }

    fun updateLastSync(timestamp: Long?) {
        _lastSync.value = timestamp
    }

    fun updateLastError(error: String?) {
        _lastError.value = error
    }

    fun updateConflictCount(count: Int) {
        _conflictCount.value = count
    }

    fun updateLastSuccessfulUpload(timestamp: Long?) {
        _lastSuccessfulUpload.value = timestamp
    }

    fun updateLastSuccessfulDownload(timestamp: Long?) {
        _lastSuccessfulDownload.value = timestamp
    }

    fun addParentWarning(warning: String) {
        if (!_parentWarnings.value.contains(warning)) {
            _parentWarnings.value = _parentWarnings.value + warning
        }
    }

    fun clearParentWarnings() {
        _parentWarnings.value = emptyList()
    }
}
