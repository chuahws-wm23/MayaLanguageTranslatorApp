package com.chuahws.mayalanguageapp.presentation.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.domain.model.TranslationRecord
import com.chuahws.mayalanguageapp.presentation.components.AccentIcon
import com.chuahws.mayalanguageapp.presentation.components.AppGradientBackground
import com.chuahws.mayalanguageapp.presentation.components.SoftBadge
import com.chuahws.mayalanguageapp.presentation.theme.MayaBlue
import com.chuahws.mayalanguageapp.presentation.theme.MayaGreen
import com.chuahws.mayalanguageapp.presentation.theme.MayaOrange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    history: List<TranslationRecord>,
) {
    var query by rememberSaveable {
        mutableStateOf("")
    }
    var typeFilter by rememberSaveable {
        mutableStateOf("All")
    }

    val filtered = history.filter { item ->
        val matchesQuery =
            query.isBlank() ||
                item.sourceText.contains(query, ignoreCase = true) ||
                item.translatedText.contains(query, ignoreCase = true)

        val matchesType =
            typeFilter == "All" ||
                item.inputType.equals(typeFilter, ignoreCase = true)

        matchesQuery && matchesType
    }

    AppGradientBackground {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 18.dp,
                        top = 18.dp,
                        end = 18.dp,
                    ),
                label = {
                    Text("Search history")
                },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                    )
                },
                trailingIcon = if (query.isNotBlank()) {
                    {
                        IconButton(
                            onClick = {
                                query = ""
                            },
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Clear search",
                            )
                        }
                    }
                } else {
                    null
                },
            )

            Row(
                modifier = Modifier.padding(
                    horizontal = 18.dp,
                    vertical = 10.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("All", "TEXT", "IMAGE", "VOICE").forEach { filter ->
                    FilterChip(
                        selected = typeFilter == filter,
                        onClick = {
                            typeFilter = filter
                        },
                        label = {
                            Text(
                                when (filter) {
                                    "TEXT" -> "Text"
                                    "IMAGE" -> "Image"
                                    "VOICE" -> "Voice"
                                    else -> "All"
                                }
                            )
                        },
                    )
                }
            }

            if (filtered.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Rounded.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = if (history.isEmpty()) {
                            "No translations yet"
                        } else {
                            "No matching translations"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Text(
                        text = if (history.isEmpty()) {
                            "Successful translations will appear here."
                        } else {
                            "Try another search or filter."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 18.dp,
                        end = 18.dp,
                        bottom = 112.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        items = filtered,
                        key = {
                            it.translationId
                        },
                    ) { item ->
                        HistoryCard(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(
    item: TranslationRecord,
) {
    val date = formatDate(item.createdAtMillis)

    val typeColor: Color
    val icon = when (item.inputType.uppercase()) {
        "IMAGE" -> {
            typeColor = MayaGreen
            Icons.Rounded.CameraAlt
        }
        "VOICE" -> {
            typeColor = MayaOrange
            Icons.Rounded.Mic
        }
        else -> {
            typeColor = MayaBlue
            Icons.Rounded.Keyboard
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
        ),
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AccentIcon(
                icon = icon,
                color = typeColor,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = "${item.sourceText} → ${item.translatedText}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${item.sourceLanguage} → ${item.targetLanguage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SoftBadge(
                        text = item.inputType.lowercase()
                            .replaceFirstChar { it.uppercase() },
                        color = typeColor,
                    )
                    Text(
                        text = date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun formatDate(
    time: Long,
): String = SimpleDateFormat(
    "dd MMM yyyy, h:mm a",
    Locale.getDefault(),
).format(Date(time))
