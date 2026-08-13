package com.example.data

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.UUID

sealed class AuthState {
    object Initial : AuthState()
    object Loading : AuthState()
    data class Authenticated(val profile: UserProfile) : AuthState()
    object Offline : AuthState()
    data class LegacyUpgradeRequired(val totals: LegacyOwnershipTotals, val backupCompleted: Boolean = false) : AuthState()
    data class LegacyUpgradeRunning(val stage: String) : AuthState()
    data class LegacyUpgradeHandoffRequired(val message: String) : AuthState()
    data class Error(
        val message: String,
        val kind: AuthErrorKind = AuthErrorKind.UNKNOWN,
        val canContinueOffline: Boolean = false
    ) : AuthState()
}

enum class AuthErrorKind { PROFILE_CONFLICT, APP_CHECK, NETWORK, TRUSTED_IDENTITY, UNKNOWN }

internal sealed interface ProfileHandoffResolution {
    data class Ready(val profile: UserProfile) : ProfileHandoffResolution
    data object IdentityConflict : ProfileHandoffResolution
}

internal enum class LocalProfileDisposition { MATCHING, EMPTY_PLACEHOLDER, MEANINGFUL_DATA, AMBIGUOUS }

/** Security classification for upgrading ownership created by historical Strength builds. */
internal enum class LegacyProfileProof {
    MATCHING_AUTHORITATIVE,
    EMPTY_PLACEHOLDER,
    VERIFIED_LEGACY_SAME_ACCOUNT,
    UNVERIFIED_LEGACY,
    MEANINGFUL_DIFFERENT_ACCOUNT,
    AMBIGUOUS_MULTIPLE_PROFILES
}

internal fun classifyLegacyProfileProof(
    currentFirebaseUid: String,
    authoritativeHumanUserId: String,
    legacyProfile: UserProfile?,
    meaningfulRecordCount: Int,
    otherProfileCount: Int,
    restoredFromUnsignedBackup: Boolean = false
): LegacyProfileProof {
    if (otherProfileCount > 1) return LegacyProfileProof.AMBIGUOUS_MULTIPLE_PROFILES
    if (meaningfulRecordCount == 0) return LegacyProfileProof.EMPTY_PLACEHOLDER
    if (legacyProfile?.humanUserId == authoritativeHumanUserId) {
        return LegacyProfileProof.MATCHING_AUTHORITATIVE
    }
    // firebaseUid is app-private historical authentication provenance. Email, googleUserId,
    // unsigned backups, names and other user-editable fields deliberately do not qualify.
    val storedUid = legacyProfile?.firebaseUid
    if (!restoredFromUnsignedBackup && storedUid != null) {
        return if (storedUid == currentFirebaseUid) LegacyProfileProof.VERIFIED_LEGACY_SAME_ACCOUNT
        else LegacyProfileProof.MEANINGFUL_DIFFERENT_ACCOUNT
    }
    return LegacyProfileProof.UNVERIFIED_LEGACY
}

internal fun classifyLocalProfileHandoff(
    authoritativeHumanUserId: String,
    existingProfile: UserProfile?,
    offlineProfile: UserProfile?,
    persistedHumanUserId: String?,
    ownership: LocalOwnershipSummary
): LocalProfileDisposition {
    val localIds = listOfNotNull(existingProfile?.humanUserId, offlineProfile?.humanUserId, persistedHumanUserId)
        .filter { it.isNotBlank() && it != authoritativeHumanUserId }
        .distinct()
    if (localIds.isEmpty()) return LocalProfileDisposition.MATCHING
    if (localIds.size > 1 || ownership.otherProfileCount > 1) return LocalProfileDisposition.AMBIGUOUS
    return if (ownership.meaningfulRecordCount == 0) LocalProfileDisposition.EMPTY_PLACEHOLDER
    else LocalProfileDisposition.MEANINGFUL_DATA
}

internal fun appCheckIdentityGate(state: com.example.AppCheckInitializationState): String? = when (state) {
    com.example.AppCheckInitializationState.READY -> null
    com.example.AppCheckInitializationState.UNAVAILABLE -> "App Check is unavailable"
    com.example.AppCheckInitializationState.FAILED -> "App Check initialization failed"
}

