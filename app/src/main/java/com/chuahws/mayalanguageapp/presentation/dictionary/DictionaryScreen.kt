package com.chuahws.mayalanguageapp.presentation.dictionary

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.presentation.components.AppGradientBackground
import com.chuahws.mayalanguageapp.presentation.components.SoftBadge
import java.util.Locale

private enum class DictionarySortMode(
    val label: String,
) {
    MAYA("Maya A–Z"),
    ENGLISH("English A–Z"),
    PART_OF_SPEECH("Word type"),
}

@Composable
fun DictionaryScreen(
    entries: List<DictionaryEntry>,
    loading: Boolean,
    hasMore: Boolean,
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit,
    onSaveEntry: (DictionaryEntry) -> Unit,
) {
    var query by rememberSaveable {
        mutableStateOf("")
    }
    var sortMode by rememberSaveable {
        mutableStateOf(DictionarySortMode.MAYA)
    }

    val sortedEntries =
        when (sortMode) {
            DictionarySortMode.MAYA ->
                entries.sortedWith(
                    compareBy(
                        String.CASE_INSENSITIVE_ORDER,
                    ) {
                        it.mayaText
                    },
                )

            DictionarySortMode.ENGLISH ->
                entries.sortedWith(
                    compareBy(
                        String.CASE_INSENSITIVE_ORDER,
                    ) {
                        it.englishText
                    },
                )

            DictionarySortMode.PART_OF_SPEECH ->
                entries.sortedWith(
                    compareBy<DictionaryEntry>(
                        {
                            it.partOfSpeech
                                ?.trim()
                                ?.lowercase()
                                .orEmpty()
                                .ifBlank { "zzz" }
                        },
                        {
                            it.mayaText.lowercase()
                        },
                    )
                )
        }

    val groupedEntries =
        sortedEntries.groupBy { entry ->
            when (sortMode) {
                DictionarySortMode.MAYA ->
                    alphabeticalSection(
                        entry.mayaText,
                    )

                DictionarySortMode.ENGLISH ->
                    alphabeticalSection(
                        entry.englishText,
                    )

                DictionarySortMode.PART_OF_SPEECH ->
                    entry.partOfSpeech
                        ?.trim()
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?.replaceFirstChar {
                            if (
                                it.isLowerCase()
                            ) {
                                it.titlecase(
                                    Locale.getDefault()
                                )
                            } else {
                                it.toString()
                            }
                        }
                        ?: "Other"
            }
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
                                    contentDescription =
                                        "Clear search",
                                )
                            }
                        }
                    } else {
                        null
                    },
                keyboardOptions =
                    KeyboardOptions(
                        imeAction =
                            ImeAction.Search,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onSearch = {
                            onSearch(query)
                        },
                    ),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 18.dp,
                        top = 10.dp,
                        end = 18.dp,
                    ),
                verticalAlignment =
                    Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = {
                        onSearch(query)
                    },
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                    )
                    Text(
                        text = "Search",
                        modifier =
                            Modifier.padding(
                                start = 6.dp
                            ),
                    )
                }

                Text(
                    text =
                        "${entries.size} " +
                            if (
                                entries.size == 1
                            ) {
                                "entry"
                            } else {
                                "entries"
                            },
                    style =
                        MaterialTheme
                            .typography.bodySmall,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                )
            }

            if (!loading && entries.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(
                        top = 14.dp,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = 18.dp,
                        ),
                        verticalAlignment =
                            Alignment.CenterVertically,
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                7.dp
                            ),
                    ) {
                        Icon(
                            imageVector =
                                Icons.Rounded.Sort,
                            contentDescription = null,
                            tint =
                                MaterialTheme
                                    .colorScheme
                                    .primary,
                        )
                        Text(
                            text = "Sort by",
                            style =
                                MaterialTheme
                                    .typography
                                    .titleSmall,
                            fontWeight =
                                FontWeight.SemiBold,
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(
                                rememberScrollState()
                            )
                            .padding(
                                start = 18.dp,
                                top = 8.dp,
                                end = 18.dp,
                                bottom = 2.dp,
                            ),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            ),
                    ) {
                        DictionarySortMode.entries
                            .forEach { mode ->
                                FilterChip(
                                    selected =
                                        sortMode == mode,
                                    onClick = {
                                        sortMode = mode
                                    },
                                    label = {
                                        Text(mode.label)
                                    },
                                )
                            }
                    }
                }
            }

            if (loading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement =
                        Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (entries.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment =
                        Alignment.CenterHorizontally,
                    verticalArrangement =
                        Arrangement.Center,
                ) {
                    Icon(
                        Icons.Rounded.MenuBook,
                        contentDescription = null,
                        tint =
                            MaterialTheme
                                .colorScheme.primary,
                    )
                    Text(
                        text =
                            if (query.isBlank()) {
                                "No dictionary entries available"
                            } else {
                                "No matching vocabulary"
                            },
                        style =
                            MaterialTheme
                                .typography
                                .titleMedium,
                        fontWeight =
                            FontWeight.Bold,
                        modifier =
                            Modifier.padding(
                                top = 10.dp
                            ),
                    )
                    Text(
                        text =
                            if (query.isBlank()) {
                                "Dictionary entries will appear here when data is available."
                            } else {
                                "Try a different Maya or English word."
                            },
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,
                        modifier =
                            Modifier.padding(
                                top = 4.dp
                            ),
                    )
                }
            } else {
                LazyColumn(
                    contentPadding =
                        PaddingValues(
                            start = 18.dp,
                            top = 8.dp,
                            end = 18.dp,
                            bottom = 112.dp,
                        ),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            10.dp
                        ),
                ) {
                    groupedEntries
                        .forEach {
                            (section, sectionEntries) ->

                            item(
                                key =
                                    "section-" +
                                        sortMode.name +
                                        "-" +
                                        section,
                            ) {
                                DictionarySectionHeader(
                                    title = section,
                                    count =
                                        sectionEntries.size,
                                )
                            }

                            items(
                                items =
                                    sectionEntries,
                                key = {
                                    it.entryId
                                },
                            ) { entry ->
                                DictionaryEntryCard(
                                    entry = entry,
                                    onSave = {
                                        onSaveEntry(
                                            entry
                                        )
                                    },
                                )
                            }
                        }

                    if (hasMore) {
                        item(
                            key = "load-more",
                        ) {
                            FilledTonalButton(
                                onClick = onLoadMore,
                                enabled = !loading,
                                modifier =
                                    Modifier.fillMaxWidth(),
                            ) {
                                Text("Load more words")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DictionarySectionHeader(
    title: String,
    count: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = 8.dp,
                bottom = 2.dp,
            ),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style =
                MaterialTheme
                    .typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color =
                MaterialTheme
                    .colorScheme.primary,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = count.toString(),
            style =
                MaterialTheme
                    .typography.labelMedium,
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
        )
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
            containerColor =
                MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.Top,
            ) {
                Column(
                    modifier =
                        Modifier.weight(1f),
                ) {
                    Text(
                        text = entry.mayaText,
                        style =
                            MaterialTheme
                                .typography
                                .titleLarge,
                        fontWeight =
                            FontWeight.Bold,
                        color =
                            MaterialTheme
                                .colorScheme.primary,
                    )
                    Text(
                        text = entry.englishText,
                        style =
                            MaterialTheme
                                .typography
                                .titleMedium,
                    )
                }

                IconButton(
                    onClick = onSave,
                ) {
                    Icon(
                        Icons.Rounded.BookmarkAdd,
                        contentDescription =
                            "Save vocabulary",
                        tint =
                            MaterialTheme
                                .colorScheme.primary,
                    )
                }
            }

            entry.partOfSpeech
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let {
                    SoftBadge(
                        text =
                            it.replaceFirstChar {
                                char ->
                                if (
                                    char.isLowerCase()
                                ) {
                                    char.titlecase(
                                        Locale
                                            .getDefault()
                                    )
                                } else {
                                    char.toString()
                                }
                            },
                    )
                }

            entry.pronunciation
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let {
                    Text(
                        text =
                            "Pronunciation: $it",
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

            if (entry.verified) {
                Text(
                    text = "Checked entry",
                    style =
                        MaterialTheme
                            .typography
                            .labelSmall,
                    color =
                        MaterialTheme
                            .colorScheme.primary,
                )
            }
        }
    }
}

private fun alphabeticalSection(
    value: String,
): String {
    val first =
        value.trim()
            .firstOrNull()
            ?: return "#"

    return if (first.isLetter()) {
        first.uppercase()
    } else {
        "#"
    }
}
