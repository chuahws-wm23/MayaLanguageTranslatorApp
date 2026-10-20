package com.chuahws.mayalanguageapp.presentation.translation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry

enum class TranslateMode(
    val label: String,
) {
    TEXT("Text"),
    IMAGE("Scan"),
    VOICE("Voice"),
}

@Composable
fun TranslationHubScreen(
    viewModel: TranslationViewModel,
    initialMode: TranslateMode,
    onSaveEntry: (DictionaryEntry) -> Unit,
) {
    var mode by rememberSaveable {
        mutableStateOf(initialMode)
    }

    LaunchedEffect(initialMode) {
        mode = initialMode
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        TabRow(selectedTabIndex = mode.ordinal) {
            TranslateMode.entries.forEach { item ->
                Tab(
                    selected = item == mode,
                    onClick = {
                        mode = item
                        viewModel.clear()
                    },
                    text = { Text(item.label) },
                    icon = {
                        Icon(
                            imageVector = when (item) {
                                TranslateMode.TEXT -> Icons.Rounded.Edit
                                TranslateMode.IMAGE -> Icons.Rounded.CameraAlt
                                TranslateMode.VOICE -> Icons.Rounded.Mic
                            },
                            contentDescription = null,
                        )
                    },
                )
            }
        }

        when (mode) {
            TranslateMode.TEXT -> TranslationScreen(
                viewModel = viewModel,
                onSaveEntry = onSaveEntry,
            )

            TranslateMode.IMAGE -> ImageTranslationScreen(
                viewModel = viewModel,
                onSaveEntry = onSaveEntry,
            )

            TranslateMode.VOICE -> VoiceTranslationScreen(
                viewModel = viewModel,
                onSaveEntry = onSaveEntry,
            )
        }
    }
}
