package com.chuahws.mayalanguageapp.integration.glyph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MayaGlyphRecognizerV4Test {
    @Test
    fun mapsTenClassesInTrainedOrder() {
        val logits = FloatArray(10) { -1f }
        logits[7] = 5f
        val result = MayaGlyphRecognizerV4.interpret(logits)
        assertEquals(122, result.signNumber)
        assertEquals("K'AK'", result.reading)
        assertEquals("Fire", result.englishGloss)
    }

    @Test
    fun mapsFinalClassCorrectly() {
        val logits = FloatArray(10) { -100f }
        logits[9] = 1f
        assertEquals(1030, MayaGlyphRecognizerV4.interpret(logits).signNumber)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesWrongNumberOfClasses() {
        MayaGlyphRecognizerV4.interpret(floatArrayOf(1f, 2f))
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesInvalidLogit() {
        MayaGlyphRecognizerV4.interpret(FloatArray(10) { if (it == 0) Float.NaN else 0f })
    }

    @Test
    fun v4Uses160PixelInputWhileV2Uses224() {
        val input = IntArray(80 * 80) { 255 }
        for (y in 15..65) for (x in 20..60) {
            if (x <= 24 || x >= 55 || y <= 19 || y >= 60) input[y * 80 + x] = 0
        }
        val tensor = GlyphV2Preprocessor.fromGrayscale(
            input, 80, 80, MayaGlyphRecognizerV4.INPUT_SIDE,
        )
        assertEquals(3 * 160 * 160, tensor.size)
        assertTrue(tensor.all { it.isFinite() })
        assertTrue(tensor.maxOrNull()!! > tensor.minOrNull()!!)
        assertEquals(3 * 224 * 224, GlyphV2Preprocessor.fromGrayscale(input, 80, 80).size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesUnexpectedImageDimensions() {
        GlyphV2Preprocessor.fromGrayscale(IntArray(80 * 80) { 255 }, 80, 80, 192)
    }
}
