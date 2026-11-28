package com.chuahws.mayalanguageapp.integration.glyph

import org.junit.Assert.*
import org.junit.Test

class GlyphReferenceRankingTest {
    @Test fun retrievesBestArtworkAndDistinctSignIdentities() {
        val rows = GlyphReferenceRanking.bestRows(floatArrayOf(.5f, .9f, .8f, .7f),
            listOf("A", "A", "B", "C"), BooleanArray(4), 5)
        assertEquals(listOf(1, 2, 3), rows)
    }
    @Test fun uncertainQueryStillProvidesRequestedNearestAlternatives() {
        val rows = GlyphReferenceRanking.bestRows(floatArrayOf(.11f, .10f, .09f, .08f, .07f, .06f),
            listOf("A", "B", "C", "D", "E", "F"), BooleanArray(6), 5)
        assertEquals(listOf(0, 1, 2, 3, 4), rows)
    }
    @Test fun identicalDrawingWinsOverRoundingOfEmbeddingSimilarity() {
        val rows = GlyphReferenceRanking.bestRows(floatArrayOf(.999999f, 1f),
            listOf("A", "B"), booleanArrayOf(true, false), 2)
        assertEquals(listOf(0, 1), rows)
    }
    @Test fun emptyGalleryHasNoFabricatedResult() {
        assertTrue(GlyphReferenceRanking.bestRows(floatArrayOf(), emptyList(), booleanArrayOf(), 5).isEmpty())
    }
    @Test fun aHighSimilarityDoesNotOverrideMismatchedStrokes() {
        val best = GlyphEnglishMatch(reference(10), .94f, strokeAgreement = .72f)
        val runnerUp = GlyphEnglishMatch(reference(11), .89f)
        val matches = listOf(best, runnerUp)
        assertEquals(GlyphReferenceDecision.STROKES_DIFFER, matches.referenceDecision())
        assertNull(matches.displayableReference())
        val aligned = best.copy(strokeAgreement = .85f)
        assertEquals(aligned, listOf(aligned, runnerUp).displayableReference())
        assertNull(listOf(aligned, runnerUp.copy(visualOverlap = .93f)).displayableReference())
        assertNull(listOf(best.copy(strokeAgreement = null), runnerUp).displayableReference())
    }

    @Test fun identicalDrawingIsAcceptedOnlyIfItPointsToOneSign() {
        val exact = GlyphEnglishMatch(reference(10), 1f, identicalNormalizedDrawing = true)
        assertEquals(exact, listOf(exact, GlyphEnglishMatch(reference(11), .99f)).displayableReference())
        assertNull(listOf(exact, exact.copy(reference = reference(11))).displayableReference())
    }

    @Test fun aCloseTieResolvesOnlyWithADistinctNearExactStrokeMatch() {
        val scores = floatArrayOf(.916f, .907f, .906f, .905f, .88f)
        val identities = listOf("WITZ", "TI", "KAK", "KAK", "EK")
        val strokes = floatArrayOf(.957f, .949f, .992f, .999f, 1f)
        val chosen = GlyphReferenceRanking.resolveCloseTie(scores, identities, 0, 1) { strokes[it] }
        assertEquals(3, chosen)
        strokes[0] = .985f
        assertNull(GlyphReferenceRanking.resolveCloseTie(scores, identities, 0, 1) { strokes[it] })
        scores[0] = .95f
        assertNull(GlyphReferenceRanking.resolveCloseTie(scores, identities, 0, 1) { strokes[it] })
    }

    @Test fun aResolvedTieUsesItsOwnValidSimilarityAndStrokeEvidence() {
        val fire = GlyphEnglishMatch(reference(122), .906f, strokeAgreement = .992f, selectedByStrokes = true)
        val waterlily = GlyphEnglishMatch(reference(1031), .916f)
        assertEquals(fire, listOf(fire, waterlily).displayableReference())
        assertNull(listOf(fire.copy(visualOverlap = .85f), waterlily).displayableReference())
        assertNull(listOf(fire.copy(strokeAgreement = .7f), waterlily).displayableReference())
    }

    private fun reference(sign: Int) = GlyphEnglishReference(
        offset = 0, twkmSign = sign, reading = "Reading", english = "Meaning", kind = "Word sign",
        scholarlyLevel = 1, graph = "${sign}st",
    )

    @Test(expected = IllegalArgumentException::class)
    fun nonFiniteSimilarityIsRejected() {
        GlyphReferenceRanking.bestRows(floatArrayOf(Float.NaN), listOf("A"), booleanArrayOf(false), 1)
    }
}
