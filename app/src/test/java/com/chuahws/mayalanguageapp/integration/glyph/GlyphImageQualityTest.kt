package com.chuahws.mayalanguageapp.integration.glyph

import org.junit.Assert.*
import org.junit.Test

class GlyphImageQualityTest {
    @Test fun blankImageHasUsefulCaptureHints() {
        val hints = GlyphImageQuality.assess(IntArray(100 * 100) { 255 }, 100, 100)
        assertTrue(hints.any { it.contains("Contrast") })
        assertTrue(hints.any { it.contains("focus") })
    }
    @Test fun underexposedImageRequestsBetterLighting() {
        assertTrue(GlyphImageQuality.assess(IntArray(10000) { 30 }, 100, 100)
            .any { it.contains("dark") })
    }
    @Test fun sharpHighContrastPatternDoesNotGetBlurWarning() {
        val gray = IntArray(10000) { i -> if ((i / 100 / 10 + i % 100 / 10) % 2 == 0) 0 else 255 }
        assertTrue(GlyphImageQuality.assess(gray, 100, 100).isEmpty())
    }
    @Test fun sourceResolutionIsNotConfusedWithDownsampledPreviewSize() {
        val gray = IntArray(256 * 32) { i -> if ((i / 256 / 4 + i % 256 / 4) % 2 == 0) 0 else 255 }
        assertFalse(GlyphImageQuality.assess(gray, 256, 32, sourceShortestSide = 300)
            .any { it.contains("small") })
        assertTrue(GlyphImageQuality.assess(gray, 256, 32, sourceShortestSide = 32)
            .any { it.contains("small") })
    }
}