internal fun resolveAuthoritativeProfileHandoff(
    firebaseUid: String,
    identity: AuthoritativeHumanIdentity,
    displayName: String?, email: String?, photoUrl: String?,
    existingProfile: UserProfile?, offlineProfile: UserProfile?,
    persistedAuthenticatedHumanUserId: String?,
    nowMillis: Long = System.currentTimeMillis()
): ProfileHandoffResolution {
    if (!isValidAuthoritativeHumanId(identity.humanUserId) ||
        identity.schemaVersion != SUPPORTED_IDENTITY_SCHEMA_VERSION) return ProfileHandoffResolution.IdentityConflict
    val existingAuthenticatedId = existingProfile?.takeIf {
        !it.isOfflineUser && it.firebaseUid == firebaseUid
    }?.humanUserId?.takeIf { it.isNotBlank() }
    val persistedId = persistedAuthenticatedHumanUserId?.takeIf { it.isNotBlank() }
    if ((existingAuthenticatedId != null && existingAuthenticatedId != identity.humanUserId) ||
        (persistedId != null && persistedId != identity.humanUserId)) return ProfileHandoffResolution.IdentityConflict
    val safe = existingProfile ?: offlineProfile
    return ProfileHandoffResolution.Ready(UserProfile(
        id = firebaseUid, googleUserId = firebaseUid, email = email,
        displayName = displayName ?: email?.substringBefore("@") ?: "Google User",
        photoUrl = photoUrl, authProvider = "google", lastLoginAt = nowMillis,
        humanUserId = identity.humanUserId, firebaseUid = firebaseUid, isOfflineUser = false,
        dateOfBirth = safe?.dateOfBirth, sex = safe?.sex,
        trainingExperience = safe?.trainingExperience, heightCm = safe?.heightCm,
        preferredUnits = safe?.preferredUnits ?: "metric",
        createdAt = existingProfile?.createdAt ?: nowMillis
    ))
}

internal fun trustedAccountDeletionPayload() = org.json.JSONObject()

