package com.chuahws.mayalanguageapp.domain.repository

import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry

interface DictionaryRepository {
    suspend fun findByMaya(normalizedText: String): DictionaryEntry?
    suspend fun findByEnglish(normalizedText: String): DictionaryEntry?

    suspend fun findAllByMaya(
        normalizedText: String,
    ): List<DictionaryEntry> =
        findByMaya(normalizedText)?.let(::listOf)
            ?: emptyList()

    suspend fun findAllByEnglish(
        normalizedText: String,
    ): List<DictionaryEntry> =
        findByEnglish(normalizedText)?.let(::listOf)
            ?: emptyList()

    suspend fun browseEntries(
        limit: Int = 100,
    ): List<DictionaryEntry> = emptyList()

    suspend fun searchEntries(
        query: String,
        limit: Int = 50,
    ): List<DictionaryEntry> =
        browseEntries(limit).filter { entry ->
            entry.mayaText.contains(query, ignoreCase = true) ||
                entry.englishText.contains(query, ignoreCase = true)
        }

    suspend fun upsertEntry(entry: DictionaryEntry) {
        throw UnsupportedOperationException(
            "Dictionary editing is not supported by this repository."
        )
    }

    suspend fun deleteEntry(entryId: String) {
        throw UnsupportedOperationException(
            "Dictionary editing is not supported by this repository."
        )
    }
}
