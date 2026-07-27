package com.core.mdm.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.core.mdm.firebase.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val resetEmailSent: Boolean = false,
)

class LoginViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun signIn(email: String, password: String) {
        if (!validate(email, password)) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            AuthRepository.signIn(email.trim(), password)
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
                .onSuccess  { _uiState.update { it.copy(isLoading = false) } }
        }
    }

    fun signUp(email: String, password: String) {
        if (!validate(email, password)) return
        if (password.length < 6) {
            _uiState.update { it.copy(error = "Password must be at least 6 characters") }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            AuthRepository.signUp(email.trim(), password)
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
                .onSuccess  { _uiState.update { it.copy(isLoading = false) } }
        }
    }

    fun sendPasswordReset(email: String) {
        if (email.isBlank()) { _uiState.update { it.copy(error = "Enter your email first") }; return }
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            AuthRepository.sendPasswordReset(email.trim())
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
                .onSuccess  { _uiState.update { it.copy(isLoading = false, resetEmailSent = true) } }
        }
    }

    fun signInWithGoogleToken(idToken: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            AuthRepository.signInWithGoogle(idToken)
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
                .onSuccess  { _uiState.update { it.copy(isLoading = false) } }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
    fun setError(msg: String) = _uiState.update { it.copy(error = msg, isLoading = false) }
    fun clearResetSent() = _uiState.update { it.copy(resetEmailSent = false) }

    private fun validate(email: String, password: String): Boolean {
        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(error = "Email and password are required") }
            return false
        }
        return true
    }
}
