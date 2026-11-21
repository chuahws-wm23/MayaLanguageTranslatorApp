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
