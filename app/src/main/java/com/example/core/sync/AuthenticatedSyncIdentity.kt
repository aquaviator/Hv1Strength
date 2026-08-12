package com.example.core.sync

import com.example.data.ACTIVE_IDENTITY_STATUS
import com.example.data.SUPPORTED_IDENTITY_SCHEMA_VERSION
import com.example.data.UserProfile
import com.example.data.isValidAuthoritativeHumanId

internal data class AuthenticatedSyncIdentity(val firebaseUid: String, val humanUserId: String)

internal enum class SyncIdentityBlockReason {
    FIREBASE_USER_MISSING, PROFILE_MISSING, PROFILE_OFFLINE, PROFILE_FIREBASE_UID_MISSING,
    PROFILE_FIREBASE_UID_MISMATCH, ACTIVE_SESSION_MISMATCH, HUMAN_USER_ID_UNRESOLVED,
    UNSUPPORTED_SCHEMA
}

internal sealed interface SyncIdentityResolution {
    data class Ready(val identity: AuthenticatedSyncIdentity) : SyncIdentityResolution
    data class Blocked(val reason: SyncIdentityBlockReason) : SyncIdentityResolution
}

internal fun resolveAuthenticatedSyncIdentity(
    firebaseUid: String?, profile: UserProfile?, isPersistedGoogleSession: Boolean,
    isProfileHandoffComplete: Boolean, persistedActiveUserId: String?,
    persistedHumanUserId: String?, persistedIdentityStatus: String?, persistedSchemaVersion: Long?
): SyncIdentityResolution {
    if (firebaseUid.isNullOrBlank()) return SyncIdentityResolution.Blocked(SyncIdentityBlockReason.FIREBASE_USER_MISSING)
    if (profile == null) return SyncIdentityResolution.Blocked(SyncIdentityBlockReason.PROFILE_MISSING)
    if (profile.isOfflineUser) return SyncIdentityResolution.Blocked(SyncIdentityBlockReason.PROFILE_OFFLINE)
    if (profile.firebaseUid.isNullOrBlank()) return SyncIdentityResolution.Blocked(SyncIdentityBlockReason.PROFILE_FIREBASE_UID_MISSING)
    if (profile.firebaseUid != firebaseUid) return SyncIdentityResolution.Blocked(SyncIdentityBlockReason.PROFILE_FIREBASE_UID_MISMATCH)
    if (!isPersistedGoogleSession || !isProfileHandoffComplete || persistedActiveUserId != firebaseUid) {
        return SyncIdentityResolution.Blocked(SyncIdentityBlockReason.ACTIVE_SESSION_MISMATCH)
    }
    if (persistedSchemaVersion != SUPPORTED_IDENTITY_SCHEMA_VERSION) {
        return SyncIdentityResolution.Blocked(SyncIdentityBlockReason.UNSUPPORTED_SCHEMA)
    }
    if (persistedIdentityStatus != ACTIVE_IDENTITY_STATUS || !isValidAuthoritativeHumanId(persistedHumanUserId) ||
        profile.humanUserId != persistedHumanUserId) {
        return SyncIdentityResolution.Blocked(SyncIdentityBlockReason.HUMAN_USER_ID_UNRESOLVED)
    }
    return SyncIdentityResolution.Ready(AuthenticatedSyncIdentity(firebaseUid, requireNotNull(persistedHumanUserId)))
}
