package com.chuahws.mayalanguageapp.domain.repository

import com.chuahws.mayalanguageapp.domain.model.FeedbackItem
import com.chuahws.mayalanguageapp.domain.model.SavedVocabularyItem
import com.chuahws.mayalanguageapp.domain.model.TranslationRecord
import com.chuahws.mayalanguageapp.domain.model.UsageAnalytics

interface UserDataRepository {
    suspend fun recordTranslation(record: TranslationRecord)
    suspend fun getHistory(userId: String, limit: Int = 100): List<TranslationRecord>

    suspend fun saveVocabulary(item: SavedVocabularyItem)
    suspend fun removeSavedVocabulary(savedId: String)
    suspend fun getSavedVocabulary(userId: String): List<SavedVocabularyItem>

    suspend fun submitFeedback(item: FeedbackItem)
    suspend fun getFeedback(userId: String): List<FeedbackItem>

    suspend fun getAnalytics(userId: String): UsageAnalytics

    suspend fun getAllFeedback(): List<FeedbackItem>
    suspend fun updateFeedbackStatus(
        feedbackId: String,
        status: String,
        reviewedAtMillis: Long,
    )
}
