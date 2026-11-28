package com.chuahws.mayalanguageapp.integration.glyph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlyphStrokeAgreementTest {
    @Test fun slightShiftsAndStrokeThicknessChangesStillAgree() {
        val reference = mask { x, y -> x in 25..28 && y in 12..50 || x in 25..48 && y in 37..40 }
        val scanned = mask { x, y -> x in 27..29 && y in 13..51 || x in 27..50 && y in 38..40 }
        val gallery = ByteArray(512) + reference
        assertEquals(1f, GlyphStrokeAgreement.compare(scanned, gallery, 512), .001f)
    }

    @Test fun aSharedOutlineAloneDoesNotLetMissingOrExtraStrokesPass() {
        fun outline(x: Int, y: Int): Boolean =
            x in 5..58 && y in 5..58 && (x in 5..6 || x in 57..58 || y in 5..6 || y in 57..58)
        val fragment = mask(::outline)
        val whole = mask { x, y -> outline(x, y) || x in 16..48 && y in 15..49 && (x % 8 < 2 || y % 8 < 2) }
        val missing = GlyphStrokeAgreement.compare(fragment, whole, 0)
        val extra = GlyphStrokeAgreement.compare(whole, fragment, 0)
        assertTrue(missing < .85f)
        assertEquals(missing, extra, .001f)
    }

    private fun mask(hasInk: (Int, Int) -> Boolean): ByteArray {
        val bytes = ByteArray(512)
        for (y in 0 until 64) for (x in 0 until 64) {
            if (!hasInk(x, y)) continue
            val index = y * 8 + x / 8
            bytes[index] = (bytes[index].toInt() or (0x80 ushr (x % 8))).toByte()
        }
        return bytes
    }
}
