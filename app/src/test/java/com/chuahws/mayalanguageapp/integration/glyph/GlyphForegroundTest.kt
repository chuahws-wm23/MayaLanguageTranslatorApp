package com.chuahws.mayalanguageapp.integration.glyph

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlyphForegroundTest {
    @Test fun filledWhiteFigureOnBlackUsesItsInteriorLinesInsteadOfTheBackground() {
        val width = 40
        val gray = IntArray(width * width)
        for (y in 4 until 36) for (x in 4 until 36) gray[y * width + x] = 255
        for (y in 12 until 28) gray[y * width + 20] = 0

        val mask = GlyphVisualCatalogue.foregroundMask(gray, width, width)
        assertNotNull(mask)
        assertFalse(mask!![0])
        assertFalse(mask[10 * width + 10])
        assertTrue(mask[20 * width + 20])
    }

    @Test fun lightStrokesOnDarkAndDarkStrokesOnLightBothRemainForeground() {
        val width = 40
        val inverse = IntArray(width * width)
        for (x in 8 until 32) inverse[20 * width + x] = 255
        val inverseMask = GlyphVisualCatalogue.foregroundMask(inverse, width, width)
        assertNotNull(inverseMask)
        assertTrue(inverseMask!![20 * width + 20])
        assertFalse(inverseMask[0])

        val normal = IntArray(width * width) { 255 }
        for (x in 8 until 32) normal[20 * width + x] = 0
        val normalMask = GlyphVisualCatalogue.foregroundMask(normal, width, width)
        assertNotNull(normalMask)
        assertTrue(normalMask!![20 * width + 20])
        assertFalse(normalMask[0])
    }

    @Test fun weakOrAmbiguousDrawingsDoNotProduceTranslations() {
        assertFalse(GlyphVisualCatalogue.hasClearVisualMatch(.55f, .10f))
        assertFalse(GlyphVisualCatalogue.hasClearVisualMatch(.64f, .61f))
        assertTrue(GlyphVisualCatalogue.hasClearVisualMatch(.72f, .48f))
    }
}
