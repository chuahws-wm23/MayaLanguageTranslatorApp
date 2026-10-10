package com.chuahws.mayalanguageapp.presentation.components

import android.speech.tts.TextToSpeech
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

@Composable
fun SpeakButton(
    text: String,
) {
    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.getDefault()
            }
        }
        tts = engine

        onDispose {
            engine?.stop()
            engine?.shutdown()
            tts = null
        }
    }

    IconButton(
        onClick = {
            if (text.isNotBlank()) {
                tts?.speak(
                    text,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "maya-translation",
                )
            }
        },
        enabled = text.isNotBlank(),
    ) {
        Icon(
            imageVector = Icons.Rounded.VolumeUp,
            contentDescription = "Listen",
        )
    }
}
