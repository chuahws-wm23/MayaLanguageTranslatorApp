package com.chuahws.mayalanguageapp.presentation.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.domain.model.FeedbackItem
import com.chuahws.mayalanguageapp.domain.model.UsageAnalytics
import com.chuahws.mayalanguageapp.domain.model.UserProfile

private enum class AdminSection(
    val label: String,
    val icon: ImageVector,
) {
    DASHBOARD("Dashboard", Icons.Rounded.Dashboard),
    USERS("Users", Icons.Rounded.People),
    DICTIONARY("Dictionary", Icons.Rounded.MenuBook),
    FEEDBACK("Feedback", Icons.Rounded.Feedback),
    ANALYTICS("Analytics", Icons.Rounded.Analytics),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    displayName: String,
    email: String,
    currentUserId: String,
    users: List<UserProfile>,
    feedback: List<FeedbackItem>,
    analytics: List<UsageAnalytics>,
    dictionaryEntries: List<DictionaryEntry>,
    loading: Boolean,
    snackbarHostState: SnackbarHostState,
    onToggleUserStatus: (String, String) -> Unit,
    onMarkReviewed: (FeedbackItem) -> Unit,
    onSearchDictionary: (String) -> Unit,
    onSaveDictionaryEntry:
        (DictionaryEntry?, String, String, String, String) -> Unit,
    onDeleteDictionaryEntry: (DictionaryEntry) -> Unit,
    onSwitchToUserView: () -> Unit,
    onSignOut: () -> Unit,
) {
    var section by rememberSaveable {
        mutableStateOf(AdminSection.DASHBOARD)
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Maya Translate Admin",
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "ADMIN CONSOLE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSwitchToUserView) {
                        Icon(
                            Icons.Rounded.SwapHoriz,
                            contentDescription = "Switch to user view",
                        )
                    }
                    IconButton(onClick = onSignOut) {
                        Icon(
                            Icons.Rounded.Logout,
                            contentDescription = "Logout",
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
        bottomBar = {
            NavigationBar {
                AdminSection.entries.forEach { item ->
                    NavigationBarItem(
                        selected = section == item,
                        onClick = { section = item },
                        icon = {
                            Icon(
                                item.icon,
                                contentDescription = item.label,
                            )
                        },
                        label = {
                            Text(
                                when (item) {
                                    AdminSection.DICTIONARY -> "Words"
                                    AdminSection.ANALYTICS -> "Stats"
                                    else -> item.label
                                }
                            )
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp),
                )
            }

            when (section) {
                AdminSection.DASHBOARD ->
                    DashboardContent(
                        displayName = displayName,
                        email = email,
                        users = users,
                        feedback = feedback,
                        analytics = analytics,
                        onOpenFeedback = {
                            section = AdminSection.FEEDBACK
                        },
                    )

                AdminSection.USERS ->
                    UserManagementContent(
                        currentUserId = currentUserId,
                        users = users,
                        onToggleStatus = onToggleUserStatus,
                    )

                AdminSection.DICTIONARY ->
                    DictionaryManagementContent(
                        entries = dictionaryEntries,
                        onSearch = onSearchDictionary,
                        onSave = onSaveDictionaryEntry,
                        onDelete = onDeleteDictionaryEntry,
                    )

                AdminSection.FEEDBACK ->
                    FeedbackContent(
                        feedback = feedback,
                        onMarkReviewed = onMarkReviewed,
                    )

                AdminSection.ANALYTICS ->
                    AnalyticsContent(
                        users = users,
                        feedback = feedback,
                        analytics = analytics,
                    )
            }
        }
    }
}

@Composable
private fun DashboardContent(
    displayName: String,
    email: String,
    users: List<UserProfile>,
    feedback: List<FeedbackItem>,
    analytics: List<UsageAnalytics>,
    onOpenFeedback: () -> Unit,
) {
    val pendingFeedback =
        feedback.count { it.status != "reviewed" }
    val totalTranslations =
        analytics.sumOf { it.totalTranslations }
    val activeUsers =
        users.count { it.accountStatus != "suspended" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 18.dp,
            top = 18.dp,
            end = 18.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor =
                        MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Administrator",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = displayName.ifBlank { "Welcome back" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = email,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text =
                            "Manage users, vocabulary, feedback and system activity.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Text(
                text = "System overview",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricCard(
                    title = "Users",
                    value = users.size.toString(),
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    title = "Active",
                    value = activeUsers.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricCard(
                    title = "Translations",
                    value = totalTranslations.toString(),
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    title = "Feedback",
                    value = feedback.size.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (pendingFeedback > 0) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor =
                            MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text =
                                pendingFeedback.toString() +
                                    " feedback item(s) need review",
                            fontWeight = FontWeight.Bold,
                        )
                        Button(onClick = onOpenFeedback) {
                            Text("Review feedback")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserManagementContent(
    currentUserId: String,
    users: List<UserProfile>,
    onToggleStatus: (String, String) -> Unit,
) {
    var query by rememberSaveable {
        mutableStateOf("")
    }

    val filtered = users.filter { user ->
        query.isBlank() ||
            user.displayName.contains(query, ignoreCase = true) ||
            user.email.contains(query, ignoreCase = true)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            label = { Text("Search users") },
            leadingIcon = {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                )
            },
            trailingIcon =
                if (query.isNotBlank()) {
                    {
                        IconButton(
                            onClick = { query = "" },
                        ) {
                            Icon(
                                Icons.Rounded.Clear,
                                contentDescription = "Clear search",
                            )
                        }
                    }
                } else {
                    null
                },
            singleLine = true,
        )

        LazyColumn(
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = "Registered users: " + users.size,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            items(
                items = filtered,
                key = { it.userId },
            ) { user ->
                UserCard(
                    user = user,
                    isCurrentAdmin =
                        user.userId == currentUserId,
                    onToggleStatus = onToggleStatus,
                )
            }
        }
    }
}

@Composable
private fun UserCard(
    user: UserProfile,
    isCurrentAdmin: Boolean,
    onToggleStatus: (String, String) -> Unit,
) {
    val suspended =
        user.accountStatus == "suspended"

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = user.displayName.ifBlank { user.email },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = user.email,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text =
                    "Role: " +
                        user.role.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text =
                    "Status: " +
                        user.accountStatus.replaceFirstChar {
                            it.uppercase()
                        },
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (suspended) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
            )

            if (isCurrentAdmin) {
                Text(
                    text = "Current administrator account",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                OutlinedButton(
                    onClick = {
                        onToggleStatus(
                            user.userId,
                            if (suspended) {
                                "active"
                            } else {
                                "suspended"
                            },
                        )
                    },
                ) {
                    Text(
                        if (suspended) {
                            "Reactivate account"
                        } else {
                            "Suspend account"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DictionaryManagementContent(
    entries: List<DictionaryEntry>,
    onSearch: (String) -> Unit,
    onSave:
        (DictionaryEntry?, String, String, String, String) -> Unit,
    onDelete: (DictionaryEntry) -> Unit,
) {
    var query by rememberSaveable {
        mutableStateOf("")
    }
    var editorOpen by rememberSaveable {
        mutableStateOf(false)
    }
    var editingEntry by remember {
        mutableStateOf<DictionaryEntry?>(null)
    }
    var pendingDelete by remember {
        mutableStateOf<DictionaryEntry?>(null)
    }

    if (editorOpen) {
        DictionaryEditorDialog(
            entry = editingEntry,
            onDismiss = {
                editorOpen = false
                editingEntry = null
            },
            onSave = {
                    entry,
                    maya,
                    english,
                    partOfSpeech,
                    pronunciation,
                ->
                onSave(
                    entry,
                    maya,
                    english,
                    partOfSpeech,
                    pronunciation,
                )
                editorOpen = false
                editingEntry = null
            },
        )
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = {
                pendingDelete = null
            },
            title = {
                Text("Delete dictionary entry?")
            },
            text = {
                Text(
                    "Remove “" +
                        entry.mayaText +
                        " — " +
                        entry.englishText +
                        "” from the editable dictionary?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(entry)
                        pendingDelete = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 18.dp,
                    top = 18.dp,
                    end = 18.dp,
                ),
            label = { Text("Search dictionary") },
            leadingIcon = {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                )
            },
            trailingIcon =
                if (query.isNotBlank()) {
                    {
                        IconButton(
                            onClick = {
                                query = ""
                                onSearch("")
                            },
                        ) {
                            Icon(
                                Icons.Rounded.Clear,
                                contentDescription = "Clear search",
                            )
                        }
                    }
                } else {
                    null
                },
            singleLine = true,
        )

        Row(
            modifier = Modifier.padding(
                start = 18.dp,
                top = 10.dp,
                end = 18.dp,
                bottom = 10.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                onClick = { onSearch(query) },
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                )
                Text(
                    text = "Search",
                    modifier = Modifier.padding(start = 6.dp),
                )
            }

            Button(
                onClick = {
                    editingEntry = null
                    editorOpen = true
                },
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                )
                Text(
                    text = "Add entry",
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = "Loaded entries: " + entries.size,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            items(
                items = entries,
                key = { it.entryId },
            ) { entry ->
                val builtIn =
                    entry.entryId.startsWith("core-") ||
                        entry.entryId.startsWith("dev-")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = entry.mayaText,
                            style =
                                MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(entry.englishText)

                        entry.partOfSpeech
                            ?.takeIf { it.isNotBlank() }
                            ?.let {
                                Text(
                                    text = "Part of speech: " + it,
                                    style =
                                        MaterialTheme.typography.bodySmall,
                                )
                            }

                        if (builtIn) {
                            Text(
                                text =
                                    "Built-in fallback entry · read only",
                                style =
                                    MaterialTheme.typography.labelSmall,
                                color =
                                    MaterialTheme.colorScheme
                                        .onSurfaceVariant,
                            )
                        } else {
                            Row(
                                horizontalArrangement =
                                    Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        editingEntry = entry
                                        editorOpen = true
                                    },
                                ) {
                                    Icon(
                                        Icons.Rounded.Edit,
                                        contentDescription = null,
                                    )
                                    Text(
                                        text = "Edit",
                                        modifier =
                                            Modifier.padding(
                                                start = 5.dp
                                            ),
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        pendingDelete = entry
                                    },
                                ) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = null,
                                        tint =
                                            MaterialTheme
                                                .colorScheme.error,
                                    )
                                    Text(
                                        text = "Delete",
                                        color =
                                            MaterialTheme
                                                .colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DictionaryEditorDialog(
    entry: DictionaryEntry?,
    onDismiss: () -> Unit,
    onSave:
        (DictionaryEntry?, String, String, String, String) -> Unit,
) {
    var maya by remember(entry?.entryId) {
        mutableStateOf(entry?.mayaText.orEmpty())
    }
    var english by remember(entry?.entryId) {
        mutableStateOf(entry?.englishText.orEmpty())
    }
    var partOfSpeech by remember(entry?.entryId) {
        mutableStateOf(entry?.partOfSpeech.orEmpty())
    }
    var pronunciation by remember(entry?.entryId) {
        mutableStateOf(entry?.pronunciation.orEmpty())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (entry == null) {
                    "Add dictionary entry"
                } else {
                    "Edit dictionary entry"
                }
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                OutlinedTextField(
                    value = maya,
                    onValueChange = { maya = it },
                    label = { Text("Yucatec Maya") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = english,
                    onValueChange = { english = it },
                    label = { Text("English") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = partOfSpeech,
                    onValueChange = { partOfSpeech = it },
                    label = {
                        Text("Part of speech (optional)")
                    },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = pronunciation,
                    onValueChange = { pronunciation = it },
                    label = {
                        Text("Pronunciation (optional)")
                    },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        entry,
                        maya,
                        english,
                        partOfSpeech,
                        pronunciation,
                    )
                },
                enabled =
                    maya.isNotBlank() &&
                        english.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun FeedbackContent(
    feedback: List<FeedbackItem>,
    onMarkReviewed: (FeedbackItem) -> Unit,
) {
    if (feedback.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Rounded.Feedback,
                contentDescription = null,
                modifier = Modifier.size(46.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "No feedback to review",
                modifier = Modifier.padding(top = 12.dp),
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "New user feedback will appear here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 18.dp,
            top = 18.dp,
            end = 18.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = "Feedback management",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Review feedback submitted by users.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        items(
            items = feedback,
            key = { it.feedbackId },
        ) { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(7.dp),
                ) {
                    Text(
                        text =
                            item.rating.toString() +
                                "/5 · " +
                                item.userEmail,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(item.comment)
                    Text(
                        text =
                            item.status.replaceFirstChar {
                                it.uppercase()
                            },
                        style = MaterialTheme.typography.labelMedium,
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (item.status != "reviewed") {
                        Button(
                            onClick = {
                                onMarkReviewed(item)
                            },
                        ) {
                            Text("Mark reviewed")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsContent(
    users: List<UserProfile>,
    feedback: List<FeedbackItem>,
    analytics: List<UsageAnalytics>,
) {
    val total =
        analytics.sumOf { it.totalTranslations }
    val textTranslations =
        analytics.sumOf { it.textTranslations }
    val imageTranslations =
        analytics.sumOf { it.imageTranslations }
    val voiceTranslations =
        analytics.sumOf { it.voiceTranslations }
    val pending =
        feedback.count { it.status != "reviewed" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                text = "System reports",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text =
                    "Summary statistics across registered users, translations and feedback.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricCard(
                    title = "Users",
                    value = users.size.toString(),
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    title = "Translations",
                    value = total.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricCard(
                    title = "Feedback",
                    value = feedback.size.toString(),
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    title = "Pending",
                    value = pending.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Text(
                text = "Translation channels",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        item {
            MetricCard(
                title = "Text translations",
                value = textTranslations.toString(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            MetricCard(
                title = "Image translations",
                value = imageTranslations.toString(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            MetricCard(
                title = "Voice translations",
                value = voiceTranslations.toString(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
