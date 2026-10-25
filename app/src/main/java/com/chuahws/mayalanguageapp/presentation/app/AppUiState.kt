package com.chuahws.mayalanguageapp.presentation.app

import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.domain.model.FeedbackItem
import com.chuahws.mayalanguageapp.domain.model.SavedVocabularyItem
import com.chuahws.mayalanguageapp.domain.model.TranslationRecord
import com.chuahws.mayalanguageapp.domain.model.UsageAnalytics
import com.chuahws.mayalanguageapp.domain.model.UserProfile

data class AppUiState(
    val userId: String? = null,
    val profile: UserProfile? = null,
    val history: List<TranslationRecord> = emptyList(),
    val savedVocabulary: List<SavedVocabularyItem> = emptyList(),
    val feedback: List<FeedbackItem> = emptyList(),
    val analytics: UsageAnalytics = UsageAnalytics(),

    val dictionaryEntries: List<DictionaryEntry> = emptyList(),
    val dictionaryLoading: Boolean = false,

    val adminUsers: List<UserProfile> = emptyList(),
    val adminFeedback: List<FeedbackItem> = emptyList(),
    val adminAnalytics: List<UsageAnalytics> = emptyList(),
    val adminDictionaryEntries: List<DictionaryEntry> = emptyList(),
    val adminLoading: Boolean = false,

    val loading: Boolean = false,
    val feedbackSubmissionVersion: Long = 0L,
    val feedbackSubmitting: Boolean = false,
    val message: String? = null,
)
