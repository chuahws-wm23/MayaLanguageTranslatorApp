package com.chuahws.mayalanguageapp.presentation.translation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chuahws.mayalanguageapp.domain.model.InputType
import com.chuahws.mayalanguageapp.domain.model.TranslationDirection
import com.chuahws.mayalanguageapp.domain.model.TranslationRecord
import com.chuahws.mayalanguageapp.domain.model.TranslationResult
import com.chuahws.mayalanguageapp.domain.repository.UserDataRepository
import com.chuahws.mayalanguageapp.domain.service.PhotoLanguageFilter
import com.chuahws.mayalanguageapp.domain.service.TranslationService
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
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

    private val _uiState =
        MutableStateFlow(TranslationUiState())
    val uiState: StateFlow<TranslationUiState> =
        _uiState.asStateFlow()

    private var translationJob: Job? = null

    fun replayStoredTranslation(
        exactSourceText: String,
        direction: TranslationDirection,
        mode: InputType,
    ) {
        // Discard any ongoing request from another screen.
        translationJob?.cancel()
        _uiState.value = TranslationUiState(
            input = exactSourceText,
            direction = direction,
            inputType = mode,
        )
        if (exactSourceText.isNotBlank()) {
            translate(mode)
        }
    }

    suspend fun filterPhotoText(
        recognizedText: String,
        direction: TranslationDirection,
    ): PhotoLanguageFilter.Result =
        translationService.filterPhotoText(recognizedText, direction)

    suspend fun possibleYucatecSpelling(
        recognizedWord: String,
    ) = translationService.suggestCorrections(
        recognizedWord,
        TranslationDirection.MAYA_TO_ENGLISH,
        limit = 3,
    )

    fun updateInput(value: String) {
        if (_uiState.value.loading) return

        _uiState.update {
            it.copy(
                input = value,
                output = "",
                matchedEntry = null,
                alternativeEntries = emptyList(),
                message = null,
                advice = null,
                approximate = false,
                needsReview = false,
                unknownTerms = emptyList(),
                ambiguousTerms = emptyList(),
                unreliableSegments = emptyList(),
                suggestions = emptyList(),
            )
        }
    }

    fun applySuggestion(sourceText: String) {
        if (_uiState.value.loading) return
        val state = _uiState.value
        val unknownWord = state.unknownTerms.singleOrNull()
        val correction = if (unknownWord != null) {
            val index = state.input.indexOf(unknownWord, ignoreCase = true)
            if (index >= 0) {
                state.input.replaceRange(
                    index,
                    index + unknownWord.length,
                    sourceText,
                )
            } else {
                sourceText
            }
        } else {
            sourceText
        }

        // Let the user check the corrected text before translating.
        updateInput(correction)
    }

    fun setDirection(
        direction: TranslationDirection,
    ) {
        if (_uiState.value.loading) return

        _uiState.update {
            it.copy(
                direction = direction,
                output = "",
                matchedEntry = null,
                alternativeEntries = emptyList(),
                message = null,
                advice = null,
                approximate = false,
                needsReview = false,
                unknownTerms = emptyList(),
                ambiguousTerms = emptyList(),
                unreliableSegments = emptyList(),
                suggestions = emptyList(),
            )
        }
    }

    fun swapDirection() {
        if (_uiState.value.loading) return

        val next = when (
            _uiState.value.direction
        ) {
            TranslationDirection.MAYA_TO_ENGLISH ->
                TranslationDirection.ENGLISH_TO_MAYA

            TranslationDirection.ENGLISH_TO_MAYA ->
                TranslationDirection.MAYA_TO_ENGLISH
        }

        setDirection(next)
    }

    fun clear() {
        if (_uiState.value.loading) return

        _uiState.update {
            TranslationUiState(
                direction = it.direction
            )
        }
    }

    fun translate(
        inputType: InputType = InputType.TEXT,
    ) {
        val state = _uiState.value

        if (state.loading) return

        if (state.input.isBlank()) {
            _uiState.update {
                it.copy(
                    message =
                        "Enter text before translating."
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                loading = true,
                message = null,
                advice = null,
                approximate = false,
                needsReview = false,
                unknownTerms = emptyList(),
                ambiguousTerms = emptyList(),
                unreliableSegments = emptyList(),
                suggestions = emptyList(),
                inputType = inputType,
            )
        }

        translationJob = viewModelScope.launch {
            val result = runCatching {
                translationService.translate(
                    state.input,
                    state.direction,
                )
            }.getOrElse { error ->
                if (error is CancellationException) return@launch
                _uiState.update {
                    it.copy(
                        loading = false,
                        output = "",
                        matchedEntry = null,
                        alternativeEntries =
                            emptyList(),
                        message =
                            if (
                                error.message
                                    ?.contains(
                                        "network",
                                        ignoreCase = true,
                                    ) == true
                            ) {
                                "Check your internet connection and try again."
                            } else {
                                "Translation is unavailable right now. Please try again."
                            },
                    )
                }
                return@launch
            }

            if (!isActive) return@launch

            when (result) {
                is TranslationResult.Success -> {
                    _uiState.update {
                        it.copy(
                            loading = false,
                            output = result.output,
                            matchedEntry =
                                result.entry,
                            alternativeEntries =
                                emptyList(),
                            message = null,
                            inputType = inputType,
                        )
                    }

                    recordSuccess(
                        source = state.input,
                        translated =
                            result.output,
                        entryId =
                            result.entry.entryId,
                        direction =
                            state.direction,
                        inputType = inputType,
                    )
                }

                is TranslationResult.Alternatives -> {
                    val outputs = result.entries
                        .map { entry ->
                            if (
                                state.direction ==
                                    TranslationDirection.MAYA_TO_ENGLISH
                            ) {
                                entry.englishText
                            } else {
                                entry.mayaText
                            }
                        }
                        .distinct()

                    _uiState.update {
                        it.copy(
                            loading = false,
                            output =
                                outputs.joinToString("\n"),
                            matchedEntry = null,
                            alternativeEntries =
                                result.entries,
                            message = null,
                            inputType = inputType,
                        )
                    }

                    recordSuccess(
                        source = state.input,
                        translated =
                            outputs.joinToString(" / "),
                        entryId = null,
                        direction =
                            state.direction,
                        inputType = inputType,
                    )
                }

                is TranslationResult.Composed -> {
                    val incomplete =
                        result.unknownTerms.isNotEmpty() ||
                            result.ambiguousTerms.isNotEmpty() ||
                            result.unreliableSegments.isNotEmpty()

                    _uiState.update {
                        it.copy(
                            loading = false,
                            output = result.output,
                            matchedEntry = null,
                            alternativeEntries = emptyList(),
                            message = null,
                            approximate = true,
                            needsReview = incomplete,
                            advice =
                                if (incomplete) {
                                    "Only part of this text matched the dictionary. " +
                                        "Please check the words below and correct the text " +
                                        "before trying again."
                                } else {
                                    "This result combines dictionary words or phrases. " +
                                        "It may not follow natural sentence grammar."
                                },
                            unknownTerms = result.unknownTerms,
                            ambiguousTerms = result.ambiguousTerms,
                            unreliableSegments = result.unreliableSegments,
                            inputType = inputType,
                        )
                    }

                    // The report requires history/analytics for successful
                    // translations, not incomplete OCR or unmatched phrases.
                    if (!incomplete) {
                        recordSuccess(
                            source = state.input,
                            translated = result.output,
                            entryId = null,
                            direction = state.direction,
                            inputType = inputType,
                        )
                    } else {
                        // Look up correction candidates after the partial
                        // result is already visible, so the UI responds fast.
                        val unrecognised = result.unknownTerms.singleOrNull()
                        if (unrecognised != null) {
                            val suggestions = runCatching {
                                translationService.suggestCorrections(
                                    unrecognised,
                                    state.direction,
                                )
                            }.getOrDefault(emptyList())
                            if (
                                _uiState.value.input == state.input &&
                                _uiState.value.direction == state.direction &&
                                !_uiState.value.loading
                            ) {
                                _uiState.update {
                                    it.copy(suggestions = suggestions)
                                }
                            }
                        }
                    }
                }

                is TranslationResult.NoMatch -> {
                    // Show the result immediately. Suggestions may require
                    // separate Firestore queries and should not block the UI.
                    _uiState.update {
                        it.copy(
                            loading = false,
                            output = "",
                            matchedEntry = null,
                            alternativeEntries = emptyList(),
                            message =
                                "No reliable translation found. Check the recognised text " +
                                    "or try a shorter phrase.",
                            suggestions = emptyList(),
                            inputType = inputType,
                        )
                    }

                    val suggestions = runCatching {
                        translationService.suggestCorrections(
                            state.input,
                            state.direction,
                        )
                    }.getOrDefault(emptyList())
                    if (
                        _uiState.value.input == state.input &&
                        _uiState.value.direction == state.direction &&
                        !_uiState.value.loading
                    ) {
                        _uiState.update {
                            it.copy(suggestions = suggestions)
                        }
                    }
                }

                is TranslationResult.InvalidInput ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            output = "",
                            matchedEntry = null,
                            alternativeEntries = emptyList(),
                            message =
                                result.message,
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
        val userId =
            userIdProvider() ?: return
        val now =
            System.currentTimeMillis()

        val sourceLanguage =
            if (direction ==
                TranslationDirection.MAYA_TO_ENGLISH
            ) {
                "Yucatec Maya"
            } else {
                "English"
            }

        val targetLanguage =
            if (direction ==
                TranslationDirection.MAYA_TO_ENGLISH
            ) {
                "English"
            } else {
                "Yucatec Maya"
            }

        runCatching {
            userDataRepository.recordTranslation(
                TranslationRecord(
                    translationId =
                        UUID.randomUUID().toString(),
                    userId = userId,
                    entryId = entryId,
                    sourceText =
                        source.trim(),
                    translatedText =
                        translated,
                    sourceLanguage =
                        sourceLanguage,
                    targetLanguage =
                        targetLanguage,
                    inputType =
                        inputType.name,
                    createdAtMillis = now,
                )
            )
        }.onSuccess {
            onDataChanged()
        }.onFailure {
            _uiState.update {
                it.copy(
                    message =
                        "Translation completed, but it could not be added to history.",
                )
            }
        }
    }
}
