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

        if (rawInput.length > MAX_INPUT_LENGTH) {
            return TranslationResult.InvalidInput(
                "Keep the text under $MAX_INPUT_LENGTH characters."
            )
        }

        if (!rawInput.any(Char::isLetter)) {
            return TranslationResult.InvalidInput(
                "Enter text that contains letters."
            )
        }

        // Exact dictionary matches can legitimately have several senses or
        // several target-language forms. Never hide that ambiguity by taking
        // an arbitrary first Firestore document.
        val exactEntries = distinctTranslations(
            findEntries(normalized, direction),
            direction,
        )

        if (exactEntries.size == 1) {
            val entry = exactEntries.first()
            return TranslationResult.Success(
                input = rawInput,
                output = translatedText(entry, direction),
                entry = entry,
            )
        }

        if (exactEntries.size > 1) {
            return TranslationResult.Alternatives(
                input = rawInput,
                entries = exactEntries,
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

            val segmentOutput =
                if (
                    fallback.totalWordCount > 1 &&
                    fallback.coverage < MIN_SEGMENT_COVERAGE
                ) {
                    segment.trim() +
                        " → [No reliable phrase translation available]"
                } else {
                    fallback.output
                }

            translatedSegments += segmentOutput
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
        var matchedWords = 0
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
                matchedWords += matchedLength
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
            matchedWordCount = matchedWords,
            totalWordCount = words.size,
        )
    }

    private suspend fun findEntry(
        normalizedText: String,
        direction: TranslationDirection,
    ): DictionaryEntry? =
        findEntries(normalizedText, direction)
            .firstOrNull()

    private suspend fun findEntries(
        normalizedText: String,
        direction: TranslationDirection,
    ): List<DictionaryEntry> = when (direction) {
        TranslationDirection.MAYA_TO_ENGLISH ->
            dictionaryRepository.findAllByMaya(normalizedText)

        TranslationDirection.ENGLISH_TO_MAYA ->
            dictionaryRepository.findAllByEnglish(normalizedText)
    }

    private fun distinctTranslations(
        entries: List<DictionaryEntry>,
        direction: TranslationDirection,
    ): List<DictionaryEntry> =
        entries
            .distinctBy {
                TextNormalizer.normalize(
                    translatedText(it, direction)
                )
            }
            .sortedWith(
                compareByDescending<DictionaryEntry> {
                    it.verified
                }.thenBy {
                    translatedText(it, direction)
                }
            )

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
        val matchedWordCount: Int = 0,
        val totalWordCount: Int = 0,
    ) {
        val coverage: Double
            get() =
                if (totalWordCount == 0) {
                    0.0
                } else {
                    matchedWordCount.toDouble() /
                        totalWordCount.toDouble()
                }
    }

    private companion object {
        // Keeps fallback lookup bounded when the backing repository is
        // Firestore while still supporting common multi-word phrases.
        const val MAX_PHRASE_WORDS = 4
        const val MAX_INPUT_LENGTH = 500

        // Below this threshold, word-by-word glosses are too likely to look
        // like a sentence translation even though most of the phrase is
        // unknown. Show a clear unavailable message instead.
        const val MIN_SEGMENT_COVERAGE = 0.5
    }
}
