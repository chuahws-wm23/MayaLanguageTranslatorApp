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
}
