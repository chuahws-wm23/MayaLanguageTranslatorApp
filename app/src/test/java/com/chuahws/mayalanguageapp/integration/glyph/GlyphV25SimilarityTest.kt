package com.chuahws.mayalanguageapp.integration.glyph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlyphV25SimilarityTest {
    @Test fun correctlyComparesPredictedSignWithOtherSign() {
        val vector = floatArrayOf(1f, 0f)
        val signs = intArrayOf(32, 44, 32)
        val gallery = floatArrayOf(
            0.8f, 0.2f,
            0.1f, 0.9f,
            0.9f, 0.1f,
        )
        val match = GlyphV25Similarity.score(vector, signs, gallery, 2, 32)
        assertEquals(.9f, match.same, .00001f)
        assertEquals(.1f, match.other, .00001f)
        assertEquals(.8f, match.margin, .00001f)
    }

    @Test fun missingClassifierSignIsRejectedRatherThanAssigned() {
        val vector = floatArrayOf(1f, 0f)
        val signs = intArrayOf(32, 44)
        val gallery = floatArrayOf(.8f, .2f, .1f, .9f)
        var rejected = false
        try {
            GlyphV25Similarity.score(vector, signs, gallery, 2, 291)
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test fun avoidsNaNSimilarity() {
        var rejected = false
        try {
            GlyphV25Similarity.score(
                floatArrayOf(Float.NaN, .5f), intArrayOf(32, 44),
                floatArrayOf(.5f, .5f, .8f, .2f), 2, 32,
            )
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }
}
