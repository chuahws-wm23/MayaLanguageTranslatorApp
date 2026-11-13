package com.chuahws.mayalanguageapp.integration.glyph

import org.junit.Assert.*
import org.junit.Test

class GlyphCropTest {
    @Test fun fullImageKeepsAllPixels() {
        assertArrayEquals(intArrayOf(0, 0, 709, 1536), GlyphCrop().pixels(709, 1536))
    }
    @Test fun reversedDragProducesSameRegion() {
        assertEquals(GlyphCrop.between(.1f, .2f, .8f, .9f), GlyphCrop.between(.8f, .9f, .1f, .2f))
    }
    @Test fun coordinatesOutsidePreviewAreClamped() {
        assertEquals(GlyphCrop(), GlyphCrop.between(-2f, -2f, 3f, 3f))
    }
    @Test fun tapIsNotATinyCrop() {
        assertNull(GlyphCrop.between(.5f, .5f, .501f, .501f))
    }
    @Test fun cropMapsToImageNotScreenPixels() {
        assertArrayEquals(intArrayOf(50, 100, 100, 200), GlyphCrop(.25f, .25f, .75f, .75f).pixels(200, 400))
    }
    @Test(expected = IllegalArgumentException::class) fun invalidCropRejected() {
        GlyphCrop(.8f, 0f, .2f, 1f)
    }
    @Test fun nonFiniteGestureIsIgnored() {
        assertNull(GlyphCrop.between(Float.NaN, 0f, 1f, 1f))
    }
}
