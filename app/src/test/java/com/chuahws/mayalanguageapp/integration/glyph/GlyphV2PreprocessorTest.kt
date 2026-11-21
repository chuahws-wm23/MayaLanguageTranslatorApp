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

    @Test
    fun grayscaleTwoToneInkAtOtsuCutoffStillRecognized() {
        // OpenCV regression discovered that Otsu may select the ink intensity
        // itself, leaving a '< threshold' mask empty on perfectly clear photos.
        val input = IntArray(80 * 80) { 235 }
        for (y in 16..64) for (x in 19..62) {
            if (x <= 23 || x >= 58 || y <= 20 || y >= 60) input[y * 80 + x] = 25
        }
        val tensor = GlyphV2Preprocessor.fromGrayscale(input, 80, 80, 160)
        assertEquals(3 * 160 * 160, tensor.size)
        assertTrue(tensor.all { it.isFinite() })
        assertTrue(tensor.maxOrNull()!! > tensor.minOrNull()!!)
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
