package com.chuahws.mayalanguageapp.presentation.dictionary

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

internal const val CATALOGUE_URL = "https://classicmayan.org/portal/signcatalog"

internal fun ArchiveEntry.reading() = word?.reading ?: title
internal fun ArchiveEntry.meaning() = if (word != null) title else subtitle

internal fun copyDictionaryText(context: Context, label: String, text: String) {
    context.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText(label, text))
}

@Composable
internal fun DictionaryToolDialog(
    title: String,
    onClose: () -> Unit,
    confirm: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        },
        confirmButton = {
            if (confirm != null) confirm() else TextButton(onClick = onClose) { Text("Close") }
        },
        dismissButton = if (confirm != null) {
            { TextButton(onClick = onClose) { Text("Cancel") } }
        } else null,
    )
}

@Composable
internal fun CompareEntries(first: ArchiveEntry, second: ArchiveEntry, onClose: () -> Unit) {
    DictionaryToolDialog("Compare glyphs", onClose) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(first, second).forEach { entry ->
                key(entry.key) {
                    var drawing by remember(entry.key) { mutableIntStateOf(0) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ArchiveDrawing(entry, drawing, 112, artworkPadding = 6)
                        Text(entry.reading(), style = MaterialTheme.typography.titleMedium)
                        Text(entry.meaning())
                        Text(entry.caption(drawing), style = MaterialTheme.typography.labelMedium)
                        if (entry.count > 1) {
                            TextButton(onClick = { drawing = (drawing + 1) % entry.count }) {
                                Text("Another drawing")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ResearchNoteEditor(
    entry: ArchiveEntry,
    initial: ResearchNote,
    onSave: (ResearchNote) -> Unit,
    onClose: () -> Unit,
) {
    var note by remember(entry.key) { mutableStateOf(initial.text) }
    var source by remember(entry.key) { mutableStateOf(initial.source) }
    DictionaryToolDialog("Research note", onClose) {
        Text(entry.reading())
        OutlinedTextField(note, { note = it }, label = { Text("Your note") },
            minLines = 3, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(source, { source = it }, label = { Text("Source link or citation") },
            modifier = Modifier.fillMaxWidth())
        Button(onClick = { onSave(ResearchNote(note.trim(), source.trim())) }) { Text("Save note") }
        if (initial.text.isNotBlank() || initial.source.isNotBlank()) {
            TextButton(onClick = { onSave(ResearchNote()) }) { Text("Delete note") }
        }
    }
}

@Composable
internal fun ResearchNotes(
    entries: Map<String, ArchiveEntry>,
    notes: Map<String, ResearchNote>,
    onEdit: (ArchiveEntry) -> Unit,
    onClose: () -> Unit,
) {
    DictionaryToolDialog("Research notes", onClose) {
        ResearchNotesContent(entries, notes, onEdit)
    }
}

@Composable
internal fun ResearchNotesContent(
    entries: Map<String, ArchiveEntry>,
    notes: Map<String, ResearchNote>,
    onEdit: (ArchiveEntry) -> Unit,
) {
    val context = LocalContext.current
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(query, { query = it }, label = { Text("Search notes") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        val matches = notes.mapNotNull { (key, note) ->
            val entry = entries[key] ?: return@mapNotNull null
            if (listOf(entry.reading(), note.text, note.source).none { it.contains(query.trim(), true) }) {
                return@mapNotNull null
            }
            entry to note
        }
        if (matches.isEmpty()) Text(if (notes.isEmpty()) "Add a note from a dictionary entry." else "No matching notes.")
        matches.forEach { (entry, note) ->
            Text(entry.reading(), style = MaterialTheme.typography.titleMedium)
            if (note.text.isNotBlank()) Text(note.text)
            if (note.source.isNotBlank()) Text(note.source, style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = { onEdit(entry) }) { Text("Edit") }
                TextButton(onClick = {
                    copyDictionaryText(context, "Research note", listOf(entry.reading(), entry.caption(0), note.text, note.source)
                        .filter { it.isNotBlank() }.joinToString("\n"))
                }) { Text("Copy note") }
            }
            HorizontalDivider()
        }
    }
}

@Composable
internal fun StudyReview(entries: List<ArchiveEntry>, onClose: () -> Unit) {
    var queue by remember(entries) { mutableStateOf(entries) }
    var revealed by remember { mutableStateOf(false) }
    val attempts = remember(entries) { mutableStateMapOf<String, Int>() }
    val entry = queue.firstOrNull()
    DictionaryToolDialog("Review glyphs", onClose) {
        if (entry == null) {
            Text("Review complete")
        } else {
            Text("${queue.size} left", style = MaterialTheme.typography.labelMedium)
            ArchiveDrawing(entry, (attempts[entry.key] ?: 0) % entry.count, 176)
            if (revealed) {
                Text(entry.reading(), style = MaterialTheme.typography.headlineSmall)
                Text(entry.meaning())
                Row {
                    TextButton(onClick = {
                        attempts[entry.key] = (attempts[entry.key] ?: 0) + 1
                        queue = queue.drop(1) + queue.first()
                        revealed = false
                    }) { Text("Again") }
                    Button(onClick = { queue = queue.drop(1); revealed = false }) { Text("Got it") }
                }
            } else Button(onClick = { revealed = true }) { Text("Reveal reading") }
        }
    }
}
