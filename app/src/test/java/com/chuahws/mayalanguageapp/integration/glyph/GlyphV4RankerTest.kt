package com.chuahws.mayalanguageapp.integration.glyph

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class GlyphV4RankerTest {
    @Test fun ranksTopThreeClassesAndNormalizesAllTen() {
        val logits = FloatArray(10) { 0f }
        logits[4] = 4f
        logits[2] = 3f
        logits[7] = 2f
        val result = GlyphV4Ranker.rank(logits)
        assertEquals(listOf(4, 2, 7), result.map { it.index })
        assertTrue(result[0].relativeScorePercent > result[1].relativeScorePercent)
        assertTrue(result[1].relativeScorePercent > result[2].relativeScorePercent)
        val all = GlyphV4Ranker.rank(logits, 10)
        assertTrue(abs(100f - all.sumOf { it.relativeScorePercent.toDouble() }) < .001)
    }

    @Test fun stableUnderHugeLogitsAndConstantOffsets() {
        val logits = FloatArray(10) { 10000f }
        logits[3] = 10005f
        val baseline = GlyphV4Ranker.rank(logits)
        assertEquals(3, baseline[0].index)
        assertTrue(baseline.all { it.relativeScorePercent.isFinite() })
        val shifted = GlyphV4Ranker.rank(FloatArray(10) { logits[it] - 10000f })
        assertEquals(baseline.map { it.index }, shifted.map { it.index })
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonFiniteLogitRejected() {
        GlyphV4Ranker.rank(FloatArray(10) { if (it == 8) Float.POSITIVE_INFINITY else 0f })
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidClassCountRejected() {
        GlyphV4Ranker.rank(FloatArray(8))
    }
}
