package com.chuahws.mayalanguageapp.integration.glyph

import org.junit.Assert.*
import org.junit.Test

/** Pure numerical smoke tests. These do not establish glyph recognition accuracy. */
class GlyphVisualCatalogueTest {
    @Test fun identicalBinaryGlyphIsPerfectVisualOverlap() {
        val glyph = ByteArray(64 * 64 / 8)
        for (y in 18..38) for (x in 25..40) {
            val bit = y * 64 + x
            glyph[bit ushr 3] = (glyph[bit ushr 3].toInt() or
                (0x80 ushr (bit and 7))).toByte()
        }
        assertEquals(1f,GlyphVisualCatalogue.binaryCosine(glyph,glyph),.00001f)
    }

    @Test fun differentGlyphsWithNoSharedInkHaveNoOverlap() {
        val left = ByteArray(512)
        val right = ByteArray(512)
        for (y in 10..30) {
            left[y*8+1] = 0x7f
            right[y*8+6] = 0x7f
        }
        assertEquals(0f,GlyphVisualCatalogue.binaryCosine(left,right),.00001f)
    }

    @Test fun missingInkDoesNotProduceFalseConfidence() {
        val blank=ByteArray(512)
        val image=ByteArray(512) { 0x7f }
        assertEquals(0f,GlyphVisualCatalogue.binaryCosine(blank,image),0f)
    }

    @Test fun binaryOverlapsAreSymmetricAndWithinValidRange() {
        val a=ByteArray(512) { i -> if (i%2==0) 0x7f else 0 }
        val b=ByteArray(512) { i -> if (i%3==0) 0x7f else 0 }
        val ab=GlyphVisualCatalogue.binaryCosine(a,b)
        val ba=GlyphVisualCatalogue.binaryCosine(b,a)
        assertTrue(ab in 0f..1f)
        assertEquals(ab,ba,.000001f)
    }
}
