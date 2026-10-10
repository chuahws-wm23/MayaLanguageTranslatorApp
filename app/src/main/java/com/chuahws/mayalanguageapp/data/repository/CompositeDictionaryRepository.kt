package com.chuahws.mayalanguageapp.data.repository

import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.domain.repository.DictionaryRepository

/**
 * Searches each dictionary in order and returns the first match.
 *
 * The built-in core phrases are kept first so essential greetings continue
 * to work even before the full Firestore dictionary has been imported.
 */
class CompositeDictionaryRepository(
    private val repositories: List<DictionaryRepository>,
) : DictionaryRepository {

    override suspend fun findByMaya(
        normalizedText: String,
    ): DictionaryEntry? {
        repositories.forEach { repository ->
            repository.findByMaya(normalizedText)?.let { return it }
        }
        return null
    }

    override suspend fun findByEnglish(
        normalizedText: String,
    ): DictionaryEntry? {
        repositories.forEach { repository ->
            repository.findByEnglish(normalizedText)?.let { return it }
        }
        return null
    }
}
