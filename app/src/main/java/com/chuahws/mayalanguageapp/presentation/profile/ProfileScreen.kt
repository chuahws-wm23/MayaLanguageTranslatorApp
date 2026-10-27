package com.chuahws.mayalanguageapp.presentation.profile

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.domain.model.UserProfile
import com.chuahws.mayalanguageapp.presentation.components.AppGradientBackground
import com.chuahws.mayalanguageapp.presentation.components.SoftBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ProfilePage {
    OVERVIEW,
    PHOTO,
    EDIT,
    PASSWORD,
    LANGUAGE,
    ABOUT,
}

@Composable
fun ProfileScreen(
    profile: UserProfile?,
    email: String,
    onUpdateName: (String) -> Unit,
    onResetPassword: () -> Unit,
    onOpenAdmin: () -> Unit,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val profileKey = remember(profile?.userId, email) {
        "profile_photo_" +
            (profile?.userId
                ?.takeIf { it.isNotBlank() }
                ?: email)
    }
    val preferences = remember {
        context.getSharedPreferences(
            "maya_profile_preferences",
            Context.MODE_PRIVATE,
        )
    }

    var page by rememberSaveable {
        mutableStateOf(ProfilePage.OVERVIEW)
    }
    var photoUri by rememberSaveable(profileKey) {
        mutableStateOf(
            preferences.getString(
                profileKey,
                null,
            )
        )
    }

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver
                    .takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
            }

            photoUri = uri.toString()
            preferences.edit()
                .putString(
                    profileKey,
                    uri.toString(),
                )
                .apply()
        }
    }

    BackHandler(
        enabled = page != ProfilePage.OVERVIEW,
    ) {
        page = ProfilePage.OVERVIEW
    }

    when (page) {
        ProfilePage.OVERVIEW ->
            ProfileOverview(
                profile = profile,
                email = email,
                photoUri = photoUri,
                context = context,
                onOpenPhoto = {
                    page = ProfilePage.PHOTO
                },
                onOpenEdit = {
                    page = ProfilePage.EDIT
                },
                onOpenPassword = {
                    page = ProfilePage.PASSWORD
                },
                onOpenLanguage = {
                    page = ProfilePage.LANGUAGE
                },
                onOpenAbout = {
                    page = ProfilePage.ABOUT
                },
                onOpenAdmin = onOpenAdmin,
                onSignOut = onSignOut,
            )

        ProfilePage.PHOTO ->
            PhotoSettingsPage(
                photoUri = photoUri,
                context = context,
                onChoosePhoto = {
                    photoLauncher.launch(
                        arrayOf("image/*")
                    )
                },
                onRemovePhoto = {
                    photoUri = null
                    preferences.edit()
                        .remove(profileKey)
                        .apply()
                },
                onBack = {
                    page = ProfilePage.OVERVIEW
                },
            )

        ProfilePage.EDIT ->
            EditProfilePage(
                currentName =
                    profile?.displayName.orEmpty(),
                onSave = { name ->
                    onUpdateName(name)
                    page = ProfilePage.OVERVIEW
                },
                onBack = {
                    page = ProfilePage.OVERVIEW
                },
            )

        ProfilePage.PASSWORD ->
            PasswordSettingsPage(
                email = email,
                onSendReset = onResetPassword,
                onBack = {
                    page = ProfilePage.OVERVIEW
                },
            )

        ProfilePage.LANGUAGE ->
            LanguageSettingsPage(
                onBack = {
                    page = ProfilePage.OVERVIEW
                },
            )

        ProfilePage.ABOUT ->
            AboutPage(
                onBack = {
                    page = ProfilePage.OVERVIEW
                },
            )
    }
}

