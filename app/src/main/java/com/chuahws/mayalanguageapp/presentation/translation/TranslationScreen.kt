package com.chuahws.mayalanguageapp.presentation.translation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.domain.model.TranslationDirection

@Composable
fun TranslationScreen(
    viewModel: TranslationViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()

    val sourceLabel =
        if (state.direction == TranslationDirection.MAYA_TO_ENGLISH)
            "Yucatec Maya"
        else
            "English"

    val targetLabel =
        if (state.direction == TranslationDirection.MAYA_TO_ENGLISH)
            "English"
        else
            "Yucatec Maya"

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Text Translation",
            style = MaterialTheme.typography.headlineMedium,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$sourceLabel → $targetLabel")
            Button(onClick = viewModel::swapDirection) {
                Text("Swap")
            }
        }

        OutlinedTextField(
            value = state.input,
            onValueChange = viewModel::updateInput,
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("Enter $sourceLabel text")
            },
            minLines = 4,
        )

        Button(
            onClick = viewModel::translate,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading,
        ) {
            Text("Translate")
        }

        if (state.loading) {
            CircularProgressIndicator()
        }

        state.message?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (state.output.isNotBlank()) {
            Text(
                text = "$targetLabel result",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = state.output,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        Text(
            text = "The starter dictionary is empty until the validated Yucatec Maya–English dataset is imported.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
