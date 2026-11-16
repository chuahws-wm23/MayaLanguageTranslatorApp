package com.chuahws.mayalanguageapp.domain.model

data class SavedVocabularyItem(
    val savedId: String = "",
    val userId: String = "",
    val entryId: String = "",
    val mayaText: String = "",
    val englishText: String = "",
    val pronunciation: String? = null,
    val partOfSpeech: String? = null,
    val savedAtMillis: Long = 0L,
    /** Glyph favourites coexist with regular vocabulary in the same Saved collection. */
    val glyphScanRecord: Boolean = false,
    val glyphSourceScanId: String = "",
    val glyphImageBase64: String = "",
    val glyphCandidates: List<GlyphScanCandidate> = emptyList(),
    val glyphScanKind: String = "",
    val glyphRegionCount: Int = 1,
)
