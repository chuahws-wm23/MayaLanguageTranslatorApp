package com.chuahws.mayalanguageapp.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chuahws.mayalanguageapp.domain.model.AuthUser
import com.chuahws.mayalanguageapp.domain.model.UserProfile
import com.chuahws.mayalanguageapp.domain.repository.AuthRepository
import com.chuahws.mayalanguageapp.domain.repository.UserProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val userProfileRepository: UserProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AuthUiState(currentUser = authRepository.currentUser())
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun updateDisplayName(value: String) {
        _uiState.update { it.copy(displayName = value, message = null) }
    }

    fun updateEmail(value: String) {
        _uiState.update {
            it.copy(
                email = value,
                message = null,
                passwordResetSent = false,
            )
        }
    }

    fun updatePassword(value: String) {
        _uiState.update { it.copy(password = value, message = null) }
    }

    fun updateConfirmPassword(value: String) {
        _uiState.update { it.copy(confirmPassword = value, message = null) }
    }

    fun register() {
        val state = _uiState.value
        val validationError = validateRegistration(state)

        if (validationError != null) {
            _uiState.update { it.copy(message = validationError) }
            return
        }

        viewModelScope.launch {
            setLoading(true)

            runCatching {
                val user = authRepository.register(
                    displayName = state.displayName,
                    email = state.email,
                    password = state.password,
                )

                val now = System.currentTimeMillis()
                userProfileRepository.createProfile(
                    UserProfile(
                        userId = user.userId,
                        displayName = state.displayName.trim(),
                        email = user.email,
                        role = "user",
                        accountStatus = "active",
                        createdAtMillis = now,
                        updatedAtMillis = now,
                    )
                )

                user
            }.onSuccess(::onAuthenticated)
                .onFailure(::onOperationFailed)
        }
    }

    fun login() {
        val state = _uiState.value

        if (!isValidEmail(state.email) || state.password.isBlank()) {
            _uiState.update {
                it.copy(message = "Enter a valid email and password.")
            }
            return
        }

        viewModelScope.launch {
            setLoading(true)

            runCatching {
                authRepository.login(state.email, state.password)
            }.onSuccess(::onAuthenticated)
                .onFailure(::onOperationFailed)
        }
    }

    fun sendPasswordReset() {
        val email = _uiState.value.email

        if (!isValidEmail(email)) {
            _uiState.update {
                it.copy(message = "Enter a valid registered email address.")
            }
            return
        }

        viewModelScope.launch {
            setLoading(true)

            runCatching {
                authRepository.sendPasswordReset(email)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        loading = false,
                        message = "If the email is registered, a reset email has been sent.",
                        passwordResetSent = true,
                    )
                }
            }.onFailure(::onOperationFailed)
        }
    }

    fun signOut() {
        authRepository.signOut()
        _uiState.update {
            AuthUiState(message = "You have been logged out.")
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun onAuthenticated(user: AuthUser) {
        _uiState.update {
            it.copy(
                currentUser = user,
                loading = false,
                message = null,
                password = "",
                confirmPassword = "",
            )
        }
    }

    private fun onOperationFailed(error: Throwable) {
        _uiState.update {
            it.copy(
                loading = false,
                message = error.message ?: "The operation could not be completed.",
            )
        }
    }

    private fun setLoading(loading: Boolean) {
        _uiState.update { it.copy(loading = loading, message = null) }
    }

    private fun validateRegistration(state: AuthUiState): String? = when {
        state.displayName.trim().length < 2 ->
            "Enter a display name with at least two characters."

        !isValidEmail(state.email) ->
            "Enter a valid email address."

        state.password.length < 6 ->
            "Password must contain at least six characters."

        state.password != state.confirmPassword ->
            "The passwords do not match."

        else -> null
    }

    private fun isValidEmail(value: String): Boolean =
        value.trim().contains("@") && value.substringAfter("@").contains(".")
}
