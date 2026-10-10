package com.chuahws.mayalanguageapp.domain.service

import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizerTest {
    @Test
    fun normalize_preservesMeaningfulMarksAndNormalizesSpacing() {
        assertEquals(
            "ba'ax k'iin",
            TextNormalizer.normalize("  BA'AX   K'IIN  ")
        )
    }
}
