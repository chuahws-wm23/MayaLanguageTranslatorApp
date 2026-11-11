package com.chuahws.mayalanguageapp.presentation.translation

import com.chuahws.mayalanguageapp.domain.model.InputType
import com.chuahws.mayalanguageapp.domain.model.SavedVocabularyItem
import com.chuahws.mayalanguageapp.domain.model.TranslationDirection
import com.chuahws.mayalanguageapp.domain.model.TranslationRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationReplayTest {
    @Test
    fun historyPhotoUsesOriginalWordsAndPhotoMode() {
        val result = TranslationReplay.history(
            TranslationRecord(
                inputType = "IMAGE",
                sourceText = "jaʼ naaj",
                sourceLanguage = "Yucatec Maya",
                targetLanguage = "English",
            ), 1L,
        )
        assertEquals("jaʼ naaj", result.sourceText)
        assertEquals(InputType.IMAGE, result.mode)
        assertEquals(TranslationDirection.MAYA_TO_ENGLISH, result.direction)
    }

    @Test
    fun englishVoiceReplayKeepsEnglishToMayaMode() {
        val result = TranslationReplay.history(
            TranslationRecord(
                inputType = "VOICE",
                sourceText = "thank you",
                sourceLanguage = "English",
            ), 2L,
        )
        assertEquals(InputType.VOICE, result.mode)
        assertEquals(TranslationDirection.ENGLISH_TO_MAYA, result.direction)
    }

    @Test
    fun savedWordReplaysItsExactMayaText() {
        val result = TranslationReplay.saved(
            SavedVocabularyItem(
                mayaText = "Baʼax ka waʼalik?",
                englishText = "hello",
            ), 3L,
        )
        assertEquals("Baʼax ka waʼalik?", result.sourceText)
        assertEquals(InputType.TEXT, result.mode)
        assertEquals(TranslationDirection.MAYA_TO_ENGLISH, result.direction)
    }
}
