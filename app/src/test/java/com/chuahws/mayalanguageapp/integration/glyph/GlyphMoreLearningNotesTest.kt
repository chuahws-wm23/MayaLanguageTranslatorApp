package com.chuahws.mayalanguageapp.integration.glyph

import org.junit.Assert.*
import org.junit.Test

class GlyphMoreLearningNotesTest {
    @Test fun additionalWordMeaningsHaveUniqueIdsAndReliableStudyNotes() {
        val extras=GlyphMoreLearningNotes.all()
        assertEquals(10,extras.size)
        assertEquals(10,extras.map { it.twkmSign }.toSet().size)
        assertTrue(extras.none { GlyphLearningNotes.forSign(it.twkmSign) != null })
        assertTrue(extras.all {
            it.reading.isNotBlank() && it.english.isNotBlank() &&
                it.explanation.length>50 && it.historicalContext.length>45 &&
                it.remember.length>35 && it.sourceUrl.startsWith("https://")
        })
    }

    @Test fun commonNewMeaningsUseTheirSourceTwkmSignIdentity() {
        assertEquals("AJAW",GlyphMoreLearningNotes.forSign(168)?.reading)
        assertEquals("Leaf",GlyphMoreLearningNotes.forSign(115)?.english)
        assertNull(GlyphMoreLearningNotes.forSign(2147483647))
    }
}
