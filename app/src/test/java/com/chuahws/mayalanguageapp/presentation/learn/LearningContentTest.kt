package com.chuahws.mayalanguageapp.presentation.learn

import org.junit.Assert.*
import org.junit.Test

class LearningContentTest {
    @Test fun numberLevelsRepresentEverySupportedValue() {
        for (value in 0..399) {
            val levels = mayaNumberLevels(value)
            assertEquals(value, levels.sumOf { it.digit * it.place })
            assertTrue(levels.all { it.digit in 0..19 && it.dots in 0..4 && it.bars in 0..3 })
            assertEquals(value, levels.sumOf { (it.bars * 5 + it.dots) * it.place })
        }
        assertEquals(listOf(MayaNumberLevel(0, 1)), mayaNumberLevels(0))
        assertEquals(listOf(MayaNumberLevel(1, 20), MayaNumberLevel(0, 1)), mayaNumberLevels(20))
        assertEquals(listOf(MayaNumberLevel(19, 20), MayaNumberLevel(19, 1)), mayaNumberLevels(399))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsNegativeNumbers() { mayaNumberLevels(-1) }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnsupportedThirdLevel() { mayaNumberLevels(400) }
}
