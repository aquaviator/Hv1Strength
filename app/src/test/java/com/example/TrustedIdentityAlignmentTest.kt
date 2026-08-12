package com.example

import com.example.core.identity.HumanUserIdGenerator
import com.example.core.sync.SyncIdentityBlockReason
import com.example.core.sync.SyncIdentityResolution
import com.example.core.sync.resolveAuthenticatedSyncIdentity
import com.example.core.sync.SyncEngineImpl
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrustedIdentityAlignmentTest {
    private val uid = "firebase-uid-a"
    private val humanId = "human_0123456789abcdef0123456789abcdef"
    private val identity = AuthoritativeHumanIdentity(humanId, "ACTIVE", 1)

    @Test fun successfulResolutionRequiresCompleteBackendResponse() {
        val result = parseHumanIdentityResponse(mapOf("humanUserId" to humanId, "status" to "ACTIVE", "schemaVersion" to 1))
            as HumanIdentityResult.Success
        assertEquals(identity, result.identity)
    }

    @Test fun malformedMissingInvalidAndUnsupportedResponsesFailClosed() {
        assertEquals(HumanIdentityResult.MalformedResponse, parseHumanIdentityResponse(null))
        assertEquals(HumanIdentityResult.MalformedResponse, parseHumanIdentityResponse(mapOf("status" to "ACTIVE", "schemaVersion" to 1)))
        assertEquals(HumanIdentityResult.MalformedResponse, parseHumanIdentityResponse(mapOf("humanUserId" to "human_bad!", "status" to "ACTIVE", "schemaVersion" to 1)))
        assertEquals(HumanIdentityResult.MissingSchema, parseHumanIdentityResponse(mapOf("humanUserId" to humanId, "status" to "ACTIVE")))
        assertEquals(HumanIdentityResult.UnsupportedSchema, parseHumanIdentityResponse(mapOf("humanUserId" to humanId, "status" to "ACTIVE", "schemaVersion" to 2)))
    }

    @Test fun clientDoesNotCallBackendWhenUnauthenticated() = runBlocking {
        var called = false
        val client = FirebaseHumanIdentityClient({ false }, { called = true; emptyMap<String, Any>() })
        assertEquals(HumanIdentityResult.Unauthenticated, client.ensureHumanIdentity())
        assertFalse(called)
    }

    @Test fun backendAndNetworkFailuresAreExplicit() = runBlocking {
        assertEquals(HumanIdentityResult.IdentityConflict,
            mapHumanIdentityBackendError(FirebaseFunctionsException.Code.FAILED_PRECONDITION))
        assertEquals(HumanIdentityResult.NetworkError,
            FirebaseHumanIdentityClient({ true }, { throw IOException("offline") }).ensureHumanIdentity())
        assertEquals(HumanIdentityResult.NetworkError,
            FirebaseHumanIdentityClient({ true }, { delay(30); emptyMap<String, Any>() }, 1).ensureHumanIdentity())
    }

    @Test fun handoffPreservesOfflineProfileDataAfterResolution() {
        val offline = UserProfile("offline", preferredUnits = "imperial", trainingExperience = "Advanced",
            humanUserId = "human_offlineusr", isOfflineUser = true)
        val result = resolveAuthoritativeProfileHandoff(uid, identity, "Athlete", null, null,
            null, offline, null, 100) as ProfileHandoffResolution.Ready
        assertEquals(humanId, result.profile.humanUserId)
        assertEquals("imperial", result.profile.preferredUnits)
        assertEquals("Advanced", result.profile.trainingExperience)
        assertTrue(offline.isOfflineUser)
    }

    @Test fun handoffRejectsExistingAndPersistedIdentityConflicts() {
        val legacy = profile(HumanUserIdGenerator.deriveLegacyHumanIdForMigration(uid))
        assertEquals(ProfileHandoffResolution.IdentityConflict,
            resolveAuthoritativeProfileHandoff(uid, identity, null, null, null, legacy, null, null))
        assertEquals(ProfileHandoffResolution.IdentityConflict,
            resolveAuthoritativeProfileHandoff(uid, identity, null, null, null, null, null,
                "human_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
    }

    @Test fun validRestorationRequiresUidProfileBindingStatusAndSchemaAgreement() {
        assertTrue(sync(profile(humanId)) is SyncIdentityResolution.Ready)
        assertBlocked(sync(null), SyncIdentityBlockReason.PROFILE_MISSING)
        assertBlocked(sync(profile(humanId), firebaseUid = "uid-b"), SyncIdentityBlockReason.PROFILE_FIREBASE_UID_MISMATCH)
        assertBlocked(sync(profile("human_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")), SyncIdentityBlockReason.HUMAN_USER_ID_UNRESOLVED)
        assertBlocked(sync(profile(humanId), persistedId = null), SyncIdentityBlockReason.HUMAN_USER_ID_UNRESOLVED)
        assertBlocked(sync(profile(humanId), schema = 2), SyncIdentityBlockReason.UNSUPPORTED_SCHEMA)
        assertBlocked(sync(profile(humanId), handoff = false), SyncIdentityBlockReason.ACTIVE_SESSION_MISMATCH)
    }

    @Test fun previousAccountCannotAuthorizeNewFirebaseSession() {
        assertBlocked(sync(profile(humanId), firebaseUid = "firebase-uid-b"), SyncIdentityBlockReason.PROFILE_FIREBASE_UID_MISMATCH)
        assertBlocked(sync(profile(humanId), activeUser = "firebase-uid-b"), SyncIdentityBlockReason.ACTIVE_SESSION_MISMATCH)
    }

    @Test fun deletionPayloadContainsNoClientIdentityAuthority() {
        val payload = trustedAccountDeletionPayload()
        assertEquals(0, payload.length())
        assertFalse(payload.has("humanUserId"))
        assertFalse(payload.has("firebaseUid"))
    }

    @Test fun blockedSyncPreservesQueuedCommandState() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, StrengthDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val repository = StrengthRepository(database.strengthDao(), null)
            database.strengthDao().enqueueCommand(CommandQueueEntity(
                commandId = "cmd-a", humanUserId = humanId, commandType = "WorkoutUpdated",
                entityType = "WORKOUT_SESSION", entityGlobalId = "session-a", payloadJson = "{}"
            ))
            val before = repository.getPendingCommands(Long.MAX_VALUE).single()
            val result = SyncEngineImpl(context, repository) {
                SyncIdentityResolution.Blocked(SyncIdentityBlockReason.HUMAN_USER_ID_UNRESOLVED)
            }.synchronizeAll()
            val after = repository.getPendingCommands(Long.MAX_VALUE).single()
            assertTrue(result.isSuccess)
            assertEquals(before, after)
            assertEquals("PENDING", after.status)
            assertEquals(0, after.attempts)
            assertNull(after.lastAttemptAt)
            assertNull(after.nextRetryAt)
        } finally { database.close() }
    }

    private fun profile(id: String) = UserProfile(uid, humanUserId = id, firebaseUid = uid,
        authProvider = "google", isOfflineUser = false)

    private fun sync(profile: UserProfile?, firebaseUid: String? = uid, persistedId: String? = humanId,
        schema: Long? = 1, handoff: Boolean = true, activeUser: String? = uid) =
        resolveAuthenticatedSyncIdentity(firebaseUid, profile, true, handoff, activeUser,
            persistedId, "ACTIVE", schema)

    private fun assertBlocked(value: SyncIdentityResolution, reason: SyncIdentityBlockReason) {
        assertEquals(reason, (value as SyncIdentityResolution.Blocked).reason)
    }
}
