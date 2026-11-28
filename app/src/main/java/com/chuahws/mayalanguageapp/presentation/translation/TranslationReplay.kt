package com.chuahws.mayalanguageapp.presentation.translation

import com.chuahws.mayalanguageapp.domain.model.InputType
import com.chuahws.mayalanguageapp.domain.model.SavedVocabularyItem
import com.chuahws.mayalanguageapp.domain.model.TranslationDirection
import com.chuahws.mayalanguageapp.domain.model.TranslationRecord

/**
 * Transfers the exact stored source text to its original translation mode.
 * History stores text but not camera files or microphone recordings.
 */
data class TranslationReplay(
    val requestId: Long,
    val sourceText: String,
    val direction: TranslationDirection,
    val mode: InputType,
) {
    companion object {
        fun history(record: TranslationRecord, id: Long): TranslationReplay {
            val input = when (record.inputType.uppercase()) {
                InputType.IMAGE.name -> InputType.IMAGE
                InputType.VOICE.name -> InputType.VOICE
                else -> InputType.TEXT
            }
            val direction = if (
                record.sourceLanguage.contains("Maya", ignoreCase = true)
            ) {
                TranslationDirection.MAYA_TO_ENGLISH
            } else {
                TranslationDirection.ENGLISH_TO_MAYA
            }
            return TranslationReplay(
                requestId = id,
                sourceText = record.sourceText,
                direction = direction,
                mode = input,
            )
        }

        fun saved(item: SavedVocabularyItem, id: Long): TranslationReplay =
            TranslationReplay(
                requestId = id,
                sourceText = item.mayaText,
                direction = TranslationDirection.MAYA_TO_ENGLISH,
                mode = InputType.TEXT,
            )
    }
}
