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
    fun translateHi_returnsCoreGreetingWithUsageMetadata() = runTest {
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
        result as TranslationResult.Success

        assertEquals("Baʼax ka waʼalik?", result.output)
        assertEquals("hello", result.entry.englishText)
        assertEquals("General greeting", result.entry.usageLabel)
        assertTrue(result.entry.acceptedEnglishInputs.contains("hi"))
        assertTrue(result.entry.acceptedEnglishInputs.contains("hello"))
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
    fun multipleKnownLines_areTranslatedIndependently() = runTest {
        val service = TranslationService(
            InMemoryDictionaryRepository(
                DevelopmentDictionaryData.entries()
            )
        )

        val result = service.translate(
            rawInput = "hello\nthank you",
            direction = TranslationDirection.ENGLISH_TO_MAYA,
        )

        assertTrue(result is TranslationResult.Composed)
        result as TranslationResult.Composed

        assertEquals(
            "Baʼax ka waʼalik?\nDios boʼotik",
            result.output,
        )
        assertTrue(result.unknownTerms.isEmpty())
        assertEquals(2, result.matchedEntryCount)
    }

    @Test
    fun partialSentence_keepsKnownWordsAndMarksUnknownTerms() = runTest {
        val service = TranslationService(
            InMemoryDictionaryRepository(
                DevelopmentDictionaryData.entries()
            )
        )

        val result = service.translate(
            rawInput = "water mystery",
            direction = TranslationDirection.ENGLISH_TO_MAYA,
        )

        assertTrue(result is TranslationResult.Composed)
        result as TranslationResult.Composed

        assertEquals(
            "jaʼ [unknown: mystery]",
            result.output,
        )
        assertEquals(
            listOf("mystery"),
            result.unknownTerms,
        )
        assertEquals(1, result.matchedEntryCount)
    }

    @Test
    fun fallbackUsesLongestKnownPhraseFirst() = runTest {
        val entries = listOf(
            DictionaryEntry(
                entryId = "phrase",
                mayaText = "PHRASE",
                mayaNormalized = "phrase",
                englishText = "good morning",
                englishNormalized = "good morning",
                active = true,
            ),
            DictionaryEntry(
                entryId = "good",
                mayaText = "GOOD",
                mayaNormalized = "good",
                englishText = "good",
                englishNormalized = "good",
                active = true,
            ),
            DictionaryEntry(
                entryId = "water",
                mayaText = "WATER",
                mayaNormalized = "water",
                englishText = "water",
                englishNormalized = "water",
                active = true,
            ),
        )

        val service = TranslationService(
            InMemoryDictionaryRepository(entries)
        )

        val result = service.translate(
            rawInput = "good morning water",
            direction = TranslationDirection.ENGLISH_TO_MAYA,
        )

        assertTrue(result is TranslationResult.Composed)
        assertEquals(
            "PHRASE WATER",
            (result as TranslationResult.Composed).output,
        )
    }

    @Test
    fun lowCoverageSentence_doesNotPretendWordGlossesAreSentenceTranslation() = runTest {
        val entry = DictionaryEntry(
            entryId = "pronoun-a",
            mayaText = "a",
            mayaNormalized = "a",
            englishText = "you (second-person singular pronoun)",
            englishNormalized = "you",
            active = true,
        )

        val service = TranslationService(
            InMemoryDictionaryRepository(listOf(entry))
        )

        val result = service.translate(
            rawInput = "Bix a k'aaba' in wiinik?",
            direction = TranslationDirection.MAYA_TO_ENGLISH,
        )

        assertTrue(result is TranslationResult.Composed)
        assertEquals(
            "Bix a k'aaba' in wiinik? → [No reliable phrase translation available]",
            (result as TranslationResult.Composed).output,
        )
    }

    @Test
    fun mayaWordsCanFallbackToEnglishLineByLine() = runTest {
        val service = TranslationService(
            InMemoryDictionaryRepository(
                DevelopmentDictionaryData.entries()
            )
        )

        val result = service.translate(
            rawInput = "jaʼ\nnaaj",
            direction = TranslationDirection.MAYA_TO_ENGLISH,
        )

        assertTrue(result is TranslationResult.Composed)
        assertEquals(
            "water\nhouse",
            (result as TranslationResult.Composed).output,
        )
    }

    @Test
    fun ambiguousMayaInput_returnsAllDistinctEnglishMeanings() = runTest {
        val entries = listOf(
            DictionaryEntry(
                entryId = "sense-1",
                mayaText = "iit",
                mayaNormalized = "iit",
                englishText = "base",
                englishNormalized = "base",
                active = true,
            ),
            DictionaryEntry(
                entryId = "sense-2",
                mayaText = "iit",
                mayaNormalized = "iit",
                englishText = "bottom",
                englishNormalized = "bottom",
                active = true,
            ),
            DictionaryEntry(
                entryId = "sense-3",
                mayaText = "iit",
                mayaNormalized = "iit",
                englishText = "base",
                englishNormalized = "base",
                active = true,
            ),
        )

        val service = TranslationService(
            InMemoryDictionaryRepository(entries)
        )

        val result = service.translate(
            rawInput = "iit",
            direction = TranslationDirection.MAYA_TO_ENGLISH,
        )

        assertTrue(result is TranslationResult.Alternatives)
        result as TranslationResult.Alternatives

        assertEquals(2, result.entries.size)
        assertEquals(
            setOf("base", "bottom"),
            result.entries.map { it.englishText }.toSet(),
        )
    }

    @Test
    fun ambiguousEnglishInput_returnsAllDistinctMayaForms() = runTest {
        val entries = listOf(
            DictionaryEntry(
                entryId = "form-1",
                mayaText = "míis",
                mayaNormalized = "míis",
                englishText = "broom",
                englishNormalized = "broom",
                active = true,
            ),
            DictionaryEntry(
                entryId = "form-2",
                mayaText = "míisib",
                mayaNormalized = "míisib",
                englishText = "broom",
                englishNormalized = "broom",
                active = true,
            ),
        )

        val service = TranslationService(
            InMemoryDictionaryRepository(entries)
        )

        val result = service.translate(
            rawInput = "broom",
            direction = TranslationDirection.ENGLISH_TO_MAYA,
        )

        assertTrue(result is TranslationResult.Alternatives)
        result as TranslationResult.Alternatives

        assertEquals(
            setOf("míis", "míisib"),
            result.entries.map { it.mayaText }.toSet(),
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
