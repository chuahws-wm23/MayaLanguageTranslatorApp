package com.chuahws.mayalanguageapp.integration.glyph

import org.junit.Assert.*
import org.junit.Test

/** Learning notes supplement, never validate, visual predictions. */
class GlyphLearningNotesTest {
    @Test fun allTenV4ReadingsHaveEnglishLearningMaterials() {
        val sourceSignNumbers=MayaGlyphRecognizerV4.supportedSigns().map { it.signNumber }.toSet()
        val mapped=GlyphLearningNotes.all()
        assertEquals(10,mapped.size)
        assertEquals(sourceSignNumbers,mapped.map { it.twkmSign }.toSet())
        assertTrue(mapped.all {
            it.explanation.length>=50 && it.historicalContext.length>=40 &&
                it.remember.length>=35 && it.sourceUrl.startsWith("https://")
        })
    }

    @Test fun properNamesAreNotClaimedAsLiteralEnglishWords() {
        for (id in listOf(291,1030)) {
            val entry=GlyphLearningNotes.forSign(id)!!
            assertTrue(entry.category.contains("name", ignoreCase=true))
            assertTrue(entry.remember.contains("name",ignoreCase=true))
        }
    }

    @Test fun educationalNotesAreNotArbitrarilyAssignedToUnknownSigns() {
        assertNull(GlyphLearningNotes.forSign(999999))
    }

    @Test fun englishMatchingOverlapHasNoIllusoryNonzeroBlankScore() {
        val a=ByteArray(512)
        val b=ByteArray(512) { i -> if (i%2==0) 0x7f else 0 }
        assertEquals(0f,GlyphEnglishMeaningCatalogue.overlap(a,b),.00001f)
        assertEquals(1f,GlyphEnglishMeaningCatalogue.overlap(b,b),.00001f)
    }
}
