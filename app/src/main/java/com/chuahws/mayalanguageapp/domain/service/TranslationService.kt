package com.chuahws.mayalanguageapp.domain.service

import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.domain.model.TranslationDirection
import com.chuahws.mayalanguageapp.domain.model.TranslationResult
import com.chuahws.mayalanguageapp.domain.repository.DictionaryRepository

class TranslationService(
    private val dictionaryRepository: DictionaryRepository
) {
    private val photoLanguageFilter = PhotoLanguageFilter(dictionaryRepository)

    suspend fun filterPhotoText(
        recognizedText: String,
        direction: TranslationDirection,
    ): PhotoLanguageFilter.Result =
        photoLanguageFilter.filter(recognizedText, direction)

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

        // Reuse matching dictionary lookups within one translation.
        // Photo OCR often contains repeated words, and repeated Firestore
        // requests would otherwise slow down the full translation.
        val lookupCache = mutableMapOf<String, List<DictionaryEntry>>()
        val matchesFor: suspend (String) -> List<DictionaryEntry> = { key ->
            lookupCache[key] ?: distinctTranslations(
                findEntries(key, direction),
                direction,
            ).also { lookupCache[key] = it }
        }

        // Exact dictionary matches may have several distinct meanings.
        val exactEntries = matchesFor(normalized)

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
        val ambiguousTerms = mutableListOf<String>()
        val unreliableSegments = mutableListOf<String>()
        var matchedEntryCount = 0

        for (segment in segments) {
            val segmentNormalized = TextNormalizer.normalize(segment)

            if (segmentNormalized.isBlank()) {
                continue
            }

            val exactMatches = matchesFor(segmentNormalized)

            if (exactMatches.size == 1) {
                translatedSegments += translatedText(
                    exactMatches.first(),
                    direction,
                )
                matchedEntryCount += 1
                continue
            }

            if (exactMatches.size > 1) {
                // Do not silently choose one meaning without context.
                translatedSegments += "—"
                ambiguousTerms += segment.trim()
                continue
            }

            val fallback = translateSegmentByPhrases(
                segment = segment,
                direction = direction,
                matchesFor = matchesFor,
            )

            if (
                fallback.totalWordCount > 1 &&
                fallback.coverage < MIN_SEGMENT_COVERAGE
            ) {
                // One known word does not make a sentence translation.
                translatedSegments += "—"
                unreliableSegments += segment.trim()
            } else {
                translatedSegments += fallback.output
                matchedEntryCount += fallback.matchedEntryCount
            }
            unknownTerms += fallback.unknownTerms
            ambiguousTerms += fallback.ambiguousTerms
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
            unknownTerms = unknownTerms.distinct(),
            ambiguousTerms = ambiguousTerms.distinct(),
            unreliableSegments = unreliableSegments.distinct(),
            matchedEntryCount = matchedEntryCount,
        )
    }

    private suspend fun translateSegmentByPhrases(
        segment: String,
        direction: TranslationDirection,
        matchesFor: suspend (String) -> List<DictionaryEntry>,
    ): SegmentTranslation {
        val words = segment
            .trim()
            // OCR sometimes omits the space after a comma or semicolon.
            .replace(Regex("(?<=\\p{L})[,;:](?=\\p{L})"), " ")
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)

        if (words.isEmpty()) {
            return SegmentTranslation("")
        }

        val outputParts = mutableListOf<String>()
        val unknownTerms = mutableListOf<String>()
        val ambiguousTerms = mutableListOf<String>()
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
            var ambiguousLength = 0

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

                val candidates = matchesFor(candidateNormalized)
                if (candidates.size == 1) {
                    matchedEntry = candidates.first()
                    matchedLength = length
                    break
                }
                if (candidates.size > 1) {
                    // This phrase has several valid meanings or forms.
                    ambiguousLength = length
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
            } else if (ambiguousLength > 0) {
                val uncertain = words
                    .subList(index, index + ambiguousLength)
                    .joinToString(" ")
                outputParts += "—"
                ambiguousTerms += uncertain
                index += ambiguousLength
            } else {
                val unknown = cleanDisplayToken(words[index])
                if (unknown.isNotBlank()) {
                    // A dash is a visual placeholder, not a made-up
                    // Maya word or an internal [unknown: ...] marker.
                    outputParts += "—"
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
            ambiguousTerms = ambiguousTerms,
            matchedEntryCount = matches,
            matchedWordCount = matchedWords,
            totalWordCount = words.size,
        )
    }

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
            .sortedWith(
                compareByDescending<DictionaryEntry> {
                    it.verified
                }.thenBy {
                    translatedText(it, direction)
                }
            )
            // When identical translations exist, retain the verified
            // entry instead of a random duplicate from the database.
            .distinctBy {
                TextNormalizer.normalize(
                    translatedText(it, direction)
                )
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
        val ambiguousTerms: List<String> = emptyList(),
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

    /**
     * Spelling suggestions are only drawn from existing dictionary entries.
     * The caller must ask the user to confirm a suggestion; this method
     * never silently replaces text extracted from a photograph.
     */
    suspend fun suggestCorrections(
        rawInput: String,
        direction: TranslationDirection,
        limit: Int = 3,
    ): List<DictionaryEntry> {
        val input = TextNormalizer.normalize(rawInput)
        if (input.length !in 4..45 || input.count { it == ' ' } > 3) {
            return emptyList()
        }

        val prefix = input.take(if (input.length <= 5) 2 else 3)
        val candidates = dictionaryRepository.searchEntries(prefix, 80)
        val maxDistance = when {
            input.length <= 5 -> 1
            input.length <= 9 -> 2
            else -> 3
        }

        return candidates.mapNotNull { entry ->
            if (!entry.active) return@mapNotNull null
            val sourceForms = when (direction) {
                TranslationDirection.MAYA_TO_ENGLISH ->
                    listOf(entry.mayaNormalized)
                TranslationDirection.ENGLISH_TO_MAYA ->
                    listOf(entry.englishNormalized) +
                        entry.acceptedEnglishInputs
            }
            val bestDistance = sourceForms
                .map(TextNormalizer::normalize)
                .filter { kotlin.math.abs(it.length - input.length) <= maxDistance }
                .minOfOrNull { editDistance(input, it) }
                ?: return@mapNotNull null

            if (bestDistance == 0 || bestDistance > maxDistance) {
                null
            } else {
                entry to bestDistance
            }
        }.sortedWith(
            compareBy<Pair<DictionaryEntry, Int>> { it.second }
                .thenByDescending { it.first.verified }
                .thenBy { it.first.mayaText }
        ).distinctBy { (entry, _) ->
            when (direction) {
                TranslationDirection.MAYA_TO_ENGLISH -> entry.mayaNormalized
                TranslationDirection.ENGLISH_TO_MAYA -> entry.englishNormalized
            }
        }.take(limit.coerceIn(1, 3)).map { it.first }
    }

    private fun editDistance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val current = IntArray(b.length + 1)
            current[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost,
                )
            }
            previous = current
        }
        return previous[b.length]
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
