package com.chuahws.mayalanguageapp.domain.service

import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.domain.model.TranslationDirection
import com.chuahws.mayalanguageapp.domain.repository.DictionaryRepository

/**
 * Filters Latin-script OCR against supported Yucatec Maya/English vocabulary.
 *
 * This is intentionally conservative: ML Kit's Latin OCR is not a reliable
 * Yucatec-language or dialect classifier. We retain only phrases/words found
 * on the selected side of the application's dictionary, with no invented
 * cross-dialect translations. The original OCR remains available for review.
 */
class PhotoLanguageFilter(
    private val dictionaryRepository: DictionaryRepository,
) {
    data class Result(
        val text: String,
        val matchedWordCount: Int,
        val skippedWordCount: Int,
        val skippedWords: List<String>,
        val limited: Boolean,
    )

    suspend fun filter(
        rawText: String,
        direction: TranslationDirection,
    ): Result {
        val scanned = rawText.take(MAX_SCAN_CHARACTERS)
        val allTokens = scanned.lineSequence()
            .flatMap { line -> TOKEN_PATTERN.findAll(line).map { it.value } }
            .toList()
        val limitedByInput = rawText.length > MAX_SCAN_CHARACTERS ||
            allTokens.size > MAX_SCANNED_WORDS
        if (allTokens.isEmpty()) {
            return Result("", 0, 0, emptyList(), limitedByInput)
        }

        // One bounded Firestore read rather than dozens of network queries
        // per word. Firestore and built-in entries are combined by repository.
        val entries = dictionaryRepository.browseEntries(MAX_DICTIONARY_ROWS)
            .filter(DictionaryEntry::active)
        val mayaForms = entries.mapNotNull { entry ->
            TextNormalizer.normalize(entry.mayaNormalized)
                .takeIf(String::isNotBlank)
        }.toSet()
        val englishForms = entries.flatMap { entry ->
            listOf(entry.englishNormalized) + entry.acceptedEnglishInputs
        }.map(TextNormalizer::normalize).filter(String::isNotBlank).toSet()

        val sourceForms = when (direction) {
            TranslationDirection.MAYA_TO_ENGLISH -> mayaForms
            TranslationDirection.ENGLISH_TO_MAYA -> englishForms
        }
        val oppositeForms = when (direction) {
            TranslationDirection.MAYA_TO_ENGLISH -> englishForms
            TranslationDirection.ENGLISH_TO_MAYA -> mayaForms
        }

        val results = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        var matched = 0
        var processed = 0
        var outputLimited = false

        for (line in scanned.lines()) {
            if (processed >= MAX_SCANNED_WORDS || outputLimited) break

            val tokens = TOKEN_PATTERN.findAll(line)
                .map { it.value }
                .toList()
            val accepted = mutableListOf<String>()
            var index = 0

            while (index < tokens.size && processed < MAX_SCANNED_WORDS) {
                var phraseLength = 0
                for (length in minOf(MAX_PHRASE_WORDS, tokens.size - index) downTo 1) {
                    val phrase = tokens.subList(index, index + length)
                        .joinToString(" ")
                    val normalized = TextNormalizer.normalize(phrase)
                    // One-letter words are not language-specific enough.
                    if (normalized.length < 2) continue
                    if (
                        normalized in sourceForms &&
                        normalized !in oppositeForms
                    ) {
                        phraseLength = length
                        break
                    }
                }

                if (phraseLength > 0) {
                    val acceptedPhrase = tokens.subList(index, index + phraseLength)
                        .joinToString(" ")
                    val currentLength = results.sumOf(String::length) +
                        accepted.sumOf(String::length) +
                        accepted.size + results.size

                    if (currentLength + acceptedPhrase.length > MAX_TRANSLATABLE_LENGTH) {
                        outputLimited = true
                        break
                    }
                    accepted += acceptedPhrase
                    matched += phraseLength
                    processed += phraseLength
                    index += phraseLength
                } else {
                    skipped += tokens[index]
                    processed++
                    index++
                }
            }

            if (accepted.isNotEmpty()) {
                results += accepted.joinToString(" ")
            }
        }

        return Result(
            text = results.joinToString("\n"),
            matchedWordCount = matched,
            skippedWordCount = skipped.size,
            skippedWords = skipped.distinct().take(12),
            limited = limitedByInput || outputLimited ||
                processed < allTokens.size,
        )
    }

    private companion object {
        // Avoid uploading or translating whole documents as one OCR request.
        const val MAX_SCAN_CHARACTERS = 2_500
        const val MAX_SCANNED_WORDS = 100
        const val MAX_TRANSLATABLE_LENGTH = 500
        const val MAX_DICTIONARY_ROWS = 3_000
        const val MAX_PHRASE_WORDS = 4
        val TOKEN_PATTERN = Regex("[\\p{L}\\p{M}]+(?:[ʼ'’ʻʹ-][\\p{L}\\p{M}]+)*(?:[ʼ'’ʻʹ])?")
    }
}
