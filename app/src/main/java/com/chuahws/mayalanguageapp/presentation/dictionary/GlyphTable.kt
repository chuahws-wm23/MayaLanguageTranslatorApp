package com.chuahws.mayalanguageapp.presentation.dictionary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun GlyphTable(
    primaryHeading: String,
    secondaryHeading: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            GlyphTableRow(
                primary = primaryHeading,
                secondary = secondaryHeading,
                header = true,
            ) { Text("Glyph", style = MaterialTheme.typography.labelMedium) }
            content()
        }
    }
}

@Composable
internal fun GlyphTableRow(
    primary: String,
    secondary: String,
    shaded: Boolean = false,
    onClick: (() -> Unit)? = null,
    header: Boolean = false,
    drawing: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val background = when {
        header -> colors.primaryContainer
        shaded -> colors.surfaceVariant.copy(alpha = 0.4f)
        else -> colors.surface
    }
    val rowModifier = Modifier.fillMaxWidth().background(background)
    Row(
        modifier = (if (onClick == null) rowModifier else rowModifier.clickable(onClick = onClick))
            .heightIn(min = if (header) 44.dp else 64.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) { drawing() }
        Text(
            primary,
            modifier = Modifier.weight(1.35f),
            style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (header) colors.onPrimaryContainer else colors.onSurface,
        )
        Text(
            secondary,
            modifier = Modifier.weight(1f),
            style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
            color = if (header) colors.onPrimaryContainer else colors.onSurfaceVariant,
        )
    }
    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f))
}
