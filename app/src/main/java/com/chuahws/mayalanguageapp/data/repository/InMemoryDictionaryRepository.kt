package com.chuahws.mayalanguageapp.data.repository

import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.domain.repository.DictionaryRepository
import com.chuahws.mayalanguageapp.domain.service.TextNormalizer

class InMemoryDictionaryRepository(
    entries: List<DictionaryEntry>
) : DictionaryRepository {

    private val entries =
        entries.filter { it.active }.toMutableList()

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
            .filter {
                it.active &&
                    it.mayaNormalized == normalizedText
            }
            .sortedWith(
                compareByDescending<DictionaryEntry> { it.verified }
                    .thenBy { it.englishText }
            )

    override suspend fun findAllByEnglish(
        normalizedText: String,
    ): List<DictionaryEntry> =
        entries
            .filter {
                it.active &&
                    (
                        it.englishNormalized == normalizedText ||
                            it.acceptedEnglishInputs.any { alias ->
                                TextNormalizer.normalize(alias) ==
                                    normalizedText
                            }
                    )
            }
            .sortedWith(
                compareByDescending<DictionaryEntry> { it.verified }
                    .thenBy { it.mayaText }
            )

    override suspend fun browseEntries(
        limit: Int,
    ): List<DictionaryEntry> =
        entries
            .filter { it.active }
            .distinctBy { it.entryId }
            .sortedBy { it.mayaNormalized }
            .take(limit)

    override suspend fun searchEntries(
        query: String,
        limit: Int,
    ): List<DictionaryEntry> {
        val normalized = TextNormalizer.normalize(query)
        if (normalized.isBlank()) {
            return browseEntries(limit)
        }

        return entries
            .asSequence()
            .filter { it.active }
            .filter { entry ->
                entry.mayaNormalized.contains(normalized) ||
                    entry.englishNormalized.contains(normalized) ||
                    entry.acceptedEnglishInputs.any { alias ->
                        TextNormalizer.normalize(alias)
                            .contains(normalized)
                    }
            }
            .distinctBy { it.entryId }
            .sortedWith(
                compareByDescending<DictionaryEntry> { it.verified }
                    .thenBy { it.mayaText }
            )
            .take(limit)
            .toList()
    }

    override suspend fun upsertEntry(entry: DictionaryEntry) {
        entries.removeAll { it.entryId == entry.entryId }
        entries += entry
    }

    override suspend fun deleteEntry(entryId: String) {
        entries.removeAll { it.entryId == entryId }
    }
}
