package com.chuahws.mayalanguageapp.presentation.dictionary

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.integration.glyph.GlyphEnglishMeaningCatalogue
import com.chuahws.mayalanguageapp.integration.glyph.GlyphEnglishReference
import com.chuahws.mayalanguageapp.integration.glyph.GlyphVisualCatalogue
import com.chuahws.mayalanguageapp.presentation.components.AppGradientBackground
import com.chuahws.mayalanguageapp.presentation.translation.EnglishMeaningLearningDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class DictionarySection(val title: String) {
    WORD_SIGNS("Word signs"), SYLLABLES("Syllables"),
}

private data class Archive(val words: GlyphEnglishMeaningCatalogue, val sounds: GlyphVisualCatalogue)
private data class ArchiveEntry(
    val key: String,
    val title: String,
    val subtitle: String,
    val searchText: String,
    val count: Int,
    val image: (Int) -> Bitmap,
    val caption: (Int) -> String,
    val word: GlyphEnglishReference? = null,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlyphDictionaryHubScreen() {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var selected by rememberSaveable { mutableStateOf(DictionarySection.WORD_SIGNS) }
    var query by rememberSaveable(selected) { mutableStateOf("") }
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val loaded by produceState<Result<Archive>?>(null, context, reload) {
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching { Archive(
                requireNotNull(GlyphEnglishMeaningCatalogue.fromAssetsOrNull(context)),
                requireNotNull(GlyphVisualCatalogue.fromAssetsOrNull(context)),
            ) }
        }
    }
    val archive = loaded?.getOrNull()
    val entries = remember(archive, selected) {
        if (archive == null) emptyList() else when (selected) {
            DictionarySection.WORD_SIGNS -> archive.words.distinctSigns.map { sign ->
                val drawings = archive.words.references.filter { it.twkmSign == sign.twkmSign }
                ArchiveEntry("word-${sign.twkmSign}", sign.english, sign.reading,
                    "${sign.english} ${sign.reading} ${sign.twkmSign}", drawings.size,
                    { archive.words.imageFor(drawings[it]) },
                    { "TWKM #${drawings[it].twkmSign} · ${drawings[it].graph}" }, sign)
            }
            DictionarySection.SYLLABLES -> archive.sounds.syllables.map { syllable ->
                val drawings = archive.sounds.forSyllable(syllable)
                ArchiveEntry("sound-$syllable", syllable, "Syllable sound",
                    "$syllable ${drawings.joinToString(" ") { it.twkmSign.toString() }}", drawings.size,
                    { archive.sounds.referenceBitmap(drawings[it]) },
                    { "TWKM #${drawings[it].twkmSign} · ${drawings[it].graph}" })
            }
        }
    }
    val filtered = remember(entries, query) {
        entries.filter { it.searchText.contains(query.trim(), ignoreCase = true) }
    }
    LaunchedEffect(selected, query) {
        if (listState.firstVisibleItemIndex > 2) listState.scrollToItem(1)
    }
    entries.firstOrNull { it.key == opened }?.let { entry ->
        ArchiveEntryDialog(entry, onDismiss = { opened = null })
    }
    AppGradientBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(), state = listState,
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.MenuBook, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        }
                    }
                    Column {
                        Text("THE REFERENCE ARCHIVE", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                        Text("Maya dictionary", style = MaterialTheme.typography.headlineLarge)
                    }
                }
            }
            stickyHeader {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = query, onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        label = { Text(if (selected == DictionarySection.WORD_SIGNS) "Search meaning, reading or number" else "Search syllable or sign number") })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DictionarySection.entries.forEach { section ->
                            FilterChip(selected = selected == section,
                                onClick = { selected = section; opened = null },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                shape = MaterialTheme.shapes.medium,
                                label = { Text(section.title, style = MaterialTheme.typography.titleSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary))
                        }
                    }
                }
            }
            if (loaded == null) item {
                Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator()
                    Text("Opening the archive…")
                }
            } else if (archive == null) item {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Archive unavailable", style = MaterialTheme.typography.titleLarge)
                    Text("The reference drawings could not be opened. Please try again.")
                    Button(onClick = { reload++ }) { Text("Retry") }
                }
            } else {
                item {
                    Text("${filtered.size} entries · tap to enlarge", Modifier.padding(horizontal = 20.dp),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                if (filtered.isEmpty()) item {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No matching glyphs", style = MaterialTheme.typography.titleLarge)
                        Text("Try a different meaning, reading or sign number.")
                        TextButton(onClick = { query = "" }) { Text("Clear search") }
                    }
                }
                items(filtered, key = { it.key }) { entry ->
                    Card(onClick = { opened = entry.key }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(if (entry.word != null) "WORD SIGN · TWKM #${entry.word.twkmSign}" else "SYLLABLE REFERENCE",
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            ArchiveDrawing(entry, 0, 168)
                            Text(entry.title, style = MaterialTheme.typography.headlineSmall)
                            Text(entry.subtitle, style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${entry.count} drawings · explore", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary)
                                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                item {
                    Text("TWKM / University of Bonn · Drawings by Christian Prager · CC BY 4.0. Catalogue readings are source-proposed.",
                        Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ArchiveDrawing(entry: ArchiveEntry, index: Int, height: Int) {
    val image = remember(entry.key, index) { entry.image(index) }
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
        Image(image.asImageBitmap(), contentDescription = "Reference drawing for ${entry.title}",
            modifier = Modifier.fillMaxWidth().height(height.dp).padding(16.dp), contentScale = ContentScale.Fit)
    }
}

@Composable
private fun ArchiveEntryDialog(entry: ArchiveEntry, onDismiss: () -> Unit) {
    var index by rememberSaveable(entry.key) { mutableIntStateOf(0) }
    val uri = LocalUriHandler.current
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(entry.title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ArchiveDrawing(entry, index, 224)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { index-- }, enabled = index > 0) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Previous drawing")
                    }
                    Text("Drawing ${index+1} of ${entry.count}", style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = { index++ }, enabled = index < entry.count-1) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Next drawing")
                    }
                }
                Text(entry.caption(index), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
                Text(entry.subtitle, style = MaterialTheme.typography.titleMedium)
                if (entry.word != null) EnglishMeaningLearningDetails(entry.word)
                else Text("This reference represents the proposed syllable ${entry.title}. Syllable signs write sounds; they are not English word translations.",
                    style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = { uri.openUri("https://classicmayan.org/portal/signcatalog") }) {
                    Text("View catalogue source")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}
