package com.chuahws.mayalanguageapp.domain.service

import com.chuahws.mayalanguageapp.data.repository.DevelopmentDictionaryData
import com.chuahws.mayalanguageapp.data.repository.InMemoryDictionaryRepository
import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.domain.model.TranslationDirection
import com.chuahws.mayalanguageapp.domain.model.TranslationResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationServiceTest {
    private val testEntry = DictionaryEntry(
        entryId = "TEST-001",
        mayaText = "TEST-MAYA-WORD",
        mayaNormalized = "test-maya-word",
        englishText = "water",
        englishNormalized = "water",
        source = "Unit test fixture",
        verified = false,
    )

    @Test
    fun translateEnglishToMaya_returnsDictionaryResult() = runTest {
        val service = TranslationService(
            InMemoryDictionaryRepository(listOf(testEntry))
        )

        val result = service.translate(
            rawInput = "Water",
            direction = TranslationDirection.ENGLISH_TO_MAYA,
        )

        assertTrue(result is TranslationResult.Success)
        assertEquals(
            "TEST-MAYA-WORD",
            (result as TranslationResult.Success).output,
        )
    }

    @Test
    fun translateHi_returnsCoreGreeting() = runTest {
        val service = TranslationService(
            InMemoryDictionaryRepository(
                DevelopmentDictionaryData.entries()
            )
        )

        val result = service.translate(
            rawInput = "hi",
            direction = TranslationDirection.ENGLISH_TO_MAYA,
        )

        assertTrue(result is TranslationResult.Success)
        assertEquals(
            "Baʼax ka waʼalik?",
            (result as TranslationResult.Success).output,
        )
    }

    @Test
    fun mayaGreeting_matchesWithoutQuestionMark() = runTest {
        val service = TranslationService(
            InMemoryDictionaryRepository(
                DevelopmentDictionaryData.entries()
            )
        )

        val result = service.translate(
            rawInput = "Ba'ax ka wa'alik",
            direction = TranslationDirection.MAYA_TO_ENGLISH,
        )

        assertTrue(result is TranslationResult.Success)
        assertEquals(
            "hello",
            (result as TranslationResult.Success).output,
        )
    }

    @Test
    fun translateUnknownInput_returnsNoMatch() = runTest {
        val service = TranslationService(
            InMemoryDictionaryRepository(listOf(testEntry))
        )

        val result = service.translate(
            rawInput = "unsupported",
            direction = TranslationDirection.ENGLISH_TO_MAYA,
        )

        assertTrue(result is TranslationResult.NoMatch)
    }
}
