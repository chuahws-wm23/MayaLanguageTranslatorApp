package com.chuahws.mayalanguageapp.data.repository

import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.domain.repository.DictionaryRepository

class InMemoryDictionaryRepository(
    entries: List<DictionaryEntry>
) : DictionaryRepository {

    private val entries = entries.filter { it.active }

    override suspend fun findByMaya(normalizedText: String): DictionaryEntry? =
        entries.firstOrNull { it.mayaNormalized == normalizedText }

    override suspend fun findByEnglish(normalizedText: String): DictionaryEntry? =
        entries.firstOrNull { it.englishNormalized == normalizedText }
}
