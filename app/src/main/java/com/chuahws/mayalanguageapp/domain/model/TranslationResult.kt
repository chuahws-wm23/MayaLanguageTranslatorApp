package com.chuahws.mayalanguageapp.domain.model

sealed interface TranslationResult {
    data class Success(
        val input: String,
        val output: String,
        val entry: DictionaryEntry,
    ) : TranslationResult

    data class NoMatch(val input: String) : TranslationResult
    data class InvalidInput(val message: String) : TranslationResult
}