@Composable
private fun ProfileOverview(
    profile: UserProfile?,
    email: String,
    photoUri: String?,
    context: Context,
    onOpenPhoto: () -> Unit,
    onOpenEdit: () -> Unit,
    onOpenPassword: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenAdmin: () -> Unit,
    onSignOut: () -> Unit,
) {
    val memberSince = remember(
        profile?.createdAtMillis
    ) {
        formatMemberSince(
            profile?.createdAtMillis ?: 0L
        )
    }

    val accountStatus =
        profile?.accountStatus
            ?.takeIf { it.isNotBlank() }
            ?.replaceFirstChar {
                it.uppercase()
            }
            ?: "Active"

    AppGradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(
                    PaddingValues(
                        start = 18.dp,
                        top = 16.dp,
                        end = 18.dp,
                        bottom = 112.dp,
                    )
                ),
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(14.dp),
        ) {
            ProfilePageTitle(
                title = "Profile",
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape =
                    MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor =
                        Color.Transparent,
                ),
                elevation =
                    CardDefaults.cardElevation(
                        defaultElevation = 4.dp,
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme
                                        .colorScheme.primary,
                                    MaterialTheme
                                        .colorScheme.primary
                                        .copy(alpha = 0.88f),
                                    MaterialTheme
                                        .colorScheme.secondary,
                                )
                            )
                        )
                        .padding(24.dp),
                    contentAlignment =
                        Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment =
                            Alignment.CenterHorizontally,
                        verticalArrangement =
                            Arrangement.spacedBy(8.dp),
                    ) {
                        Box {
                            ProfilePhoto(
                                uri = photoUri,
                                context = context,
                            )

                            Surface(
                                modifier = Modifier
                                    .align(
                                        Alignment.BottomEnd
                                    )
                                    .size(36.dp)
                                    .clickable(
                                        onClick =
                                            onOpenPhoto
                                    ),
                                shape = CircleShape,
                                color = Color.White,
                                shadowElevation = 3.dp,
                            ) {
                                Box(
                                    contentAlignment =
                                        Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector =
                                            Icons.Rounded
                                                .PhotoCamera,
                                        contentDescription =
                                            "Profile photo settings",
                                        tint =
                                            MaterialTheme
                                                .colorScheme.primary,
                                        modifier =
                                            Modifier.size(
                                                18.dp
                                            ),
                                    )
                                }
                            }
                        }

                        Text(
                            text =
                                profile?.displayName
                                    ?.ifBlank { email }
                                    ?: email,
                            color = Color.White,
                            style =
                                MaterialTheme
                                    .typography.titleLarge,
                            fontWeight =
                                FontWeight.Bold,
                        )

                        Text(
                            text = email,
                            color = Color.White
                                .copy(alpha = 0.84f),
                            style =
                                MaterialTheme
                                    .typography.bodyMedium,
                        )

                        Row(
                            horizontalArrangement =
                                Arrangement.spacedBy(8.dp),
                        ) {
                            SoftBadge(
                                text =
                                    if (
                                        profile?.role ==
                                            "admin"
                                    ) {
                                        "Administrator"
                                    } else {
                                        "Learner"
                                    },
                                color = Color.White,
                            )

                            SoftBadge(
                                text = accountStatus,
                                color = Color.White,
                            )
                        }
                    }
                }
            }

            ProfileAction(
                icon = Icons.Rounded.PhotoCamera,
                title =
                    if (photoUri == null) {
                        "Add profile photo"
                    } else {
                        "Change profile photo"
                    },
                subtitle =
                    "Manage your profile picture",
                onClick = onOpenPhoto,
            )

            ProfileAction(
                icon = Icons.Rounded.Edit,
                title = "Edit profile",
                subtitle =
                    "Update your display name",
                onClick = onOpenEdit,
            )

            ProfileAction(
                icon = Icons.Rounded.LockReset,
                title = "Change password",
                subtitle =
                    "Manage password recovery",
                onClick = onOpenPassword,
            )

            ProfileInfo(
                icon = Icons.Rounded.Person,
                title = "Member since",
                subtitle =
                    "Account creation date",
                value = memberSince,
            )

            ProfileAction(
                icon = Icons.Rounded.Language,
                title = "App language",
                subtitle =
                    "English",
                onClick = onOpenLanguage,
            )

            ProfileAction(
                icon = Icons.Rounded.Info,
                title = "About app",
                subtitle =
                    "Project information and scope",
                onClick = onOpenAbout,
            )

            if (profile?.role == "admin") {
                ProfileAction(
                    icon =
                        Icons.Rounded
                            .AdminPanelSettings,
                    title =
                        "Admin dashboard",
                    subtitle =
                        "Manage users, words, feedback & reports",
                    onClick = onOpenAdmin,
                )
            }

            OutlinedButton(
                onClick = onSignOut,
                modifier =
                    Modifier.fillMaxWidth(),
                contentPadding =
                    PaddingValues(
                        vertical = 14.dp
                    ),
            ) {
                Icon(
                    Icons.Rounded.Logout,
                    contentDescription = null,
                    tint =
                        MaterialTheme
                            .colorScheme.error,
                )
                Text(
                    text = "Logout",
                    color =
                        MaterialTheme
                            .colorScheme.error,
                    modifier =
                        Modifier.padding(
                            start = 8.dp
                        ),
                    fontWeight =
                        FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun PhotoSettingsPage(
    photoUri: String?,
    context: Context,
    onChoosePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onBack: () -> Unit,
) {
    ProfileDetailScaffold(
        title = "Profile photo",
        subtitle =
            "Choose how you appear in Maya Translate.",
        onBack = onBack,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape =
                MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor =
                    MaterialTheme.colorScheme.surface,
            ),
            elevation =
                CardDefaults.cardElevation(
                    defaultElevation = 2.dp,
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment =
                    Alignment.CenterHorizontally,
                verticalArrangement =
                    Arrangement.spacedBy(16.dp),
            ) {
                ProfilePhoto(
                    uri = photoUri,
                    context = context,
                )

                Text(
                    text =
                        if (photoUri == null) {
                            "No custom photo yet"
                        } else {
                            "Current profile photo"
                        },
                    style =
                        MaterialTheme
                            .typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text =
                        "Choose a clear square or portrait image. The app displays it as a circle.",
                    style =
                        MaterialTheme
                            .typography.bodySmall,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Button(
                    onClick = onChoosePhoto,
                    modifier =
                        Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Rounded.PhotoCamera,
                        contentDescription = null,
                    )
                    Text(
                        text =
                            if (photoUri == null) {
                                "Choose photo"
                            } else {
                                "Choose another photo"
                            },
                        modifier =
                            Modifier.padding(
                                start = 8.dp
                            ),
                    )
                }

                if (photoUri != null) {
                    TextButton(
                        onClick = onRemovePhoto,
                    ) {
                        Text("Remove photo")
                    }
                }
            }
        }
    }
}

