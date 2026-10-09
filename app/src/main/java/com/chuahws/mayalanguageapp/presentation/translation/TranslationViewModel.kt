package com.chuahws.mayalanguageapp.presentation.translation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chuahws.mayalanguageapp.domain.model.TranslationDirection
import com.chuahws.mayalanguageapp.domain.model.TranslationResult
import com.chuahws.mayalanguageapp.domain.service.TranslationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TranslationViewModel(
    private val translationService: TranslationService
) : ViewModel() {

    private val _uiState = MutableStateFlow(TranslationUiState())
    val uiState: StateFlow<TranslationUiState> = _uiState.asStateFlow()

    fun updateInput(value: String) {
        _uiState.update {
            it.copy(input = value, message = null)
        }
    }

    fun swapDirection() {
        val next = when (_uiState.value.direction) {
            TranslationDirection.MAYA_TO_ENGLISH ->
                TranslationDirection.ENGLISH_TO_MAYA

            TranslationDirection.ENGLISH_TO_MAYA ->
                TranslationDirection.MAYA_TO_ENGLISH
        }

        _uiState.update {
            it.copy(
                direction = next,
                output = "",
                message = null,
            )
        }
    }

    fun translate() {
        val state = _uiState.value

        viewModelScope.launch {
            _uiState.update {
                it.copy(loading = true, message = null)
            }

            when (
                val result = translationService.translate(
                    state.input,
                    state.direction,
                )
            ) {
                is TranslationResult.Success ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            output = result.output,
                            message = null,
                        )
                    }

                is TranslationResult.NoMatch ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            output = "",
                            message = "No supported translation found."
                        )
                    }

                is TranslationResult.InvalidInput ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            output = "",
                            message = result.message,
                        )
                    }
            }
        }
    }
}
