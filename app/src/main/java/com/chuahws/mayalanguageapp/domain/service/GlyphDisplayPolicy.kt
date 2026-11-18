package com.chuahws.mayalanguageapp.domain.service

import com.chuahws.mayalanguageapp.domain.model.GlyphScanCandidate
import java.util.Locale

/** User-selected display filter, not a calibrated identification confidence. */
object GlyphDisplayPolicy {
    const val MIN_SIMILARITY = 0.85f
    fun accepts(score: Float): Boolean = score.isFinite() && score > MIN_SIMILARITY && score <= 1f
    fun percent(score: Float): String = String.format(Locale.ROOT, "%.2f%%", score * 100)
    fun visible(candidates: List<GlyphScanCandidate>): List<GlyphScanCandidate> = candidates
        .groupBy { it.regionIndex }.values.mapNotNull { group ->
            group.maxByOrNull { it.visualOverlap }?.takeIf { accepts(it.visualOverlap.toFloat()) }
        }
}
