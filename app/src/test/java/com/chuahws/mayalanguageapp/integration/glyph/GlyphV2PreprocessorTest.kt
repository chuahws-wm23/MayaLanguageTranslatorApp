package com.chuahws.mayalanguageapp.integration.glyph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlyphV2PreprocessorTest {
    @Test
    fun blackInkOnWhiteProducesFinite224RgbTensor() {
        val input = IntArray(80 * 80) { 255 }
        for (y in 15..65) for (x in 22..60) {
            if (x < 26 || x > 56 || y < 19 || y > 61) input[y * 80 + x] = 0
        }
        val out = GlyphV2Preprocessor.fromGrayscale(input, 80, 80)
        assertEquals(3 * 224 * 224, out.size)
        assertTrue(out.all { it.isFinite() })
        assertTrue(out.maxOrNull()!! > out.minOrNull()!!)
    }

    @Test
    fun whiteInkOnBlackIsInverted() {
        val input = IntArray(80 * 80)
        for (y in 20..60) for (x in 20..60) {
            if (x < 24 || y < 24 || x > 56 || y > 56) input[y * 80 + x] = 255
        }
        val out = GlyphV2Preprocessor.fromGrayscale(input, 80, 80)
        assertEquals(3 * 224 * 224, out.size)
        assertTrue(out.all { it.isFinite() })
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankWhiteImageIsRejected() {
        GlyphV2Preprocessor.fromGrayscale(IntArray(100 * 100) { 255 }, 100, 100)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidImageDimensionsAreRejected() {
        GlyphV2Preprocessor.fromGrayscale(IntArray(12), 3, 4)
    }
}
