package com.chuahws.mayalanguageapp.domain.model

data class TranslationRecord(
    val translationId: String = "",
    val userId: String = "",
    val entryId: String? = null,
    val sourceText: String = "",
    val translatedText: String = "",
    val sourceLanguage: String = "",
    val targetLanguage: String = "",
    val inputType: String = InputType.TEXT.name,
    val createdAtMillis: Long = 0L,
    /** True only for a deliberately recorded glyph scan; old photo OCR records remain unchanged. */
    val glyphScanRecord: Boolean = false,
    val glyphImageBase64: String = "",
    val glyphCandidates: List<GlyphScanCandidate> = emptyList(),
    val glyphScanKind: String = "",
    val glyphRegionCount: Int = 1,
)
