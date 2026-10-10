package com.chuahws.mayalanguageapp.presentation.app

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
    val adminFeedback: List<FeedbackItem> = emptyList(),
    val analytics: UsageAnalytics = UsageAnalytics(),
    val loading: Boolean = false,
    val message: String? = null,
)
