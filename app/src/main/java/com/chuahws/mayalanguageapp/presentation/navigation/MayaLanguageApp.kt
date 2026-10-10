package com.chuahws.mayalanguageapp.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chuahws.mayalanguageapp.presentation.admin.AdminScreen
import com.chuahws.mayalanguageapp.presentation.app.AppDataViewModel
import com.chuahws.mayalanguageapp.presentation.auth.AuthViewModel
import com.chuahws.mayalanguageapp.presentation.auth.LoginScreen
import com.chuahws.mayalanguageapp.presentation.auth.RegisterScreen
import com.chuahws.mayalanguageapp.presentation.auth.ResetPasswordScreen
import com.chuahws.mayalanguageapp.presentation.history.HistoryScreen
import com.chuahws.mayalanguageapp.presentation.home.HomeScreen
import com.chuahws.mayalanguageapp.presentation.profile.ProfileScreen
import com.chuahws.mayalanguageapp.presentation.saved.SavedScreen
import com.chuahws.mayalanguageapp.presentation.translation.TranslateMode
import com.chuahws.mayalanguageapp.presentation.translation.TranslationHubScreen
import com.chuahws.mayalanguageapp.presentation.translation.TranslationViewModel

private enum class MainTab(
    val label: String,
) {
    HOME("Home"),
    TRANSLATE("Translate"),
    HISTORY("History"),
    SAVED("Saved"),
    PROFILE("Profile"),
}

@Composable
fun MayaLanguageApp(
    authViewModel: AuthViewModel,
    appDataViewModel: AppDataViewModel,
    translationViewModel: TranslationViewModel,
    firebaseConfigured: Boolean,
) {
    val authState by authViewModel.uiState.collectAsState()

    if (authState.currentUser == null) {
        LaunchedEffect(Unit) {
            appDataViewModel.clearUser()
        }

        AuthFlow(
            authViewModel = authViewModel,
            firebaseConfigured = firebaseConfigured,
        )
    } else {
        val user = requireNotNull(authState.currentUser)

        LaunchedEffect(user.userId) {
            appDataViewModel.loadForUser(user.userId)
        }

        MainShell(
            email = user.email,
            appDataViewModel = appDataViewModel,
            translationViewModel = translationViewModel,
            onSignOut = authViewModel::signOut,
        )
    }
}

@Composable
private fun AuthFlow(
    authViewModel: AuthViewModel,
    firebaseConfigured: Boolean,
) {
    val navController = rememberNavController()
    val state by authViewModel.uiState.collectAsState()

    NavHost(
        navController = navController,
        startDestination = "login",
    ) {
        composable("login") {
            LoginScreen(
                state = state,
                firebaseConfigured = firebaseConfigured,
                onEmailChanged = authViewModel::updateEmail,
                onPasswordChanged = authViewModel::updatePassword,
                onLogin = authViewModel::login,
                onRegister = {
                    authViewModel.clearMessage()
                    navController.navigate("register")
                },
                onForgotPassword = {
                    authViewModel.clearMessage()
                    navController.navigate("reset")
                },
            )
        }

        composable("register") {
            RegisterScreen(
                state = state,
                onDisplayNameChanged =
                    authViewModel::updateDisplayName,
                onEmailChanged = authViewModel::updateEmail,
                onPasswordChanged =
                    authViewModel::updatePassword,
                onConfirmPasswordChanged =
                    authViewModel::updateConfirmPassword,
                onRegister = authViewModel::register,
                onBackToLogin = {
                    authViewModel.clearMessage()
                    navController.popBackStack()
                },
            )
        }

        composable("reset") {
            ResetPasswordScreen(
                state = state,
                onEmailChanged = authViewModel::updateEmail,
                onResetPassword =
                    authViewModel::sendPasswordReset,
                onBackToLogin = {
                    authViewModel.clearMessage()
                    navController.popBackStack()
                },
            )
        }
    }
}

@Composable
private fun MainShell(
    email: String,
    appDataViewModel: AppDataViewModel,
    translationViewModel: TranslationViewModel,
    onSignOut: () -> Unit,
) {
    val state by appDataViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by rememberSaveable {
        mutableStateOf(MainTab.HOME)
    }
    var translateMode by rememberSaveable {
        mutableStateOf(TranslateMode.TEXT)
    }
    var showAdmin by rememberSaveable {
        mutableStateOf(false)
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            appDataViewModel.clearMessage()
        }
    }

    if (showAdmin) {
        LaunchedEffect(Unit) {
            appDataViewModel.loadAdminFeedback()
        }

        AdminScreen(
            feedback = state.adminFeedback,
            onBack = { showAdmin = false },
            onMarkReviewed =
                appDataViewModel::markFeedbackReviewed,
        )
        return
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        },
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    MainTab.HOME ->
                                        Icons.Rounded.Home

                                    MainTab.TRANSLATE ->
                                        Icons.Rounded.Translate

                                    MainTab.HISTORY ->
                                        Icons.Rounded.History

                                    MainTab.SAVED ->
                                        Icons.Rounded.Bookmark

                                    MainTab.PROFILE ->
                                        Icons.Rounded.Person
                                },
                                contentDescription = tab.label,
                            )
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        val contentModifier = Modifier.padding(padding)

        androidx.compose.foundation.layout.Box(
            modifier = contentModifier,
        ) {
            when (selectedTab) {
                MainTab.HOME -> HomeScreen(
                    displayName =
                        state.profile?.displayName.orEmpty(),
                    analytics = state.analytics,
                    recent = state.history,
                    onTranslate = { mode ->
                        translateMode = mode
                        selectedTab = MainTab.TRANSLATE
                    },
                )

                MainTab.TRANSLATE ->
                    TranslationHubScreen(
                        viewModel = translationViewModel,
                        initialMode = translateMode,
                        onSaveEntry =
                            appDataViewModel::saveEntry,
                    )

                MainTab.HISTORY ->
                    HistoryScreen(state.history)

                MainTab.SAVED ->
                    SavedScreen(
                        items = state.savedVocabulary,
                        onRemove =
                            appDataViewModel::removeSaved,
                    )

                MainTab.PROFILE ->
                    ProfileScreen(
                        profile = state.profile,
                        email = email,
                        analytics = state.analytics,
                        onUpdateName =
                            appDataViewModel::updateDisplayName,
                        onSubmitFeedback = { rating, comment ->
                            appDataViewModel.submitFeedback(
                                email = email,
                                rating = rating,
                                comment = comment,
                            )
                        },
                        onOpenAdmin = {
                            showAdmin = true
                        },
                        onSignOut = {
                            appDataViewModel.clearUser()
                            onSignOut()
                        },
                    )
            }
        }
    }
}
