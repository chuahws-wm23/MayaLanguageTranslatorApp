package com.chuahws.mayalanguageapp.presentation.translation

import androidx.compose.runtime.Composable
import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry

/** Retained route compatibility: photo-based Maya glyph exploration only. */
enum class TranslateMode(val label: String) {
    IMAGE("Scan"),
}

@Composable
fun TranslationHubScreen(
    viewModel: TranslationViewModel,
    initialMode: TranslateMode,
    onSaveEntry: (DictionaryEntry) -> Unit,
) {
    // Existing callers can keep their entry point. Text and Voice routes,
    // switcher tabs and associated user-facing modes are retired.
    ImageTranslationScreen(
        viewModel = viewModel,
        onSaveEntry = onSaveEntry,
    )
}
