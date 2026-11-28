package com.chuahws.mayalanguageapp.integration.glyph

import com.chuahws.mayalanguageapp.domain.service.GlyphDisplayPolicy

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

    /** Resolve only close embedding ties backed by nearly identical stroke geometry. */
    fun resolveCloseTie(
        scores: FloatArray,
        signs: List<String>,
        best: Int,
        runnerUp: Int,
        strokeAgreement: (Int) -> Float,
    ): Int? {
        val leadingScore = scores[best]
        if (!GlyphDisplayPolicy.accepts(leadingScore) || leadingScore - scores[runnerUp] >= SIMILARITY_GAP) return null
        val shapeRanks = scores.indices.asSequence()
            .filter { GlyphDisplayPolicy.accepts(scores[it]) && leadingScore - scores[it] < SIMILARITY_GAP }
            .map { it to strokeAgreement(it) }
            .sortedWith(compareByDescending<Pair<Int, Float>> { it.second }.thenByDescending { scores[it.first] })
            .distinctBy { signs[it.first] }
            .take(2).toList()
        if (shapeRanks.size != 2) return null
        val (row, agreement) = shapeRanks[0]
        return row.takeIf { agreement >= .98f && agreement - shapeRanks[1].second >= .03f }
    }
}

private const val SIMILARITY_GAP = .02f

internal enum class GlyphReferenceDecision {
    UNREADABLE, LOW_SIMILARITY, AMBIGUOUS, STROKES_DIFFER, SYLLABLE_REFERENCE, SHOW_REFERENCE,
}

internal fun List<GlyphEnglishMatch>.referenceDecision(): GlyphReferenceDecision {
    val best = firstOrNull() ?: return GlyphReferenceDecision.UNREADABLE
    if (best.syllableConflict) return GlyphReferenceDecision.SYLLABLE_REFERENCE
    if (!GlyphDisplayPolicy.accepts(best.visualOverlap)) return GlyphReferenceDecision.LOW_SIMILARITY
    val runnerUp = firstOrNull { it.reference.twkmSign != best.reference.twkmSign }
    if (best.identicalNormalizedDrawing) {
        return if (runnerUp?.identicalNormalizedDrawing == true) GlyphReferenceDecision.AMBIGUOUS
        else GlyphReferenceDecision.SHOW_REFERENCE
    }
    if (!best.selectedByStrokes && runnerUp != null && best.visualOverlap - runnerUp.visualOverlap < SIMILARITY_GAP) {
        return GlyphReferenceDecision.AMBIGUOUS
    }
    // Use agreement as a separate check. The displayed percentage is still embedding similarity.
    return if (best.strokeAgreement?.let { it >= .85f } == true) GlyphReferenceDecision.SHOW_REFERENCE
    else GlyphReferenceDecision.STROKES_DIFFER
}

internal fun List<GlyphEnglishMatch>.displayableReference(): GlyphEnglishMatch? =
    firstOrNull().takeIf { referenceDecision() == GlyphReferenceDecision.SHOW_REFERENCE }
