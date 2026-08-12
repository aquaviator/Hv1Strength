package com.example

import com.example.data.*
import org.junit.Assert.*
import org.junit.Test

class AppCheckIdentityGateTest {
    private val humanId = "human_0123456789abcdef0123456789abcdef"

    @Test fun debugSelectsDebugProvider() = assertEquals(AppCheckProviderKind.DEBUG, appCheckProviderKind(true))
    @Test fun releaseSelectsPlayIntegrity() = assertEquals(AppCheckProviderKind.PLAY_INTEGRITY, appCheckProviderKind(false))
    @Test fun releaseNeverSelectsDebugProvider() = assertNotEquals(AppCheckProviderKind.DEBUG, appCheckProviderKind(false))
    @Test fun readyAppCheckAllowsIdentityRequest() = assertNull(appCheckIdentityGate(AppCheckInitializationState.READY))
    @Test fun unavailableAppCheckBlocksIdentityRequestTruthfully() = assertEquals("App Check is unavailable", appCheckIdentityGate(AppCheckInitializationState.UNAVAILABLE))
    @Test fun failedProviderInitializationBlocksIdentityRequest() = assertEquals("App Check initialization failed", appCheckIdentityGate(AppCheckInitializationState.FAILED))
    @Test fun firebaseSuccessCanStillFailTrustedIdentity() {
        assertEquals(HumanIdentityResult.MissingSchema, parseHumanIdentityResponse(mapOf("humanUserId" to humanId, "status" to "ACTIVE")))
    }
    @Test fun unsupportedSchemaFailsClosed() = assertEquals(HumanIdentityResult.UnsupportedSchema,
        parseHumanIdentityResponse(mapOf("humanUserId" to humanId,"status" to "ACTIVE","schemaVersion" to 2)))
    @Test fun schemaOneSucceeds() = assertTrue(parseHumanIdentityResponse(mapOf("humanUserId" to humanId,"status" to "ACTIVE","schemaVersion" to 1)) is HumanIdentityResult.Success)
    @Test fun missingSchemaReportsBackendContractUpdate() = assertTrue(HumanIdentityResult.MissingSchema.safeMessage().startsWith("BACKEND CONTRACT UPDATE REQUIRED"))
    @Test fun safeMessagesAndResultNamesContainNoIdentifiersOrTokens() {
        HumanIdentityResult.MissingSchema.safeMessage().also { assertFalse(it.contains(humanId)); assertFalse(it.contains("token", true)); assertFalse(it.contains("@")) }
        assertEquals("MISSING_SCHEMA", HumanIdentityResult.MissingSchema.safeResultName())
    }
}
