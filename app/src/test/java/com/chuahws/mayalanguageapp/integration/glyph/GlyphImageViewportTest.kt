package com.chuahws.mayalanguageapp.integration.glyph

import org.junit.Assert.*
import org.junit.Test

class GlyphImageViewportTest {
    private val halves = listOf(GlyphCrop(0f, 0f, .5f, 1f), GlyphCrop(.5f, 0f, 1f, 1f))

    @Test fun landscapeTouchMatchesBoxesAndIgnoresTopAndBottomBars() {
        val view = GlyphImageViewport.fit(400, 200, 200f, 300f)
        assertEquals(100f, view.top, .001f)
        assertEquals(0, view.regionAt(50f, 150f, halves))
        assertEquals(1, view.regionAt(150f, 150f, halves))
        assertNull(view.regionAt(50f, 99f, halves))
        assertNull(view.regionAt(50f, 201f, halves))
    }

    @Test fun portraitTouchIgnoresSideBars() {
        val view = GlyphImageViewport.fit(100, 200, 300f, 200f)
        assertEquals(100f, view.left, .001f)
        assertNull(view.regionAt(99f, 100f, halves))
        assertNull(view.regionAt(201f, 100f, halves))
        assertEquals(1, view.regionAt(175f, 100f, halves))
    }

    @Test fun whitespaceBetweenBoxesDoesNotSelectAnUnrelatedRegion() {
        val boxes = listOf(GlyphCrop(.1f, .1f, .3f, .9f), GlyphCrop(.7f, .1f, .9f, .9f))
        val view = GlyphImageViewport.fit(100, 100, 200f, 200f)
        assertNull(view.regionAt(100f, 100f, boxes))
        assertNull(view.regionAt(10f, 10f, boxes))
        assertEquals(1, view.regionAt(160f, 100f, boxes))
    }

    @Test fun overlappingBoxesSelectTheSmallestContainingRegion() {
        val boxes = listOf(GlyphCrop(), GlyphCrop(.4f, .4f, .6f, .6f))
        val view = GlyphImageViewport.fit(100, 100, 100f, 100f)
        assertEquals(1, view.regionAt(50f, 50f, boxes))
        assertEquals(0, view.regionAt(10f, 50f, boxes))
    }

    @Test fun nonfiniteAndOutOfImageTouchesAreIgnored() {
        val view = GlyphImageViewport.fit(100, 100, 100f, 100f)
        assertNull(view.regionAt(Float.NaN, 10f, halves))
        assertNull(view.regionAt(10f, Float.POSITIVE_INFINITY, halves))
        assertNull(view.regionAt(-1f, 10f, halves))
        assertNull(view.regionAt(101f, 10f, halves))
    }
}
