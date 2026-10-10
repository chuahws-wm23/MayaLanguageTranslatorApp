package com.chuahws.mayalanguageapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.chuahws.mayalanguageapp.domain.service.TranslationService
import com.chuahws.mayalanguageapp.presentation.app.AppDataViewModel
import com.chuahws.mayalanguageapp.presentation.auth.AuthViewModel
import com.chuahws.mayalanguageapp.presentation.common.AppViewModelFactory
import com.chuahws.mayalanguageapp.presentation.navigation.MayaLanguageApp
import com.chuahws.mayalanguageapp.presentation.theme.MayaTranslateTheme
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
                    userProfileRepository =
                        dependencies.userProfileRepository,
                )
            },
        )[AuthViewModel::class.java]

        val appDataViewModel = ViewModelProvider(
            this,
            AppViewModelFactory {
                AppDataViewModel(
                    userDataRepository =
                        dependencies.userDataRepository,
                    userProfileRepository =
                        dependencies.userProfileRepository,
                )
            },
        )[AppDataViewModel::class.java]

        val translationViewModel = ViewModelProvider(
            this,
            AppViewModelFactory {
                TranslationViewModel(
                    translationService = TranslationService(
                        dependencies.dictionaryRepository
                    ),
                    userDataRepository =
                        dependencies.userDataRepository,
                    userIdProvider = {
                        authViewModel.uiState.value
                            .currentUser?.userId
                    },
                    onDataChanged = {
                        appDataViewModel.refresh()
                    },
                )
            },
        )[TranslationViewModel::class.java]

        setContent {
            MayaTranslateTheme {
                MayaLanguageApp(
                    authViewModel = authViewModel,
                    appDataViewModel = appDataViewModel,
                    translationViewModel = translationViewModel,
                    firebaseConfigured =
                        dependencies.firebaseConfigured,
                )
            }
        }
    }
}