@Composable
private fun EditProfilePage(
    currentName: String,
    onSave: (String) -> Unit,
    onBack: () -> Unit,
) {
    val focusManager =
        LocalFocusManager.current
    var name by rememberSaveable(
        currentName
    ) {
        mutableStateOf(currentName)
    }

    ProfileDetailScaffold(
        title = "Edit profile",
        subtitle =
            "Keep the name shown across the app up to date.",
        onBack = onBack,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape =
                MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor =
                    MaterialTheme.colorScheme.surface,
            ),
            elevation =
                CardDefaults.cardElevation(
                    defaultElevation = 2.dp,
                ),
        ) {
            Column(
                modifier =
                    Modifier.padding(20.dp),
                verticalArrangement =
                    Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    label = {
                        Text("Display name")
                    },
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(
                            imeAction =
                                ImeAction.Done,
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onDone = {
                                focusManager
                                    .clearFocus()
                                if (
                                    name.trim()
                                        .length >= 2
                                ) {
                                    onSave(name)
                                }
                            },
                        ),
                )

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        onSave(name)
                    },
                    enabled =
                        name.trim().length >= 2 &&
                            name.trim() !=
                                currentName.trim(),
                    modifier =
                        Modifier.fillMaxWidth(),
                ) {
                    Text("Save changes")
                }
            }
        }
    }
}

