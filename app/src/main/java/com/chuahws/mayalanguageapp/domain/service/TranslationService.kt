package com.chuahws.mayalanguageapp.domain.service

import com.chuahws.mayalanguageapp.domain.model.TranslationDirection
import com.chuahws.mayalanguageapp.domain.model.TranslationResult
import com.chuahws.mayalanguageapp.domain.repository.DictionaryRepository

class TranslationService(
    private val dictionaryRepository: DictionaryRepository
) {
    suspend fun translate(
        rawInput: String,
        direction: TranslationDirection,
    ): TranslationResult {
        val normalized = TextNormalizer.normalize(rawInput)

        if (normalized.isBlank()) {
            return TranslationResult.InvalidInput(
                "Enter text before translating."
            )
        }

        val entry = when (direction) {
            TranslationDirection.MAYA_TO_ENGLISH ->
                dictionaryRepository.findByMaya(normalized)

            TranslationDirection.ENGLISH_TO_MAYA ->
                dictionaryRepository.findByEnglish(normalized)
        } ?: return TranslationResult.NoMatch(rawInput)

        val output = when (direction) {
            TranslationDirection.MAYA_TO_ENGLISH -> entry.englishText
            TranslationDirection.ENGLISH_TO_MAYA -> entry.mayaText
        }

        return TranslationResult.Success(
            input = rawInput,
            output = output,
            entry = entry,
        )
    }
}
