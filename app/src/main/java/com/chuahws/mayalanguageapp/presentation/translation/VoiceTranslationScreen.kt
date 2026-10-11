package com.chuahws.mayalanguageapp.presentation.translation

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.domain.model.InputType
import com.chuahws.mayalanguageapp.domain.model.TranslationDirection
import com.chuahws.mayalanguageapp.presentation.components.TranslationResultCard

@Composable
fun VoiceTranslationScreen(
    viewModel: TranslationViewModel,
    onSaveEntry: (DictionaryEntry) -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val speechIntent = Intent(
        RecognizerIntent.ACTION_RECOGNIZE_SPEECH
    ).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
        )
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE,
            "en-US",
        )
        putExtra(
            RecognizerIntent.EXTRA_PROMPT,
            "Speak in English",
        )
    }

    val speechAvailable =
        speechIntent.resolveActivity(
            context.packageManager
        ) != null

    LaunchedEffect(Unit) {
        viewModel.setDirection(
            TranslationDirection.ENGLISH_TO_MAYA
        )
    }

    val speechLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts
                .StartActivityForResult()
        ) { result ->
            if (result.resultCode ==
                Activity.RESULT_OK
            ) {
                result.data
                    ?.getStringArrayListExtra(
                        RecognizerIntent.EXTRA_RESULTS
                    )
                    ?.firstOrNull()
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.let(
                        viewModel::updateInput
                    )
            }
        }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text =
                "Tap the microphone and speak in English.",
            color =
                MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
            onClick = {
                if (speechAvailable) {
                    speechLauncher.launch(speechIntent)
                }
            },
            enabled =
                speechAvailable &&
                    !state.loading,
            shape = CircleShape,
            color =
                MaterialTheme.colorScheme.primary,
            shadowElevation = 8.dp,
        ) {
            Box(
                modifier = Modifier.size(96.dp),
                contentAlignment =
                    Alignment.Center,
            ) {
                Icon(
                    imageVector =
                        Icons.Rounded.Mic,
                    contentDescription =
                        "Start English speech recognition",
                    tint =
                        MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(44.dp),
                )
            }
        }

        if (!speechAvailable) {
            Text(
                text =
                    "Speech recognition is unavailable on this device. You can type the English transcript below instead.",
                style =
                    MaterialTheme.typography.bodySmall,
                color =
                    MaterialTheme.colorScheme.error,
            )
        }

        Text(
            text = "Recognised speech",
            style =
                MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.input,
            onValueChange = viewModel::updateInput,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading,
            label = {
                Text("Transcript")
            },
            minLines = 3,
        )

        Button(
            onClick = {
                viewModel.translate(InputType.VOICE)
            },
            enabled =
                !state.loading &&
                    state.input.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Translate")
        }

        state.message?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        TranslationResultCard(
            output = state.output,
            entry = state.matchedEntry,
            outputLanguageTag = "yua",
            onSave =
                state.matchedEntry?.let { entry ->
                    {
                        onSaveEntry(entry)
                    }
                },
        )
    }
}