@Composable
private fun PasswordSettingsPage(
    email: String,
    onSendReset: () -> Unit,
    onBack: () -> Unit,
) {
    ProfileDetailScaffold(
        title = "Change password",
        subtitle =
            "Password changes are completed securely through your email.",
        onBack = onBack,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape =
                MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor =
                    MaterialTheme.colorScheme.surface,
            ),
            elevation =
                CardDefaults.cardElevation(
                    defaultElevation = 2.dp,
                ),
        ) {
            Column(
                modifier =
                    Modifier.padding(20.dp),
                verticalArrangement =
                    Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    imageVector =
                        Icons.Rounded.LockReset,
                    contentDescription = null,
                    tint =
                        MaterialTheme
                            .colorScheme.primary,
                    modifier =
                        Modifier.size(38.dp),
                )

                Text(
                    text = "Reset via email",
                    style =
                        MaterialTheme
                            .typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text =
                        "We will send a password reset link to:",
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                )

                Text(
                    text = email,
                    color =
                        MaterialTheme
                            .colorScheme.primary,
                    fontWeight =
                        FontWeight.SemiBold,
                )

                Button(
                    onClick = onSendReset,
                    modifier =
                        Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = null,
                    )
                    Text(
                        text =
                            "Send password reset link",
                        modifier =
                            Modifier.padding(
                                start = 8.dp
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageSettingsPage(
    onBack: () -> Unit,
) {
    ProfileDetailScaffold(
        title = "App language",
        subtitle =
            "Choose the language used for the application interface.",
        onBack = onBack,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape =
                MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor =
                    MaterialTheme.colorScheme.surface,
            ),
        ) {
            Row(
                modifier =
                    Modifier.padding(18.dp),
                verticalAlignment =
                    Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    color =
                        MaterialTheme
                            .colorScheme
                            .primaryContainer,
                    shape =
                        MaterialTheme
                            .shapes.medium,
                ) {
                    Box(
                        modifier =
                            Modifier.size(46.dp),
                        contentAlignment =
                            Alignment.Center,
                    ) {
                        Icon(
                            imageVector =
                                Icons.Rounded
                                    .Language,
                            contentDescription = null,
                            tint =
                                MaterialTheme
                                    .colorScheme.primary,
                        )
                    }
                }

                Column(
                    modifier =
                        Modifier.weight(1f),
                ) {
                    Text(
                        text = "English",
                        fontWeight =
                            FontWeight.SemiBold,
                    )
                    Text(
                        text =
                            "Current interface language",
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,
                    )
                }

                Icon(
                    imageVector =
                        Icons.Rounded
                            .CheckCircle,
                    contentDescription =
                        "Selected",
                    tint =
                        MaterialTheme
                            .colorScheme.primary,
                )
            }
        }

        Text(
            text =
                "Additional interface languages can be added in a future version.",
            style =
                MaterialTheme
                    .typography.bodySmall,
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
        )
    }
}

@Composable
private fun AboutPage(
    onBack: () -> Unit,
) {
    ProfileDetailScaffold(
        title = "About Maya Translate",
        subtitle =
            "Project information and supported scope.",
        onBack = onBack,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape =
                MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor =
                    MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier =
                    Modifier.padding(20.dp),
                verticalArrangement =
                    Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Maya Translate",
                    style =
                        MaterialTheme
                            .typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text =
                        "A mobile translation and learning application for modern Yucatec Maya written in Latin orthography.",
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                )

                Text(
                    text = "Supported features",
                    style =
                        MaterialTheme
                            .typography.titleMedium,
                    fontWeight =
                        FontWeight.SemiBold,
                )

                Text(
                    text =
                        "• Maya ↔ English text translation\n" +
                            "• Image OCR translation\n" +
                            "• English voice input\n" +
                            "• Pronunciation support\n" +
                            "• Dictionary and saved vocabulary\n" +
                            "• History and learning analytics",
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProfileDetailScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    AppGradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .imePadding()
                .padding(
                    PaddingValues(
                        start = 18.dp,
                        top = 12.dp,
                        end = 18.dp,
                        bottom = 112.dp,
                    )
                ),
            verticalArrangement =
                Arrangement.spacedBy(18.dp),
        ) {
            ProfilePageTitle(
                title = title,
                subtitle = subtitle,
                onBack = onBack,
            )

            content()
        }
    }
}

