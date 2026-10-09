package com.chuahws.mayalanguageapp.domain.repository

import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry

interface DictionaryRepository {
    suspend fun findByMaya(normalizedText: String): DictionaryEntry?
    suspend fun findByEnglish(normalizedText: String): DictionaryEntry?
}
