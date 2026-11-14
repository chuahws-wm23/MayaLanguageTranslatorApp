package com.chuahws.mayalanguageapp.data.repository

import com.chuahws.mayalanguageapp.domain.model.AuthUser
import com.chuahws.mayalanguageapp.domain.repository.AuthRepository
import java.util.UUID

/**
 * Development-only authentication used before Firebase is configured.
 * It must not be used for the final evaluated build.
 */
class DevelopmentAuthRepository : AuthRepository {
    private data class Account(
        val password: String,
        val user: AuthUser,
    )

    private val accounts = mutableMapOf<String, Account>()
    private var activeUser: AuthUser? = null

    override fun currentUser(): AuthUser? = activeUser

    override suspend fun register(
        displayName: String,
        email: String,
        password: String,
    ): AuthUser {
        val key = email.trim().lowercase()

        require(key !in accounts) {
            "An account with this email already exists."
        }

        val user = AuthUser(
            userId = "development-${UUID.randomUUID()}",
            email = key,
            displayName = displayName.trim(),
        )

        accounts[key] = Account(password = password, user = user)
        activeUser = user
        return user
    }

    override suspend fun login(
        email: String,
        password: String,
    ): AuthUser {
        val account = accounts[email.trim().lowercase()]
            ?: throw IllegalArgumentException("Invalid email or password.")

        require(account.password == password) {
            "Invalid email or password."
        }

        activeUser = account.user
        return account.user
    }

    override suspend fun sendPasswordReset(email: String) {
        // Mimic Firebase's privacy-preserving response. Do not reveal whether
        // an email exists in this temporary in-memory repository.
    }

    override fun signOut() {
        activeUser = null
    }
}
