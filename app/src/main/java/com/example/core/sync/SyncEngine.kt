package com.example.core.sync

import com.example.core.versioning.VersionedEntity
import kotlinx.coroutines.flow.StateFlow

/**
 * Defines strategies for resolving conflicts during synchronization.
 */
enum class ConflictResolutionStrategy {
    CLIENT_WINS,      // Overwrite remote server state with client state
    SERVER_WINS,      // Overwrite local state with remote server state
    MERGE_LATEST,     // Resolve by comparing updatedAt timestamps
    MANUAL_PROMPT     // Flag entity for manual user decision (Custom rule)
}

/**
 * Network connectivity state of the Sync Engine.
 */
enum class ConnectivityState {
    ONLINE,
    OFFLINE
}

/**
 * Structure of a Synchronization Batch.
 */
data class SyncBatch(
    val batchId: String,
    val entitiesToUpload: List<VersionedEntity>,
    val entitiesToDelete: List<String> // IDs of deleted records
)

/**
 * Core contract for the Sync Engine which decouples local data storage (Room) from Cloud database (Firestore).
 */
interface SyncEngine {
    val connectivity: StateFlow<ConnectivityState>
    val activeSyncing: StateFlow<Boolean>
    val pendingUploadsCount: StateFlow<Int>

    /**
     * Initiates a full bidirectional sync across all modules.
     */
    suspend fun synchronizeAll(): Result<Unit>

    /**
     * Enqueues a single entity for synchronization.
     */
    suspend fun enqueueEntity(entity: VersionedEntity): Result<Unit>

    /**
     * Forcefully runs conflict resolution for a specific entity ID.
     */
    suspend fun resolveConflict(
        entityId: String,
        strategy: ConflictResolutionStrategy
    ): Result<Unit>

    /**
     * Clears all local caches and stops all active upload/download background sync workers.
     */
    suspend fun resetSyncWorkers()
}
