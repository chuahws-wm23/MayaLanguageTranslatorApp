package com.chuahws.mayalanguageapp.presentation.translation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.integration.glyph.GlyphLearningNotes
import com.chuahws.mayalanguageapp.integration.glyph.GlyphMoreLearningNotes

/**
 * Interpretation aids, not evidence that the predicted photo was identified.
 * The explicit source URL lets students check the original scholarship.
 */
@Composable
internal fun GlyphLearningDetails(
    signNumber: Int,
    initialExpanded: Boolean = false,
) {
    val note = remember(signNumber) { GlyphLearningNotes.forSign(signNumber) ?: GlyphMoreLearningNotes.forSign(signNumber) } ?: return
    var expanded by remember(signNumber) { mutableStateOf(initialExpanded) }
    val uriHandler = LocalUriHandler.current

    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Learn about this glyph",
                style=MaterialTheme.typography.titleMedium,
                fontWeight=FontWeight.SemiBold)
            Text("English gloss: ${note.english}",
                style=MaterialTheme.typography.bodyMedium)
            Text("Type: ${note.category}",style=MaterialTheme.typography.labelMedium)
            Text(note.explanation,style=MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { expanded = !expanded }) {
                Text(if(expanded) "Show less" else "Read history, context and an interesting fact")
            }
            if(expanded) {
                HorizontalDivider()
                Text("How it was used", style=MaterialTheme.typography.titleSmall)
                Text(note.historicalContext, style=MaterialTheme.typography.bodyMedium)
                Text("Worth knowing", style=MaterialTheme.typography.titleSmall)
                Text(note.remember, style=MaterialTheme.typography.bodyMedium)
                Text(
                    "The match on this screen is experimental. Verify the " +
                        "sign itself before applying this meaning to an inscription.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { uriHandler.openUri(note.sourceUrl) }) {
                    Text("Read the original source")
                }
            }
        }
    }
}
