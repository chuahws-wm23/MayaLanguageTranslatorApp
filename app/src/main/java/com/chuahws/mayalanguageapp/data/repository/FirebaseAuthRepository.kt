package com.chuahws.mayalanguageapp.data.repository

import com.chuahws.mayalanguageapp.domain.model.AuthUser
import com.chuahws.mayalanguageapp.domain.repository.AuthRepository
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(
    private val firebaseAuth: FirebaseAuth =
        FirebaseAuth.getInstance(),
) : AuthRepository {

    override fun currentUser(): AuthUser? =
        firebaseAuth.currentUser?.toDomain()

    override suspend fun register(
        displayName: String,
        email: String,
        password: String,
    ): AuthUser = authCall {
        val result = firebaseAuth
            .createUserWithEmailAndPassword(
                email.trim(),
                password,
            )
            .await()

        val user = requireNotNull(result.user) {
            "Could not create the account."
        }

        user.updateProfile(
            UserProfileChangeRequest.Builder()
                .setDisplayName(displayName.trim())
                .build()
        ).await()

        user.toDomain(displayName.trim())
    }

    override suspend fun login(
        email: String,
        password: String,
    ): AuthUser = authCall {
        val result = firebaseAuth
            .signInWithEmailAndPassword(
                email.trim(),
                password,
            )
            .await()

        requireNotNull(result.user) {
            "Could not sign in."
        }.toDomain()
    }

    override suspend fun sendPasswordReset(
        email: String,
    ) {
        authCall {
            firebaseAuth
                .sendPasswordResetEmail(email.trim())
                .await()
        }
    }

    override fun signOut() {
        firebaseAuth.signOut()
    }

    private suspend fun <T> authCall(
        block: suspend () -> T,
    ): T {
        try {
            return block()
        } catch (error: Throwable) {
            throw when (error) {
                is FirebaseAuthUserCollisionException ->
                    IllegalArgumentException(
                        "An account already exists for this email."
                    )

                is FirebaseAuthWeakPasswordException ->
                    IllegalArgumentException(
                        "Use a stronger password with at least 6 characters."
                    )

                is FirebaseAuthInvalidUserException,
                is FirebaseAuthInvalidCredentialsException ->
                    IllegalArgumentException(
                        "Email or password is incorrect."
                    )

                is FirebaseNetworkException ->
                    IllegalStateException(
                        "Check your internet connection and try again."
                    )

                is FirebaseTooManyRequestsException ->
                    IllegalStateException(
                        "Too many attempts. Try again later."
                    )

                else -> IllegalStateException(
                    "Could not complete this request. Please try again."
                )
            }
        }
    }

    private fun FirebaseUser.toDomain(
        fallbackDisplayName: String? = null,
    ): AuthUser = AuthUser(
        userId = uid,
        email = email.orEmpty(),
        displayName =
            displayName ?: fallbackDisplayName,
    )
}
