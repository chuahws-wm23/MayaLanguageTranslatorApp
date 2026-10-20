package com.chuahws.mayalanguageapp.domain.service

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MayaOcrTextFilterTest {

    @Test
    fun mixedMayaEnglishAndNoise_keepsOnlyMayaLines() = runTest {
        val raw = """
            Bix a k’aaba’ in wíinik?
            What is your name?
            8
            Ma’alo’ob.
            Thank you.
            Tene’ in k’aj óolal.
            Have a good day.
        """.trimIndent()

        val filtered = MayaOcrTextFilter.filter(
            rawText = raw,
            isKnownMaya = { false },
        )

        assertEquals(
            """
                Bix a k’aaba’ in wíinik?
                Ma’alo’ob.
                Tene’ in k’aj óolal.
            """.trimIndent(),
            filtered,
        )
    }

    @Test
    fun plainLatinMayaLine_isKeptWhenDictionaryRecognisesIt() = runTest {
        val filtered = MayaOcrTextFilter.filter(
            rawText = "Bix a bel?",
            isKnownMaya = {
                TextNormalizer.normalize(it) == "bix a bel"
            },
        )

        assertEquals("Bix a bel?", filtered)
    }

    @Test
    fun obviousEnglishAndNumbers_areRemoved() = runTest {
        val filtered = MayaOcrTextFilter.filter(
            rawText = "Thank you.\n123\nWelcome",
            isKnownMaya = { false },
        )

        assertEquals("", filtered)
    }

    @Test
    fun asciiAndCurlyApostrophes_arePreservedAsMayaSignals() = runTest {
        val filtered = MayaOcrTextFilter.filter(
            rawText = "k'aaba'\nMa’alo’ob",
            isKnownMaya = { false },
        )

        assertEquals(
            "k'aaba'\nMa’alo’ob",
            filtered,
        )
    }

    @Test
    fun mixedLettersAndSmallNumber_keepsKnownMayaText() = runTest {
        val filtered = MayaOcrTextFilter.filter(
            rawText = "jaʼ 2",
            isKnownMaya = {
                TextNormalizer.normalize(it) == "jaʼ 2"
            },
        )

        assertEquals("jaʼ 2", filtered)
    }

    @Test
    fun plainLatinLine_withKnownMayaToken_isKept() = runTest {
        val filtered = MayaOcrTextFilter.filter(
            rawText = "tuunich bej",
            isKnownMaya = {
                TextNormalizer.normalize(it) in
                    setOf("tuunich", "bej")
            },
        )

        assertEquals("tuunich bej", filtered)
    }

}
