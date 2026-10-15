package com.chuahws.mayalanguageapp.domain.repository

import com.chuahws.mayalanguageapp.domain.model.AuthUser

interface AuthRepository {
    fun currentUser(): AuthUser?

    suspend fun register(
        displayName: String,
        email: String,
        password: String,
    ): AuthUser

    suspend fun login(
        email: String,
        password: String,
    ): AuthUser

    suspend fun sendPasswordReset(email: String)

    fun signOut()
}
