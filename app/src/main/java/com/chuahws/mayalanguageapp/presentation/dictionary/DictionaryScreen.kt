package com.chuahws.mayalanguageapp.presentation.dictionary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.presentation.components.AppGradientBackground
import com.chuahws.mayalanguageapp.presentation.components.SoftBadge

@Composable
fun DictionaryScreen(
    entries: List<DictionaryEntry>,
    loading: Boolean,
    onSearch: (String) -> Unit,
    onSaveEntry: (DictionaryEntry) -> Unit,
) {
    var query by rememberSaveable {
        mutableStateOf("")
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
                    Text("Search Maya or English")
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
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        onSearch(query)
                    },
                ),
            )

            FilledTonalButton(
                onClick = {
                    onSearch(query)
                },
                modifier = Modifier.padding(
                    start = 18.dp,
                    top = 10.dp,
                    end = 18.dp,
                ),
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                )
                Text(
                    text = "Search dictionary",
                    modifier = Modifier.padding(start = 6.dp),
                )
            }

            if (loading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (entries.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Rounded.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = if (query.isBlank()) {
                            "No dictionary entries available"
                        } else {
                            "No matching vocabulary"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Text(
                        text = if (query.isBlank()) {
                            "Dictionary entries will appear here when data is available."
                        } else {
                            "Try a different Maya or English word."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 18.dp,
                        top = 12.dp,
                        end = 18.dp,
                        bottom = 112.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text(
                            text = "Showing ${entries.size} entr" +
                                if (entries.size == 1) "y" else "ies",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    items(
                        items = entries,
                        key = { it.entryId },
                    ) { entry ->
                        DictionaryEntryCard(
                            entry = entry,
                            onSave = {
                                onSaveEntry(entry)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DictionaryEntryCard(
    entry: DictionaryEntry,
    onSave: () -> Unit,
) {
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
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = entry.mayaText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = entry.englishText,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                IconButton(onClick = onSave) {
                    Icon(
                        Icons.Rounded.BookmarkAdd,
                        contentDescription = "Save vocabulary",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            entry.partOfSpeech
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    SoftBadge(text = it)
                }

            entry.pronunciation
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    Text(
                        text = "Pronunciation: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

            if (entry.verified) {
                Text(
                    text = "Verified entry",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
