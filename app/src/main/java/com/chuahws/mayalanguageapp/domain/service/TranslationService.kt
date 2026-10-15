package com.chuahws.mayalanguageapp.domain.service

import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
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

        // Keep the existing deterministic behaviour for a verified/known
        // complete word or phrase.
        findEntry(normalized, direction)?.let { entry ->
            return TranslationResult.Success(
                input = rawInput,
                output = translatedText(entry, direction),
                entry = entry,
            )
        }

        // When the complete input is not a dictionary entry, translate each
        // line/sentence independently. This is especially useful for OCR,
        // where one image commonly contains several separate Maya lines.
        val segments = splitSegments(rawInput)

        if (segments.isEmpty()) {
            return TranslationResult.NoMatch(rawInput)
        }

        val translatedSegments = mutableListOf<String>()
        val unknownTerms = mutableListOf<String>()
        var matchedEntryCount = 0

        for (segment in segments) {
            val segmentNormalized = TextNormalizer.normalize(segment)

            if (segmentNormalized.isBlank()) {
                continue
            }

            val exactEntry = findEntry(
                segmentNormalized,
                direction,
            )

            if (exactEntry != null) {
                translatedSegments += translatedText(
                    exactEntry,
                    direction,
                )
                matchedEntryCount += 1
                continue
            }

            val fallback = translateSegmentByPhrases(
                segment = segment,
                direction = direction,
            )

            translatedSegments += fallback.output
            unknownTerms += fallback.unknownTerms
            matchedEntryCount += fallback.matchedEntryCount
        }

        // If not even one word/phrase could be matched, keep the original
        // NoMatch behaviour instead of returning a screen full of
        // [unknown: ...] markers.
        if (matchedEntryCount == 0) {
            return TranslationResult.NoMatch(rawInput)
        }

        return TranslationResult.Composed(
            input = rawInput,
            output = translatedSegments
                .filter(String::isNotBlank)
                .joinToString("\n"),
            unknownTerms = unknownTerms,
            matchedEntryCount = matchedEntryCount,
        )
    }

    suspend fun filterMayaOcrText(
        rawText: String,
    ): String = MayaOcrTextFilter.filter(
        rawText = rawText,
        isKnownMaya = { candidate ->
            dictionaryRepository.findByMaya(
                TextNormalizer.normalize(candidate)
            ) != null
        },
    )

    private suspend fun translateSegmentByPhrases(
        segment: String,
        direction: TranslationDirection,
    ): SegmentTranslation {
        val words = segment
            .trim()
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)

        if (words.isEmpty()) {
            return SegmentTranslation("")
        }

        val outputParts = mutableListOf<String>()
        val unknownTerms = mutableListOf<String>()
        var matches = 0
        var index = 0

        while (index < words.size) {
            val maxWindow = minOf(
                MAX_PHRASE_WORDS,
                words.size - index,
            )

            var matchedEntry: DictionaryEntry? = null
            var matchedLength = 0

            // Longest phrase first. This prevents a known multi-word phrase
            // from being broken into less accurate individual word matches.
            for (length in maxWindow downTo 1) {
                val candidate = words
                    .subList(index, index + length)
                    .joinToString(" ")

                val candidateNormalized =
                    TextNormalizer.normalize(candidate)

                if (candidateNormalized.isBlank()) {
                    continue
                }

                val entry = findEntry(
                    candidateNormalized,
                    direction,
                )

                if (entry != null) {
                    matchedEntry = entry
                    matchedLength = length
                    break
                }
            }

            if (matchedEntry != null) {
                outputParts += translatedText(
                    matchedEntry,
                    direction,
                )
                matches += 1
                index += matchedLength
            } else {
                val unknown = cleanDisplayToken(
                    words[index]
                )

                if (unknown.isNotBlank()) {
                    outputParts += "[unknown: $unknown]"
                    unknownTerms += unknown
                }

                index += 1
            }
        }

        var output = outputParts.joinToString(" ")

        // Preserve simple sentence punctuation for a composed translation.
        val punctuation = segment
            .trim()
            .lastOrNull()
            ?.takeIf { it in ".!?;:" }

        if (
            punctuation != null &&
            output.isNotBlank() &&
            output.last() !in ".!?;:"
        ) {
            output += punctuation
        }

        return SegmentTranslation(
            output = output,
            unknownTerms = unknownTerms,
            matchedEntryCount = matches,
        )
    }

    private suspend fun findEntry(
        normalizedText: String,
        direction: TranslationDirection,
    ): DictionaryEntry? = when (direction) {
        TranslationDirection.MAYA_TO_ENGLISH ->
            dictionaryRepository.findByMaya(normalizedText)

        TranslationDirection.ENGLISH_TO_MAYA ->
            dictionaryRepository.findByEnglish(normalizedText)
    }

    private fun translatedText(
        entry: DictionaryEntry,
        direction: TranslationDirection,
    ): String = when (direction) {
        TranslationDirection.MAYA_TO_ENGLISH ->
            entry.englishText

        TranslationDirection.ENGLISH_TO_MAYA ->
            entry.mayaText
    }

    private fun splitSegments(
        rawInput: String,
    ): List<String> =
        rawInput
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .split(
                Regex(
                    "\\n+|(?<=[.!?])\\s+"
                )
            )
            .map(String::trim)
            .filter(String::isNotBlank)

    private fun cleanDisplayToken(
        value: String,
    ): String =
        value.trim().trim(
            '.', ',', '!', '?', ';', ':'
        )

    private data class SegmentTranslation(
        val output: String,
        val unknownTerms: List<String> = emptyList(),
        val matchedEntryCount: Int = 0,
    )

    private companion object {
        // Keeps fallback lookup bounded when the backing repository is
        // Firestore while still supporting common multi-word phrases.
        const val MAX_PHRASE_WORDS = 4
    }
}
