package com.chuahws.mayalanguageapp.domain.service

import java.util.Locale

/**
 * Conservative post-processing for OCR that is expected to contain
 * Yucatec Maya written in the modern Latin alphabet.
 *
 * ML Kit intentionally recognises every visible text line. This filter removes
 * obvious numbers, symbols and English lines before the editable OCR result is
 * shown to the user. Ambiguous plain-Latin lines are kept only when the
 * dictionary recognises the complete line or one of its words.
 *
 * The final OCR field stays editable because language filtering cannot replace
 * user verification.
 */
object MayaOcrTextFilter {

    private val wordRegex = Regex(
        """[\p{L}\p{M}]+(?:[ʼ’'ʻ][\p{L}\p{M}]+)*[ʼ’'ʻ]?"""
    )

    private val mayaAccentRegex = Regex(
        """[áéíóúÁÉÍÓÚ]"""
    )

    private val mayaGlottalRegex = Regex(
        """\p{L}[ʼ’'ʻ]\p{L}|\p{L}[ʼ’'ʻ](?=\s|[?.!,;:]|$)"""
    )

    private val obviousEnglishWords = setOf(
        "what",
        "is",
        "are",
        "your",
        "you",
        "name",
        "thank",
        "thanks",
        "have",
        "good",
        "day",
        "hello",
        "welcome",
        "please",
        "the",
        "this",
        "that",
        "who",
        "where",
        "when",
        "why",
        "how",
        "from",
        "for",
        "of",
        "and",
        "or",
    )

    suspend fun filter(
        rawText: String,
        isKnownMaya: suspend (String) -> Boolean,
    ): String {
        return rawText
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .mapNotNull { line ->
                if (shouldKeep(line, isKnownMaya)) {
                    line
                } else {
                    null
                }
            }
            .joinToString("\n")
            .trim()
    }

    private suspend fun shouldKeep(
        line: String,
        isKnownMaya: suspend (String) -> Boolean,
    ): Boolean {
        if (!line.any(Char::isLetter)) {
            return false
        }

        val digitCount = line.count(Char::isDigit)
        val letterCount = line.count(Char::isLetter)

        // Reject OCR noise such as "8", page numbers and lines dominated by
        // numeric characters.
        if (digitCount > 0 && digitCount >= letterCount) {
            return false
        }

        // Yucatec Maya frequently uses glottal-stop apostrophes and accented
        // vowels. These are strong orthographic signals and should be
        // preserved even when the complete sentence is not yet in the
        // dictionary.
        if (
            mayaAccentRegex.containsMatchIn(line) ||
            mayaGlottalRegex.containsMatchIn(line)
        ) {
            return true
        }

        if (isKnownMaya(line)) {
            return true
        }

        val words = wordRegex
            .findAll(line)
            .map { normalizeWord(it.value) }
            .filter(String::isNotBlank)
            .toList()

        if (words.isEmpty()) {
            return false
        }

        // English subtitle/caption lines are removed before token-level Maya
        // dictionary checks. Common one-letter words are deliberately excluded
        // because forms such as "a" and "in" can also appear in Yucatec Maya.
        if (words.any(obviousEnglishWords::contains)) {
            return false
        }

        // For an otherwise ambiguous Latin-script line, keep it only if at
        // least one token exists in the Maya dictionary. Limit the number of
        // lookups so a long OCR line cannot trigger excessive Firestore reads.
        for (word in words.take(8)) {
            if (word.length > 1 && isKnownMaya(word)) {
                return true
            }
        }

        return false
    }

    private fun normalizeWord(value: String): String =
        value
            .trim()
            .lowercase(Locale.ROOT)
            .replace('’', 'ʼ')
            .replace('\'', 'ʼ')
            .replace('ʻ', 'ʼ')
}
