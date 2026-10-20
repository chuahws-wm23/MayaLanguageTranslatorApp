package com.chuahws.mayalanguageapp.domain.model

data class TranslationRecord(
    val translationId: String = "",
    val userId: String = "",
    val entryId: String? = null,
    val sourceText: String = "",
    val translatedText: String = "",
    val sourceLanguage: String = "",
    val targetLanguage: String = "",
    val inputType: String = InputType.TEXT.name,
    val createdAtMillis: Long = 0L,
)
