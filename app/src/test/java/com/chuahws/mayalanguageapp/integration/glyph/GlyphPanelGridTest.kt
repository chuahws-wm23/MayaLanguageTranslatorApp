package com.chuahws.mayalanguageapp.integration.glyph

import org.junit.Assert.*
import org.junit.Test

class GlyphPanelGridTest {
    @Test fun assistedGridSupportsMoreThanFortyEightBlocks() {
        val regions = GlyphPanelGrid.regions(GlyphCrop(.1f, .2f, .9f, .8f), 9, 10)
        assertEquals(90, regions.size)
        assertEquals(1, regions.first().row)
        assertEquals(1, regions.first().column)
        assertEquals(9, regions.last().row)
        assertEquals(10, regions.last().column)
        assertTrue(regions.first().crop.left >= .1f)
        assertTrue(regions.last().crop.right <= .9f)
        assertTrue(regions[0].crop.right < regions[1].crop.left)
    }

    @Test fun mappedDetectionRespectsSelectedPanelCrop() {
        val selected = GlyphCrop(.2f, .1f, .8f, .9f)
        val local = GlyphDetectedRegion(GlyphCrop(.25f, .5f, .75f, 1f), 2, 3)
        val mapped = GlyphPanelGrid.toOriginal(selected, local)
        assertEquals(.35f, mapped.crop.left, .0001f)
        assertEquals(.5f, mapped.crop.top, .0001f)
        assertEquals(.65f, mapped.crop.right, .0001f)
        assertEquals(.9f, mapped.crop.bottom, .0001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidGridCountIsRejected() {
        GlyphPanelGrid.regions(GlyphCrop(), 1, 10)
    }
}
