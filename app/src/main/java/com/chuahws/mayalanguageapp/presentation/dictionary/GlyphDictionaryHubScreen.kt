package com.chuahws.mayalanguageapp.presentation.dictionary

import com.chuahws.mayalanguageapp.presentation.components.ReferenceDrawing
import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import com.chuahws.mayalanguageapp.domain.service.MayaReadingOrder
import com.chuahws.mayalanguageapp.domain.service.ListPaging
import com.chuahws.mayalanguageapp.presentation.components.ListPaginationControls
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
    val graph: (Int) -> String,
    val word: GlyphEnglishReference? = null,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlyphDictionaryHubScreen() {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val focus = LocalFocusManager.current
    var selected by rememberSaveable { mutableStateOf(DictionarySection.WORD_SIGNS) }
    var query by rememberSaveable(selected) { mutableStateOf("") }
    var page by rememberSaveable(selected, query) { mutableIntStateOf(1) }
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
                    { "TWKM #${drawings[it].twkmSign} · ${drawings[it].graph}" }, { drawings[it].graph }, sign)
            }
            DictionarySection.SYLLABLES -> archive.sounds.syllables.map { syllable ->
                val drawings = archive.sounds.forSyllable(syllable)
                ArchiveEntry("sound-$syllable", syllable, "Syllable sound",
                    "$syllable ${drawings.joinToString(" ") { it.twkmSign.toString() }}", drawings.size,
                    { archive.sounds.referenceBitmap(drawings[it]) },
                    { "TWKM #${drawings[it].twkmSign} · ${drawings[it].graph}" }, { drawings[it].graph })
            }
        }
    }
    val filtered = remember(entries, query) {
        entries.filter { it.searchText.contains(query.trim(), ignoreCase = true) }
            .sortedWith { a,b ->
                val result=MayaReadingOrder.compare(a.word?.reading ?: a.title,b.word?.reading ?: b.title)
                if(result!=0)result else a.key.compareTo(b.key)
            }
    }
    val currentPage=ListPaging.safePage(page,filtered.size)
    val visibleEntries=ListPaging.pageItems(filtered,currentPage)
    LaunchedEffect(selected, query, currentPage) {
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
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
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
                    ListPaginationControls(filtered.size,currentPage,{ page=it })
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
                    Text("${ListPaging.firstVisibleIndex(currentPage,filtered.size)}–${ListPaging.lastVisibleIndex(currentPage,filtered.size)} of ${filtered.size} · Maya reading A–Y", Modifier.padding(horizontal = 20.dp),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                if (filtered.isEmpty()) item {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No matching glyphs", style = MaterialTheme.typography.titleLarge)
                        Text("Try a different meaning, reading or sign number.")
                        TextButton(onClick = { query = "" }) { Text("Clear search") }
                    }
                }
                items(visibleEntries, key = { it.key }) { entry ->
                    Card(onClick = { opened = entry.key }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(if (entry.word != null) "WORD SIGN · TWKM #${entry.word.twkmSign}" else "SYLLABLE REFERENCE",
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            ArchiveDrawing(entry, 0, 168)
                            Text(entry.word?.reading ?: entry.title, style = MaterialTheme.typography.headlineSmall)
                            Text(if(entry.word!=null)entry.title else entry.subtitle, style = MaterialTheme.typography.bodyLarge,
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
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
        ReferenceDrawing(entry.graph(index),"Reference drawing for ${entry.title}",
            modifier = Modifier.fillMaxWidth().height(height.dp).padding(16.dp),
            fallback = { entry.image(index) })
    }
}

@Composable
private fun ArchiveEntryDialog(entry: ArchiveEntry, onDismiss: () -> Unit) {
    var index by rememberSaveable(entry.key) { mutableIntStateOf(0) }
    val uri = LocalUriHandler.current
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(entry.word?.reading ?: entry.title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ArchiveDrawing(entry, index, 224)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { index=maxOf(0,index-1) }, enabled = index > 0) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Previous drawing")
                    }
                    Text("Drawing ${index+1} of ${entry.count}", style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = { index=minOf(entry.count-1,index+1) }, enabled = index < entry.count-1) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Next drawing")
                    }
                }
                Text(entry.caption(index), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
                Text(if(entry.word!=null)entry.title else entry.subtitle, style = MaterialTheme.typography.titleMedium)
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
