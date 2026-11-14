package com.chuahws.mayalanguageapp.integration.glyph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlyphV3ScreeningTest {
    private val screening = GlyphV3Screening.forTesting(
        intArrayOf(0, 1),
        arrayOf(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)),
    )

    @Test
    fun identicalReferenceForPredictedClassIsAccepted() {
        val v = screening.screen(floatArrayOf(1f, 0f), 0)
        assertTrue(v.accepted)
        assertEquals(1f, v.nearestOwnSignSimilarity, .00001f)
    }

    @Test
    fun weakSimilarityIsRejectedEvenWhenClassifierPicksKnownSign() {
        val v = screening.screen(floatArrayOf(.5f, .5f), 0)
        assertFalse(v.accepted)
    }

    @Test
    fun ambiguousSignIsRejectedEvenWithHighOwnSimilarity() {
        val v = screening.screen(floatArrayOf(.95f, .94f), 0)
        assertFalse(v.accepted)
        assertTrue(v.nearestOtherSignMargin < GlyphV3Screening.MARGIN_THRESHOLD)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidClassIsRejected() {
        screening.screen(floatArrayOf(1f, 0f), 8)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonFiniteFeatureVectorIsRejected() {
        screening.screen(floatArrayOf(Float.NaN, 0f), 0)
    }
}
