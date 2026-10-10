package com.chuahws.mayalanguageapp.presentation.translation

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.domain.model.InputType
import com.chuahws.mayalanguageapp.domain.model.TranslationDirection
import com.chuahws.mayalanguageapp.presentation.components.TranslationResultCard
import java.util.Locale

@Composable
fun VoiceTranslationScreen(
    viewModel: TranslationViewModel,
    onSaveEntry: (DictionaryEntry) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.setDirection(
            TranslationDirection.ENGLISH_TO_MAYA
        )
    }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS
                )
                ?.firstOrNull()

            if (!spoken.isNullOrBlank()) {
                viewModel.updateInput(spoken)
            }
        }
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Speak in English",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = "Speak clearly, check the transcript, then translate it to Yucatec Maya.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FilledTonalButton(
            onClick = {
                val intent = Intent(
                    RecognizerIntent.ACTION_RECOGNIZE_SPEECH
                ).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                    )
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE,
                        Locale.getDefault(),
                    )
                    putExtra(
                        RecognizerIntent.EXTRA_PROMPT,
                        "Speak now",
                    )
                }

                speechLauncher.launch(intent)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.Mic, contentDescription = null)
            Text(
                text = "Start listening",
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        OutlinedTextField(
            value = state.input,
            onValueChange = viewModel::updateInput,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Transcript") },
            minLines = 4,
        )

        Button(
            onClick = { viewModel.translate(InputType.VOICE) },
            enabled = !state.loading && state.input.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Translate to Yucatec Maya")
        }

        state.message?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
            )
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
