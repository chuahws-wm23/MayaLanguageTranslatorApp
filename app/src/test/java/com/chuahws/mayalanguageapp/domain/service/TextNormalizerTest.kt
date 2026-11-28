package com.chuahws.mayalanguageapp.domain.service

import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizerTest {
    @Test
    fun normalize_preservesMeaningfulMarksAndNormalizesSpacing() {
        assertEquals(
            "baʼax kʼiin",
            TextNormalizer.normalize("  BA'AX   K'IIN  ")
        )
    }
    @Test
    fun punctuationBetweenOcrWords_isTreatedAsASeparator() {
        assertEquals(
            "hello world",
            TextNormalizer.normalize("Hello,World!")
        )
    }

}
