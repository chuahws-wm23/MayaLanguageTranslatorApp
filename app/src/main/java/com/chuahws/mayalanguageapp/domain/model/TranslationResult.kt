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
     * Unknown terms are kept explicitly in the output rather than causing the
     * whole request to fail.
     */
    data class Composed(
        val input: String,
        val output: String,
        val unknownTerms: List<String> = emptyList(),
        val matchedEntryCount: Int,
    ) : TranslationResult

    data class NoMatch(val input: String) : TranslationResult
    data class InvalidInput(val message: String) : TranslationResult
}
