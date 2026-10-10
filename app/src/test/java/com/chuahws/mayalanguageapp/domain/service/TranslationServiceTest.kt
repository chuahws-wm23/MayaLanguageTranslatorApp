package com.chuahws.mayalanguageapp.domain.service

import com.chuahws.mayalanguageapp.data.repository.InMemoryDictionaryRepository
import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.domain.model.TranslationDirection
import com.chuahws.mayalanguageapp.domain.model.TranslationResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationServiceTest {
    private val entry = DictionaryEntry(
        entryId = "TEST-001",
        mayaText = "TEST-MAYA-WORD",
        mayaNormalized = "test-maya-word",
        englishText = "water",
        englishNormalized = "water",
        source = "Unit test fixture",
        verified = false,
    )

    private val service = TranslationService(
        InMemoryDictionaryRepository(listOf(entry))
    )

    @Test
    fun translateEnglishToMaya_returnsDictionaryResult() = runTest {
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
    fun translateUnknownInput_returnsNoMatch() = runTest {
        val result = service.translate(
            rawInput = "unsupported",
            direction = TranslationDirection.ENGLISH_TO_MAYA,
        )

        assertTrue(result is TranslationResult.NoMatch)
    }
}
