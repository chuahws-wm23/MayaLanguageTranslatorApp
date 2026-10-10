package com.chuahws.mayalanguageapp.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.domain.model.TranslationRecord
import com.chuahws.mayalanguageapp.domain.model.UsageAnalytics
import com.chuahws.mayalanguageapp.presentation.translation.TranslateMode

@Composable
fun HomeScreen(
    displayName: String,
    analytics: UsageAnalytics,
    recent: List<TranslationRecord>,
    onTranslate: (TranslateMode) -> Unit,
) {
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Welcome back",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = displayName.ifBlank { "Maya Translate" },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        item {
            Text(
                text = "Translate",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                QuickActionCard(
                    title = "Type",
                    subtitle = "Enter text",
                    icon = {
                        Icon(Icons.Rounded.Edit, contentDescription = null)
                    },
                    modifier = Modifier.weight(1f),
                    onClick = { onTranslate(TranslateMode.TEXT) },
                )
                QuickActionCard(
                    title = "Scan",
                    subtitle = "Use a photo",
                    icon = {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                    },
                    modifier = Modifier.weight(1f),
                    onClick = { onTranslate(TranslateMode.IMAGE) },
                )
                QuickActionCard(
                    title = "Voice",
                    subtitle = "Speak English",
                    icon = {
                        Icon(Icons.Rounded.Mic, contentDescription = null)
                    },
                    modifier = Modifier.weight(1f),
                    onClick = { onTranslate(TranslateMode.VOICE) },
                )
            }
        }

        item {
            Text(
                text = "Your activity",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor =
                        MaterialTheme.colorScheme.primaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement =
                        Arrangement.SpaceBetween,
                ) {
                    Metric(
                        value = analytics.totalTranslations.toString(),
                        label = "Translations",
                    )
                    Metric(
                        value = analytics.savedWords.toString(),
                        label = "Saved",
                    )
                    Metric(
                        value = analytics.imageTranslations.toString(),
                        label = "Scans",
                    )
                }
            }
        }

        item {
            Text(
                text = "Recent translations",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (recent.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Your latest translations will appear here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp),
                    )
                }
            }
        } else {
            items(recent.take(4), key = { it.translationId }) {
                RecentTranslationRow(it)
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            icon()
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Metric(
    value: String,
    label: String,
) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun RecentTranslationRow(
    item: TranslationRecord,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.sourceText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = item.translatedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
            )
        }
    }
}
