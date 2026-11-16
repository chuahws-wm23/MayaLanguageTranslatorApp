package com.chuahws.mayalanguageapp.domain.model

/**
 * A visual candidate copied from the TWKM sign catalogue.
 *
 * [visualOverlap] is an uncalibrated shape score, NOT the chance that
 * the image has this Maya reading. Never present these as verified facts.
 * All properties have defaults so Firestore can deserialize saved records.
 */
data class GlyphScanCandidate(
    val twkmSign: Int = 0,
    val reading: String = "",
    val englishGloss: String = "",
    val visualOverlap: Double = 0.0,
    val scholarlyLevel: Int = 0,
    /** Detected panel region, if this was an experimental multi-glyph scan. */
    val regionIndex: Int = 0,
)

/** A compact, user-triggered image-search event prepared for account History. */
data class GlyphScanCapture(
    /** JPEG preview of the selected glyph (max 120KiB Base64). Not full resolution. */
    val previewBase64: String = "",
    val candidates: List<GlyphScanCandidate> = emptyList(),
    val scanKind: String = "WORD_SIGN",
    val regionCount: Int = 1,
) {
    init {
        require(previewBase64.length <= 160_000)
        require(candidates.size <= 10)
        require(regionCount in 1..64)
    }
}

object GlyphScanRecords {
    fun summary(capture: GlyphScanCapture): String {
        val first = capture.candidates.firstOrNull()
        return if (first != null)
            "Possible: ${first.reading} — ${first.englishGloss} (unverified)"
        else "No verified Maya reading identified"
    }

    fun makeHistory(
        userId: String,
        id: String,
        millis: Long,
        capture: GlyphScanCapture,
    ): TranslationRecord {
        require(userId.isNotBlank() && id.isNotBlank())
        return TranslationRecord(
            translationId = id,
            userId = userId,
            entryId = null,
            sourceText = when (capture.scanKind) {
                "COMPONENTS" -> "Maya glyph image · ${capture.regionCount} detected components"
                "BLOCKS" -> "Maya inscription · ${capture.regionCount} tentative blocks"
                "RESEARCH" -> "Maya glyph image · experimental recognition"
                else -> "Maya glyph image · visual reference search"
            },
            translatedText = summary(capture),
            sourceLanguage = "Classic Maya glyph (unverified)",
            targetLanguage = "Possible English gloss (unverified)",
            inputType = InputType.IMAGE.name,
            createdAtMillis = millis,
            isGlyphScan = true,
            glyphImageBase64 = capture.previewBase64,
            glyphCandidates = capture.candidates,
            glyphScanKind = capture.scanKind,
            glyphRegionCount = capture.regionCount,
        )
    }

    fun makeFavorite(history: TranslationRecord, millis: Long): SavedVocabularyItem {
        require(history.isGlyphScan)
        require(history.userId.isNotBlank() && history.translationId.isNotBlank())
        return SavedVocabularyItem(
            savedId = favoriteId(history.userId, history.translationId),
            userId = history.userId,
            entryId = "glyph-scan-${history.translationId}",
            mayaText = history.glyphCandidates.firstOrNull()?.reading ?: "Unidentified glyph",
            englishText = history.glyphCandidates.firstOrNull()?.englishGloss
                ?: "No verified English meaning",
            pronunciation = null,
            partOfSpeech = "Glyph scan",
            savedAtMillis = millis,
            isGlyphScan = true,
            glyphSourceScanId = history.translationId,
            glyphImageBase64 = history.glyphImageBase64,
            glyphCandidates = history.glyphCandidates,
            glyphScanKind = history.glyphScanKind,
            glyphRegionCount = history.glyphRegionCount,
        )
    }

    fun favoriteId(userId: String, scanId: String): String = "${userId}_glyph_${scanId}"
}
