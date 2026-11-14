package com.chuahws.mayalanguageapp.integration.glyph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GlyphV4PhotoNormalizerTest {
    @Test
    fun softShadowIsFlattenedWithoutErasingDarkInk() {
        val width = 100
        val height = 100
        val photo = IntArray(width * height) { i -> 150 + (i % width) * 80 / (width - 1) }
        for (y in 20..80) for (x in 25..29) {
            photo[y * width + x] -= 100
        }
        val corrected = GlyphV4PhotoNormalizer.correct(photo, width, height)
        assertEquals(photo.size, corrected.size)
        assertTrue(corrected.all { it in 0..255 })
        assertTrue(abs(corrected[10 * width + 5] - corrected[10 * width + 95]) < 24)
        assertTrue(corrected[50 * width + 27] + 35 < corrected[50 * width + 75])
        val tensor = GlyphV2Preprocessor.fromGrayscale(corrected, width, height, 160)
        assertEquals(3 * 160 * 160, tensor.size)
        assertTrue(tensor.all { it.isFinite() })
    }

    @Test
    fun invertedPhotoIsAlsoHandled() {
        val width = 80
        val height = 80
        val photo = IntArray(width * height) { 15 }
        for (y in 20..60) for (x in 20..25) photo[y * width + x] = 235
        val corrected = GlyphV4PhotoNormalizer.correct(photo, width, height)
        assertTrue(corrected[40 * width + 22] < corrected[40 * width + 70])
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankPhotoStillIsNotARecognisableGlyph() {
        val photo = GlyphV4PhotoNormalizer.correct(IntArray(80 * 80) { 255 }, 80, 80)
        GlyphV2Preprocessor.fromGrayscale(photo, 80, 80, 160)
    }

    @Test(expected = IllegalArgumentException::class)
    fun corruptedPixelBufferIsRejected() {
        GlyphV4PhotoNormalizer.correct(IntArray(6), 80, 80)
    }
}
