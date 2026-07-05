package com.scribesync.scribesync.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.auth.FirebaseUser
import com.scribesync.scribesync.ScribeSyncApplication
import com.scribesync.scribesync.data.AttendeeRequestRepository
import com.scribesync.scribesync.data.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModel(
    application: Application,
    private val authRepository: AuthRepository,
    private val attendeeRequestRepository: AttendeeRequestRepository
) : AndroidViewModel(application) {

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as ScribeSyncApplication)
                AuthViewModel(
                    application = application,
                    authRepository = application.authRepository,
                    attendeeRequestRepository = application.attendeeRequestRepository
                )
            }
        }
    }

    val currentUser: StateFlow<FirebaseUser?> = authRepository.currentUser
    val currentUsername: StateFlow<String?> = authRepository.currentUsername

    // Drives the pending-invite badge on the Contacts tab, visible from any
    // screen - not just while Contacts itself is open.
    val pendingAttendeeRequestCount: StateFlow<Int> = currentUser
        .flatMapLatest { user ->
            if (user == null) flowOf(emptyList()) else attendeeRequestRepository.getPendingRequestsForUser(user.uid)
        }
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        currentUser.value?.uid?.let { uid ->
            viewModelScope.launch { authRepository.loadCachedUsername(uid) }
        }
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("Enter your email and password")
            return
        }
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            authRepository.signIn(email.trim(), password)
                .onSuccess { _uiState.value = AuthUiState.Idle }
                .onFailure { _uiState.value = AuthUiState.Error(it.message ?: "Sign in failed") }
        }
    }

    fun signUp(email: String, password: String, username: String) {
        if (email.isBlank() || password.isBlank() || username.isBlank()) {
            _uiState.value = AuthUiState.Error("Fill in username, email, and password")
            return
        }
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            authRepository.signUp(email.trim(), password, username.trim())
                .onSuccess { _uiState.value = AuthUiState.Idle }
                .onFailure { _uiState.value = AuthUiState.Error(it.message ?: "Sign up failed") }
        }
    }

    fun sendPasswordReset(email: String) {
        if (email.isBlank()) {
            _uiState.value = AuthUiState.Error("Enter your email to reset your password")
            return
        }
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            authRepository.sendPasswordReset(email.trim())
                .onSuccess { _uiState.value = AuthUiState.ResetEmailSent }
                .onFailure { _uiState.value = AuthUiState.Error(it.message ?: "Could not send reset email") }
        }
    }

    fun signOut() {
        authRepository.signOut()
        _uiState.value = AuthUiState.Idle
    }

    fun clearError() {
        if (_uiState.value is AuthUiState.Error) _uiState.value = AuthUiState.Idle
    }

    sealed class AuthUiState {
        object Idle : AuthUiState()
        object Loading : AuthUiState()
        object ResetEmailSent : AuthUiState()
        data class Error(val message: String) : AuthUiState()
    }
}
