package com.chuahws.mayalanguageapp.data.repository

import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.domain.repository.DictionaryRepository

class InMemoryDictionaryRepository(
    entries: List<DictionaryEntry>
) : DictionaryRepository {

    private val entries = entries.filter { it.active }

    override suspend fun findByMaya(
        normalizedText: String,
    ): DictionaryEntry? =
        findAllByMaya(normalizedText).firstOrNull()

    override suspend fun findByEnglish(
        normalizedText: String,
    ): DictionaryEntry? =
        findAllByEnglish(normalizedText).firstOrNull()

    override suspend fun findAllByMaya(
        normalizedText: String,
    ): List<DictionaryEntry> =
        entries
            .filter { it.mayaNormalized == normalizedText }
            .sortedWith(
                compareByDescending<DictionaryEntry> { it.verified }
                    .thenBy { it.englishText }
            )

    override suspend fun findAllByEnglish(
        normalizedText: String,
    ): List<DictionaryEntry> =
        entries
            .filter {
                it.englishNormalized == normalizedText ||
                    it.acceptedEnglishInputs.any { alias ->
                        com.chuahws.mayalanguageapp.domain.service.TextNormalizer
                            .normalize(alias) == normalizedText
                    }
            }
            .sortedWith(
                compareByDescending<DictionaryEntry> { it.verified }
                    .thenBy { it.mayaText }
            )
}
