package com.chuahws.mayalanguageapp.integration.glyph

import android.graphics.Bitmap

internal data class GlyphRegionComparison(val matches: List<GlyphEnglishMatch>, val note: String)

/** Same guarded comparison is used by the panel UI and its device diagnostic. */
internal object GlyphRegionMatcher {
    fun compare(image: Bitmap, words: GlyphEnglishMeaningCatalogue, sounds: GlyphVisualCatalogue): GlyphRegionComparison {
        val separated = GlyphWhitespaceDetector.detect(image).size >= 2
        val hints = GlyphImageQuality.hints(image)
        val matches = if (separated) emptyList() else words.searchForScan(image, sounds)
        val note = when {
            separated -> "This region still contains separated shapes. Try a clearer photo with one complete glyph per region."
            matches.isEmpty() -> "No readable reference comparison. Try a clearer photo."
            else -> "Unconfirmed visual alternatives. A block may contain several signs."
        } + if (hints.isNotEmpty()) " " + hints.joinToString(" ") else ""
        return GlyphRegionComparison(matches, note)
    }
}
