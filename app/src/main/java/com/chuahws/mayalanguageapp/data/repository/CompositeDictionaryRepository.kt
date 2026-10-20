package com.chuahws.mayalanguageapp.data.repository

import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.domain.repository.DictionaryRepository

/**
 * Searches the built-in and Firestore dictionaries together.
 *
 * Built-in entries remain first so essential verified phrases keep priority,
 * while all distinct matching senses/forms can still be shown to the user.
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
