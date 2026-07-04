package com.example.core.versioning

/**
 * Interface implementing progressive versioning and sync tracking for all syncable domain models.
 */
interface VersionedEntity {
    val globalId: String
    val humanUserId: String
    val createdAt: Long
    val updatedAt: Long
    val deletedAt: Long?
    val revision: Long
    val syncStatus: String
    val lastSyncedAt: Long?
    val conflictState: String?
    val originDeviceId: String
}
