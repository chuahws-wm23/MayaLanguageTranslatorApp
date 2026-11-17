package com.chuahws.mayalanguageapp.presentation.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.Dp
import android.graphics.Bitmap
import com.chuahws.mayalanguageapp.integration.glyph.GlyphImageLoader
import kotlin.math.roundToInt
import com.chuahws.mayalanguageapp.domain.model.UserProfile
import com.chuahws.mayalanguageapp.presentation.components.AppGradientBackground
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
    onOpenMenu: () -> Unit,
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
                onOpenMenu = onOpenMenu,
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
                    onUpdateName(name.trim())
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
    onOpenMenu: () -> Unit,
    onOpenPhoto: () -> Unit,
    onOpenEdit: () -> Unit,
    onOpenPassword: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenAdmin: () -> Unit,
    onSignOut: () -> Unit,
) {
    val name = profile?.displayName?.takeIf { it.isNotBlank() } ?: "Maya explorer"
    val address = email.ifBlank { profile?.email.orEmpty() }
    val memberSince = remember(profile?.createdAtMillis) { formatMemberSince(profile?.createdAtMillis ?: 0L) }
    val status = profile?.accountStatus?.takeIf { it.isNotBlank() }
        ?.replaceFirstChar { it.uppercase() } ?: "Details unavailable"
    val role = if (profile?.role == "admin") "Administrator" else "Explorer"
    AppGradientBackground {
        Column(Modifier.fillMaxSize()) {
            ProfilePageTitle(title = "Your profile", onMenu = onOpenMenu)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF183B30))) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("YOUR PLACE IN THE ARCHIVE", style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFD5DDCA))
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box {
                                ProfilePhoto(photoUri, context, onDark = true, diameter = 80.dp)
                                Surface(onClick = onOpenPhoto, shape = CircleShape,
                                    color = Color(0xFFF4EDDD), modifier = Modifier.align(Alignment.BottomEnd).size(48.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.PhotoCamera, contentDescription = "Profile photo settings",
                                            tint = Color(0xFF245A49), modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(name, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                                Text(address.ifBlank { "Email unavailable" },
                                    style = MaterialTheme.typography.bodyMedium, color = Color(0xFFE1E8DC))
                                Text(role, style = MaterialTheme.typography.labelMedium, color = Color(0xFFE8D7A7))
                            }
                        }
                        TextButton(onClick = onOpenEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Edit profile", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Box(Modifier.weight(1f)) { ProfileFact("Member since", memberSince) }
                        Box(Modifier.weight(1f)) { ProfileFact("Account status", status) }
                    }
                }
                ProfileSection("ACCOUNT") {
                    ProfileAction(Icons.Rounded.PhotoCamera, "Profile photo", "Choose or change your picture", onOpenPhoto)
                    ProfileAction(Icons.Rounded.LockReset, "Password recovery", "Send a reset link to your email", onOpenPassword)
                }
                ProfileSection("PREFERENCES & HELP") {
                    ProfileAction(Icons.Rounded.Language, "App language", "English", onOpenLanguage)
                    ProfileAction(Icons.Rounded.Info, "About the archive", "Features, sources and recognition limits", onOpenAbout)
                }
                if (profile?.role == "admin") ProfileSection("ADMINISTRATION") {
                    ProfileAction(Icons.Rounded.AdminPanelSettings, "Admin dashboard",
                        "Manage users, words and feedback", onOpenAdmin)
                }
                OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Rounded.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Sign out")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ProfileFact(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ProfileSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
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
            "Choose a photo for your profile.",
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
                                    name.trim().length >= 2 && name.trim() != currentName.trim()
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
            "Choose the language used in the app.",
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
                "English is the interface language for this version.",
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
            "See what the app can do.",
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
                        "Explore Classic Maya glyph drawings and their source-proposed meanings. Text and voice tools use the modern Yucatec Maya dictionary.",
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                )

                Text("Photo recognition is experimental. Reference suggestions can be wrong, and meanings depend on inscription context.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                Text(
                    text = "What you can do",
                    style =
                        MaterialTheme
                            .typography.titleMedium,
                    fontWeight =
                        FontWeight.SemiBold,
                )

                Text(
                    text =
                        "• Compare single glyphs or detected regions\n" +
                            "• Browse illustrated reference drawings\n" +
                            "• Save scan pictures and proposed meanings\n" +
                            "• Revisit history and favourites\n" +
                            "• Use text, voice and pronunciation tools",
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
        Column(modifier = Modifier.fillMaxSize()) {
            // Detail headers remain visible when editing a long form.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                ProfilePageTitle(
                    title = title,
                    onBack = onBack,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(
                        PaddingValues(
                            start = 18.dp,
                            top = 12.dp,
                            end = 18.dp,
                            bottom = 112.dp,
                        )
                    ),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                content()
            }
        }
    }
}

@Composable
private fun ProfilePageTitle(
    title: String,
    onBack: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            onBack != null -> {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = "Back to profile",
                    )
                }
            }
            onMenu != null -> {
                IconButton(onClick = onMenu) {
                    Icon(
                        imageVector = Icons.Rounded.Menu,
                        contentDescription = "Open menu",
                    )
                }
            }
            else -> {
                Spacer(modifier = Modifier.size(48.dp))
            }
        }

        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )

        Spacer(modifier = Modifier.size(48.dp))
    }
}

@Composable
private fun ProfilePhoto(
    uri: String?,
    context: Context,
    onDark: Boolean = false,
    diameter: Dp = 104.dp,
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
                        val decoded = GlyphImageLoader.load(context, Uri.parse(uri))
                        val factor = minOf(1f, 320f / maxOf(decoded.width, decoded.height))
                        val small = if (factor < 1f) Bitmap.createScaledBitmap(decoded,
                            (decoded.width * factor).roundToInt().coerceAtLeast(1),
                            (decoded.height * factor).roundToInt().coerceAtLeast(1), true) else decoded
                        if (small !== decoded) decoded.recycle()
                        small.asImageBitmap()
                    }.getOrNull()
                }
            }
    }

    Surface(
        color =
            if (onDark) Color.White.copy(alpha = 0.16f) else MaterialTheme.colorScheme.primaryContainer,
        shape = CircleShape,
    ) {
        Box(
            modifier = Modifier
                .size(diameter)
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
                    tint = if (onDark) Color.White else MaterialTheme.colorScheme.primary,
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
            defaultElevation = 0.dp,
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

private fun formatMemberSince(
    timestamp: Long,
): String {
    if (timestamp <= 0L) {
        return "Unavailable"
    }

    return SimpleDateFormat(
        "MMM yyyy",
        Locale.getDefault(),
    ).format(Date(timestamp))
}
