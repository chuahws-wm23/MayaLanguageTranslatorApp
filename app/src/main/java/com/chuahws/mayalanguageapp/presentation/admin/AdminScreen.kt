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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.domain.model.FeedbackItem

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
    feedback: List<FeedbackItem>,
    snackbarHostState: SnackbarHostState,
    onMarkReviewed: (FeedbackItem) -> Unit,
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
                    IconButton(
                        onClick = onSwitchToUserView,
                    ) {
                        Icon(
                            Icons.Rounded.SwapHoriz,
                            contentDescription = "Switch to user view",
                        )
                    }
                    IconButton(
                        onClick = onSignOut,
                    ) {
                        Icon(
                            Icons.Rounded.Logout,
                            contentDescription = "Logout",
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            NavigationBar {
                AdminSection.entries.forEach { item ->
                    NavigationBarItem(
                        selected = section == item,
                        onClick = {
                            section = item
                        },
                        icon = {
                            Icon(
                                item.icon,
                                contentDescription = item.label,
                            )
                        },
                        label = {
                            Text(
                                text = when (item) {
                                    AdminSection.DICTIONARY -> "Words"
                                    AdminSection.ANALYTICS -> "Stats"
                                    else -> item.label
                                },
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
            when (section) {
                AdminSection.DASHBOARD -> DashboardContent(
                    displayName = displayName,
                    email = email,
                    feedback = feedback,
                    onOpenFeedback = {
                        section = AdminSection.FEEDBACK
                    },
                )

                AdminSection.USERS -> PlaceholderManagementSection(
                    icon = Icons.Rounded.Group,
                    title = "User management",
                    description =
                        "Manage registered accounts, roles and account status from one place.",
                    plannedActions = listOf(
                        "View registered users",
                        "Search and filter accounts",
                        "Activate or suspend accounts",
                        "View basic user activity",
                    ),
                )

                AdminSection.DICTIONARY -> PlaceholderManagementSection(
                    icon = Icons.Rounded.Language,
                    title = "Dictionary management",
                    description =
                        "Maintain the Yucatec Maya-English dictionary used by the translator.",
                    plannedActions = listOf(
                        "View dictionary entries",
                        "Add new words and meanings",
                        "Edit existing entries",
                        "Remove invalid entries",
                    ),
                )

                AdminSection.FEEDBACK -> FeedbackContent(
                    feedback = feedback,
                    onMarkReviewed = onMarkReviewed,
                )

                AdminSection.ANALYTICS -> AnalyticsContent(
                    feedback = feedback,
                )
            }
        }
    }
}

@Composable
private fun DashboardContent(
    displayName: String,
    email: String,
    feedback: List<FeedbackItem>,
    onOpenFeedback: () -> Unit,
) {
    val pendingFeedback = feedback.count {
        it.status != "reviewed"
    }
    val reviewedFeedback = feedback.size - pendingFeedback
    val averageRating = if (feedback.isEmpty()) {
        0.0
    } else {
        feedback.map { it.rating }.average()
    }

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
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
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
                        text = "Manage the application, content and user feedback from this console.",
                        modifier = Modifier.padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Text(
                text = "Overview",
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
                    title = "Feedback",
                    value = feedback.size.toString(),
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    title = "Pending",
                    value = pendingFeedback.toString(),
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
                    title = "Reviewed",
                    value = reviewedFeedback.toString(),
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    title = "Avg. rating",
                    value = if (feedback.isEmpty()) {
                        "-"
                    } else {
                        "%.1f / 5".format(averageRating)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (pendingFeedback > 0) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "$pendingFeedback feedback item(s) need review",
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Open Feedback to review new submissions.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = onOpenFeedback) {
                            Text("Review feedback")
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Administration modules",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ModuleStatusCard(
                    title = "User management",
                    status = "Shell ready",
                )
                ModuleStatusCard(
                    title = "Dictionary management",
                    status = "Shell ready",
                )
                ModuleStatusCard(
                    title = "Feedback management",
                    status = "Connected",
                )
                ModuleStatusCard(
                    title = "System analytics",
                    status = "Shell ready",
                )
            }
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
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

@Composable
private fun ModuleStatusCard(
    title: String,
    status: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PlaceholderManagementSection(
    icon: ImageVector,
    title: String,
    description: String,
    plannedActions: List<String>,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(42.dp),
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Text(
                text = "Planned controls",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        items(plannedActions) { action ->
            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(action)
                }
            }
        }

        item {
            Text(
                text = "The admin interface is ready. The data operations for this module will be connected next.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
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
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text(
                        text = "${item.rating}/5 · ${item.userEmail}",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(item.comment)
                    Text(
                        text = item.status.replaceFirstChar {
                            it.uppercase()
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    feedback: List<FeedbackItem>,
) {
    val pending = feedback.count {
        it.status != "reviewed"
    }
    val averageRating = if (feedback.isEmpty()) {
        0.0
    } else {
        feedback.map { it.rating }.average()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                text = "System analytics",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Current admin-level indicators and future system-wide usage metrics.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            MetricCard(
                title = "Average user rating",
                value = if (feedback.isEmpty()) {
                    "No data"
                } else {
                    "%.1f / 5".format(averageRating)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Next analytics connection",
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "System-wide users, total translations, text/image/voice usage and dictionary statistics will be connected to this page next.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
