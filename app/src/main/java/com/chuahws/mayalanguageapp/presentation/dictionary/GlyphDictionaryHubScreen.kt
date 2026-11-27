package com.chuahws.mayalanguageapp.presentation.dictionary

import com.chuahws.mayalanguageapp.presentation.components.ClearSearchButton
import android.graphics.Bitmap
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import com.chuahws.mayalanguageapp.domain.service.MayaReadingOrder
import com.chuahws.mayalanguageapp.domain.service.ListPaging
import com.chuahws.mayalanguageapp.presentation.components.ListPaginationControls
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FindInPage
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.integration.glyph.GlyphDrawingSource
import com.chuahws.mayalanguageapp.integration.glyph.GlyphEnglishMeaningCatalogue
import com.chuahws.mayalanguageapp.integration.glyph.GlyphEnglishReference
import com.chuahws.mayalanguageapp.integration.glyph.GlyphVisualCatalogue
import com.chuahws.mayalanguageapp.presentation.components.AppGradientBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class DictionarySection(val title: String) {
    WORD_SIGNS("Word signs"), SYLLABLES("Syllables"),
}

private enum class VisualClue(val label: String, val example: String) {
    ROUNDS("Rows of circles", "32vb"),
    LINES("Parallel lines", "12st"),
    PROFILE("Face in profile", "4vc"),
    CROSS("Crossed bands", "44bb"),
}

// Tags describe individual TWKM drawings. Variants of a sign can have different clues.
private val drawingClues = mapOf(
    "4vc" to setOf(VisualClue.PROFILE),
    "4va" to setOf(VisualClue.PROFILE),
    "12st" to setOf(VisualClue.LINES),
    "12md" to setOf(VisualClue.LINES),
    "32vb" to setOf(VisualClue.ROUNDS),
    "32vl" to setOf(VisualClue.ROUNDS),
    "32vr" to setOf(VisualClue.ROUNDS),
    "44bv" to setOf(VisualClue.ROUNDS, VisualClue.CROSS),
    "44do" to setOf(VisualClue.ROUNDS),
    "44bb" to setOf(VisualClue.CROSS, VisualClue.LINES),
    "45st" to setOf(VisualClue.ROUNDS),
    "45dh" to setOf(VisualClue.ROUNDS),
    "79bv" to setOf(VisualClue.LINES),
    "109ex" to setOf(VisualClue.LINES),
    "122bv" to setOf(VisualClue.LINES),
    "128hh" to setOf(VisualClue.PROFILE),
    "187hh" to setOf(VisualClue.PROFILE, VisualClue.LINES),
    "200br" to setOf(VisualClue.ROUNDS),
)

private val cataloguePrefix = Regex("""^(?:TWKM\s*#?\s*|#\s*)(?=\d)""", RegexOption.IGNORE_CASE)
private val apostrophes = Regex("['’ʼ‘]")
private fun searchKey(text: String) = text.trim().replace(cataloguePrefix, "").replace(apostrophes, "")

private data class Archive(val words: GlyphEnglishMeaningCatalogue, val sounds: GlyphVisualCatalogue)
internal data class ArchiveEntry(
    val key: String,
    val title: String,
    val subtitle: String,
    val searchText: String,
    val count: Int,
    val image: (Int) -> Bitmap,
    val caption: (Int) -> String,
    val graph: (Int) -> String,
    val word: GlyphEnglishReference? = null,
    val drawingSource: (Int) -> GlyphDrawingSource? = { null },
) {
    val typeLabel get() = if (word != null) "Word sign" else "Syllable"
}

