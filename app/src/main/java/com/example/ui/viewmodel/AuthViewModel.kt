package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AuthRepository
import com.example.data.AuthState
import com.example.data.StrengthRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: StrengthRepository,
    private val context: Context
) : ViewModel() {
    val authRepository = AuthRepository(context, repository, viewModelScope)
    val authState: StateFlow<AuthState> = authRepository.authState

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeUserId: StateFlow<String> = authRepository.authState.map { state ->
        when (state) {
            is AuthState.Authenticated -> state.profile.id
            is AuthState.Offline -> "offline"
            else -> "offline"
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "offline")

    fun signInWithGoogle(
        idToken: String,
        displayName: String?,
        email: String?,
        photoUrl: String?
    ) {
        launchAuthentication(viewModelScope) {
            authRepository.signInWithGoogle(idToken, displayName, email, photoUrl)
        }
    }

    fun cancelAuthenticatedAccount() = viewModelScope.launch { authRepository.signOut(keepLocalData = true) }

    fun continueWithExistingLocalData() = viewModelScope.launch {
        authRepository.signOut(keepLocalData = true)
        authRepository.signInAnonymously()
    }

    fun updateVerifiedLegacyAndContinue() = viewModelScope.launch { authRepository.updateVerifiedLegacyAndContinue() }
    fun retryLegacyMigrationHandoff() = viewModelScope.launch { authRepository.retryLegacyMigrationHandoff() }
    fun markLegacyBackupCompleted() = viewModelScope.launch { authRepository.markLegacyBackupCompleted() }
}

internal fun launchAuthentication(
    authenticationScope: kotlinx.coroutines.CoroutineScope,
    authenticate: suspend () -> Unit
) = authenticationScope.launch { authenticate() }
