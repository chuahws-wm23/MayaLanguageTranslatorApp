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
    fun partialSentence_reportsMissingTermsWithoutCodeMarkers() = runTest {
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
            "jaʼ —",
            result.output,
        )
        assertEquals(
            listOf("mystery"),
            result.unknownTerms,
        )
        assertEquals(1, result.matchedEntryCount)
        assertTrue(result.ambiguousTerms.isEmpty())
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

        // A single matching pronoun cannot justify a full translation.
        assertTrue(result is TranslationResult.NoMatch)
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
    fun englishAlias_canFindQualifiedDictionaryGloss() = runTest {
        val entry = DictionaryEntry(
            entryId = "hand-entry",
            mayaText = "kʼab",
            mayaNormalized = "kʼab",
            englishText = "hand, arm",
            englishNormalized = "hand, arm",
            acceptedEnglishInputs = listOf("hand", "arm"),
            active = true,
        )

        val service = TranslationService(
            InMemoryDictionaryRepository(listOf(entry))
        )

        val result = service.translate(
            rawInput = "hand",
            direction = TranslationDirection.ENGLISH_TO_MAYA,
        )

        assertTrue(result is TranslationResult.Success)
        assertEquals(
            "kʼab",
            (result as TranslationResult.Success).output,
        )
    }


    @Test
    fun inputLongerThanLimit_isRejected() = runTest {
        val service = TranslationService(
            InMemoryDictionaryRepository(
                listOf(testEntry)
            )
        )

        val result = service.translate(
            rawInput = "a".repeat(501),
            direction =
                TranslationDirection.ENGLISH_TO_MAYA,
        )

        assertTrue(
            result is TranslationResult.InvalidInput
        )
    }

    @Test
    fun inputWithoutLetters_isRejected() = runTest {
        val service = TranslationService(
            InMemoryDictionaryRepository(
                listOf(testEntry)
            )
        )

        val result = service.translate(
            rawInput = "12345 !!!",
            direction =
                TranslationDirection.ENGLISH_TO_MAYA,
        )

        assertTrue(
            result is TranslationResult.InvalidInput
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
    @Test
    fun punctuationFromOcr_doesNotJoinDistinctDictionaryWords() = runTest {
        val service = TranslationService(
            InMemoryDictionaryRepository(
                DevelopmentDictionaryData.entries()
            )
        )

        val result = service.translate(
            rawInput = "water,house",
            direction = TranslationDirection.ENGLISH_TO_MAYA,
        )

        assertTrue(result is TranslationResult.Composed)
        assertEquals(
            "jaʼ naaj",
            (result as TranslationResult.Composed).output,
        )
        assertTrue(result.unknownTerms.isEmpty())
    }

    @Test
    fun ambiguousPartialPhrase_isNotSilentlyAssignedFirstMeaning() = runTest {
        val entries = listOf(
            DictionaryEntry(
                entryId = "broom-a",
                mayaText = "MAYA-A",
                mayaNormalized = "maya-a",
                englishText = "broom",
                englishNormalized = "broom",
                active = true,
            ),
            DictionaryEntry(
                entryId = "broom-b",
                mayaText = "MAYA-B",
                mayaNormalized = "maya-b",
                englishText = "broom",
                englishNormalized = "broom",
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
        val result = TranslationService(
            InMemoryDictionaryRepository(entries)
        ).translate("broom water", TranslationDirection.ENGLISH_TO_MAYA)

        assertTrue(result is TranslationResult.Composed)
        result as TranslationResult.Composed
        assertEquals("— WATER", result.output)
        assertEquals(listOf("broom"), result.ambiguousTerms)
        assertEquals(1, result.matchedEntryCount)
    }

    @Test
    fun duplicateTranslation_prefersVerifiedSource() = runTest {
        val first = DictionaryEntry(
            entryId = "unverified",
            mayaText = "source",
            mayaNormalized = "source",
            englishText = "water",
            englishNormalized = "water",
            verified = false,
            active = true,
        )
        val second = first.copy(entryId = "verified", verified = true)
        val result = TranslationService(
            InMemoryDictionaryRepository(listOf(first, second))
        ).translate("water", TranslationDirection.ENGLISH_TO_MAYA)

        assertTrue(result is TranslationResult.Success)
        assertTrue((result as TranslationResult.Success).entry.verified)
    }

    @Test
    fun misspelling_offersDictionarySuggestionsWithoutAutoTranslating() = runTest {
        val service = TranslationService(
            InMemoryDictionaryRepository(DevelopmentDictionaryData.entries())
        )

        assertTrue(
            service.translate(
                "watwr",
                TranslationDirection.ENGLISH_TO_MAYA
            ) is TranslationResult.NoMatch
        )

        val suggestions = service.suggestCorrections(
            "watwr",
            TranslationDirection.ENGLISH_TO_MAYA,
        )
        assertTrue(suggestions.any { it.englishText == "water" })
    }

    @Test
    fun unsupportedSentence_doesNotProduceMisleadingPartialTranslation() = runTest {
        val service = TranslationService(
            InMemoryDictionaryRepository(DevelopmentDictionaryData.entries())
        )
        val result = service.translate(
            "water mystery impossible",
            TranslationDirection.ENGLISH_TO_MAYA,
        )
        assertTrue(result is TranslationResult.NoMatch)
    }

}
