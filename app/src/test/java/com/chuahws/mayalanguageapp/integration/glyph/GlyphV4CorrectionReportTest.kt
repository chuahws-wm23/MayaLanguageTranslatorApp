package com.chuahws.mayalanguageapp.integration.glyph

import org.junit.Assert.*
import org.junit.Test

class GlyphV4CorrectionReportTest {
    @Test fun exportKeepsUserCorrectionExplicitlyUnverified() {
        val p = MayaGlyphRecognizerV4.interpret(FloatArray(10) { if (it == 4) 5f else 0f })
        val csv = GlyphV4CorrectionReport.csv(p, "UNKNOWN", "test photo, camera", "line, \"thin\"")
        assertTrue(csv.contains("UNVERIFIED_USER_FEEDBACK"))
        assertTrue(csv.contains("\"44\""))
        assertTrue(csv.contains("\"UNKNOWN\""))
        assertTrue(csv.contains("\"line, \"\"thin\"\"\""))
    }
    @Test(expected = IllegalArgumentException::class)
    fun emptyReviewSelectionIsRejected() {
        GlyphV4CorrectionReport.csv(GlyphV4Result(32,"K'UH","God"), " ", "", "")
    }
}
