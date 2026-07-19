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
}
