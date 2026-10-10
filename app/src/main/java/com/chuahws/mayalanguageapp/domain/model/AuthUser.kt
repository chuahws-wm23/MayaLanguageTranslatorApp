package com.chuahws.mayalanguageapp.domain.model

data class AuthUser(
    val userId: String,
    val email: String,
    val displayName: String? = null,
)