class AuthRepository(
    private val context: Context,
    private val strengthRepository: StrengthRepository,
    private val scope: CoroutineScope,
    private val identityClient: HumanIdentityClient = FirebaseHumanIdentityClient()
) {
    private val TAG = "AuthRepository"
    private val prefs = context.getSharedPreferences("strength_settings", Context.MODE_PRIVATE)
    
    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState

    private var firebaseAuth: FirebaseAuth? = null
    @Volatile private var pendingLegacyPlan: LegacyOwnershipPlan? = null
    private val legacyUpgradeMutex = kotlinx.coroutines.sync.Mutex()

    init {
        Log.i(TAG, "Initializing AuthRepository. isFirebaseConfigured=${com.example.HumanStrengthApplication.isFirebaseConfigured}")
        if (com.example.HumanStrengthApplication.isFirebaseConfigured) {
            try {
                Log.d(TAG, "Attempting to get FirebaseAuth instance...")
                firebaseAuth = FirebaseAuth.getInstance()
                Log.i(TAG, "FirebaseAuth instance obtained successfully.")
            } catch (e: Exception) {
                Log.w(TAG, "Firebase Auth not initialized. Falling back to offline-first Google profile management.", e)
            }
        } else {
            Log.w(TAG, "Firebase is not configured. Operating in offline fallback mode.")
        }
        
        Log.i(TAG, "Launching legacy profile clean-up coroutine...")
        // Clean legacy profile placeholder names from Room
        scope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Fetching offline profile from Room...")
                val offlineProfile = strengthRepository.getUserProfile("offline")
                Log.d(TAG, "Offline profile fetched: present=${offlineProfile != null}")
                if (offlineProfile != null && (offlineProfile.displayName == "Jane Doe" || offlineProfile.displayName == "John Doe")) {
                    val updatedOfflineProfile = offlineProfile.copy(
                        displayName = "Offline User",
                        updatedAt = System.currentTimeMillis()
                    )
                    strengthRepository.insertUserProfile(updatedOfflineProfile)
                    Log.i(TAG, "Successfully repaired legacy offline user profile name from ${offlineProfile.displayName} to Offline User.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning legacy profiles", e)
            }
        }
        
        Log.i(TAG, "Restoring session on app startup...")
        restoreSession()
    }

    private suspend fun offerVerifiedLegacyUpgrade(
        firebaseUid: String,
        identity: AuthoritativeHumanIdentity,
        legacy: UserProfile,
        displayName: String?, email: String?, photoUrl: String?,
        ownership: LocalOwnershipSummary
    ): Boolean {
        val proof = classifyLegacyProfileProof(firebaseUid, identity.humanUserId, legacy,
            ownership.meaningfulRecordCount, ownership.otherProfileCount)
        if (proof != LegacyProfileProof.VERIFIED_LEGACY_SAME_ACCOUNT) return false
        com.example.core.sync.SyncScheduler.cancelCloudSync(context)
        val target = legacy.copy(
            id = firebaseUid, googleUserId = firebaseUid, firebaseUid = firebaseUid,
            humanUserId = identity.humanUserId, isOfflineUser = false, authProvider = "google",
            displayName = displayName ?: legacy.displayName, email = email ?: legacy.email,
            photoUrl = photoUrl ?: legacy.photoUrl, lastLoginAt = System.currentTimeMillis()
        )
        pendingLegacyPlan = strengthRepository.dao.prepareVerifiedLegacyMigration(
            legacy.id, firebaseUid, identity.humanUserId, target
        )
        _authState.value = AuthState.LegacyUpgradeRequired(requireNotNull(pendingLegacyPlan).totals)
        return true
    }

    suspend fun markLegacyBackupCompleted() {
        val current = _authState.value as? AuthState.LegacyUpgradeRequired ?: return
        _authState.value = current.copy(backupCompleted = true)
    }

    suspend fun updateVerifiedLegacyAndContinue() = legacyUpgradeMutex.withLock {
        val plan = pendingLegacyPlan ?: run {
            _authState.value = AuthState.Error("The verified upgrade must be checked again before it can start.", AuthErrorKind.PROFILE_CONFLICT, true)
            return@withLock
        }
        if (_authState.value !is AuthState.LegacyUpgradeRequired) return@withLock
        _authState.value = AuthState.LegacyUpgradeRunning("Updating local ownership")
        try {
            strengthRepository.dao.commitVerifiedLegacyMigration(plan)
            pendingLegacyPlan = null
            completeLegacyMigrationHandoff()
        } catch (e: Exception) {
            Log.e(TAG, "stage=legacy_upgrade result=FAILED_BEFORE_COMMIT")
            _authState.value = AuthState.Error("The local update did not complete. Your existing data is unchanged.", AuthErrorKind.PROFILE_CONFLICT, true)
        }
    }

    private suspend fun completeLegacyMigrationHandoff() {
        val journal = strengthRepository.dao.getMigrationState()
            ?: throw IllegalStateException("Migration handoff state is missing")
        require(journal.phase == "ROOM_COMMITTED" || journal.phase == "HANDOFF_REQUIRED")
        val profile = requireNotNull(strengthRepository.getUserProfile(journal.sourceProfileId))
        require(profile.humanUserId == journal.targetHumanUserId && profile.firebaseUid == journal.sourceProfileId)
        require(strengthRepository.dao.countUploadableLegacyCommands(journal.sourceHumanUserId) == 0)
        try {
            strengthRepository.dao.updateMigrationPhase("HANDOFF_REQUIRED", System.currentTimeMillis())
            persistGoogleAuthentication(profile, SUPPORTED_IDENTITY_SCHEMA_VERSION)
            strengthRepository.dao.allOccurrencesForMigrationHandoff(profile.id).forEach {
                com.example.planner.PlannerReminderScheduler.schedule(context, it)
            }
            strengthRepository.dao.updateMigrationPhase("COMPLETE", System.currentTimeMillis())
            _authState.value = AuthState.Authenticated(profile)
            com.example.core.sync.SyncScheduler.scheduleImmediate(context)
            com.example.core.sync.SyncScheduler.schedulePeriodic(context)
        } catch (e: Exception) {
            Log.e(TAG, "stage=legacy_handoff result=RETRY_REQUIRED")
            strengthRepository.dao.updateMigrationPhase("HANDOFF_REQUIRED", System.currentTimeMillis())
            com.example.core.sync.SyncScheduler.cancelCloudSync(context)
            _authState.value = AuthState.LegacyUpgradeHandoffRequired("Your local data is safe. Finishing account setup can be retried.")
        }
    }

    suspend fun retryLegacyMigrationHandoff() = legacyUpgradeMutex.withLock {
        _authState.value = AuthState.LegacyUpgradeRunning("Finishing account setup")
        completeLegacyMigrationHandoff()
    }

    private fun restoreSession() {
        if (com.example.HumanStrengthApplication.isFirebaseConfigured) {
            _authState.value = AuthState.Loading
            scope.launch(Dispatchers.IO) {
                try {
                    val firebaseUser = firebaseAuth?.currentUser
                    if (firebaseUser == null) {
                        clearPersistedAuthentication()
                        _authState.value = AuthState.Initial
                        return@launch
                    }

                    val userId = firebaseUser.uid
                    val migrationState = strengthRepository.dao.getMigrationState()
                    if (migrationState?.phase == "ROOM_COMMITTED" || migrationState?.phase == "HANDOFF_REQUIRED") {
                        if (migrationState.sourceProfileId != userId) {
                            com.example.core.sync.SyncScheduler.cancelCloudSync(context)
                            _authState.value = AuthState.Error("A local account update is awaiting the verified account that started it.", AuthErrorKind.PROFILE_CONFLICT, true)
                        } else completeLegacyMigrationHandoff()
                        return@launch
                    }
                    if (prefs.getString("auth_active_user_id", null)?.let { it != userId } == true) {
                        clearAuthoritativeIdentityState()
                    }
                    prefs.edit().putBoolean("auth_profile_handoff_complete", false).apply()
                    appCheckIdentityGate(com.example.HumanStrengthApplication.appCheckInitializationState)?.let { message ->
                        Log.e(TAG, "stage=identity_gate result=APP_CHECK_UNAVAILABLE")
                        clearAuthoritativeIdentityState()
                        _authState.value = AuthState.Error(message, AuthErrorKind.APP_CHECK)
                        return@launch
                    }
                    Log.i(TAG, "stage=identity_request result=STARTED")
                    val identityResult = identityClient.ensureHumanIdentity()
                    Log.i(TAG, "stage=identity_request result=${identityResult.safeResultName()}")
                    val identity = (identityResult as? HumanIdentityResult.Success)?.identity
                    if (identity == null) {
                        clearAuthoritativeIdentityState()
                        _authState.value = AuthState.Error(identityResult.safeMessage(), AuthErrorKind.TRUSTED_IDENTITY)
                        return@launch
                    }
                    val existingProfile = strengthRepository.getUserProfile(userId)
                    val offlineProfile = strengthRepository.getUserProfile("offline")
                    val persistedHumanId = prefs.getString("auth_human_user_id", null)
                    val mismatchedProfile = existingProfile?.takeIf { it.humanUserId.isNotBlank() && it.humanUserId != identity.humanUserId }
                        ?: offlineProfile?.takeIf { it.humanUserId.isNotBlank() && it.humanUserId != identity.humanUserId }
                    val ownership = mismatchedProfile?.let {
                        strengthRepository.inspectLocalOwnership(it.id, it.humanUserId, identity.humanUserId)
                    } ?: LocalOwnershipSummary(0, strengthRepository.dao.countOtherProfiles(identity.humanUserId))
                    val disposition = classifyLocalProfileHandoff(
                        identity.humanUserId, existingProfile, offlineProfile, persistedHumanId, ownership
                    )
                    Log.i(TAG, "stage=local_profile result=$disposition")
                    if (disposition == LocalProfileDisposition.MEANINGFUL_DATA || disposition == LocalProfileDisposition.AMBIGUOUS) {
                        if (disposition == LocalProfileDisposition.MEANINGFUL_DATA && mismatchedProfile != null &&
                            offerVerifiedLegacyUpgrade(userId, identity, mismatchedProfile, firebaseUser.displayName,
                                firebaseUser.email, firebaseUser.photoUrl?.toString(), ownership)) return@launch
                        _authState.value = AuthState.Error(
                            "Sign-in succeeded, but this device contains data belonging to a different local profile. Nothing was deleted or uploaded.",
                            AuthErrorKind.PROFILE_CONFLICT, offlineProfile != null
                        )
                        return@launch
                    }
                    val profile = (resolveAuthoritativeProfileHandoff(
                        userId, identity, firebaseUser.displayName, firebaseUser.email,
                        firebaseUser.photoUrl?.toString(),
                        existingProfile?.takeIf { it.humanUserId.isBlank() || it.humanUserId == identity.humanUserId },
                        offlineProfile, null
                    ) as? ProfileHandoffResolution.Ready)?.profile
                    if (profile == null) {
                        clearAuthoritativeIdentityState()
                        _authState.value = AuthState.Error("The trusted account could not be matched to a safe local profile.", AuthErrorKind.PROFILE_CONFLICT)
                        return@launch
                    }

                    if (disposition == LocalProfileDisposition.EMPTY_PLACEHOLDER && offlineProfile != null) {
                        Log.i(TAG, "stage=placeholder_adoption result=STARTED")
                        strengthRepository.adoptEmptyOfflinePlaceholder(profile, offlineProfile.humanUserId)
                        Log.i(TAG, "stage=placeholder_adoption result=SUCCESS")
                    } else {
                        strengthRepository.insertUserProfile(profile)
                    }
                    strengthRepository.linkExistingDataToUser(userId, identity.humanUserId)
                    persistGoogleAuthentication(profile, identity.schemaVersion)
                    Log.i(TAG, "stage=profile_persistence result=SUCCESS")
                    _authState.value = AuthState.Authenticated(profile)
                    Log.i(TAG, "stage=authentication result=AUTHENTICATED")
                    com.example.core.sync.SyncScheduler.scheduleImmediate(context)
                    com.example.core.sync.SyncScheduler.schedulePeriodic(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Error restoring Firebase session", e)
                    _authState.value = AuthState.Error(
                        e.localizedMessage ?: "Unable to restore cloud authentication"
                    )
                }
            }
            return
        }

        val isLoggedIn = prefs.getBoolean("auth_is_logged_in", false)
        val authProvider = prefs.getString("auth_provider", "offline")
        val activeUserId = prefs.getString("auth_active_user_id", "offline") ?: "offline"

        if (isLoggedIn && authProvider == "google" && activeUserId != "offline") {
            clearPersistedAuthentication()
            _authState.value = AuthState.Error("Firebase authentication is required to restore a Human account")
        } else if (isLoggedIn && authProvider == "offline") {
            _authState.value = AuthState.Offline
        } else {
            _authState.value = AuthState.Initial
        }
    }

    private fun clearPersistedAuthentication() {
        prefs.edit()
            .remove("auth_is_logged_in")
            .remove("auth_provider")
            .remove("auth_active_user_id")
            .remove("auth_email")
            .remove("auth_display_name")
            .remove("auth_photo_url")
            .remove("auth_human_user_id")
            .remove("auth_identity_status")
            .remove("auth_identity_schema_version")
            .putBoolean("auth_profile_handoff_complete", false)
            .apply()
    }

    private fun clearAuthoritativeIdentityState() {
        com.example.core.sync.SyncScheduler.cancelCloudSync(context)
        prefs.edit().remove("auth_human_user_id").remove("auth_identity_status")
            .remove("auth_identity_schema_version").putBoolean("auth_profile_handoff_complete", false).apply()
    }

    private fun persistGoogleAuthentication(profile: UserProfile, schemaVersion: Long) {
        prefs.edit()
            .putBoolean("auth_is_logged_in", true)
            .putString("auth_provider", "google")
            .putString("auth_active_user_id", profile.id)
            .putString("auth_email", profile.email)
            .putString("auth_display_name", profile.displayName)
            .putString("auth_photo_url", profile.photoUrl)
            .putString("auth_human_user_id", profile.humanUserId)
            .putString("auth_identity_status", ACTIVE_IDENTITY_STATUS)
            .putLong("auth_identity_schema_version", schemaVersion)
            .putBoolean("auth_profile_handoff_complete", true)
            .apply()
    }

    suspend fun signInAnonymously() = withContext(Dispatchers.IO) {
        prefs.edit()
            .putBoolean("auth_is_logged_in", true)
            .putString("auth_provider", "offline")
            .putString("auth_active_user_id", "offline")
            .apply()

        // Create offline profile if not exists
        val existingProfile = strengthRepository.getUserProfile("offline")
        if (existingProfile == null) {
            val offlineProfile = UserProfile(
                id = "offline",
                displayName = "Offline User",
                authProvider = "offline",
                isOfflineUser = true,
                humanUserId = com.example.core.identity.HumanUserIdGenerator.getOrGenerateOfflineHumanId(context),
                firebaseUid = null
            )
            strengthRepository.insertUserProfile(offlineProfile)
        }

        _authState.value = AuthState.Offline
    }

    suspend fun signInWithGoogle(idToken: String, displayName: String?, email: String?, photoUrl: String?): UserProfile? = withContext(Dispatchers.IO) {
        _authState.value = AuthState.Loading
        try {
            // Generate deterministic or firebase user ID
            var userId = "google_" + idToken.hashCode().toString().replace("-", "n")
            var fUid: String? = null
            
            if (firebaseAuth != null) {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = com.google.android.gms.tasks.Tasks.await(firebaseAuth!!.signInWithCredential(credential))
                val firebaseUser = requireNotNull(authResult.user) {
                    "Firebase Google authentication returned no user"
                }
                userId = firebaseUser.uid
                fUid = firebaseUser.uid
                if (prefs.getString("auth_active_user_id", null)?.let { it != userId } == true) {
                    clearAuthoritativeIdentityState()
                }
            } else {
                Log.w(TAG, "Firebase is genuinely unconfigured; creating a local-only Google profile without cloud entitlement.")
            }

            val existingProfile = strengthRepository.getUserProfile(userId)
            if (fUid == null) {
                _authState.value = AuthState.Error("Firebase authentication is required for a Human account")
                return@withContext null
            }
            prefs.edit().putBoolean("auth_profile_handoff_complete", false).apply()
            appCheckIdentityGate(com.example.HumanStrengthApplication.appCheckInitializationState)?.let { message ->
                Log.e(TAG, "stage=identity_gate result=APP_CHECK_UNAVAILABLE")
                clearAuthoritativeIdentityState()
                _authState.value = AuthState.Error(message, AuthErrorKind.APP_CHECK)
                return@withContext null
            }
            Log.i(TAG, "stage=identity_request result=STARTED")
            val identityResult = identityClient.ensureHumanIdentity()
            Log.i(TAG, "stage=identity_request result=${identityResult.safeResultName()}")
            val identity = (identityResult as? HumanIdentityResult.Success)?.identity
            if (identity == null) {
                clearAuthoritativeIdentityState()
                _authState.value = AuthState.Error(identityResult.safeMessage(), AuthErrorKind.TRUSTED_IDENTITY)
                return@withContext null
            }
            val offlineProfile = strengthRepository.getUserProfile("offline")
            val persistedHumanId = prefs.getString("auth_human_user_id", null)
            val mismatchedProfile = existingProfile?.takeIf { it.humanUserId.isNotBlank() && it.humanUserId != identity.humanUserId }
                ?: offlineProfile?.takeIf { it.humanUserId.isNotBlank() && it.humanUserId != identity.humanUserId }
            val ownership = mismatchedProfile?.let {
                strengthRepository.inspectLocalOwnership(it.id, it.humanUserId, identity.humanUserId)
            } ?: LocalOwnershipSummary(0, strengthRepository.dao.countOtherProfiles(identity.humanUserId))
            val disposition = classifyLocalProfileHandoff(
                identity.humanUserId, existingProfile, offlineProfile, persistedHumanId, ownership
            )
            Log.i(TAG, "stage=local_profile result=$disposition")
            if (disposition == LocalProfileDisposition.MEANINGFUL_DATA || disposition == LocalProfileDisposition.AMBIGUOUS) {
                if (disposition == LocalProfileDisposition.MEANINGFUL_DATA && mismatchedProfile != null &&
                    offerVerifiedLegacyUpgrade(fUid, identity, mismatchedProfile, displayName, email, photoUrl, ownership)) {
                    return@withContext null
                }
                _authState.value = AuthState.Error(
                    message = if (disposition == LocalProfileDisposition.AMBIGUOUS)
                        "This device contains multiple local profiles. Sign-in succeeded, but synchronization is paused until the profiles are reviewed."
                    else "Sign-in succeeded, but this device already contains data belonging to a different local profile. Nothing was deleted or uploaded.",
                    kind = AuthErrorKind.PROFILE_CONFLICT,
                    canContinueOffline = offlineProfile != null
                )
                return@withContext null
            }
            val profile = (resolveAuthoritativeProfileHandoff(
                fUid, identity, displayName, email, photoUrl,
                existingProfile?.takeIf { it.humanUserId.isBlank() || it.humanUserId == identity.humanUserId },
                offlineProfile, null
            ) as? ProfileHandoffResolution.Ready)?.profile
            if (profile == null) {
                clearAuthoritativeIdentityState()
                _authState.value = AuthState.Error("The trusted account could not be matched to a safe local profile.", AuthErrorKind.PROFILE_CONFLICT)
                return@withContext null
            }

            // Save only after the ownership decision. Empty offline placeholders are replaced atomically.
            if (disposition == LocalProfileDisposition.EMPTY_PLACEHOLDER && offlineProfile != null) {
                Log.i(TAG, "stage=placeholder_adoption result=STARTED")
                strengthRepository.adoptEmptyOfflinePlaceholder(profile, offlineProfile.humanUserId)
                Log.i(TAG, "stage=placeholder_adoption result=SUCCESS")
            } else {
                strengthRepository.insertUserProfile(profile)
            }
            strengthRepository.linkExistingDataToUser(userId, identity.humanUserId)

            // Save to shared preferences
            persistGoogleAuthentication(profile, identity.schemaVersion)

            Log.i(TAG, "stage=profile_persistence result=SUCCESS")
            _authState.value = AuthState.Authenticated(profile)
            Log.i(TAG, "stage=authentication result=AUTHENTICATED")
            com.example.core.sync.SyncScheduler.scheduleImmediate(context)
            com.example.core.sync.SyncScheduler.schedulePeriodic(context)
            return@withContext profile
        } catch (e: CancellationException) {
            throw e
        } catch (e: com.google.firebase.FirebaseNetworkException) {
            Log.e(TAG, "Google Sign-In network failure", e)
            _authState.value = AuthState.Error("Network unavailable. Check your connection and try again.", AuthErrorKind.NETWORK)
            return@withContext null
        } catch (e: com.google.firebase.auth.FirebaseAuthException) {
            Log.e(TAG, "Firebase rejected Google authentication (${e.errorCode})")
            _authState.value = AuthState.Error("Firebase rejected the Google sign-in. Please try again.")
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In integration failed", e)
            _authState.value = AuthState.Error(e.localizedMessage ?: "Unknown Google authentication error")
            return@withContext null
        }
    }

    suspend fun linkOfflineDataToUser(userId: String) = withContext(Dispatchers.IO) {
        try {
            val profile = strengthRepository.getUserProfile(userId)
            val authoritativeId = profile?.takeIf {
                it.firebaseUid == userId && isValidAuthoritativeHumanId(it.humanUserId)
            }?.humanUserId ?: throw IllegalStateException("Authoritative Human identity is unavailable")
            strengthRepository.linkExistingDataToUser(userId, authoritativeId)
            Log.d(TAG, "Successfully linked existing offline data to user: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to link existing offline data to user: $userId", e)
        }
    }

    suspend fun signOut(keepLocalData: Boolean) = withContext(Dispatchers.IO) {
        com.example.core.sync.SyncScheduler.cancelCloudSync(context)
        try {
            if (firebaseAuth != null) {
                firebaseAuth!!.signOut()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase sign-out failed", e)
        }

        val activeUserId = prefs.getString("auth_active_user_id", "offline") ?: "offline"

        if (!keepLocalData && activeUserId != "offline") {
            try {
                // Delete user specific profile
                strengthRepository.deleteUserProfile(activeUserId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete user profile on sign out", e)
            }
        }

        prefs.edit()
            .putBoolean("auth_is_logged_in", false)
            .putString("auth_provider", "offline")
            .putString("auth_active_user_id", "offline")
            .remove("auth_email")
            .remove("auth_display_name")
            .remove("auth_photo_url")
            .remove("auth_human_user_id")
            .remove("auth_identity_status")
            .remove("auth_identity_schema_version")
            .putBoolean("auth_profile_handoff_complete", false)
            .apply()

        _authState.value = AuthState.Initial
    }

    /**
     * Executes authoritative Cloud Account Deletion for authenticated users.
     * Purges all user-owned cloud data and deletes the Firebase Authentication identity.
     * PRESERVES all local workout history, logged sets, routines, and body measurements on device as offline data.
     */
    suspend fun deleteCloudAccount(): Result<Unit> = withContext(Dispatchers.IO) {
        val activeUserId = prefs.getString("auth_active_user_id", "offline") ?: "offline"
        val currentUser = firebaseAuth?.currentUser

        if (currentUser == null && activeUserId == "offline") {
            return@withContext Result.failure(IllegalStateException("No active cloud account found to delete. App is in offline mode."))
        }

        try {
            // Step 1: Force fresh ID token retrieval to verify/reauthenticate identity before destructive action
            var idToken: String? = null
            if (currentUser != null) {
                try {
                    val tokenResult = com.google.android.gms.tasks.Tasks.await(currentUser.getIdToken(true))
                    idToken = tokenResult.token
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to retrieve fresh ID token for account deletion. Reauthentication required.", e)
                    return@withContext Result.failure(e)
                }
            }

            // Step 2: Invoke server-side deletion endpoint if token exists
            if (idToken != null) {
                try {
                    val url = java.net.URL("https://europe-west1-596361666131.cloudfunctions.net/deleteUserAccount")
                    val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("Authorization", "Bearer $idToken")
                        connectTimeout = 10000
                        readTimeout = 10000
                        doOutput = true
                    }
                    val payload = trustedAccountDeletionPayload()
                    java.io.OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                        writer.write(payload.toString())
                        writer.flush()
                    }
                    val code = conn.responseCode
                    if (code !in 200..299) return@withContext Result.failure(
                        IllegalStateException("Cloud account deletion was rejected")
                    )
                } catch (netErr: Exception) {
                    Log.w(TAG, "Network exception invoking cloud deletion function (offline or test environment)", netErr)
                    return@withContext Result.failure(netErr)
                }
            }

            // Step 4: Unlink local user profile from deleted cloud identity while PRESERVING local training history
            val activeProfile = strengthRepository.getUserProfile(activeUserId)
            if (activeProfile != null) {
                val unlinkedProfile = activeProfile.copy(
                    firebaseUid = null,
                    googleUserId = null,
                    authProvider = "offline",
                    isOfflineUser = true,
                    updatedAt = System.currentTimeMillis()
                )
                strengthRepository.insertUserProfile(unlinkedProfile)
            }

            // Step 5: Clear cloud session state in shared preferences
            prefs.edit()
                .putBoolean("auth_is_logged_in", false)
                .putString("auth_provider", "offline")
                .putString("auth_active_user_id", "offline")
                .remove("auth_email")
                .remove("auth_display_name")
                .remove("auth_photo_url")
                .remove("auth_human_user_id")
                .remove("auth_identity_status")
                .remove("auth_identity_schema_version")
                .putBoolean("auth_profile_handoff_complete", false)
                .apply()

            _authState.value = AuthState.Offline
            Log.i(TAG, "Cloud account deletion completed successfully. Local data preserved.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Account deletion failed", e)
            Result.failure(e)
        }
    }
}
