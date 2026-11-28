package com.chuahws.mayalanguageapp.integration.glyph

/**
 * Local explicit-user export. Feedback remains UNVERIFIED until a qualified
 * reviewer checks source provenance and the drawing's actual sign identity.
 * No image, Firebase account details, or user identifier is transmitted.
 */
object GlyphV4CorrectionReport {
    private val HEADER = listOf(
        "model_version", "predicted_twkm_sign", "user_selected_twkm_sign",
        "source_description", "user_notes", "status"
    )

    fun csv(
        prediction: GlyphV4Result,
        selected: String,
        source: String,
        notes: String,
    ): String {
        require(selected.isNotBlank()) { "Choose a correction or Unknown." }
        // Prevent unlisted/mistyped numbers from silently entering a reviewed label queue.
        // Legacy UNKNOWN remains readable alongside the current UI's UNKNOWN_OR_OTHER.
        require(selected == "UNKNOWN" || selected == "UNKNOWN_OR_OTHER" ||
            MayaGlyphRecognizerV4.supportedSigns().any { it.signNumber.toString() == selected }) {
            "Correction must be a supported TWKM sign or Unknown / other."
        }
        val row = listOf(
            prediction.modelVersion, prediction.signNumber.toString(),
            selected, source, notes, "UNVERIFIED_USER_FEEDBACK",
        )
        return HEADER.joinToString(",") { quote(it) } + "\n" +
            row.joinToString(",") { quote(it) } + "\n"
    }

    private fun quote(input: String): String =
        "\"" + input.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\""
}
