package com.chuahws.mayalanguageapp.domain.service

import com.chuahws.mayalanguageapp.data.repository.InMemoryDictionaryRepository
import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.domain.model.TranslationDirection
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoLanguageFilterTest {
    private fun filter() = PhotoLanguageFilter(
        InMemoryDictionaryRepository(
            listOf(
                DictionaryEntry(
                    entryId = "water",
                    mayaText = "jaʼ",
                    mayaNormalized = "jaʼ",
                    englishText = "water",
                    englishNormalized = "water",
                    active = true,
                ),
                DictionaryEntry(
                    entryId = "house",
                    mayaText = "naaj",
                    mayaNormalized = "naaj",
                    englishText = "house",
                    englishNormalized = "house",
                    active = true,
                ),
                DictionaryEntry(
                    entryId = "morning",
                    mayaText = "MAYA-GREETING",
                    mayaNormalized = "maya-greeting",
                    englishText = "good morning",
                    englishNormalized = "good morning",
                    active = true,
                ),
                DictionaryEntry(
                    entryId = "hand",
                    mayaText = "kʼab",
                    mayaNormalized = "kʼab",
                    englishText = "hand, arm",
                    englishNormalized = "hand arm",
                    acceptedEnglishInputs = listOf("hand", "arm"),
                    active = true,
                ),
            )
        )
    )

    @Test
    fun selectingYucatecMaya_keepsOnlyKnownYucatecTokens() = runTest {
        val result = filter().filter(
            "water ja' house naaj",
            TranslationDirection.MAYA_TO_ENGLISH,
        )
        assertEquals("ja' naaj", result.text)
        assertEquals(2, result.matchedWordCount)
        assertTrue(result.skippedWords.contains("water"))
        assertFalse(result.limited)
    }

    @Test
    fun selectingEnglish_keepsOnlyKnownEnglishTokens() = runTest {
        val result = filter().filter(
            "water jaʼ house naaj",
            TranslationDirection.ENGLISH_TO_MAYA,
        )
        assertEquals("water house", result.text)
        assertEquals(2, result.matchedWordCount)
    }

    @Test
    fun matchingEnglishPhraseAndAlias_preservesOriginalOcr() = runTest {
        val result = filter().filter(
            "good morning hand",
            TranslationDirection.ENGLISH_TO_MAYA,
        )
        assertEquals("good morning hand", result.text)
        assertEquals(3, result.matchedWordCount)
    }

    @Test
    fun unknownMayaVariety_isNotSilentlyConverted() = runTest {
        val result = filter().filter(
            "unfamiliarDialectWord water",
            TranslationDirection.MAYA_TO_ENGLISH,
        )
        assertEquals("", result.text)
        assertTrue(result.skippedWords.contains("unfamiliarDialectWord"))
    }

    @Test
    fun nonWordsAndEmptyInput_doNotBecomeTranslations() = runTest {
        val result = filter().filter(
            "123 !? 🙂",
            TranslationDirection.ENGLISH_TO_MAYA,
        )
        assertEquals("", result.text)
        assertEquals(0, result.matchedWordCount)
    }

    @Test
    fun punctuationAndLines_doNotJoinUnrelatedWords() = runTest {
        val result = filter().filter(
            "water,jaʼ\nhouse,naaj",
            TranslationDirection.ENGLISH_TO_MAYA,
        )
        assertEquals("water\nhouse", result.text)
    }

    @Test
    fun largeInput_isFlaggedAndNeverExceedsTranslationLimit() = runTest {
        val result = filter().filter(
            "water ".repeat(450),
            TranslationDirection.ENGLISH_TO_MAYA,
        )
        assertTrue(result.limited)
        assertTrue(result.text.length <= 500)
    }
}
