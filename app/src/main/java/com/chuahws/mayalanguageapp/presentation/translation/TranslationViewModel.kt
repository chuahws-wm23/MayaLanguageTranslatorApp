package com.chuahws.mayalanguageapp.presentation.translation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chuahws.mayalanguageapp.domain.model.InputType
import com.chuahws.mayalanguageapp.domain.model.TranslationDirection
import com.chuahws.mayalanguageapp.domain.model.TranslationRecord
import com.chuahws.mayalanguageapp.domain.model.TranslationResult
import com.chuahws.mayalanguageapp.domain.repository.UserDataRepository
import com.chuahws.mayalanguageapp.domain.service.TranslationService
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TranslationViewModel(
    private val translationService: TranslationService,
    private val userDataRepository: UserDataRepository,
    private val userIdProvider: () -> String?,
    private val onDataChanged: () -> Unit = {},
) : ViewModel() {

    private val _uiState = MutableStateFlow(TranslationUiState())
    val uiState: StateFlow<TranslationUiState> = _uiState.asStateFlow()

    fun updateInput(value: String) {
        _uiState.update {
            it.copy(
                input = value,
                output = "",
                matchedEntry = null,
                message = null,
            )
        }
    }

    fun setDirection(direction: TranslationDirection) {
        _uiState.update {
            it.copy(
                direction = direction,
                output = "",
                matchedEntry = null,
                message = null,
            )
        }
    }

    fun swapDirection() {
        val next = when (_uiState.value.direction) {
            TranslationDirection.MAYA_TO_ENGLISH ->
                TranslationDirection.ENGLISH_TO_MAYA

            TranslationDirection.ENGLISH_TO_MAYA ->
                TranslationDirection.MAYA_TO_ENGLISH
        }

        setDirection(next)
    }

    fun clear() {
        _uiState.update {
            TranslationUiState(direction = it.direction)
        }
    }

    fun translate(inputType: InputType = InputType.TEXT) {
        val state = _uiState.value

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    loading = true,
                    message = null,
                    inputType = inputType,
                )
            }

            when (
                val result = translationService.translate(
                    state.input,
                    state.direction,
                )
            ) {
                is TranslationResult.Success -> {
                    _uiState.update {
                        it.copy(
                            loading = false,
                            output = result.output,
                            matchedEntry = result.entry,
                            message = null,
                            inputType = inputType,
                        )
                    }

                    recordSuccess(
                        source = state.input,
                        translated = result.output,
                        entryId = result.entry.entryId,
                        direction = state.direction,
                        inputType = inputType,
                    )
                }

                is TranslationResult.NoMatch ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            output = "",
                            matchedEntry = null,
                            message = "No matching entry found.",
                        )
                    }

                is TranslationResult.InvalidInput ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            output = "",
                            matchedEntry = null,
                            message = result.message,
                        )
                    }
            }
        }
    }

    private suspend fun recordSuccess(
        source: String,
        translated: String,
        entryId: String?,
        direction: TranslationDirection,
        inputType: InputType,
    ) {
        val userId = userIdProvider() ?: return
        val now = System.currentTimeMillis()

        val sourceLanguage =
            if (direction == TranslationDirection.MAYA_TO_ENGLISH)
                "Yucatec Maya"
            else
                "English"

        val targetLanguage =
            if (direction == TranslationDirection.MAYA_TO_ENGLISH)
                "English"
            else
                "Yucatec Maya"

        runCatching {
            userDataRepository.recordTranslation(
                TranslationRecord(
                    translationId = UUID.randomUUID().toString(),
                    userId = userId,
                    entryId = entryId,
                    sourceText = source.trim(),
                    translatedText = translated,
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLanguage,
                    inputType = inputType.name,
                    createdAtMillis = now,
                )
            )
        }.onSuccess {
            onDataChanged()
        }
    }
}
