package com.chuahws.mayalanguageapp.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.launch

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

private data class DrawerItem(
    val page: AppPage,
    val label: String,
    val icon: ImageVector,
)

private val translateDrawerItems = listOf(
    DrawerItem(
        AppPage.TEXT_TRANSLATION,
        "Text",
        Icons.Rounded.Keyboard,
    ),
    DrawerItem(
        AppPage.IMAGE_TRANSLATION,
        "Photo",
        Icons.Rounded.CameraAlt,
    ),
    DrawerItem(
        AppPage.VOICE_TRANSLATION,
        "Voice",
        Icons.Rounded.Mic,
    ),
)

private val learnDrawerItems = listOf(
    DrawerItem(
        AppPage.DICTIONARY,
        "Dictionary",
        Icons.Rounded.MenuBook,
    ),
    DrawerItem(
        AppPage.SAVED,
        "Saved words",
        Icons.Rounded.Bookmark,
    ),
)

private val activityDrawerItems = listOf(
    DrawerItem(
        AppPage.HISTORY,
        "History",
        Icons.Rounded.History,
    ),
    DrawerItem(
        AppPage.ANALYTICS,
        "My activity",
        Icons.Rounded.Analytics,
    ),
)

private val accountDrawerItems = listOf(
    DrawerItem(
        AppPage.FEEDBACK,
        "Feedback",
        Icons.Rounded.Feedback,
    ),
    DrawerItem(
        AppPage.PROFILE,
        "Profile",
        Icons.Rounded.Person,
    ),
)

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
            LaunchedEffect(
                state.registrationCompleted,
            ) {
                if (state.registrationCompleted) {
                    navController.popBackStack()
                }
            }

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
    val drawerState = rememberDrawerState(
        initialValue = DrawerValue.Closed,
    )
    val scope = rememberCoroutineScope()

    var page by rememberSaveable {
        mutableStateOf(AppPage.HOME)
    }
    var adminUserView by rememberSaveable {
        mutableStateOf(false)
    }

    fun openDrawer() {
        scope.launch {
            drawerState.open()
        }
    }

    fun goTo(destination: AppPage) {
        page = destination
        scope.launch {
            drawerState.close()
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            val retryTarget =
                when {
                    message.startsWith(
                        "Could not refresh your data"
                    ) -> "user"

                    message.startsWith(
                        "Could not load administrator data"
                    ) -> "admin"

                    message.startsWith(
                        "Could not load the dictionary"
                    ) -> "dictionary"

                    else -> null
                }

            val result =
                snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel =
                        if (retryTarget != null) {
                            "Retry"
                        } else {
                            null
                        },
                )

            appDataViewModel.clearMessage()

            if (
                result ==
                    SnackbarResult.ActionPerformed
            ) {
                when (retryTarget) {
                    "user" ->
                        appDataViewModel.refresh()

                    "admin" ->
                        appDataViewModel
                            .loadAdminData()

                    "dictionary" ->
                        appDataViewModel
                            .loadDictionary(
                                state.dictionaryQuery
                            )
                }
            }
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
        state.profile?.accountStatus,
    ) {
        if (
            state.profile?.accountStatus ==
                "suspended"
        ) {
            appDataViewModel.clearUser()
            authViewModel.signOut()
        }
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
            translations =
                state.adminTranslations,
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
            onImportDictionaryCsv =
                appDataViewModel::importAdminDictionaryCsv,
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

    BackHandler(
        enabled = drawerState.isOpen,
    ) {
        scope.launch {
            drawerState.close()
        }
    }

    BackHandler(
        enabled =
            drawerState.isClosed &&
                page != AppPage.HOME,
    ) {
        page = AppPage.HOME
    }

    val pageTitle = when (page) {
        AppPage.HOME -> "Maya Translate"
        AppPage.TEXT_TRANSLATION -> "Text translation"
        AppPage.IMAGE_TRANSLATION -> "Photo translation"
        AppPage.VOICE_TRANSLATION -> "Voice translation"
        AppPage.DICTIONARY -> "Dictionary"
        AppPage.SAVED -> "Saved words"
        AppPage.HISTORY -> "History"
        AppPage.ANALYTICS -> "My activity"
        AppPage.FEEDBACK -> "Feedback"
        AppPage.PROFILE -> "Profile"
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 18.dp,
                    ),
                ) {
                    Text(
                        text = "Maya Translate",
                        style =
                            MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier =
                            Modifier.padding(
                                horizontal = 12.dp,
                            ),
                    )

                    Text(
                        text =
                            state.profile?.displayName
                                ?.ifBlank { email }
                                ?: email,
                        style =
                            MaterialTheme.typography.bodyMedium,
                        modifier =
                            Modifier.padding(
                                start = 12.dp,
                                top = 10.dp,
                                end = 12.dp,
                            ),
                    )

                    Text(
                        text = email,
                        style =
                            MaterialTheme.typography.bodySmall,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        modifier =
                            Modifier.padding(
                                start = 12.dp,
                                top = 2.dp,
                                end = 12.dp,
                                bottom = 16.dp,
                            ),
                    )

                    NavigationDrawerItem(
                        selected =
                            page == AppPage.HOME,
                        onClick = {
                            goTo(AppPage.HOME)
                        },
                        icon = {
                            Icon(
                                Icons.Rounded.Home,
                                contentDescription = null,
                            )
                        },
                        label = {
                            Text("Home")
                        },
                    )

                    DrawerSectionLabel("Translate")
                    DrawerItems(
                        items = translateDrawerItems,
                        currentPage = page,
                        onSelect = ::goTo,
                    )

                    DrawerSectionLabel("Learn")
                    DrawerItems(
                        items = learnDrawerItems,
                        currentPage = page,
                        onSelect = ::goTo,
                    )

                    DrawerSectionLabel("Activity")
                    DrawerItems(
                        items = activityDrawerItems,
                        currentPage = page,
                        onSelect = ::goTo,
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(
                            vertical = 10.dp,
                        ),
                    )

                    DrawerSectionLabel("Account")
                    DrawerItems(
                        items = accountDrawerItems,
                        currentPage = page,
                        onSelect = ::goTo,
                    )
                }
            }
        },
    ) {
        Scaffold(
            snackbarHost = {
                SnackbarHost(snackbarHostState)
            },
            topBar = {
                if (page != AppPage.PROFILE) {
                    Column {
                        CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    text = pageTitle,
                                    style =
                                        MaterialTheme
                                            .typography
                                            .titleLarge,
                                )
                            },
                            navigationIcon = {
                                IconButton(
                                    onClick = ::openDrawer,
                                ) {
                                    Icon(
                                        imageVector =
                                            Icons.Rounded.Menu,
                                        contentDescription =
                                            "Open menu",
                                    )
                                }
                            },
                            colors =
                                TopAppBarDefaults
                                    .centerAlignedTopAppBarColors(
                                        containerColor =
                                            MaterialTheme
                                                .colorScheme
                                                .surface,
                                        titleContentColor =
                                            MaterialTheme
                                                .colorScheme
                                                .onSurface,
                                        navigationIconContentColor =
                                            MaterialTheme
                                                .colorScheme
                                                .primary,
                                    ),
                        )

                        if (
                            state.loading ||
                            state.dictionaryLoading
                        ) {
                            LinearProgressIndicator()
                        }
                    }
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor =
                        MaterialTheme
                            .colorScheme.surface,
                    tonalElevation = 4.dp,
                ) {
                    BottomItem.entries.forEach { item ->
                        val itemPage =
                            when (item) {
                                BottomItem.HOME ->
                                    AppPage.HOME

                                BottomItem.SAVED ->
                                    AppPage.SAVED

                                BottomItem.HISTORY ->
                                    AppPage.HISTORY

                                BottomItem.PROFILE ->
                                    AppPage.PROFILE
                            }

                        NavigationBarItem(
                            selected =
                                page == itemPage,
                            onClick = {
                                page = itemPage
                            },
                            icon = {
                                Icon(
                                    imageVector =
                                        when (item) {
                                            BottomItem.HOME ->
                                                Icons.Rounded.Home

                                            BottomItem.SAVED ->
                                                Icons.Rounded.Bookmark

                                            BottomItem.HISTORY ->
                                                Icons.Rounded.History

                                            BottomItem.PROFILE ->
                                                Icons.Rounded.Person
                                        },
                                    contentDescription =
                                        item.label,
                                )
                            },
                            label = {
                                Text(
                                    item.label,
                                    style =
                                        MaterialTheme
                                            .typography
                                            .labelMedium,
                                )
                            },
                            colors =
                                NavigationBarItemDefaults
                                    .colors(
                                        selectedIconColor =
                                            MaterialTheme
                                                .colorScheme
                                                .primary,
                                        selectedTextColor =
                                            MaterialTheme
                                                .colorScheme
                                                .primary,
                                        indicatorColor =
                                            MaterialTheme
                                                .colorScheme
                                                .primaryContainer,
                                        unselectedIconColor =
                                            MaterialTheme
                                                .colorScheme
                                                .onSurfaceVariant,
                                        unselectedTextColor =
                                            MaterialTheme
                                                .colorScheme
                                                .onSurfaceVariant,
                                    ),
                        )
                    }
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier.padding(
                    innerPadding
                ),
            ) {
                when (page) {
                    AppPage.HOME ->
                        HomeScreen(
                            displayName =
                                state.profile
                                    ?.displayName
                                    .orEmpty(),
                            onTextTranslation = {
                                page =
                                    AppPage
                                        .TEXT_TRANSLATION
                            },
                            onImageTranslation = {
                                page =
                                    AppPage
                                        .IMAGE_TRANSLATION
                            },
                            onVoiceTranslation = {
                                page =
                                    AppPage
                                        .VOICE_TRANSLATION
                            },
                            onSaved = {
                                page = AppPage.SAVED
                            },
                            onHistory = {
                                page = AppPage.HISTORY
                            },
                            onAnalytics = {
                                page =
                                    AppPage.ANALYTICS
                            },
                            onFeedback = {
                                page = AppPage.FEEDBACK
                            },
                            onDictionary = {
                                page =
                                    AppPage.DICTIONARY
                            },
                            onProfile = {
                                page = AppPage.PROFILE
                            },
                        )

                    AppPage.TEXT_TRANSLATION ->
                        TranslationScreen(
                            viewModel =
                                translationViewModel,
                            onSaveEntry =
                                appDataViewModel
                                    ::saveEntry,
                        )

                    AppPage.IMAGE_TRANSLATION ->
                        ImageTranslationScreen(
                            viewModel =
                                translationViewModel,
                            onSaveEntry =
                                appDataViewModel
                                    ::saveEntry,
                        )

                    AppPage.VOICE_TRANSLATION ->
                        VoiceTranslationScreen(
                            viewModel =
                                translationViewModel,
                            onSaveEntry =
                                appDataViewModel
                                    ::saveEntry,
                        )

                    AppPage.DICTIONARY ->
                        DictionaryScreen(
                            entries =
                                state
                                    .dictionaryEntries,
                            loading =
                                state
                                    .dictionaryLoading,
                            hasMore =
                                state
                                    .dictionaryHasMore,
                            onSearch =
                                appDataViewModel
                                    ::loadDictionary,
                            onLoadMore =
                                appDataViewModel
                                    ::loadMoreDictionary,
                            onSaveEntry =
                                appDataViewModel
                                    ::saveEntry,
                        )

                    AppPage.SAVED ->
                        SavedScreen(
                            items =
                                state
                                    .savedVocabulary,
                            onRemove =
                                appDataViewModel
                                    ::removeSaved,
                        )

                    AppPage.HISTORY ->
                        HistoryScreen(
                            history = state.history,
                            onDelete =
                                appDataViewModel
                                    ::deleteHistory,
                        )

                    AppPage.ANALYTICS ->
                        AnalyticsScreen(
                            analytics =
                                state.analytics,
                            history = state.history,
                        )

                    AppPage.FEEDBACK ->
                        FeedbackScreen(
                            email = email,
                            submissionVersion =
                                state
                                    .feedbackSubmissionVersion,
                            submitting =
                                state
                                    .feedbackSubmitting,
                            onSubmit = {
                                    rating,
                                    comment,
                                ->
                                appDataViewModel
                                    .submitFeedback(
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
                            onOpenMenu = ::openDrawer,
                            onUpdateName =
                                appDataViewModel
                                    ::updateDisplayName,
                            onResetPassword =
                                authViewModel
                                    ::sendPasswordReset,
                            onOpenAdmin = {
                                adminUserView = false
                            },
                            onSignOut = {
                                appDataViewModel
                                    .clearUser()
                                authViewModel
                                    .signOut()
                            },
                        )
                }
            }
        }
    }
}

@Composable
private fun DrawerSectionLabel(
    text: String,
) {
    Text(
        text = text,
        style =
            MaterialTheme.typography.labelMedium,
        color =
            MaterialTheme.colorScheme
                .onSurfaceVariant,
        modifier = Modifier.padding(
            start = 16.dp,
            top = 14.dp,
            end = 16.dp,
            bottom = 4.dp,
        ),
    )
}

@Composable
private fun DrawerItems(
    items: List<DrawerItem>,
    currentPage: AppPage,
    onSelect: (AppPage) -> Unit,
) {
    items.forEach { item ->
        NavigationDrawerItem(
            selected =
                currentPage == item.page,
            onClick = {
                onSelect(item.page)
            },
            icon = {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                )
            },
            label = {
                Text(item.label)
            },
        )
    }
}
