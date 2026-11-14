package com.chuahws.mayalanguageapp.presentation.translation

import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.domain.model.InputType
import com.chuahws.mayalanguageapp.domain.model.TranslationDirection

data class TranslationUiState(
    val input: String = "",
    val direction: TranslationDirection =
        TranslationDirection.ENGLISH_TO_MAYA,
    val output: String = "",
    val matchedEntry: DictionaryEntry? = null,
    val alternativeEntries: List<DictionaryEntry> = emptyList(),
    val inputType: InputType = InputType.TEXT,
    val message: String? = null,
    val advice: String? = null,
    val approximate: Boolean = false,
    val needsReview: Boolean = false,
    val unknownTerms: List<String> = emptyList(),
    val ambiguousTerms: List<String> = emptyList(),
    val unreliableSegments: List<String> = emptyList(),
    val suggestions: List<DictionaryEntry> = emptyList(),
    val loading: Boolean = false,
)
