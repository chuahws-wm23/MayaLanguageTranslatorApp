package com.chuahws.mayalanguageapp

import android.content.Context
import com.chuahws.mayalanguageapp.data.repository.DevelopmentAuthRepository
import com.chuahws.mayalanguageapp.data.repository.FirebaseAuthRepository
import com.chuahws.mayalanguageapp.data.repository.FirestoreDictionaryRepository
import com.chuahws.mayalanguageapp.data.repository.FirestoreUserProfileRepository
import com.chuahws.mayalanguageapp.data.repository.InMemoryDictionaryRepository
import com.chuahws.mayalanguageapp.data.repository.InMemoryUserProfileRepository
import com.chuahws.mayalanguageapp.domain.repository.AuthRepository
import com.chuahws.mayalanguageapp.domain.repository.DictionaryRepository
import com.chuahws.mayalanguageapp.domain.repository.UserProfileRepository
import com.google.firebase.FirebaseApp

/**
 * Creates the application dependencies without requiring a DI framework.
 *
 * When Firebase is not configured yet, the application remains runnable by
 * using development-only in-memory repositories. Once google-services.json is
 * added and the Google Services plugin is enabled, the Firebase repositories
 * are selected automatically.
 */
data class AppDependencies(
    val authRepository: AuthRepository,
    val userProfileRepository: UserProfileRepository,
    val dictionaryRepository: DictionaryRepository,
    val firebaseConfigured: Boolean,
) {
    companion object {
        fun create(context: Context): AppDependencies {
            val firebaseConfigured = runCatching {
                FirebaseApp.initializeApp(context.applicationContext)
            }.getOrNull() != null

            return if (firebaseConfigured) {
                AppDependencies(
                    authRepository = FirebaseAuthRepository(),
                    userProfileRepository = FirestoreUserProfileRepository(),
                    dictionaryRepository = FirestoreDictionaryRepository(),
                    firebaseConfigured = true,
                )
            } else {
                AppDependencies(
                    authRepository = DevelopmentAuthRepository(),
                    userProfileRepository = InMemoryUserProfileRepository(),
                    dictionaryRepository = InMemoryDictionaryRepository(emptyList()),
                    firebaseConfigured = false,
                )
            }
        }
    }
}
