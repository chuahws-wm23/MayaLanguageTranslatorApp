package com.chuahws.mayalanguageapp.data.repository

import com.chuahws.mayalanguageapp.domain.model.AuthUser
import com.chuahws.mayalanguageapp.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
) : AuthRepository {

    override fun currentUser(): AuthUser? =
        firebaseAuth.currentUser?.toDomain()

    override suspend fun register(
        displayName: String,
        email: String,
        password: String,
    ): AuthUser {
        val result = firebaseAuth
            .createUserWithEmailAndPassword(email.trim(), password)
            .await()

        val user = requireNotNull(result.user) {
            "Firebase did not return the newly created user."
        }

        user.updateProfile(
            UserProfileChangeRequest.Builder()
                .setDisplayName(displayName.trim())
                .build()
        ).await()

        return user.toDomain(displayName.trim())
    }

    override suspend fun login(
        email: String,
        password: String,
    ): AuthUser {
        val result = firebaseAuth
            .signInWithEmailAndPassword(email.trim(), password)
            .await()

        return requireNotNull(result.user) {
            "Firebase did not return the authenticated user."
        }.toDomain()
    }

    override suspend fun sendPasswordReset(email: String) {
        firebaseAuth.sendPasswordResetEmail(email.trim()).await()
    }

    override fun signOut() {
        firebaseAuth.signOut()
    }

    private fun FirebaseUser.toDomain(
        fallbackDisplayName: String? = null,
    ): AuthUser = AuthUser(
        userId = uid,
        email = email.orEmpty(),
        displayName = displayName ?: fallbackDisplayName,
    )
}
