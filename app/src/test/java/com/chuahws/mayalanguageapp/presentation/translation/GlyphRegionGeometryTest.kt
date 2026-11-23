package com.chuahws.mayalanguageapp.presentation.translation

import com.chuahws.mayalanguageapp.integration.glyph.GlyphCrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlyphRegionGeometryTest {
    @Test fun movingRegionClampsToPhotoWithoutChangingItsSize() {
        val original = GlyphCrop(.30f, .30f, .60f, .60f)
        val shifted = GlyphRegionGeometry.move(original, .80f, -.80f)
        assertEquals(.70f, shifted.left, .0001f)
        assertEquals(1f, shifted.right, .0001f)
        assertEquals(0f, shifted.top, .0001f)
        assertEquals(.30f, shifted.bottom, .0001f)
    }

    @Test fun resizingCannotCreateAnInvalidOrOutOfImageBox() {
        val original = GlyphCrop(.30f, .30f, .60f, .60f)
        val small = GlyphRegionGeometry.resize(original, -1f, -1f)
        assertEquals(.36f, small.right, .0001f)
        assertEquals(.36f, small.bottom, .0001f)
        val large = GlyphRegionGeometry.resize(original, 2f, 2f)
        assertEquals(1f, large.right, .0001f)
        assertEquals(1f, large.bottom, .0001f)
        assertTrue(large.right > large.left && large.bottom > large.top)
    }

    @Test fun defaultNewRegionIsInsideThePhoto() {
        val boxes = mutableListOf<GlyphCrop>()
        repeat(10) {
            val newBox = GlyphRegionGeometry.newRegion(boxes)
            assertTrue(newBox.left >= 0f && newBox.top >= 0f)
            assertTrue(newBox.right <= 1f && newBox.bottom <= 1f)
            boxes.add(newBox)
        }
    }
}
