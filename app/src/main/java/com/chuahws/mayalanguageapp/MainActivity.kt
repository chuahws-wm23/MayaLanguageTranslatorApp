package com.chuahws.mayalanguageapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.ViewModelProvider
import com.chuahws.mayalanguageapp.domain.service.TranslationService
import com.chuahws.mayalanguageapp.presentation.auth.AuthViewModel
import com.chuahws.mayalanguageapp.presentation.common.AppViewModelFactory
import com.chuahws.mayalanguageapp.presentation.navigation.MayaLanguageApp
import com.chuahws.mayalanguageapp.presentation.translation.TranslationViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dependencies = AppDependencies.create(this)

        val authViewModel = ViewModelProvider(
            this,
            AppViewModelFactory {
                AuthViewModel(
                    authRepository = dependencies.authRepository,
                    userProfileRepository = dependencies.userProfileRepository,
                )
            },
        )[AuthViewModel::class.java]

        val translationViewModel = ViewModelProvider(
            this,
            AppViewModelFactory {
                TranslationViewModel(
                    translationService = TranslationService(
                        dependencies.dictionaryRepository
                    )
                )
            },
        )[TranslationViewModel::class.java]

        setContent {
            MaterialTheme {
                Surface {
                    MayaLanguageApp(
                        authViewModel = authViewModel,
                        translationViewModel = translationViewModel,
                        firebaseConfigured = dependencies.firebaseConfigured,
                    )
                }
            }
        }
    }
}
