package com.chuahws.mayalanguageapp.data.repository

import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.domain.repository.DictionaryRepository

/**
 * Searches the built-in and Firestore dictionaries together.
 *
 * Built-in entries remain first so essential verified phrases keep priority,
 * while all distinct matching senses/forms can still be shown to the user.
 * Write operations are delegated to the final repository, which is Firestore
 * in the persistent application configuration.
 */
class CompositeDictionaryRepository(
    private val repositories: List<DictionaryRepository>,
) : DictionaryRepository {

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
    ): List<DictionaryEntry> {
        val matches = mutableListOf<DictionaryEntry>()
        for (repository in repositories) {
            matches += repository.findAllByMaya(normalizedText)
        }
        return matches.distinctBy(::entryIdentity)
    }

    override suspend fun findAllByEnglish(
        normalizedText: String,
    ): List<DictionaryEntry> {
        val matches = mutableListOf<DictionaryEntry>()
        for (repository in repositories) {
            matches += repository.findAllByEnglish(normalizedText)
        }
        return matches.distinctBy(::entryIdentity)
    }

    override suspend fun browseEntries(
        limit: Int,
    ): List<DictionaryEntry> {
        val entries = mutableListOf<DictionaryEntry>()
        for (repository in repositories) {
            entries += repository.browseEntries(limit)
        }
        return entries
            .distinctBy(::entryIdentity)
            .sortedBy { it.mayaNormalized }
            .take(limit)
    }

    override suspend fun searchEntries(
        query: String,
        limit: Int,
    ): List<DictionaryEntry> {
        val entries = mutableListOf<DictionaryEntry>()
        for (repository in repositories) {
            entries += repository.searchEntries(query, limit)
        }
        return entries
            .distinctBy(::entryIdentity)
            .sortedWith(
                compareByDescending<DictionaryEntry> { it.verified }
                    .thenBy { it.mayaText }
            )
            .take(limit)
    }

    override suspend fun upsertEntry(entry: DictionaryEntry) {
        repositories.lastOrNull()?.upsertEntry(entry)
            ?: throw IllegalStateException(
                "No dictionary repository is configured."
            )
    }

    override suspend fun deleteEntry(entryId: String) {
        repositories.lastOrNull()?.deleteEntry(entryId)
            ?: throw IllegalStateException(
                "No dictionary repository is configured."
            )
    }

    private fun entryIdentity(
        entry: DictionaryEntry,
    ): String =
        listOf(
            entry.entryId,
            entry.mayaNormalized,
            entry.englishNormalized,
            entry.partOfSpeech.orEmpty(),
        ).joinToString("|")
}
