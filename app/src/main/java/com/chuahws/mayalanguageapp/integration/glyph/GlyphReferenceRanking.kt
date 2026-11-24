package com.chuahws.mayalanguageapp.integration.glyph

/** Rank reference ROWS, preserving the best artwork for each catalogue identity. */
internal object GlyphReferenceRanking {
    fun bestRows(
        scores: FloatArray,
        identities: List<String>,
        identical: BooleanArray,
        limit: Int,
    ): List<Int> {
        require(limit in 1..10 && scores.size == identities.size && scores.size == identical.size)
        require(scores.all { it.isFinite() && it in -1f..1f })
        val order = compareByDescending<Int> { identical[it] }
            .thenByDescending { scores[it] }
            .thenBy { identities[it] }
            .thenBy { it }
        return scores.indices.sortedWith(order).distinctBy { identities[it] }.take(limit)
    }
}

/** The leading reference needs separation from the next distinct sign. */
internal fun List<GlyphEnglishMatch>.displayableReference(): GlyphEnglishMatch? {
    val best = firstOrNull() ?: return null
    if (!com.chuahws.mayalanguageapp.domain.service.GlyphDisplayPolicy.accepts(best.visualOverlap)) return null
    if (best.identicalNormalizedDrawing) return best
    val runnerUp = firstOrNull { it.reference.twkmSign != best.reference.twkmSign }
    return best.takeIf { runnerUp == null || best.visualOverlap - runnerUp.visualOverlap >= .02f }
}
