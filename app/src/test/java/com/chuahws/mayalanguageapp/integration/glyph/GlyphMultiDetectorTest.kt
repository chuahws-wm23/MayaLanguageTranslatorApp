package com.chuahws.mayalanguageapp.integration.glyph

import org.junit.Assert.*
import org.junit.Test

class GlyphMultiDetectorTest {
    @Test fun findsFourColumnsBySevenRowsInRegularGlyphPanel() {
        val width = 480
        val height = 700
        val gray = IntArray(width * height) { 212 }
        for (y in 0 until height) for (x in 0 until width) {
            if (listOf(120, 240, 360).any { kotlin.math.abs(x - it) < 5 } ||
                listOf(100, 200, 300, 400, 500, 600).any { kotlin.math.abs(y - it) < 5 }) {
                gray[y * width + x] = 28
            }
        }
        val found = GlyphMultiDetector.detectFromGrayscale(gray, width, height)
        assertEquals(28, found.size)
        assertEquals(1, found.first().row)
        assertEquals(1, found.first().column)
        assertEquals(7, found.last().row)
        assertEquals(4, found.last().column)
        assertTrue(found[0].crop.right < found[1].crop.left)
        assertTrue(found[0].crop.bottom < found[4].crop.top)
    }

    @Test fun singleSolidGlyphCannotPretendToBeMultipleBlocks() {
        val width = 480
        val height = 480
        val gray = IntArray(width * height) { 250 }
        for (y in 100..380) for (x in 105..375) {
            if (x in 110..140 || x in 345..375 || y in 110..140 || y in 345..375) {
                gray[y * width + x] = 20
            }
        }
        assertTrue(GlyphMultiDetector.detectFromGrayscale(gray, width, height).isEmpty())
    }

    @Test fun plainEmptyPhotoReturnsNoFakeRegions() {
        assertTrue(
            GlyphMultiDetector.detectFromGrayscale(IntArray(480 * 640) { 240 }, 480, 640)
                .isEmpty()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun corruptedPixelDataFailsSafely() {
        GlyphMultiDetector.detectFromGrayscale(IntArray(120), 120, 120)
    }
}
