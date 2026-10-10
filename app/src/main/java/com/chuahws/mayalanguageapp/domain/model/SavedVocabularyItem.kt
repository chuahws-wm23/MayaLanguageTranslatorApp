package com.chuahws.mayalanguageapp.domain.model

data class SavedVocabularyItem(
    val savedId: String = "",
    val userId: String = "",
    val entryId: String = "",
    val mayaText: String = "",
    val englishText: String = "",
    val pronunciation: String? = null,
    val partOfSpeech: String? = null,
    val savedAtMillis: Long = 0L,
)
