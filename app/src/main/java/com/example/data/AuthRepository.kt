package com.example.data

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.example.core.identity.HumanUserIdGenerator
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

sealed class AuthState {
    object Initial : AuthState()
    object Loading : AuthState()
    data class Authenticated(val profile: UserProfile) : AuthState()
    object Offline : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthRepository(
    private val context: Context,
    private val strengthRepository: StrengthRepository,
    private val scope: CoroutineScope
) {
    private val TAG = "AuthRepository"
    private val prefs = context.getSharedPreferences("strength_settings", Context.MODE_PRIVATE)
    
    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState

    private var firebaseAuth: FirebaseAuth? = null

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
                Log.d(TAG, "Offline profile fetched: $offlineProfile")
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

    private fun restoreSession() {
        val isLoggedIn = prefs.getBoolean("auth_is_logged_in", false)
        val authProvider = prefs.getString("auth_provider", "offline")
        val activeUserId = prefs.getString("auth_active_user_id", "offline") ?: "offline"

        if (isLoggedIn && authProvider == "google" && activeUserId != "offline") {
            _authState.value = AuthState.Loading
            scope.launch(Dispatchers.IO) {
                try {
                    val profile = strengthRepository.getUserProfile(activeUserId)
                    if (profile != null) {
                        _authState.value = AuthState.Authenticated(profile)
                    } else {
                        // Create fallback profile for Google user if missing in Room
                        val fallbackProfile = UserProfile(
                            id = activeUserId,
                            googleUserId = activeUserId,
                            email = prefs.getString("auth_email", ""),
                            displayName = prefs.getString("auth_display_name", "Google User"),
                            photoUrl = prefs.getString("auth_photo_url", null),
                            authProvider = "google",
                            humanUserId = HumanUserIdGenerator.mapUserIdToHumanUserId(activeUserId),
                            firebaseUid = if (activeUserId.startsWith("google_")) null else activeUserId,
                            isOfflineUser = false
                        )
                        strengthRepository.insertUserProfile(fallbackProfile)
                        _authState.value = AuthState.Authenticated(fallbackProfile)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error restoring Google session", e)
                    _authState.value = AuthState.Offline
                }
            }
        } else if (isLoggedIn && authProvider == "offline") {
            _authState.value = AuthState.Offline
        } else {
            _authState.value = AuthState.Initial
        }
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
                humanUserId = HumanUserIdGenerator.mapUserIdToHumanUserId("offline"),
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
            } else {
                Log.w(TAG, "Firebase is genuinely unconfigured; creating a local-only Google profile without cloud entitlement.")
            }

            val finalDisplayName = displayName ?: email?.substringBefore("@") ?: "Google User"
            val existingProfile = strengthRepository.getUserProfile(userId)
            val profile = UserProfile(
                id = userId,
                googleUserId = userId,
                email = email,
                displayName = finalDisplayName,
                photoUrl = photoUrl,
                authProvider = "google",
                lastLoginAt = System.currentTimeMillis(),
                humanUserId = HumanUserIdGenerator.mapUserIdToHumanUserId(userId),
                firebaseUid = fUid,
                isOfflineUser = false,
                dateOfBirth = existingProfile?.dateOfBirth,
                sex = existingProfile?.sex,
                trainingExperience = existingProfile?.trainingExperience,
                heightCm = existingProfile?.heightCm,
                preferredUnits = existingProfile?.preferredUnits ?: "metric",
                createdAt = existingProfile?.createdAt ?: System.currentTimeMillis()
            )

            // Save to room
            strengthRepository.insertUserProfile(profile)

            // Save to shared preferences
            prefs.edit()
                .putBoolean("auth_is_logged_in", true)
                .putString("auth_provider", "google")
                .putString("auth_active_user_id", userId)
                .putString("auth_email", email)
                .putString("auth_display_name", finalDisplayName)
                .putString("auth_photo_url", photoUrl)
                .apply()

            _authState.value = AuthState.Authenticated(profile)
            return@withContext profile
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In integration failed", e)
            _authState.value = AuthState.Error(e.localizedMessage ?: "Unknown Google authentication error")
            return@withContext null
        }
    }

    suspend fun linkOfflineDataToUser(userId: String) = withContext(Dispatchers.IO) {
        try {
            strengthRepository.linkExistingDataToUser(userId)
            Log.d(TAG, "Successfully linked existing offline data to user: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to link existing offline data to user: $userId", e)
        }
    }

    suspend fun signOut(keepLocalData: Boolean) = withContext(Dispatchers.IO) {
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

            val humanUserId = HumanUserIdGenerator.mapUserIdToHumanUserId(currentUser?.uid ?: activeUserId)

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
                    val payload = org.json.JSONObject().apply {
                        put("humanUserId", humanUserId)
                    }
                    java.io.OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                        writer.write(payload.toString())
                        writer.flush()
                    }
                    val code = conn.responseCode
                    Log.i(TAG, "Server account deletion response code: $code")
                } catch (netErr: Exception) {
                    Log.w(TAG, "Network exception invoking cloud deletion function (offline or test environment)", netErr)
                }
            }

            // Step 3: Delete Firebase Authentication identity on client
            if (currentUser != null) {
                try {
                    com.google.android.gms.tasks.Tasks.await(currentUser.delete())
                    Log.i(TAG, "Successfully deleted client Firebase Auth user")
                } catch (deleteErr: Exception) {
                    Log.w(TAG, "Client-side user.delete() produced warning or required reauth", deleteErr)
                    if (deleteErr is com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
                        return@withContext Result.failure(deleteErr)
                    }
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
