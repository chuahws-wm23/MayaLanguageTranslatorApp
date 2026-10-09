package com.chuahws.mayalanguageapp.domain.model

data class DictionaryEntry(
    val entryId: String = "",
    val mayaText: String = "",
    val mayaNormalized: String = "",
    val englishText: String = "",
    val englishNormalized: String = "",
    val partOfSpeech: String? = null,
    val pronunciation: String? = null,
    val exampleSentence: String? = null,
    val category: String? = null,
    val source: String? = null,
    val verified: Boolean = false,
    val active: Boolean = true,
)
