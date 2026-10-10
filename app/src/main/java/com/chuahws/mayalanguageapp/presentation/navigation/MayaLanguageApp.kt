package com.chuahws.mayalanguageapp.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chuahws.mayalanguageapp.presentation.admin.AdminScreen
import com.chuahws.mayalanguageapp.presentation.analytics.AnalyticsScreen
import com.chuahws.mayalanguageapp.presentation.app.AppDataViewModel
import com.chuahws.mayalanguageapp.presentation.auth.AuthViewModel
import com.chuahws.mayalanguageapp.presentation.auth.LoginScreen
import com.chuahws.mayalanguageapp.presentation.auth.RegisterScreen
import com.chuahws.mayalanguageapp.presentation.auth.ResetPasswordScreen
import com.chuahws.mayalanguageapp.presentation.feedback.FeedbackScreen
import com.chuahws.mayalanguageapp.presentation.history.HistoryScreen
import com.chuahws.mayalanguageapp.presentation.home.HomeScreen
import com.chuahws.mayalanguageapp.presentation.profile.ProfileScreen
import com.chuahws.mayalanguageapp.presentation.saved.SavedScreen
import com.chuahws.mayalanguageapp.presentation.translation.ImageTranslationScreen
import com.chuahws.mayalanguageapp.presentation.translation.TranslationScreen
import com.chuahws.mayalanguageapp.presentation.translation.TranslationViewModel
import com.chuahws.mayalanguageapp.presentation.translation.VoiceTranslationScreen

private enum class AppPage {
    HOME,
    TEXT_TRANSLATION,
    IMAGE_TRANSLATION,
    VOICE_TRANSLATION,
    DICTIONARY,
    HISTORY,
    ANALYTICS,
    FEEDBACK,
    PROFILE,
    ADMIN,
}

