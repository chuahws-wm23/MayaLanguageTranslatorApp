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
    @Test(expected = IllegalArgumentException::class)
    fun nonFiniteSimilarityIsRejected() {
        GlyphReferenceRanking.bestRows(floatArrayOf(Float.NaN), listOf("A"), booleanArrayOf(false), 1)
    }
}
