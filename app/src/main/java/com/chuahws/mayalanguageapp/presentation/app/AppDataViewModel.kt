package com.chuahws.mayalanguageapp.presentation.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.domain.model.FeedbackItem
import com.chuahws.mayalanguageapp.domain.model.SavedVocabularyItem
import com.chuahws.mayalanguageapp.domain.repository.UserDataRepository
import com.chuahws.mayalanguageapp.domain.repository.UserProfileRepository
import java.util.UUID
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
        _uiState.update { it.copy(userId = userId) }
        refresh()
    }

    fun clearUser() {
        _uiState.value = AppUiState()
    }

    fun refresh() {
        val userId = _uiState.value.userId ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }

            runCatching {
                val profile = userProfileRepository.getProfile(userId)
                val history = userDataRepository.getHistory(userId)
                val saved = userDataRepository.getSavedVocabulary(userId)
                val feedback = userDataRepository.getFeedback(userId)
                val analytics = userDataRepository.getAnalytics(userId)

                AppUiState(
                    userId = userId,
                    profile = profile,
                    history = history,
                    savedVocabulary = saved,
                    feedback = feedback,
                    analytics = analytics,
                    loading = false,
                )
            }.onSuccess { loaded ->
                _uiState.value = loaded
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        loading = false,
                        message = error.message
                            ?: "Could not refresh your data.",
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
                it.copy(message = "Already saved.")
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
                userDataRepository.removeSavedVocabulary(item.savedId)
            }.onSuccess {
                refresh()
            }.onFailure(::showError)
        }
    }

    fun updateDisplayName(displayName: String) {
        val profile = _uiState.value.profile ?: return
        val cleanName = displayName.trim()

        if (cleanName.length < 2) {
            _uiState.update {
                it.copy(message = "Enter at least two characters.")
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
                    it.copy(message = "Thanks for the feedback.")
                }
                refresh()
            }.onFailure(::showError)
        }
    }

    fun loadAdminFeedback() {
        if (_uiState.value.profile?.role != "admin") return

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
        if (_uiState.value.profile?.role != "admin") return

        viewModelScope.launch {
            runCatching {
                userDataRepository.updateFeedbackStatus(
                    feedbackId = item.feedbackId,
                    status = "reviewed",
                    reviewedAtMillis = System.currentTimeMillis(),
                )
            }.onSuccess {
                loadAdminFeedback()
            }.onFailure(::showError)
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun showError(error: Throwable) {
        _uiState.update {
            it.copy(
                loading = false,
                message = error.message ?: "Something went wrong.",
            )
        }
    }
}
