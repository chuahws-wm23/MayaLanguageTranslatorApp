package com.chuahws.mayalanguageapp.domain.model

sealed interface TranslationResult {
    data class Success(
        val input: String,
        val output: String,
        val entry: DictionaryEntry,
    ) : TranslationResult

    /**
     * Exact input with more than one distinct dictionary translation.
     *
     * Natural-language ambiguity is presented instead of silently selecting
     * whichever Firestore document happens to be returned first.
     */
    data class Alternatives(
        val input: String,
        val entries: List<DictionaryEntry>,
    ) : TranslationResult

    /**
     * Translation assembled from more than one dictionary match.
     *
     * Unsupported or ambiguous terms are tracked separately so the UI can
     * explain a partial result without showing developer-style markers.
     */
    data class Composed(
        val input: String,
        val output: String,
        val unknownTerms: List<String> = emptyList(),
        val ambiguousTerms: List<String> = emptyList(),
        val unreliableSegments: List<String> = emptyList(),
        val matchedEntryCount: Int,
    ) : TranslationResult

    data class NoMatch(val input: String) : TranslationResult
    data class InvalidInput(val message: String) : TranslationResult
}
