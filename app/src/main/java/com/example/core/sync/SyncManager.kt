package com.example.core.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object SyncManager {
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
