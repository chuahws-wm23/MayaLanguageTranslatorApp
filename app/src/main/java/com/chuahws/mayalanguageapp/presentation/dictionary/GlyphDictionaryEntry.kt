package com.chuahws.mayalanguageapp.presentation.dictionary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chuahws.mayalanguageapp.integration.glyph.GlyphLearningNotes
import com.chuahws.mayalanguageapp.integration.glyph.GlyphMoreLearningNotes
import com.chuahws.mayalanguageapp.presentation.components.ReferenceDrawing
import com.chuahws.mayalanguageapp.presentation.theme.MayaSize

@Composable
internal fun ArchiveEntryCard(entry: ArchiveEntry, drawing: Int, clues: List<String>, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    OutlinedCard(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, colors.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = colors.surface),
    ) {
        Column(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(colors.primaryContainer, colors.surface)))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(entry.typeLabel, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer)
                Text(entry.caption(drawing).substringBefore(" · "), style = MaterialTheme.typography.labelMedium, color = colors.onPrimaryContainer)
            }
            Text(entry.reading(), style = MaterialTheme.typography.headlineMedium)
            Text(
                if (entry.word != null) glosses(entry.title).joinToString(" · ") else "Represents a sound",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            ArchiveDrawing(entry, drawing, height = 168)
            if (clues.isNotEmpty()) {
                Text(clues.joinToString(" · "), Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    if (entry.count == 1) "View drawing and details" else "Explore ${entry.count} drawings",
                    Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = colors.primary,
                )
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun glosses(text: String) = text.split('|').map(String::trim).filter(String::isNotEmpty).distinct()

@Composable
internal fun ArchiveDrawing(entry: ArchiveEntry, index: Int, height: Int, artworkPadding: Int = 16) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = Color.White,
        contentColor = Color(0xFF245744),
        border = BorderStroke(1.dp, Color(0xFFD9D9C9)),
    ) {
        ReferenceDrawing(
            entry.graph(index), "Reference drawing for ${entry.title}",
            modifier = Modifier.fillMaxWidth().height(height.dp).padding(artworkPadding.dp),
            fallback = { entry.image(index) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArchiveEntryDialog(
    entry: ArchiveEntry,
    initialIndex: Int,
    compareLabel: String,
    onCompare: () -> Unit,
    onStudy: () -> Unit,
    onNote: () -> Unit,
    closeLabel: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val word = entry.word
    val colors = MaterialTheme.colorScheme
    val note = remember(word?.twkmSign) {
        word?.let { GlyphLearningNotes.forSign(it.twkmSign) ?: GlyphMoreLearningNotes.forSign(it.twkmSign) }
    }
    var index by rememberSaveable(entry.key, initialIndex) { mutableIntStateOf(initialIndex) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = colors.background,
            topBar = {
                TopAppBar(
                    title = { Text("Glyph reference", style = MaterialTheme.typography.titleLarge) },
                    actions = { TextButton(onClick = onDismiss) { Text(closeLabel) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface),
                )
            },
        ) { insets ->
            Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = MayaSize.contentMaxWidth).fillMaxSize()
                        .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                if (word == null) "Syllable sound" else "Word sign · Proposed reading",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(entry.reading(), style = MaterialTheme.typography.displaySmall)
                            if (word != null) {
                                Text("English meanings", style = MaterialTheme.typography.titleSmall)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    glosses(word.english).forEach { gloss ->
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = colors.surface,
                                            contentColor = colors.onSurface,
                                        ) {
                                            Text(gloss, Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            } else {
                                Text("Syllable signs spell sounds. They can be combined to write words or clarify a word sign’s reading.",
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    ReferenceGallery(entry, index, onSelect = { index = it })

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilledTonalButton(onClick = onStudy) {
                            Icon(Icons.Rounded.BookmarkAdd, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add to study set")
                        }
                        OutlinedButton(onClick = onNote) {
                            Icon(Icons.Rounded.EditNote, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Write research note")
                        }
                        OutlinedButton(onClick = onCompare) { Text(compareLabel) }
                    }

                    if (word != null) {
                        ReferenceSection(if (note != null) "How it was used" else "Using this sign") {
                            if (note != null) {
                                Text(note.category, style = MaterialTheme.typography.labelLarge, color = colors.primary)
                                Text(note.explanation)
                                Text(note.historicalContext)
                                if (note.sourceUrl != CATALOGUE_URL) {
                                    TextButton(onClick = { uri.openUri(note.sourceUrl) }) { Text("Read the context source") }
                                }
                            } else {
                                Text("A word sign represents a word or meaningful word element. Use nearby signs to decide which English meaning fits the inscription.")
                            }
                        }
                    }

                    ReferenceSection("Catalogue and sources") {
                        if (word != null) {
                            Text("TWKM reading evidence · Level ${word.scholarlyLevel} of 8", style = MaterialTheme.typography.titleSmall)
                            Text("Levels describe the evidence for a reading. Level 1 is the most established and level 8 is the most tentative.",
                                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                        Text("Text Database and Dictionary of Classic Mayan (TWKM), University of Bonn. Drawing by Christian Prager, CC BY 4.0.",
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        TextButton(onClick = {
                            copyDictionaryText(context, "Glyph details",
                                listOf(entry.reading(), entry.meaning(), entry.caption(index), CATALOGUE_URL).joinToString("\n"))
                        }) {
                            Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy glyph details")
                        }
                        TextButton(onClick = { uri.openUri(CATALOGUE_URL) }) {
                            Text("View catalogue source")
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                        }
                        TextButton(onClick = { uri.openUri("https://www.mayaglyphs.org/concordances.html") }) {
                            Text("Cross-check catalogue IDs online")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReferenceGallery(entry: ArchiveEntry, index: Int, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Reference drawings", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            Text(entry.caption(index), style = MaterialTheme.typography.labelMedium, color = colors.primary)
        }
        ArchiveDrawing(entry, index, height = 232, artworkPadding = 12)
        if (entry.count > 1) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { onSelect(index - 1) }, enabled = index > 0) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Previous drawing")
                }
                Text("Drawing ${index + 1} of ${entry.count}", style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = { onSelect(index + 1) }, enabled = index < entry.count - 1) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Next drawing")
                }
            }
            val thumbnails = rememberLazyListState(initialFirstVisibleItemIndex = index)
            LaunchedEffect(index) {
                if (thumbnails.layoutInfo.visibleItemsInfo.none { it.index == index }) thumbnails.scrollToItem(index)
            }
            LazyRow(state = thumbnails, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items((0 until entry.count).toList(), key = { it }) { drawing ->
                    Surface(
                        onClick = { onSelect(drawing) },
                        modifier = Modifier.size(60.dp).semantics {
                            selected = drawing == index
                            contentDescription = "Show drawing ${drawing + 1}, ${entry.graph(drawing)}"
                        },
                        shape = MaterialTheme.shapes.small,
                        color = Color.White,
                        contentColor = Color(0xFF245744),
                        border = BorderStroke(
                            if (drawing == index) 2.dp else 1.dp,
                            if (drawing == index) colors.primary else colors.outlineVariant,
                        ),
                    ) {
                        val thumbnail = remember(entry.key, drawing) { entry.image(drawing).asImageBitmap() }
                        Image(
                            thumbnail, contentDescription = null,
                            modifier = Modifier.fillMaxSize().padding(7.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReferenceSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}
