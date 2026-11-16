package com.chuahws.mayalanguageapp.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.domain.model.GlyphScanCandidate
import com.chuahws.mayalanguageapp.integration.glyph.GlyphLearningNotes
import com.chuahws.mayalanguageapp.integration.glyph.GlyphMoreLearningNotes
import com.chuahws.mayalanguageapp.integration.glyph.GlyphScanPreviewCodec
import java.util.Locale

/** Render the small cropped photo preview that was explicitly stored with this scan. */
@Composable
fun GlyphScanThumbnail(
    imageBase64: String,
    side: Dp = 74.dp,
    modifier: Modifier = Modifier.size(side),
) {
    val image = remember(imageBase64) {
        GlyphScanPreviewCodec.decode(imageBase64)
    }
    if (image != null) {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = "Saved preview of the scanned Maya glyph",
            modifier = modifier.background(Color.White),
            contentScale = ContentScale.Fit,
        )
    } else {
        androidx.compose.material3.Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.CameraAlt,
                    contentDescription = "No saved glyph photo preview",
                )
            }
        }
    }
}

/**
 * Shared detail view for History and Saved; photo, proposed readings, glosses,
 * cultural notes and source warnings stay together across both collections.
 */
@Composable
fun GlyphScanDetailsDialog(
    title: String,
    imageBase64: String,
    candidates: List<GlyphScanCandidate>,
    secondaryText: String,
    onDismiss: () -> Unit,
    scanKind: String = "WORD_SIGN",
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val uri = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GlyphScanThumbnail(imageBase64, side = 116.dp)
                    Column(Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            "Maya glyph photo",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            secondaryText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text("Unverified visual reference search",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
                if (candidates.isEmpty()) {
                    Text(
                        "No reliable English reading was confirmed for this image. " +
                            "You can return to Scan to try another crop.",
                    )
                } else {
                    Text("Possible reference matches",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    candidates.take(10).forEachIndexed { index, candidate ->
                        var expanded by remember(index,candidate.twkmSign) {
                            mutableStateOf(index == 0)
                        }
                        Card(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    (if (candidate.regionIndex > 0)
                                        "Region ${candidate.regionIndex} · " else "") +
                                        "${index+1}. ${candidate.reading}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(candidate.englishGloss)
                                Text(
                                    if (scanKind == "RESEARCH" || candidate.visualOverlap <= 0.0)
                                        "TWKM #${candidate.twkmSign} · experimental research model; " +
                                            "not verified from this photograph"
                                    else "TWKM #${candidate.twkmSign} · ${String.format(Locale.US,"%.0f",
                                        candidate.visualOverlap*100)}% shape resemblance " +
                                        "(NOT translation confidence)",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                val note = remember(candidate.twkmSign) {
                                    GlyphLearningNotes.forSign(candidate.twkmSign)
                                        ?: GlyphMoreLearningNotes.forSign(candidate.twkmSign)
                                }
                                if (note != null) {
                                    Text(note.explanation,
                                        style = MaterialTheme.typography.bodySmall)
                                } else {
                                    Text(
                                        "This source-proposed gloss belongs to a catalogue sign; " +
                                            "its meaning depends on the other signs in the inscription.",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                TextButton(onClick = { expanded = !expanded }) {
                                    Text(if (expanded) "Hide learning notes" else
                                        "Learn about this sign")
                                }
                                if (expanded) {
                                    if (note != null) {
                                        Text("Historical context",
                                            fontWeight = FontWeight.SemiBold)
                                        Text(note.historicalContext,
                                            style = MaterialTheme.typography.bodySmall)
                                        Text("Worth knowing",
                                            fontWeight = FontWeight.SemiBold)
                                        Text(note.remember,
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                    TextButton(onClick = {
                                        uri.openUri(note?.sourceUrl ?:
                                            "https://classicmayan.org/portal/signcatalog")
                                    }) { Text("Read original source") }
                                }
                            }
                        }
                    }
                }
                Text(
                    "The saved JPEG is a small cropped preview, not the original full-resolution photo. " +
                        "TWKM / University of Bonn sign drawings are research references; " +
                        "a visual resemblance does not establish an archaeological identification.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete") }
                }
                if (actionLabel != null) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        },
    )
}
