package com.chuahws.mayalanguageapp.domain.model

data class UsageAnalytics(
    val userId: String = "",
    val totalTranslations: Long = 0,
    val textTranslations: Long = 0,
    val imageTranslations: Long = 0,
    val voiceTranslations: Long = 0,
    val savedWords: Long = 0,
    val lastUpdatedMillis: Long = 0L,
)
