package com.chuahws.mayalanguageapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.chuahws.mayalanguageapp.domain.service.TranslationService
import com.chuahws.mayalanguageapp.presentation.app.AppDataViewModel
import com.chuahws.mayalanguageapp.presentation.auth.AuthViewModel
import com.chuahws.mayalanguageapp.presentation.common.AppViewModelFactory
import com.chuahws.mayalanguageapp.presentation.navigation.MayaLanguageApp
import com.chuahws.mayalanguageapp.presentation.theme.MayaTranslateTheme
import com.chuahws.mayalanguageapp.presentation.translation.TranslationViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(
            window,
            true,
        )

        val dependencies =
            AppDependencies.create(this)

        val authViewModel =
            ViewModelProvider(
                this,
                AppViewModelFactory {
                    AuthViewModel(
                        authRepository =
                            dependencies.authRepository,
                        userProfileRepository =
                            dependencies.userProfileRepository,
                    )
                },
            )[AuthViewModel::class.java]

        val appDataViewModel =
            ViewModelProvider(
                this,
                AppViewModelFactory {
                    AppDataViewModel(
                        userDataRepository =
                            dependencies.userDataRepository,
                        userProfileRepository =
                            dependencies.userProfileRepository,
                        dictionaryRepository =
                            dependencies.dictionaryRepository,
                    )
                },
            )[AppDataViewModel::class.java]

        val translationViewModel =
            ViewModelProvider(
                this,
                AppViewModelFactory {
                    TranslationViewModel(
                        translationService =
                            TranslationService(
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
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color =
                        MaterialTheme.colorScheme.background,
                ) {
                    MayaLanguageApp(
                        authViewModel = authViewModel,
                        appDataViewModel =
                            appDataViewModel,
                        translationViewModel =
                            translationViewModel,
                        firebaseConfigured =
                            dependencies.firebaseConfigured,
                    )
                }
            }
        }
    }
}
