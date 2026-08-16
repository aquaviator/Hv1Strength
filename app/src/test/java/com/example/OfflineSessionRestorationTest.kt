package com.example

import com.example.data.ACTIVE_IDENTITY_STATUS
import com.example.data.SUPPORTED_IDENTITY_SCHEMA_VERSION
import com.example.data.HumanIdentityResult
import com.example.data.UserProfile
import com.example.data.allowsVerifiedOfflineRestore
import com.example.data.canRestoreVerifiedOfflineSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineSessionRestorationTest {
    private val uid = "firebase-user"
    private val human = "human_12345678901234567890123456789012"
    private val profile = UserProfile(
        id = uid,
        firebaseUid = uid,
        humanUserId = human,
        authProvider = "google",
        isOfflineUser = false
    )

    @Test fun matchingPreviouslyVerifiedAccountCanRestoreWithoutConnectivity() {
        assertTrue(canRestoreVerifiedOfflineSession(
            uid, profile, uid, human, ACTIVE_IDENTITY_STATUS,
            SUPPORTED_IDENTITY_SCHEMA_VERSION, true
        ))
    }

    @Test fun incompleteOrMismatchedTrustNeverUsesOfflineRestoration() {
        assertFalse(canRestoreVerifiedOfflineSession(
            uid, profile, uid, human, ACTIVE_IDENTITY_STATUS,
            SUPPORTED_IDENTITY_SCHEMA_VERSION, false
        ))
        assertFalse(canRestoreVerifiedOfflineSession(
            uid, profile.copy(firebaseUid = "different"), uid, human, ACTIVE_IDENTITY_STATUS,
            SUPPORTED_IDENTITY_SCHEMA_VERSION, true
        ))
        assertFalse(canRestoreVerifiedOfflineSession(
            uid, profile, uid, "human_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            ACTIVE_IDENTITY_STATUS, SUPPORTED_IDENTITY_SCHEMA_VERSION, true
        ))
        assertFalse(canRestoreVerifiedOfflineSession(
            uid, profile, uid, human, ACTIVE_IDENTITY_STATUS,
            SUPPORTED_IDENTITY_SCHEMA_VERSION + 1, true
        ))
    }

    @Test fun onlyTemporaryServiceFailuresMayUseTheVerifiedCache() {
        assertTrue(HumanIdentityResult.NetworkError.allowsVerifiedOfflineRestore())
        assertTrue(HumanIdentityResult.BackendError.allowsVerifiedOfflineRestore())
        assertFalse(HumanIdentityResult.IdentityConflict.allowsVerifiedOfflineRestore())
        assertFalse(HumanIdentityResult.IdentityDisabled.allowsVerifiedOfflineRestore())
        assertFalse(HumanIdentityResult.IdentityUnavailable.allowsVerifiedOfflineRestore())
        assertFalse(HumanIdentityResult.UnsupportedSchema.allowsVerifiedOfflineRestore())
        assertFalse(HumanIdentityResult.MalformedResponse.allowsVerifiedOfflineRestore())
        assertFalse(HumanIdentityResult.Unauthenticated.allowsVerifiedOfflineRestore())
    }
}
