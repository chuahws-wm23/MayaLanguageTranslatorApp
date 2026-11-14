package com.chuahws.mayalanguageapp.presentation.translation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.integration.glyph.GlyphSampleStore

/**
 * Manual-only FYP collection UI: no upload, no camera/gallery copy except cropped
 * images explicitly saved here. All annotations remain marked UNVERIFIED.
 */
@Composable
internal fun GlyphSampleCollectionCard(
    state: GlyphUiState,
    onSave: (label: String, drawingId: String, source: String, permission: Boolean) -> Unit,
    onExport: (Uri) -> Unit,
    onClear: () -> Unit,
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> if (uri != null) onExport(uri) }
    val enabled = !state.busy && !state.sampleBusy

    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Build your glyph test dataset", style = MaterialTheme.typography.titleMedium)
            Text(
                "Save labelled crops for later review. These are USER labels, not verified answers. " +
                    "Nothing is uploaded automatically.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Samples stored on this device: " + state.sampleCount + " / 200",
                style = MaterialTheme.typography.labelMedium,
            )

            if (state.bitmap != null) {
                Button(
                    onClick = { showSaveDialog = true },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save this cropped glyph for testing") }
            }
            if (state.sampleCount > 0) {
                OutlinedButton(
                    onClick = { exportLauncher.launch("MayaGlyph_ResearchSamples.zip") },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Export collected samples as ZIP") }
                TextButton(
                    onClick = { showClearDialog = true },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Delete local collected samples") }
            }
            if (state.sampleBusy) Text(
                "Saving or exporting samples…",
                style = MaterialTheme.typography.bodySmall,
            )
            state.sampleMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Stored privately on your device and excluded from automatic Android backup. " +
                    "Export the ZIP before clearing app data or uninstalling.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showSaveDialog) {
        var selected by remember { mutableStateOf(GlyphSampleStore.options[0]) }
        var expanded by remember { mutableStateOf(false) }
        var drawingId by remember { mutableStateOf("") }
        var source by remember { mutableStateOf("") }
        var rightsConfirmed by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (enabled) showSaveDialog = false },
            title = { Text("Annotate cropped glyph") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Select the expected sign only if you know it from a trustworthy source. " +
                            "Choose Not sure if you cannot verify it.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("Expected sign", style = MaterialTheme.typography.labelMedium)
                    androidx.compose.foundation.layout.Box {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(selected.description) }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            GlyphSampleStore.options.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.description) },
                                    onClick = {
                                        selected = option
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = drawingId,
                        onValueChange = { if (it.length <= 80) drawingId = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Original drawing ID *") },
                        supportingText = {
                            Text("Use the SAME ID for multiple photos of the same underlying drawing.")
                        },
                    )
                    OutlinedTextField(
                        value = source,
                        onValueChange = { if (it.length <= 300) source = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 1,
                        maxLines = 3,
                        label = { Text("Source / reference (optional)") },
                        supportingText = {
                            Text("E.g. TWKM sign URL, book page, or my own original sketch.")
                        },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = rightsConfirmed,
                            onCheckedChange = { rightsConfirmed = it },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "I created this drawing or have permission to export and share it for FYP research.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        "This saves only the selected crop. All records remain UNVERIFIED until " +
                            "separately reviewed and are not added to model training automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSaveDialog = false
                        onSave(selected.code, drawingId.trim(), source.trim(), rightsConfirmed)
                    },
                    enabled = enabled && drawingId.trim().length in 2..80,
                ) { Text("Save crop") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSaveDialog = false },
                    enabled = enabled,
                ) { Text("Cancel") }
            },
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Delete local glyph samples?") },
            text = { Text(
                "This permanently deletes " + state.sampleCount +
                    " research samples stored by this app. Export a ZIP first if you want to keep them."
            ) },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    onClear()
                }) { Text("Delete samples") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            },
        )
    }
}
