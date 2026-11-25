package com.chuahws.mayalanguageapp

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.chuahws.mayalanguageapp.presentation.theme.LocalMayaAppearance
import com.chuahws.mayalanguageapp.presentation.theme.MayaAppearance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        enableEdgeToEdge()

        val dependencies = AppDependencies.create(this)

        val authViewModel = ViewModelProvider(
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

        val appDataViewModel = ViewModelProvider(
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

        val translationViewModel = ViewModelProvider(
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

        val appearance = getSharedPreferences("maya_appearance", MODE_PRIVATE)
        setContent {
            val dark = remember { mutableStateOf(appearance.getBoolean("dark_theme", false)) }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark.value
                    isAppearanceLightNavigationBars = !dark.value
                }
            }
            CompositionLocalProvider(LocalMayaAppearance provides MayaAppearance(dark.value) { enabled ->
                dark.value = enabled
                appearance.edit().putBoolean("dark_theme", enabled).apply()
            }) {
                MayaTranslateTheme(darkTheme = dark.value) {
                    MayaLanguageApp(
                        authViewModel = authViewModel,
                        appDataViewModel = appDataViewModel,
                        translationViewModel = translationViewModel,
                        firebaseConfigured = dependencies.firebaseConfigured,
                    )
                }
            }
        }
    }
}
