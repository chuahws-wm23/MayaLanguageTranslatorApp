package com.chuahws.mayalanguageapp.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chuahws.mayalanguageapp.presentation.auth.AuthViewModel
import com.chuahws.mayalanguageapp.presentation.auth.LoginScreen
import com.chuahws.mayalanguageapp.presentation.auth.RegisterScreen
import com.chuahws.mayalanguageapp.presentation.auth.ResetPasswordScreen
import com.chuahws.mayalanguageapp.presentation.home.HomeScreen
import com.chuahws.mayalanguageapp.presentation.translation.TranslationScreen
import com.chuahws.mayalanguageapp.presentation.translation.TranslationViewModel

private object Route {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val RESET_PASSWORD = "reset-password"
    const val HOME = "home"
    const val TRANSLATION = "translation"
}

@Composable
fun MayaLanguageApp(
    authViewModel: AuthViewModel,
    translationViewModel: TranslationViewModel,
    firebaseConfigured: Boolean,
) {
    val navController = rememberNavController()
    val authState by authViewModel.uiState.collectAsState()

    val startDestination = if (authState.currentUser == null) {
        Route.LOGIN
    } else {
        Route.HOME
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(Route.LOGIN) {
            LaunchedEffect(authState.currentUser?.userId) {
                if (authState.currentUser != null) {
                    navController.navigate(Route.HOME) {
                        popUpTo(Route.LOGIN) { inclusive = true }
                    }
                }
            }

            LoginScreen(
                state = authState,
                firebaseConfigured = firebaseConfigured,
                onEmailChanged = authViewModel::updateEmail,
                onPasswordChanged = authViewModel::updatePassword,
                onLogin = authViewModel::login,
                onRegister = {
                    authViewModel.clearMessage()
                    navController.navigate(Route.REGISTER)
                },
                onForgotPassword = {
                    authViewModel.clearMessage()
                    navController.navigate(Route.RESET_PASSWORD)
                },
            )
        }

        composable(Route.REGISTER) {
            LaunchedEffect(authState.currentUser?.userId) {
                if (authState.currentUser != null) {
                    navController.navigate(Route.HOME) {
                        popUpTo(Route.LOGIN) { inclusive = true }
                    }
                }
            }

            RegisterScreen(
                state = authState,
                onDisplayNameChanged = authViewModel::updateDisplayName,
                onEmailChanged = authViewModel::updateEmail,
                onPasswordChanged = authViewModel::updatePassword,
                onConfirmPasswordChanged = authViewModel::updateConfirmPassword,
                onRegister = authViewModel::register,
                onBackToLogin = { navController.popBackStack() },
            )
        }

        composable(Route.RESET_PASSWORD) {
            ResetPasswordScreen(
                state = authState,
                onEmailChanged = authViewModel::updateEmail,
                onResetPassword = authViewModel::sendPasswordReset,
                onBackToLogin = { navController.popBackStack() },
            )
        }

        composable(Route.HOME) {
            val currentUser = authState.currentUser

            LaunchedEffect(currentUser?.userId) {
                if (currentUser == null) {
                    navController.navigate(Route.LOGIN) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = true
                        }
                    }
                }
            }

            if (currentUser != null) {
                HomeScreen(
                    user = currentUser,
                    onTextTranslation = {
                        navController.navigate(Route.TRANSLATION)
                    },
                    onSignOut = authViewModel::signOut,
                )
            }
        }

        composable(Route.TRANSLATION) {
            TranslationScreen(
                viewModel = translationViewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