private enum class BottomItem(
    val label: String,
) {
    HOME("Home"),
    DICTIONARY("Dictionary"),
    HISTORY("History"),
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
            authViewModel = authViewModel,
            appDataViewModel = appDataViewModel,
            translationViewModel = translationViewModel,
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
                onDisplayNameChanged = authViewModel::updateDisplayName,
                onEmailChanged = authViewModel::updateEmail,
                onPasswordChanged = authViewModel::updatePassword,
                onConfirmPasswordChanged = authViewModel::updateConfirmPassword,
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
                onResetPassword = authViewModel::sendPasswordReset,
                onBackToLogin = {
                    authViewModel.clearMessage()
                    navController.popBackStack()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(
    email: String,
    authViewModel: AuthViewModel,
    appDataViewModel: AppDataViewModel,
    translationViewModel: TranslationViewModel,
) {
    val state by appDataViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var page by rememberSaveable { mutableStateOf(AppPage.HOME) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            appDataViewModel.clearMessage()
        }
    }

    if (page == AppPage.ADMIN) {
        LaunchedEffect(Unit) {
            appDataViewModel.loadAdminFeedback()
        }

        AdminScreen(
            feedback = state.adminFeedback,
            onBack = { page = AppPage.PROFILE },
            onMarkReviewed = appDataViewModel::markFeedbackReviewed,
        )
        return
    }

    val pageTitle = when (page) {
        AppPage.HOME -> "Maya Language App"
        AppPage.TEXT_TRANSLATION -> "Text Translation"
        AppPage.IMAGE_TRANSLATION -> "Image Translation"
        AppPage.VOICE_TRANSLATION -> "Voice Translation"
        AppPage.DICTIONARY -> "Dictionary"
        AppPage.HISTORY -> "History"
        AppPage.ANALYTICS -> "Analytics"
        AppPage.FEEDBACK -> "Feedback"
        AppPage.PROFILE -> "Profile"
        AppPage.ADMIN -> "Admin Dashboard"
    }

    val isDetailPage = page in setOf(
        AppPage.TEXT_TRANSLATION,
        AppPage.IMAGE_TRANSLATION,
        AppPage.VOICE_TRANSLATION,
        AppPage.ANALYTICS,
        AppPage.FEEDBACK,
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(pageTitle) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isDetailPage) {
                                page = AppPage.HOME
                            }
                        },
                    ) {
                        Icon(
                            imageVector = if (isDetailPage) {
                                Icons.Rounded.ArrowBack
                            } else {
                                Icons.Rounded.Menu
                            },
                            contentDescription = if (isDetailPage) {
                                "Back"
                            } else {
                                "Menu"
                            },
                        )
                    }
                },
                actions = {
                    if (page == AppPage.HOME) {
                        IconButton(onClick = {}) {
                            Icon(
                                Icons.Rounded.NotificationsNone,
                                contentDescription = "Notifications",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    titleContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                BottomItem.entries.forEach { item ->
                    val itemPage = when (item) {
                        BottomItem.HOME -> AppPage.HOME
                        BottomItem.DICTIONARY -> AppPage.DICTIONARY
                        BottomItem.HISTORY -> AppPage.HISTORY
                        BottomItem.PROFILE -> AppPage.PROFILE
                    }

                    NavigationBarItem(
                        selected = page == itemPage,
                        onClick = { page = itemPage },
                        icon = {
                            Icon(
                                imageVector = when (item) {
                                    BottomItem.HOME -> Icons.Rounded.Home
                                    BottomItem.DICTIONARY -> Icons.Rounded.Book
                                    BottomItem.HISTORY -> Icons.Rounded.History
                                    BottomItem.PROFILE -> Icons.Rounded.Person
                                },
                                contentDescription = item.label,
                            )
                        },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (page) {
                AppPage.HOME -> HomeScreen(
                    displayName = state.profile?.displayName.orEmpty(),
                    onTextTranslation = { page = AppPage.TEXT_TRANSLATION },
                    onImageTranslation = { page = AppPage.IMAGE_TRANSLATION },
                    onVoiceTranslation = { page = AppPage.VOICE_TRANSLATION },
                    onHistory = { page = AppPage.HISTORY },
                    onAnalytics = { page = AppPage.ANALYTICS },
                    onFeedback = { page = AppPage.FEEDBACK },
                    onProfile = { page = AppPage.PROFILE },
                )

                AppPage.TEXT_TRANSLATION -> TranslationScreen(
                    viewModel = translationViewModel,
                    onSaveEntry = appDataViewModel::saveEntry,
                )

                AppPage.IMAGE_TRANSLATION -> ImageTranslationScreen(
                    viewModel = translationViewModel,
                    onSaveEntry = appDataViewModel::saveEntry,
                )

                AppPage.VOICE_TRANSLATION -> VoiceTranslationScreen(
                    viewModel = translationViewModel,
                    onSaveEntry = appDataViewModel::saveEntry,
                )

                AppPage.DICTIONARY -> SavedScreen(
                    items = state.savedVocabulary,
                    onRemove = appDataViewModel::removeSaved,
                )

                AppPage.HISTORY -> HistoryScreen(state.history)

                AppPage.ANALYTICS -> AnalyticsScreen(state.analytics)

                AppPage.FEEDBACK -> FeedbackScreen(
                    email = email,
                    onSubmit = { rating, comment ->
                        appDataViewModel.submitFeedback(
                            email = email,
                            rating = rating,
                            comment = comment,
                        )
                    },
                )

                AppPage.PROFILE -> ProfileScreen(
                    profile = state.profile,
                    email = email,
                    onUpdateName = appDataViewModel::updateDisplayName,
                    onResetPassword = authViewModel::sendPasswordReset,
                    onOpenAdmin = { page = AppPage.ADMIN },
                    onSignOut = {
                        appDataViewModel.clearUser()
                        authViewModel.signOut()
                    },
                )

                AppPage.ADMIN -> Unit
            }
        }
    }
}
