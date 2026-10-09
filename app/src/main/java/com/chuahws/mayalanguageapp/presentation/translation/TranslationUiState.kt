package com.chuahws.mayalanguageapp.presentation.translation

import com.chuahws.mayalanguageapp.domain.model.TranslationDirection

data class TranslationUiState(
    val input: String = "",
    val direction: TranslationDirection = TranslationDirection.MAYA_TO_ENGLISH,
    val output: String = "",
    val message: String? = null,
    val loading: Boolean = false,
)
