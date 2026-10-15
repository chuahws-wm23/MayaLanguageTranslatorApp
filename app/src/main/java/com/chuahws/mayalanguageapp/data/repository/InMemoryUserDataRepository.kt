package com.chuahws.mayalanguageapp.data.repository

import com.chuahws.mayalanguageapp.domain.model.FeedbackItem
import com.chuahws.mayalanguageapp.domain.model.InputType
import com.chuahws.mayalanguageapp.domain.model.SavedVocabularyItem
import com.chuahws.mayalanguageapp.domain.model.TranslationRecord
import com.chuahws.mayalanguageapp.domain.model.UsageAnalytics
import com.chuahws.mayalanguageapp.domain.repository.UserDataRepository

class InMemoryUserDataRepository : UserDataRepository {
    private val history = mutableListOf<TranslationRecord>()
    private val saved = linkedMapOf<String, SavedVocabularyItem>()
    private val feedback = mutableListOf<FeedbackItem>()

    override suspend fun recordTranslation(record: TranslationRecord) {
        history.removeAll { it.translationId == record.translationId }
        history += record
    }

    override suspend fun getHistory(
        userId: String,
        limit: Int,
    ): List<TranslationRecord> =
        history
            .filter { it.userId == userId }
            .sortedByDescending { it.createdAtMillis }
            .take(limit)

    override suspend fun saveVocabulary(item: SavedVocabularyItem) {
        saved[item.savedId] = item
    }

    override suspend fun removeSavedVocabulary(savedId: String) {
        saved.remove(savedId)
    }

    override suspend fun getSavedVocabulary(
        userId: String
    ): List<SavedVocabularyItem> =
        saved.values
            .filter { it.userId == userId }
            .sortedByDescending { it.savedAtMillis }

    override suspend fun submitFeedback(item: FeedbackItem) {
        feedback.removeAll { it.feedbackId == item.feedbackId }
        feedback += item
    }

    override suspend fun getFeedback(userId: String): List<FeedbackItem> =
        feedback
            .filter { it.userId == userId }
            .sortedByDescending { it.submittedAtMillis }

    override suspend fun getAnalytics(userId: String): UsageAnalytics {
        val userHistory = history.filter { it.userId == userId }
        val savedCount = saved.values.count { it.userId == userId }

        return UsageAnalytics(
            userId = userId,
            totalTranslations = userHistory.size.toLong(),
            textTranslations = userHistory.count {
                it.inputType == InputType.TEXT.name
            }.toLong(),
            imageTranslations = userHistory.count {
                it.inputType == InputType.IMAGE.name
            }.toLong(),
            voiceTranslations = userHistory.count {
                it.inputType == InputType.VOICE.name
            }.toLong(),
            savedWords = savedCount.toLong(),
            lastUpdatedMillis = userHistory.maxOfOrNull {
                it.createdAtMillis
            } ?: 0L,
        )
    }

    override suspend fun getAllFeedback(): List<FeedbackItem> =
        feedback.sortedByDescending { it.submittedAtMillis }

    override suspend fun updateFeedbackStatus(
        feedbackId: String,
        status: String,
        reviewedAtMillis: Long,
    ) {
        val index = feedback.indexOfFirst {
            it.feedbackId == feedbackId
        }

        if (index >= 0) {
            feedback[index] = feedback[index].copy(
                status = status,
                reviewedAtMillis = reviewedAtMillis,
            )
        }
    }
}
