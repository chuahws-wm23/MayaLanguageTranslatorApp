package com.chuahws.mayalanguageapp.presentation.components

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign

@Composable
fun SpeakButton(
    text: String,
    languageTag: String,
    showUnavailableLabel: Boolean = false,
) {
    val context = LocalContext.current
    var tts by remember {
        mutableStateOf<TextToSpeech?>(null)
    }
    var ready by remember {
        mutableStateOf(false)
    }
    var supported by remember {
        mutableStateOf(false)
    }

    DisposableEffect(
        context,
        languageTag,
    ) {
        var engine: TextToSpeech? = null

        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale =
                    java.util.Locale.forLanguageTag(
                        languageTag
                    )
                val availability =
                    engine?.isLanguageAvailable(
                        locale
                    )
                        ?: TextToSpeech
                            .LANG_NOT_SUPPORTED

                supported =
                    availability >=
                        TextToSpeech.LANG_AVAILABLE

                if (supported) {
                    engine?.language = locale
                }
                ready = true
            } else {
                ready = true
                supported = false
            }
        }

        tts = engine

        onDispose {
            engine?.stop()
            engine?.shutdown()
            tts = null
        }
    }

    val enabled =
        text.isNotBlank() &&
            ready &&
            supported

    Column(
        horizontalAlignment =
            Alignment.CenterHorizontally,
    ) {
        IconButton(
            onClick = {
                if (enabled) {
                    tts?.speak(
                        text,
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "maya-translation",
                    )
                }
            },
            enabled = enabled,
        ) {
            Icon(
                imageVector =
                    if (enabled) {
                        Icons.Rounded.VolumeUp
                    } else {
                        Icons.Rounded.VolumeOff
                    },
                contentDescription =
                    if (enabled) {
                        "Listen"
                    } else {
                        "Audio pronunciation is unavailable on this device"
                    },
            )
        }

        if (
            showUnavailableLabel &&
            text.isNotBlank() &&
            ready &&
            !supported
        ) {
            Text(
                text = "Audio unavailable",
                style =
                    MaterialTheme.typography
                        .labelSmall,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
