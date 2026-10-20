package com.chuahws.mayalanguageapp.presentation.auth

import com.chuahws.mayalanguageapp.domain.model.AuthUser

data class AuthUiState(
    val displayName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val currentUser: AuthUser? = null,
    val loading: Boolean = false,
    val message: String? = null,
    val passwordResetSent: Boolean = false,
)
