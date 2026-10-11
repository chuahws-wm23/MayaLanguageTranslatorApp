package com.chuahws.mayalanguageapp.presentation.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.domain.model.FeedbackItem
import com.chuahws.mayalanguageapp.domain.model.SavedVocabularyItem
import com.chuahws.mayalanguageapp.domain.repository.UserDataRepository
import com.chuahws.mayalanguageapp.domain.repository.UserProfileRepository
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppDataViewModel(
    private val userDataRepository: UserDataRepository,
    private val userProfileRepository: UserProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    fun loadForUser(userId: String) {
        if (_uiState.value.userId == userId &&
            _uiState.value.profile != null
        ) {
            return
        }

        _uiState.update {
            it.copy(userId = userId)
        }
        refresh()
    }

    fun clearUser() {
        _uiState.value = AppUiState()
    }

    fun refresh() {
        val userId = _uiState.value.userId ?: return
        if (_uiState.value.loading) return

        _uiState.update {
            it.copy(loading = true)
        }

        viewModelScope.launch {
            runCatching {
                coroutineScope {
                    val profile = async {
                        userProfileRepository.getProfile(userId)
                    }
                    val history = async {
                        userDataRepository.getHistory(userId)
                    }
                    val saved = async {
                        userDataRepository.getSavedVocabulary(userId)
                    }
                    val feedback = async {
                        userDataRepository.getFeedback(userId)
                    }
                    val analytics = async {
                        userDataRepository.getAnalytics(userId)
                    }

                    LoadedUserData(
                        profile = profile.await(),
                        history = history.await(),
                        saved = saved.await(),
                        feedback = feedback.await(),
                        analytics = analytics.await(),
                    )
                }
            }.onSuccess { loaded ->
                _uiState.update {
                    it.copy(
                        profile = loaded.profile,
                        history = loaded.history,
                        savedVocabulary = loaded.saved,
                        feedback = loaded.feedback,
                        analytics = loaded.analytics,
                        loading = false,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        loading = false,
                        message = "Could not refresh your data. Check your connection and try again.",
                    )
                }
            }
        }
    }

    fun saveEntry(entry: DictionaryEntry) {
        val userId = _uiState.value.userId ?: return

        if (_uiState.value.savedVocabulary.any {
                it.entryId == entry.entryId
            }) {
            _uiState.update {
                it.copy(message = "This word is already saved.")
            }
            return
        }

        viewModelScope.launch {
            val item = SavedVocabularyItem(
                savedId = "${userId}_${entry.entryId}",
                userId = userId,
                entryId = entry.entryId,
                mayaText = entry.mayaText,
                englishText = entry.englishText,
                pronunciation = entry.pronunciation,
                partOfSpeech = entry.partOfSpeech,
                savedAtMillis = System.currentTimeMillis(),
            )

            runCatching {
                userDataRepository.saveVocabulary(item)
            }.onSuccess {
                _uiState.update {
                    it.copy(message = "Saved to vocabulary.")
                }
                refresh()
            }.onFailure(::showError)
        }
    }

    fun removeSaved(item: SavedVocabularyItem) {
        viewModelScope.launch {
            runCatching {
                userDataRepository.removeSavedVocabulary(
                    item.savedId
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(message = "Removed from saved vocabulary.")
                }
                refresh()
            }.onFailure(::showError)
        }
    }

    fun updateDisplayName(displayName: String) {
        val profile = _uiState.value.profile ?: return
        val cleanName = displayName.trim()

        if (cleanName.length < 2) {
            _uiState.update {
                it.copy(
                    message = "Enter at least two characters."
                )
            }
            return
        }

        viewModelScope.launch {
            val updated = profile.copy(
                displayName = cleanName,
                updatedAtMillis = System.currentTimeMillis(),
            )

            runCatching {
                userProfileRepository.updateProfile(updated)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        profile = updated,
                        message = "Profile updated.",
                    )
                }
            }.onFailure(::showError)
        }
    }

    fun submitFeedback(
        email: String,
        rating: Int,
        comment: String,
    ) {
        val userId = _uiState.value.userId ?: return
        val cleanComment = comment.trim()

        if (rating !in 1..5) {
            _uiState.update {
                it.copy(message = "Choose a rating first.")
            }
            return
        }

        if (cleanComment.length < 4) {
            _uiState.update {
                it.copy(message = "Tell us a little more.")
            }
            return
        }

        viewModelScope.launch {
            val item = FeedbackItem(
                feedbackId = UUID.randomUUID().toString(),
                userId = userId,
                userEmail = email,
                rating = rating,
                comment = cleanComment,
                status = "new",
                submittedAtMillis = System.currentTimeMillis(),
            )

            runCatching {
                userDataRepository.submitFeedback(item)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        message = "Thanks for the feedback.",
                        feedbackSubmissionVersion =
                            it.feedbackSubmissionVersion + 1,
                    )
                }
                refresh()
            }.onFailure(::showError)
        }
    }

    fun loadAdminFeedback() {
        if (_uiState.value.profile?.role != "admin") {
            return
        }

        viewModelScope.launch {
            runCatching {
                userDataRepository.getAllFeedback()
            }.onSuccess { items ->
                _uiState.update {
                    it.copy(adminFeedback = items)
                }
            }.onFailure(::showError)
        }
    }

    fun markFeedbackReviewed(item: FeedbackItem) {
        if (_uiState.value.profile?.role != "admin") {
            return
        }

        viewModelScope.launch {
            runCatching {
                userDataRepository.updateFeedbackStatus(
                    feedbackId = item.feedbackId,
                    status = "reviewed",
                    reviewedAtMillis = System.currentTimeMillis(),
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(message = "Feedback marked as reviewed.")
                }
                loadAdminFeedback()
            }.onFailure(::showError)
        }
    }

    fun clearMessage() {
        _uiState.update {
            it.copy(message = null)
        }
    }

    private fun showError(error: Throwable) {
        _uiState.update {
            it.copy(
                loading = false,
                message = "Something went wrong. Please try again.",
            )
        }
    }

    private data class LoadedUserData(
        val profile: com.chuahws.mayalanguageapp.domain.model.UserProfile?,
        val history: List<com.chuahws.mayalanguageapp.domain.model.TranslationRecord>,
        val saved: List<SavedVocabularyItem>,
        val feedback: List<FeedbackItem>,
        val analytics: com.chuahws.mayalanguageapp.domain.model.UsageAnalytics,
    )
}
