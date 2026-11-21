package com.chuahws.mayalanguageapp.integration.glyph

import org.junit.Assert.*
import org.junit.Test

class GlyphPanelGuardTest {
    @Test fun repeatedDenseMarkingsAreNotAWholeSingleGlyph() {
        val side = 256
        val gray = IntArray(side * side) { i ->
            val y = i / side
            val x = i % side
            if ((x / 4 + y / 4) % 2 == 0) 20 else 240
        }
        assertTrue(GlyphPanelGuard.looksLikeDensePanel(gray, side, side))
    }

    @Test fun oneLargeOutlineIsNotFlaggedAsDensePanel() {
        val side = 256
        val gray = IntArray(side * side) { 255 }
        for (y in 30..220) for (x in 35..215) {
            if (x in 35..42 || x in 208..215 || y in 30..37 || y in 213..220) {
                gray[y * side + x] = 0
            }
        }
        assertFalse(GlyphPanelGuard.looksLikeDensePanel(gray, side, side))
    }

    @Test fun blankImageIsNotFlaggedAsAnInscription() {
        assertFalse(GlyphPanelGuard.looksLikeDensePanel(IntArray(200 * 200) { 255 }, 200, 200))
    }
}
