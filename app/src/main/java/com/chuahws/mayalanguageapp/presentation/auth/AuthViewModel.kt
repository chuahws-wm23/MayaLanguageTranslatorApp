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

    private val existingUser = authRepository.currentUser()

    private val _uiState = MutableStateFlow(
        AuthUiState(
            currentUser = existingUser,
            email = existingUser?.email.orEmpty(),
            displayName =
                existingUser?.displayName.orEmpty(),
        )
    )
    val uiState: StateFlow<AuthUiState> =
        _uiState.asStateFlow()

    fun updateDisplayName(value: String) {
        _uiState.update {
            it.copy(
                displayName = value,
                message = null,
            )
        }
    }

    fun updateEmail(value: String) {
        _uiState.update {
            it.copy(
                email = value,
                message = null,
                passwordResetSent = false,
                registrationCompleted = false,
            )
        }
    }

    fun updatePassword(value: String) {
        _uiState.update {
            it.copy(
                password = value,
                message = null,
                registrationCompleted = false,
            )
        }
    }

    fun updateConfirmPassword(value: String) {
        _uiState.update {
            it.copy(
                confirmPassword = value,
                message = null,
            )
        }
    }

    fun register() {
        if (_uiState.value.loading) return

        val state = _uiState.value
        val validationError =
            validateRegistration(state)

        if (validationError != null) {
            _uiState.update {
                it.copy(
                    message = validationError
                )
            }
            return
        }

        setLoading(true)

        viewModelScope.launch {
            runCatching {
                val user = authRepository.register(
                    displayName = state.displayName,
                    email = state.email,
                    password = state.password,
                )

                val now =
                    System.currentTimeMillis()
                userProfileRepository.createProfile(
                    UserProfile(
                        userId = user.userId,
                        displayName =
                            state.displayName.trim(),
                        email = user.email,
                        role = "user",
                        accountStatus = "active",
                        createdAtMillis = now,
                        updatedAtMillis = now,
                    )
                )

                user
            }.onSuccess { user ->
                // Firebase signs a newly-created account in automatically.
                // The documented flow requires registration to finish at
                // the login screen, so end that temporary session here.
                authRepository.signOut()
                _uiState.update {
                    it.copy(
                        currentUser = null,
                        email = user.email,
                        displayName = "",
                        password = "",
                        confirmPassword = "",
                        loading = false,
                        message =
                            "Account created. Sign in to continue.",
                        registrationCompleted = true,
                    )
                }
            }.onFailure { error ->
                authRepository.signOut()
                onOperationFailed(error)
            }
        }
    }

    fun login() {
        if (_uiState.value.loading) return

        _uiState.update {
            it.copy(registrationCompleted = false)
        }
        val state = _uiState.value

        if (
            !isValidEmail(state.email) ||
            state.password.isBlank()
        ) {
            _uiState.update {
                it.copy(
                    message =
                        "Enter a valid email and password."
                )
            }
            return
        }

        setLoading(true)

        viewModelScope.launch {
            runCatching {
                val user = authRepository.login(
                    state.email,
                    state.password,
                )

                var profile =
                    userProfileRepository.getProfile(
                        user.userId
                    )

                if (profile == null) {
                    val now =
                        System.currentTimeMillis()
                    profile = UserProfile(
                        userId = user.userId,
                        displayName =
                            user.displayName
                                .orEmpty()
                                .ifBlank {
                                    user.email
                                        .substringBefore(
                                            "@"
                                        )
                                },
                        email = user.email,
                        role = "user",
                        accountStatus = "active",
                        createdAtMillis = now,
                        updatedAtMillis = now,
                    )
                    userProfileRepository
                        .createProfile(profile)
                }

                if (
                    profile.accountStatus ==
                        "suspended"
                ) {
                    authRepository.signOut()
                    throw IllegalStateException(
                        "This account has been suspended. Contact an administrator."
                    )
                }

                user
            }.onSuccess(::onAuthenticated)
                .onFailure(::onOperationFailed)
        }
    }

    fun sendPasswordReset() {
        if (_uiState.value.loading) return

        val email = _uiState.value.email

        if (!isValidEmail(email)) {
            _uiState.update {
                it.copy(
                    message =
                        "Enter a valid registered email address."
                )
            }
            return
        }

        setLoading(true)

        viewModelScope.launch {
            runCatching {
                authRepository.sendPasswordReset(email)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        loading = false,
                        message =
                            "If the email is registered, a reset email has been sent.",
                        passwordResetSent = true,
                    )
                }
            }.onFailure(::onOperationFailed)
        }
    }

    fun signOut() {
        authRepository.signOut()
        _uiState.value = AuthUiState(
            message = "You have been logged out.",
        )
    }

    fun clearMessage() {
        _uiState.update {
            it.copy(message = null)
        }
    }

    private fun onAuthenticated(user: AuthUser) {
        _uiState.update {
            it.copy(
                currentUser = user,
                email = user.email,
                displayName =
                    user.displayName
                        ?: it.displayName,
                loading = false,
                message = null,
                password = "",
                confirmPassword = "",
                passwordResetSent = false,
            )
        }
    }

    private fun onOperationFailed(error: Throwable) {
        _uiState.update {
            it.copy(
                loading = false,
                message =
                    error.message
                        ?: "The operation could not be completed.",
            )
        }
    }

    private fun setLoading(loading: Boolean) {
        _uiState.update {
            it.copy(
                loading = loading,
                message = null,
            )
        }
    }

    private fun validateRegistration(
        state: AuthUiState,
    ): String? = when {
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

    private fun isValidEmail(value: String): Boolean {
        val trimmed = value.trim()
        val atIndex = trimmed.indexOf('@')
        return atIndex > 0 &&
            atIndex < trimmed.lastIndex &&
            trimmed.substring(atIndex + 1)
                .contains('.')
    }
}
