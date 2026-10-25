package com.chuahws.mayalanguageapp.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.chuahws.mayalanguageapp.presentation.dictionary.DictionaryScreen
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
    SAVED,
    HISTORY,
    ANALYTICS,
    FEEDBACK,
    PROFILE,
}

private enum class BottomItem(
    val label: String,
) {
    HOME("Home"),
    SAVED("Saved"),
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
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()

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
    val state by authViewModel.uiState.collectAsStateWithLifecycle()

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(
    email: String,
    authViewModel: AuthViewModel,
    appDataViewModel: AppDataViewModel,
    translationViewModel: TranslationViewModel,
) {
    val state by appDataViewModel.uiState.collectAsStateWithLifecycle()
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var page by rememberSaveable { mutableStateOf(AppPage.HOME) }
    var adminUserView by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            appDataViewModel.clearMessage()
        }
    }

    LaunchedEffect(authState.message) {
        authState.message?.let {
            snackbarHostState.showSnackbar(it)
            authViewModel.clearMessage()
        }
    }

    if (state.profile == null && state.loading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    LaunchedEffect(
        state.profile?.role,
        adminUserView,
    ) {
        if (
            state.profile?.role == "admin" &&
            !adminUserView
        ) {
            appDataViewModel.loadAdminData()
        }
    }

    LaunchedEffect(page) {
        if (
            page == AppPage.DICTIONARY &&
            state.dictionaryEntries.isEmpty()
        ) {
            appDataViewModel.loadDictionary()
        }
    }

    if (
        state.profile?.role == "admin" &&
        !adminUserView
    ) {
        AdminScreen(
            displayName =
                state.profile?.displayName.orEmpty(),
            email = email,
            currentUserId =
                state.userId.orEmpty(),
            users = state.adminUsers,
            feedback = state.adminFeedback,
            analytics = state.adminAnalytics,
            dictionaryEntries =
                state.adminDictionaryEntries,
            loading = state.adminLoading,
            snackbarHostState =
                snackbarHostState,
            onToggleUserStatus =
                appDataViewModel::setUserAccountStatus,
            onMarkReviewed =
                appDataViewModel::markFeedbackReviewed,
            onSearchDictionary =
                appDataViewModel::searchAdminDictionary,
            onSaveDictionaryEntry =
                appDataViewModel::saveAdminDictionaryEntry,
            onDeleteDictionaryEntry =
                appDataViewModel::deleteAdminDictionaryEntry,
            onSwitchToUserView = {
                adminUserView = true
                page = AppPage.HOME
            },
            onSignOut = {
                appDataViewModel.clearUser()
                authViewModel.signOut()
            },
        )
        return
    }

    BackHandler(enabled = page != AppPage.HOME) {
        page = AppPage.HOME
    }

    val pageTitle = when (page) {
        AppPage.HOME -> "Maya Language App"
        AppPage.TEXT_TRANSLATION -> "Text Translation"
        AppPage.IMAGE_TRANSLATION -> "Image Translation"
        AppPage.VOICE_TRANSLATION -> "Voice Translation"
        AppPage.DICTIONARY -> "Dictionary"
        AppPage.SAVED -> "Saved vocabulary"
        AppPage.HISTORY -> "History"
        AppPage.ANALYTICS -> "Analytics"
        AppPage.FEEDBACK -> "Feedback"
        AppPage.PROFILE -> "Profile"
    }

    val isDetailPage = page in setOf(
        AppPage.TEXT_TRANSLATION,
        AppPage.IMAGE_TRANSLATION,
        AppPage.VOICE_TRANSLATION,
        AppPage.DICTIONARY,
        AppPage.ANALYTICS,
        AppPage.FEEDBACK,
    )

    val homeTopBar = page == AppPage.HOME

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        },
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        Text(pageTitle)
                    },
                    navigationIcon = {
                        if (isDetailPage) {
                            IconButton(
                                onClick = {
                                    page = AppPage.HOME
                                },
                            ) {
                                Icon(
                                    imageVector =
                                        Icons.Rounded.ArrowBack,
                                    contentDescription = "Back",
                                )
                            }
                        }
                    },
                    colors =
                        TopAppBarDefaults
                            .centerAlignedTopAppBarColors(
                                containerColor = if (homeTopBar) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                titleContentColor = if (homeTopBar) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                navigationIconContentColor =
                                    MaterialTheme.colorScheme.primary,
                            ),
                )

                if (state.loading || state.dictionaryLoading) {
                    LinearProgressIndicator()
                }
            }
        },
        bottomBar = {
            NavigationBar {
                BottomItem.entries.forEach { item ->
                    val itemPage = when (item) {
                        BottomItem.HOME -> AppPage.HOME
                        BottomItem.SAVED -> AppPage.SAVED
                        BottomItem.HISTORY -> AppPage.HISTORY
                        BottomItem.PROFILE -> AppPage.PROFILE
                    }

                    NavigationBarItem(
                        selected = page == itemPage,
                        onClick = {
                            page = itemPage
                        },
                        icon = {
                            Icon(
                                imageVector = when (item) {
                                    BottomItem.HOME ->
                                        Icons.Rounded.Home
                                    BottomItem.SAVED ->
                                        Icons.Rounded.Bookmark
                                    BottomItem.HISTORY ->
                                        Icons.Rounded.History
                                    BottomItem.PROFILE ->
                                        Icons.Rounded.Person
                                },
                                contentDescription = item.label,
                            )
                        },
                        label = {
                            Text(item.label)
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.padding(innerPadding),
        ) {
            when (page) {
                AppPage.HOME -> HomeScreen(
                    displayName =
                        state.profile?.displayName.orEmpty(),
                    onTextTranslation = {
                        page = AppPage.TEXT_TRANSLATION
                    },
                    onImageTranslation = {
                        page = AppPage.IMAGE_TRANSLATION
                    },
                    onVoiceTranslation = {
                        page = AppPage.VOICE_TRANSLATION
                    },
                    onSaved = {
                        page = AppPage.SAVED
                    },
                    onHistory = {
                        page = AppPage.HISTORY
                    },
                    onAnalytics = {
                        page = AppPage.ANALYTICS
                    },
                    onFeedback = {
                        page = AppPage.FEEDBACK
                    },
                    onDictionary = {
                        page = AppPage.DICTIONARY
                    },
                    onProfile = {
                        page = AppPage.PROFILE
                    },
                )

                AppPage.TEXT_TRANSLATION ->
                    TranslationScreen(
                        viewModel = translationViewModel,
                        onSaveEntry =
                            appDataViewModel::saveEntry,
                    )

                AppPage.IMAGE_TRANSLATION ->
                    ImageTranslationScreen(
                        viewModel = translationViewModel,
                        onSaveEntry =
                            appDataViewModel::saveEntry,
                    )

                AppPage.VOICE_TRANSLATION ->
                    VoiceTranslationScreen(
                        viewModel = translationViewModel,
                        onSaveEntry =
                            appDataViewModel::saveEntry,
                    )

                AppPage.DICTIONARY ->
                    DictionaryScreen(
                        entries =
                            state.dictionaryEntries,
                        loading =
                            state.dictionaryLoading,
                        onSearch =
                            appDataViewModel::loadDictionary,
                        onSaveEntry =
                            appDataViewModel::saveEntry,
                    )

                AppPage.SAVED ->
                    SavedScreen(
                        items = state.savedVocabulary,
                        onRemove =
                            appDataViewModel::removeSaved,
                    )

                AppPage.HISTORY ->
                    HistoryScreen(
                        history = state.history,
                        onDelete =
                            appDataViewModel::deleteHistory,
                    )

                AppPage.ANALYTICS ->
                    AnalyticsScreen(
                        analytics = state.analytics,
                        history = state.history,
                    )

                AppPage.FEEDBACK ->
                    FeedbackScreen(
                        email = email,
                        submissionVersion =
                            state.feedbackSubmissionVersion,
                        submitting =
                            state.feedbackSubmitting,
                        onSubmit = { rating, comment ->
                            appDataViewModel.submitFeedback(
                                email = email,
                                rating = rating,
                                comment = comment,
                            )
                        },
                    )

                AppPage.PROFILE ->
                    ProfileScreen(
                        profile = state.profile,
                        email = email,
                        onUpdateName =
                            appDataViewModel::updateDisplayName,
                        onResetPassword =
                            authViewModel::sendPasswordReset,
                        onOpenAdmin = {
                            adminUserView = false
                        },
                        onSignOut = {
                            appDataViewModel.clearUser()
                            authViewModel.signOut()
                        },
                    )
            }
        }
    }
}
