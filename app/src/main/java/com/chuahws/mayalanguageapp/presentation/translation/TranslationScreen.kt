package com.chuahws.mayalanguageapp.presentation.translation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chuahws.mayalanguageapp.domain.model.InputType
import com.chuahws.mayalanguageapp.domain.model.TranslationDirection
import com.chuahws.mayalanguageapp.presentation.components.AppGradientBackground
import com.chuahws.mayalanguageapp.presentation.components.TranslationResultCard

@Composable
fun TranslationScreen(
    viewModel: TranslationViewModel,
    onSaveEntry: (com.chuahws.mayalanguageapp.domain.model.DictionaryEntry) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        viewModel.setDirection(
            TranslationDirection.ENGLISH_TO_MAYA
        )
    }

    val source =
        if (state.direction ==
            TranslationDirection.MAYA_TO_ENGLISH
        ) "Maya" else "English"

    val target =
        if (state.direction ==
            TranslationDirection.MAYA_TO_ENGLISH
        ) "English" else "Maya"

    val outputLanguageTag =
        if (state.direction ==
            TranslationDirection.MAYA_TO_ENGLISH
        ) "en-US" else "yua"

    AppGradientBackground {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(
                    PaddingValues(
                        start = 18.dp,
                        top = 18.dp,
                        end = 18.dp,
                        bottom = 112.dp,
                    )
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 2.dp,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LanguagePill(
                        label = source,
                        selected = true,
                        modifier = Modifier.weight(1f),
                    )

                    FilledTonalIconButton(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.swapDirection()
                        },
                        enabled = !state.loading,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SwapHoriz,
                            contentDescription = "Swap languages",
                        )
                    }

                    LanguagePill(
                        label = target,
                        selected = false,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Type something",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${state.input.length}/500",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    OutlinedTextField(
                        value = state.input,
                        onValueChange = {
                            if (it.length <= 500) {
                                viewModel.updateInput(it)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.loading,
                        placeholder = {
                            Text(
                                if (source == "English") {
                                    "Type or paste English text…"
                                } else {
                                    "Type or paste Maya text…"
                                }
                            )
                        },
                        trailingIcon =
                            if (state.input.isNotBlank()) {
                                {
                                    IconButton(
                                        onClick = {
                                            viewModel.updateInput("")
                                        },
                                    ) {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = "Clear text",
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                        minLines = 6,
                        maxLines = 10,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                viewModel.translate(InputType.TEXT)
                            },
                        ),
                    )
                }
            }

            state.message?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.translate(InputType.TEXT)
                },
                enabled =
                    !state.loading &&
                        state.input.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 15.dp),
            ) {
                if (state.loading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Language,
                        contentDescription = null,
                    )
                    Text(
                        text = "Translate",
                        modifier = Modifier.padding(start = 8.dp),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            TranslationResultCard(
                output = state.output,
                entry = state.matchedEntry,
                alternatives = state.alternativeEntries,
                outputLanguageTag = outputLanguageTag,
                onSave =
                    state.matchedEntry?.let { entry ->
                        { onSaveEntry(entry) }
                    },
                onSaveAlternative = onSaveEntry,
            )
        }
    }
}

@Composable
private fun LanguagePill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = if (selected) {
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.86f),
        )
    } else {
        listOf(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant,
        )
    }

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(colors)
                )
                .padding(
                    horizontal = 14.dp,
                    vertical = 11.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
