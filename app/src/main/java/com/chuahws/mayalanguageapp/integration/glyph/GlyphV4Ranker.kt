package com.chuahws.mayalanguageapp.integration.glyph

import kotlin.math.exp

/**
 * Exact ten-class softmax ranking for the existing v4 ONNX logits.
 * Percentages are RELATIVE MODEL SCORES, not measured real-world confidence.
 * No class is fabricated or matched using a stored-image lookup.
 */
internal object GlyphV4Ranker {
    data class Ranked(val index: Int, val relativeScorePercent: Float)

    fun rank(logits: FloatArray, count: Int = 3): List<Ranked> {
        require(logits.size == 10 && logits.all { it.isFinite() }) {
            "Expected 10 finite trained-model logits."
        }
        require(count in 1..10)
        val max = logits.maxOrNull()!!.toDouble()
        val weights = DoubleArray(logits.size) {
            exp(logits[it].toDouble() - max)
        }
        val sum = weights.sum()
        require(sum.isFinite() && sum > 0)
        return logits.indices.sortedWith(
            compareByDescending<Int> { logits[it] }.thenBy { it }
        ).take(count).map { index ->
            Ranked(index, (100.0 * weights[index] / sum).toFloat())
        }
    }
}