@Composable
private fun ProfilePageTitle(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
            ) {
                Icon(
                    imageVector =
                        Icons.Rounded.ArrowBack,
                    contentDescription =
                        "Back to profile",
                )
            }
        } else {
            Spacer(
                modifier = Modifier.size(48.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment =
                Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style =
                    MaterialTheme
                        .typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            subtitle?.let {
                Text(
                    text = it,
                    style =
                        MaterialTheme
                            .typography.bodySmall,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                    textAlign =
                        TextAlign.Center,
                )
            }
        }

        Spacer(
            modifier = Modifier.size(48.dp)
        )
    }
}

@Composable
private fun ProfilePhoto(
    uri: String?,
    context: Context,
) {
    var bitmap by remember(uri) {
        mutableStateOf<ImageBitmap?>(null)
    }

    LaunchedEffect(uri) {
        bitmap =
            if (uri.isNullOrBlank()) {
                null
            } else {
                withContext(
                    Dispatchers.IO
                ) {
                    runCatching {
                        context.contentResolver
                            .openInputStream(
                                Uri.parse(uri)
                            )
                            ?.use { stream ->
                                BitmapFactory
                                    .decodeStream(
                                        stream
                                    )
                                    ?.asImageBitmap()
                            }
                    }.getOrNull()
                }
            }
    }

    Surface(
        color =
            Color.White.copy(alpha = 0.20f),
        shape = CircleShape,
    ) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape),
            contentAlignment =
                Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap =
                        requireNotNull(bitmap),
                    contentDescription =
                        "Profile photo",
                    modifier =
                        Modifier.fillMaxSize(),
                    contentScale =
                        ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector =
                        Icons.Rounded.Person,
                    contentDescription =
                        "Default profile photo",
                    tint = Color.White,
                    modifier =
                        Modifier.size(56.dp),
                )
            }
        }
    }
}

@Composable
private fun ProfileAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                color =
                    MaterialTheme
                        .colorScheme
                        .primaryContainer,
                shape =
                    MaterialTheme.shapes.medium,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp),
                    contentAlignment =
                        Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint =
                            MaterialTheme
                                .colorScheme.primary,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title,
                    fontWeight =
                        FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style =
                        MaterialTheme
                            .typography.bodySmall,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                )
            }

            Icon(
                imageVector =
                    Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProfileInfo(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                color =
                    MaterialTheme
                        .colorScheme
                        .primaryContainer,
                shape =
                    MaterialTheme.shapes.medium,
            ) {
                Box(
                    modifier =
                        Modifier.size(44.dp),
                    contentAlignment =
                        Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint =
                            MaterialTheme
                                .colorScheme.primary,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title,
                    fontWeight =
                        FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style =
                        MaterialTheme
                            .typography.bodySmall,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                )
            }

            Text(
                text = value,
                color =
                    MaterialTheme
                        .colorScheme.primary,
                fontWeight =
                    FontWeight.SemiBold,
            )
        }
    }
}

private fun formatMemberSince(
    timestamp: Long,
): String {
    if (timestamp <= 0L) {
        return "Current account"
    }

    return SimpleDateFormat(
        "MMM yyyy",
        Locale.getDefault(),
    ).format(Date(timestamp))
}
