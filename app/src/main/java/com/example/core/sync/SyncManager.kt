package com.example.core.sync

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ManualSyncResult(
    val phase: String = "NEVER_CHECKED",
    val requestId: String? = null,
    val requestedAt: Long? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val lastSuccessfulCompletedAt: Long? = null,
    val downloaded: Int = 0,
    val uploaded: Int = 0,
    val attentionCount: Int = 0,
    val offline: Boolean = false,
    val deterministicAttention: Boolean = false,
    val reason: String? = null,
    val errorClassification: String? = null
)

data class SyncRunCounts(val downloaded: Int = 0, val uploaded: Int = 0, val attentionCount: Int = 0)

object SyncManager {
    private const val RESULT_PREFS = "sync_check_result"
    private val resultLock = Any()
    private var resultContext: Context? = null

    private val _manualResult = MutableStateFlow(ManualSyncResult())
    val manualResult: StateFlow<ManualSyncResult> = _manualResult
    private var currentRunDownloaded = 0
    private var currentRunUploaded = 0
    private var currentRunAttentionCount = 0

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
            requestedAt = prefs.getLong("requested_at", 0L).takeIf { it > 0 },
            startedAt = prefs.getLong("started_at", 0L).takeIf { it > 0 },
            completedAt = prefs.getLong("completed_at", 0L).takeIf { it > 0 },
            lastSuccessfulCompletedAt = prefs.getLong("last_successful_completed_at",
                prefs.getLong("last_successful_at", 0L)).takeIf { it > 0 },
            downloaded = prefs.getInt("downloaded", 0),
            uploaded = prefs.getInt("uploaded", 0),
            attentionCount = prefs.getInt("attention_count", 0),
            offline = prefs.getBoolean("offline", false),
            deterministicAttention = prefs.getBoolean("deterministic_attention", false),
            reason = prefs.getString("reason", null),
            errorClassification = prefs.getString("error_classification", null)
        )
    }

    fun beginManualCheck(context: Context, requestId: String, requestedAt: Long): Boolean = synchronized(resultLock) {
        initialize(context)
        if (_manualResult.value.phase == "QUEUED" || _manualResult.value.phase == "CHECKING") return@synchronized false
        persist(ManualSyncResult(phase = "QUEUED", requestId = requestId, requestedAt = requestedAt,
            lastSuccessfulCompletedAt = _manualResult.value.lastSuccessfulCompletedAt))
        currentRunDownloaded = 0
        currentRunUploaded = 0
        currentRunAttentionCount = 0
        true
    }

    fun startManualCheck(context: Context, requestId: String, startedAt: Long): Boolean = synchronized(resultLock) {
        initialize(context)
        val current = _manualResult.value
        if (current.requestId != requestId || current.phase !in setOf("QUEUED", "CHECKING")) return@synchronized false
        if (current.phase != "CHECKING" || current.startedAt == null) persist(current.copy(phase = "CHECKING", startedAt = startedAt))
        true
    }

    fun updateCurrentRunCounts(downloaded: Int, uploaded: Int, attentionCount: Int = 0) = synchronized(resultLock) {
        currentRunDownloaded = downloaded
        currentRunUploaded = uploaded
        currentRunAttentionCount = attentionCount
    }

    fun currentRunCounts(): SyncRunCounts = synchronized(resultLock) {
        SyncRunCounts(currentRunDownloaded, currentRunUploaded, currentRunAttentionCount)
    }

    fun completeManualCheck(context: Context, requestId: String, downloaded: Int, uploaded: Int,
                            attentionCount: Int, offline: Boolean, reason: String?, errorClassification: String?,
                            completedAt: Long) = synchronized(resultLock) {
        initialize(context)
        val current = _manualResult.value
        if (current.phase !in setOf("QUEUED", "CHECKING") || current.requestId != requestId) return@synchronized
        val phase = when {
            offline -> "OFFLINE"
            reason != null -> "FAILED"
            attentionCount > 0 -> "ATTENTION"
            downloaded > 0 || uploaded > 0 -> "UPDATED"
            else -> "UP_TO_DATE"
        }
        persist(current.copy(phase = phase, completedAt = completedAt, downloaded = downloaded,
            uploaded = uploaded, attentionCount = attentionCount, offline = offline,
            deterministicAttention = attentionCount > 0 && reason == null && !offline,
            reason = reason, errorClassification = errorClassification,
            lastSuccessfulCompletedAt = if (reason == null && !offline) completedAt else current.lastSuccessfulCompletedAt))
    }

    private fun persist(value: ManualSyncResult) {
        _manualResult.value = value
        resultContext?.getSharedPreferences(RESULT_PREFS, Context.MODE_PRIVATE)?.edit()?.apply {
            putString("phase", value.phase); putString("request_id", value.requestId)
            putLong("requested_at", value.requestedAt ?: 0L)
            putLong("started_at", value.startedAt ?: 0L); putLong("completed_at", value.completedAt ?: 0L)
            putLong("last_successful_completed_at", value.lastSuccessfulCompletedAt ?: 0L)
            putInt("downloaded", value.downloaded); putInt("uploaded", value.uploaded)
            putInt("attention_count", value.attentionCount); putBoolean("offline", value.offline)
            putBoolean("deterministic_attention", value.deterministicAttention)
            putString("reason", value.reason); putString("error_classification", value.errorClassification)
        }?.commit()
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
