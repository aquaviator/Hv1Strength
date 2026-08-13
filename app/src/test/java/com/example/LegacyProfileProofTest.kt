package com.example

import com.example.data.LegacyProfileProof
import com.example.data.UserProfile
import com.example.data.classifyLegacyProfileProof
import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyProfileProofTest {
    private val uid = "firebase-uid-current"
    private val human = "human_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

    private fun profile(firebaseUid: String? = null, humanId: String = "human_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb") =
        UserProfile(id = "legacy", email = "same@example.test", displayName = "Athlete",
            authProvider = "google", isOfflineUser = false, humanUserId = humanId, firebaseUid = firebaseUid)

    @Test fun matchingAuthoritativeIsRecognized() = assertEquals(LegacyProfileProof.MATCHING_AUTHORITATIVE,
        classifyLegacyProfileProof(uid, human, profile(humanId = human), 2, 1))

    @Test fun emptyPlaceholderNeedsNoOwnershipMigration() = assertEquals(LegacyProfileProof.EMPTY_PLACEHOLDER,
        classifyLegacyProfileProof(uid, human, profile(), 0, 1))

    @Test fun storedMatchingFirebaseUidIsVerified() = assertEquals(LegacyProfileProof.VERIFIED_LEGACY_SAME_ACCOUNT,
        classifyLegacyProfileProof(uid, human, profile(uid), 4, 1))

    @Test fun mismatchedFirebaseUidIsDifferentAccount() = assertEquals(LegacyProfileProof.MEANINGFUL_DIFFERENT_ACCOUNT,
        classifyLegacyProfileProof(uid, human, profile("other-uid"), 4, 1))

    @Test fun emailOnlyEvidenceIsRejected() = assertEquals(LegacyProfileProof.UNVERIFIED_LEGACY,
        classifyLegacyProfileProof(uid, human, profile(null), 4, 1))

    @Test fun unsignedBackupCannotEstablishOwnership() = assertEquals(LegacyProfileProof.UNVERIFIED_LEGACY,
        classifyLegacyProfileProof(uid, human, profile(uid), 4, 1, restoredFromUnsignedBackup = true))

    @Test fun multipleProfilesAreAmbiguous() = assertEquals(LegacyProfileProof.AMBIGUOUS_MULTIPLE_PROFILES,
        classifyLegacyProfileProof(uid, human, profile(uid), 4, 2))
}
