package com.chuahws.mayalanguageapp.integration.glyph

import android.graphics.Bitmap

internal data class GlyphRegionComparison(val matches: List<GlyphEnglishMatch>, val note: String)

/** Same guarded comparison is used by the panel UI and its device diagnostic. */
internal object GlyphRegionMatcher {
    fun compare(image: Bitmap, words: GlyphEnglishMeaningCatalogue, sounds: GlyphVisualCatalogue): GlyphRegionComparison {
        val separated = GlyphWhitespaceDetector.detect(image).size >= 2
        val hints = GlyphImageQuality.hints(image)
        val matches = if (separated) emptyList() else words.search(image, sounds, 5)
        val note = when {
            separated -> "This region contains separated shapes. Inspect and isolate one complete sign before comparing."
            matches.isEmpty() -> "No usable reference comparison. Inspect the crop and image quality."
            else -> "Unconfirmed visual alternatives. A block may contain several signs."
        } + if (hints.isNotEmpty()) " " + hints.joinToString(" ") else ""
        return GlyphRegionComparison(matches, note)
    }
}