@Composable
fun GlyphDictionaryHubScreen(
    accountId: String = "local",
    requestedSign: Int? = null,
    requestedDrawing: String? = null,
    onReferenceOpened: () -> Unit = {},
    onBackToScan: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val store = remember(context, accountId) { DictionaryNotebookStore(context, accountId) }
    var notebook by remember(store) { mutableStateOf(store.load()) }
    fun updateNotebook(value: DictionaryNotebook) {
        store.save(value)
        notebook = value
    }
    var tool by rememberSaveable { mutableStateOf<String?>(null) }
    var activeEntry by rememberSaveable { mutableStateOf<String?>(null) }
    var compareFirst by rememberSaveable { mutableStateOf<String?>(null) }
    var activeSet by rememberSaveable { mutableStateOf<String?>(null) }
    var setName by rememberSaveable { mutableStateOf("") }
    val uri = LocalUriHandler.current
    var onlineSources by rememberSaveable { mutableStateOf(false) }
    if (onlineSources) DictionaryToolDialog("Online glyph references", { onlineSources = false }) {
        Text("The Learner’s Maya Glyph Guide includes readings, catalogue cross-references, and examples from inscriptions.")
        TextButton(onClick = { uri.openUri("https://mayaglyphs.org/") }) { Text("Open MayaGlyphs.org") }
        TextButton(onClick = { uri.openUri("https://www.mayaglyphs.org/concordances.html") }) { Text("Browse catalogue concordances") }
    }
    val listState = rememberLazyListState()
    val focus = LocalFocusManager.current
    var selected by rememberSaveable { mutableStateOf(DictionarySection.WORD_SIGNS) }
    var query by rememberSaveable { mutableStateOf("") }
    var guided by rememberSaveable { mutableStateOf(false) }
    var clueNames by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    val clues = VisualClue.entries.filter { it.name in clueNames }
    val guiding = guided && selected == DictionarySection.WORD_SIGNS
    var page by rememberSaveable { mutableIntStateOf(1) }
    var showTools by remember { mutableStateOf(false) }
    fun resetResults() {
        page = 1
        listState.requestScrollToItem(0)
    }
    fun toggleClues() {
        focus.clearFocus()
        showTools = false
        guided = !guided
        resetResults()
    }
    fun search(term: String) {
        if (query != term) {
            query = term
            resetResults()
        }
    }
    var openedFromScan by rememberSaveable { mutableStateOf(false) }
    var openedDrawing by rememberSaveable { mutableStateOf<String?>(null) }
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
    val archiveEntries = remember(archive) {
        fun build(section: DictionarySection): List<ArchiveEntry> = if (archive == null) emptyList() else when (section) {
            DictionarySection.WORD_SIGNS -> archive.words.distinctSigns.map { sign ->
                val drawings = archive.words.references.filter { it.twkmSign == sign.twkmSign }
                ArchiveEntry("word-${sign.twkmSign}", sign.english, sign.reading,
                    searchKey("${sign.english} ${sign.reading} ${sign.twkmSign} ${drawings.joinToString(" ") { it.graph }}"), drawings.size,
                    { archive.words.imageFor(drawings[it]) },
                    { index ->
                        val drawing = drawings[index]
                        val label = drawing.drawingSource?.let { "Supplemental drawing" } ?: drawing.graph
                        "TWKM #${drawing.twkmSign} · $label"
                    }, { drawings[it].graph }, sign, { drawings[it].drawingSource })
            }
            DictionarySection.SYLLABLES -> archive.sounds.syllables.map { syllable ->
                val drawings = archive.sounds.forSyllable(syllable)
                ArchiveEntry("sound-$syllable", syllable, "Syllable sound",
                    searchKey("$syllable ${drawings.joinToString(" ") { "${it.twkmSign} ${it.graph}" }}"), drawings.size,
                    { archive.sounds.referenceBitmap(drawings[it]) },
                    { "TWKM #${drawings[it].twkmSign} · ${drawings[it].graph}" }, { drawings[it].graph })
            }
        }
        DictionarySection.entries.associateWith(::build)
    }
    val entries = archiveEntries[selected].orEmpty()
    val byKey = remember(archiveEntries) { archiveEntries.values.flatten().associateBy { it.key } }
    LaunchedEffect(archive, requestedSign, requestedDrawing) {
        if (archive == null || requestedSign == null) return@LaunchedEffect
        val entry = byKey["word-$requestedSign"]
        if (entry != null) {
            openedFromScan = true
            openedDrawing = requestedDrawing
            opened = entry.key
        }
        onReferenceOpened()
    }
    val chosen = byKey[activeEntry]
    val first = byKey[compareFirst]
    fun closeTool() { tool = null; activeEntry = null }
    when (tool) {
        "compare" -> if (first != null && chosen != null) CompareEntries(first, chosen) {
            compareFirst = null
            closeTool()
        }
        "note" -> if (chosen != null) ResearchNoteEditor(chosen, notebook.notes[chosen.key] ?: ResearchNote(), { note ->
            val notes = notebook.notes.toMutableMap()
            if (note.text.isBlank() && note.source.isBlank()) notes.remove(chosen.key) else notes[chosen.key] = note
            updateNotebook(notebook.copy(notes = notes))
            closeTool()
        }, ::closeTool)
        "notes" -> ResearchNotes(byKey, notebook.notes, { entry -> activeEntry = entry.key; tool = "note" }, ::closeTool)
        "add" -> if (chosen != null) {
            fun addToSet(name: String) {
                if (name.isBlank()) return
                val signs = (notebook.sets[name].orEmpty() + chosen.key).distinct()
                updateNotebook(notebook.copy(sets = notebook.sets + (name to signs)))
                activeSet = name
                activeEntry = null
                tool = "set"
            }
            DictionaryToolDialog(
                title = "Add to study set",
                onClose = ::closeTool,
                confirm = {
                    Button(onClick = { addToSet(setName.trim()) }, enabled = setName.isNotBlank()) {
                        Text("Add glyph")
                    }
                },
            ) {
                notebook.sets.keys.sorted().forEach { name ->
                    TextButton(onClick = { addToSet(name) }) { Text(name) }
                }
                OutlinedTextField(
                    value = setName,
                    onValueChange = { setName = it.take(60) },
                    singleLine = true,
                    label = { Text("New set name") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addToSet(setName.trim()) }),
                )
            }
        }
        "sets" -> DictionaryToolDialog("Study sets", ::closeTool) {
            if (notebook.sets.isEmpty()) Text("Open a dictionary entry to add its glyph to a study set.")
            notebook.sets.toSortedMap().forEach { (name, keys) ->
                TextButton(onClick = { activeSet = name; tool = "set" }) { Text("$name · ${keys.size} ${if (keys.size == 1) "glyph" else "glyphs"}") }
            }
        }
        "set" -> {
            val signs = notebook.sets[activeSet].orEmpty().mapNotNull(byKey::get)
            DictionaryToolDialog(activeSet.orEmpty(), { tool = "sets" }) {
                Button(onClick = { tool = "review" }, enabled = signs.isNotEmpty()) { Text("Review set") }
                if (signs.isEmpty()) Text("This set is empty.")
                signs.forEach { entry ->
                    Text(entry.reading())
                    TextButton(onClick = {
                        activeSet?.let { name ->
                            val remaining = notebook.sets[name].orEmpty() - entry.key
                            updateNotebook(notebook.copy(sets = notebook.sets + (name to remaining)))
                        }
                    }) { Text("Remove ${entry.reading()} from set") }
                }
                if (signs.isEmpty()) TextButton(onClick = {
                    updateNotebook(notebook.copy(sets = notebook.sets.filterKeys { it != activeSet }))
                    tool = "sets"
                }) { Text("Delete empty set") }
            }
        }
        "review" -> StudyReview(notebook.sets[activeSet].orEmpty().mapNotNull(byKey::get)) { tool = "set" }
    }
    val matchingDrawings = remember(entries, guiding, clues) {
        if (!guiding) emptyMap() else entries.mapNotNull { entry ->
            (0 until entry.count).firstOrNull { index ->
                drawingClues[entry.graph(index)]?.containsAll(clues) == true
            }?.let { entry.key to it }
        }.toMap()
    }
    val term = remember(query) { searchKey(query) }
    val filtered = remember(entries, term, guiding, matchingDrawings) {
        entries.filter { (!guiding || it.key in matchingDrawings) && it.searchText.contains(term, ignoreCase = true) }
            .sortedWith { a,b ->
                val result=MayaReadingOrder.compare(a.word?.reading ?: a.title,b.word?.reading ?: b.title)
                if(result!=0)result else a.key.compareTo(b.key)
            }
    }
    val currentPage=ListPaging.safePage(page,filtered.size)
    val visibleEntries=ListPaging.pageItems(filtered,currentPage)
    byKey[opened]?.let { entry ->
        val returnToScan = onBackToScan.takeIf { openedFromScan }
        fun closeEntry() {
            opened = null
            openedDrawing = null
            openedFromScan = false
        }
        val requestedIndex = openedDrawing?.let { graph -> (0 until entry.count).firstOrNull { entry.graph(it) == graph } }
        ArchiveEntryDialog(entry, initialIndex = requestedIndex ?: matchingDrawings[entry.key] ?: 0,
            compareLabel = if (compareFirst == null || compareFirst == entry.key) "Select for comparison" else "Compare with ${first?.reading().orEmpty()}",
            onCompare = {
                closeEntry()
                if (compareFirst == null || compareFirst == entry.key) compareFirst = entry.key
                else { activeEntry = entry.key; tool = "compare" }
            },
            onStudy = { closeEntry(); activeEntry = entry.key; setName = ""; tool = "add" },
            onNote = { closeEntry(); activeEntry = entry.key; tool = "note" },
            closeLabel = if (returnToScan != null) "Back to scan" else "Close",
            onDismiss = {
                closeEntry()
                returnToScan?.invoke()
            })
    }
    AppGradientBackground {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = ::search,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            query.trim().takeIf { it.isNotEmpty() }?.let { term ->
                                updateNotebook(notebook.copy(searches = (listOf(term) + notebook.searches.filterNot { it.equals(term, true) }).take(5)))
                            }
                            listState.requestScrollToItem(0)
                            focus.clearFocus()
                        }),
                        shape = MaterialTheme.shapes.large,
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = { ClearSearchButton(query) { search("") } },
                        label = { Text(if (selected == DictionarySection.WORD_SIGNS) "Meaning, reading or ID" else "Search syllable or sign number") },
                    )
                    Box {
                        IconButton(onClick = { focus.clearFocus(); showTools = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "Dictionary tools")
                        }
                        DropdownMenu(expanded = showTools, onDismissRequest = { showTools = false }) {
                            if (archive != null) {
                                if (selected == DictionarySection.WORD_SIGNS) {
                                    DropdownMenuItem(
                                        text = { Text(if (guiding) "Return to all word signs" else "Find by visible clues") },
                                        onClick = ::toggleClues,
                                    )
                                }
                                DropdownMenuItem(text = { Text("Study sets") }, onClick = { showTools = false; tool = "sets" })
                                DropdownMenuItem(text = { Text("Research notes") }, onClick = { showTools = false; tool = "notes" })
                            }
                            DropdownMenuItem(text = { Text("Online references") }, onClick = { showTools = false; onlineSources = true })
                            if (notebook.searches.isNotEmpty()) {
                                HorizontalDivider()
                                Text("Recent searches", Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium)
                                notebook.searches.forEach { term ->
                                    DropdownMenuItem(text = { Text(term) }, onClick = { showTools = false; search(term) })
                                }
                                DropdownMenuItem(
                                    text = { Text("Clear recent searches") },
                                    onClick = { updateNotebook(notebook.copy(searches = emptyList())) },
                                )
                            }
                        }
                    }
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    DictionarySection.entries.forEachIndexed { index, section ->
                        SegmentedButton(
                            selected = selected == section,
                            onClick = {
                                if (selected != section) {
                                    selected = section
                                    query = ""
                                    resetResults()
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, DictionarySection.entries.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                inactiveContainerColor = MaterialTheme.colorScheme.surface,
                                inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                activeBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Text(section.title, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
                ListPaginationControls(filtered.size, currentPage, { nextPage ->
                    if (page != nextPage) {
                        page = nextPage
                        listState.requestScrollToItem(0)
                    }
                })
            }
            if (loaded == null) {
                Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator()
                    Text("Opening the archive…")
                }
            } else if (archive == null) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Archive unavailable", style = MaterialTheme.typography.titleLarge)
                    Text("The reference drawings could not be opened. Please try again.")
                    Button(onClick = { reload++ }) { Text("Retry") }
                }
            } else LazyColumn(
                modifier = Modifier.weight(1f), state = listState,
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (first != null) item {
                    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Selected ${first.reading()}. Open another entry to compare.", Modifier.weight(1f))
                        TextButton(onClick = { compareFirst = null }) { Text("Clear selection") }
                    }
                }
                if (guiding) item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Guided Glyph Lookup", style = MaterialTheme.typography.titleLarge)
                        Text("Choose visible features to find drawings that share every selected clue. Covers ${drawingClues.size} curated drawings.")
                        VisualClue.entries.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { clue ->
                                    FilterChip(
                                        selected = clue in clues,
                                        onClick = {
                                            clueNames = ArrayList(clueNames).apply {
                                                if (!remove(clue.name)) add(clue.name)
                                            }
                                            resetResults()
                                        },
                                        label = {
                                            Column(Modifier.padding(vertical = 6.dp)) {
                                                val example = archive.words.references.firstOrNull { it.graph == clue.example }
                                                if (example != null) {
                                                    val bitmap = remember(example) { archive.words.imageFor(example).asImageBitmap() }
                                                    Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxWidth().height(44.dp))
                                                }
                                                Text(clue.label)
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                        TextButton(onClick = { clueNames = arrayListOf(); resetResults() }, enabled = clues.isNotEmpty()) { Text("Clear clues") }
                    }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val start = ListPaging.firstVisibleIndex(currentPage, filtered.size)
                        val end = ListPaging.lastVisibleIndex(currentPage, filtered.size)
                        Text(
                            if (filtered.isEmpty()) "0 results" else "$start–$end of ${filtered.size}",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (selected == DictionarySection.WORD_SIGNS && query.isBlank() && !guiding) {
                            TextButton(onClick = ::toggleClues, contentPadding = PaddingValues(horizontal = 6.dp)) {
                                Icon(Icons.Rounded.FindInPage, contentDescription = null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Visual clues")
                            }
                        }
                    }
                }
                if (filtered.isEmpty()) item {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No matching glyphs", style = MaterialTheme.typography.titleLarge)
                        Text(if (guiding) "Remove a clue or clear your text search to see more drawings." else "Try a different meaning, reading or sign number.")
                        TextButton(onClick = { search("") }) { Text("Clear search") }
                    }
                }
                items(visibleEntries, key = { it.key }) { entry ->
                    val drawing = matchingDrawings[entry.key]
                        ?: (0 until entry.count).firstOrNull { entry.graph(it).equals(term, ignoreCase = true) }
                        ?: 0
                    ArchiveEntryCard(
                        entry = entry,
                        drawing = drawing,
                        clues = if (guiding) drawingClues[entry.graph(drawing)].orEmpty().map { it.label } else emptyList(),
                        onOpen = { openedDrawing = entry.graph(drawing); opened = entry.key },
                    )
                }
                item {
                    Text("TWKM / University of Bonn · Catalogue readings are source-proposed. See each drawing for its attribution.",
                        Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
