package com.chuahws.mayalanguageapp.presentation.translation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.domain.model.InputType
import com.chuahws.mayalanguageapp.domain.model.TranslationDirection
import com.chuahws.mayalanguageapp.presentation.components.TranslationResultCard

@Composable
fun TranslationScreen(
    viewModel: TranslationViewModel,
    onSaveEntry: (com.chuahws.mayalanguageapp.domain.model.DictionaryEntry) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    val source = if (
        state.direction == TranslationDirection.MAYA_TO_ENGLISH
    ) "Yucatec Maya" else "English"

    val target = if (
        state.direction == TranslationDirection.MAYA_TO_ENGLISH
    ) "English" else "Yucatec Maya"

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Type to translate",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = source,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = target,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FilledTonalIconButton(
                onClick = viewModel::swapDirection,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Rounded.SwapHoriz,
                    contentDescription = "Swap languages",
                )
            }
        }

        OutlinedTextField(
            value = state.input,
            onValueChange = viewModel::updateInput,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Enter $source text") },
            placeholder = { Text("Start typing") },
            minLines = 5,
            maxLines = 9,
        )

        state.message?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Button(
            onClick = { viewModel.translate(InputType.TEXT) },
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.loading) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            } else {
                Text("Translate")
            }
        }

        TranslationResultCard(
            output = state.output,
            entry = state.matchedEntry,
            onSave = state.matchedEntry?.let { entry ->
                { onSaveEntry(entry) }
            },
        )
    }
}
