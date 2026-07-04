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
        if (com.example.StrengthApplication.isFirebaseConfigured) {
            try {
                // Attempt to get FirebaseAuth instance if Firebase is configured
                firebaseAuth = FirebaseAuth.getInstance()
            } catch (e: Exception) {
                Log.w(TAG, "Firebase Auth not initialized. Falling back to offline-first Google profile management.", e)
            }
        } else {
            Log.w(TAG, "Firebase is not configured. Operating in offline fallback mode.")
        }
        
        // Restore session on app startup
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
                try {
                    val credential = GoogleAuthProvider.getCredential(idToken, null)
                    val authResult = com.google.android.gms.tasks.Tasks.await(firebaseAuth!!.signInWithCredential(credential))
                    val firebaseUser = authResult.user
                    if (firebaseUser != null) {
                        userId = firebaseUser.uid
                        fUid = firebaseUser.uid
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Firebase signInWithCredential failed, using local Google profile fallback.", e)
                }
            }

            val finalDisplayName = displayName ?: email?.substringBefore("@") ?: "Google User"
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
                isOfflineUser = false
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
}
