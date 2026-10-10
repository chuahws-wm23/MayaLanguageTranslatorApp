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
    val inputType: InputType = InputType.TEXT,
    val message: String? = null,
    val loading: Boolean = false,
)
