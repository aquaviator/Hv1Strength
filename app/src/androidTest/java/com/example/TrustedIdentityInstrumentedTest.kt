package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.core.sync.SyncIdentityResolution
import com.example.core.sync.resolveAuthenticatedSyncIdentity
import com.example.data.HumanIdentityResult
import com.example.data.UserProfile
import com.example.data.parseHumanIdentityResponse
import com.example.data.trustedAccountDeletionPayload
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrustedIdentityInstrumentedTest {
    private val humanA = "human_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

    @Test fun authoritativeResponseRequiresSchemaAndActiveStatus() {
        assertTrue(parseHumanIdentityResponse(mapOf("humanUserId" to humanA, "status" to "ACTIVE", "schemaVersion" to 1)) is HumanIdentityResult.Success)
        assertEquals(HumanIdentityResult.MalformedResponse,
            parseHumanIdentityResponse(mapOf("humanUserId" to humanA, "status" to "ACTIVE")))
        assertEquals(HumanIdentityResult.UnsupportedSchema,
            parseHumanIdentityResponse(mapOf("humanUserId" to humanA, "status" to "ACTIVE", "schemaVersion" to 2)))
    }

    @Test fun accountBSessionCannotReuseAccountAProfileOrBinding() {
        val profileA = UserProfile("uid-a", humanUserId = humanA, firebaseUid = "uid-a", authProvider = "google")
        val result = resolveAuthenticatedSyncIdentity("uid-b", profileA, true, true, "uid-b", humanA, "ACTIVE", 1)
        assertTrue(result is SyncIdentityResolution.Blocked)
    }

    @Test fun deletionPayloadCarriesNoClientAuthority() {
        val payload = trustedAccountDeletionPayload()
        assertEquals(0, payload.length())
        assertFalse(payload.has("humanUserId"))
        assertFalse(payload.has("firebaseUid"))
    }
}
