package com.chuahws.mayalanguageapp

import android.content.Context
import com.chuahws.mayalanguageapp.data.repository.CompositeDictionaryRepository
import com.chuahws.mayalanguageapp.data.repository.DevelopmentAuthRepository
import com.chuahws.mayalanguageapp.data.repository.DevelopmentDictionaryData
import com.chuahws.mayalanguageapp.data.repository.FirebaseAuthRepository
import com.chuahws.mayalanguageapp.data.repository.FirebaseUserDataRepository
import com.chuahws.mayalanguageapp.data.repository.FirestoreDictionaryRepository
import com.chuahws.mayalanguageapp.data.repository.FirestoreUserProfileRepository
import com.chuahws.mayalanguageapp.data.repository.InMemoryDictionaryRepository
import com.chuahws.mayalanguageapp.data.repository.InMemoryUserDataRepository
import com.chuahws.mayalanguageapp.data.repository.InMemoryUserProfileRepository
import com.chuahws.mayalanguageapp.domain.repository.AuthRepository
import com.chuahws.mayalanguageapp.domain.repository.DictionaryRepository
import com.chuahws.mayalanguageapp.domain.repository.UserDataRepository
import com.chuahws.mayalanguageapp.domain.repository.UserProfileRepository
import com.google.firebase.FirebaseApp

data class AppDependencies(
    val authRepository: AuthRepository,
    val userProfileRepository: UserProfileRepository,
    val dictionaryRepository: DictionaryRepository,
    val userDataRepository: UserDataRepository,
    val firebaseConfigured: Boolean,
) {
    companion object {
        fun create(context: Context): AppDependencies {
            val firebaseConfigured = runCatching {
                FirebaseApp.initializeApp(context.applicationContext)
            }.getOrNull() != null

            val coreDictionary = InMemoryDictionaryRepository(
                DevelopmentDictionaryData.entries()
            )

            return if (firebaseConfigured) {
                AppDependencies(
                    authRepository = FirebaseAuthRepository(),
                    userProfileRepository = FirestoreUserProfileRepository(),
                    dictionaryRepository = CompositeDictionaryRepository(
                        listOf(
                            coreDictionary,
                            FirestoreDictionaryRepository(),
                        )
                    ),
                    userDataRepository = FirebaseUserDataRepository(),
                    firebaseConfigured = true,
                )
            } else {
                AppDependencies(
                    authRepository = DevelopmentAuthRepository(),
                    userProfileRepository = InMemoryUserProfileRepository(),
                    dictionaryRepository = coreDictionary,
                    userDataRepository = InMemoryUserDataRepository(),
                    firebaseConfigured = false,
                )
            }
        }
    }
}
