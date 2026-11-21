package com.chuahws.mayalanguageapp.domain.service
import com.chuahws.mayalanguageapp.domain.model.GlyphScanCandidate
import org.junit.Assert.*
import org.junit.Test
class GlyphDisplayPolicyTest {
    @Test fun strictBoundaryAndInvalidScores() {
        for (score in listOf(0f, .84f, .85f, Float.NaN, Float.POSITIVE_INFINITY, 1.1f)) assertFalse(GlyphDisplayPolicy.accepts(score))
        assertTrue(GlyphDisplayPolicy.accepts(.8501f)); assertTrue(GlyphDisplayPolicy.accepts(1f))
        assertEquals("95.00%", GlyphDisplayPolicy.percent(.95f))
    }
    @Test fun strongestEligibleResultPerRegionOnly() {
        val candidates = listOf(GlyphScanCandidate(twkmSign=1, visualOverlap=.84,regionIndex=1),
            GlyphScanCandidate(twkmSign=2, visualOverlap=.95,regionIndex=2),
            GlyphScanCandidate(twkmSign=3, visualOverlap=.90,regionIndex=2))
        assertEquals(listOf(2), GlyphDisplayPolicy.visible(candidates).map { it.twkmSign })
    }
}
