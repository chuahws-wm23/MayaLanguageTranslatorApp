package com.chuahws.mayalanguageapp.domain.model

data class UserProfile(
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val role: String = "user",
    val accountStatus: String = "active",
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
)
