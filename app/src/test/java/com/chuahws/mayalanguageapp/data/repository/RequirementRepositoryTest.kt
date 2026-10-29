package com.chuahws.mayalanguageapp.data.repository

import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.domain.model.FeedbackItem
import com.chuahws.mayalanguageapp.domain.model.InputType
import com.chuahws.mayalanguageapp.domain.model.TranslationRecord
import com.chuahws.mayalanguageapp.domain.model.UserProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RequirementRepositoryTest {

    @Test
    fun historyDeletionOnlyRemovesMatchingUserRecord() = runTest {
        val repository = InMemoryUserDataRepository()
        repository.recordTranslation(
            TranslationRecord(
                translationId = "t-1",
                userId = "user-1",
                sourceText = "water",
                translatedText = "jaʼ",
            )
        )

        repository.deleteTranslation(
            translationId = "t-1",
            userId = "user-2",
        )
        assertEquals(
            1,
            repository.getHistory("user-1").size,
        )

        repository.deleteTranslation(
            translationId = "t-1",
            userId = "user-1",
        )
        assertTrue(
            repository.getHistory("user-1").isEmpty()
        )
    }

    @Test
    fun dictionaryEntryCanBeAddedEditedSearchedAndDeleted() = runTest {
        val repository =
            InMemoryDictionaryRepository(emptyList())

        val original = DictionaryEntry(
            entryId = "admin-1",
            mayaText = "jaʼ",
            mayaNormalized = "jaʼ",
            englishText = "water",
            englishNormalized = "water",
            verified = true,
        )

        repository.upsertEntry(original)
        assertEquals(
            "water",
            repository.searchEntries("water")
                .single()
                .englishText,
        )

        repository.upsertEntry(
            original.copy(
                englishText = "fresh water",
                englishNormalized = "fresh water",
            )
        )
        assertEquals(
            "fresh water",
            repository.searchEntries("fresh")
                .single()
                .englishText,
        )

        repository.deleteEntry("admin-1")
        assertTrue(
            repository.searchEntries("fresh").isEmpty()
        )
    }

    @Test
    fun administratorCanChangeAccountStatus() = runTest {
        val repository =
            InMemoryUserProfileRepository()

        repository.createProfile(
            UserProfile(
                userId = "user-1",
                displayName = "Test User",
                email = "user@example.com",
            )
        )

        repository.updateAccountStatus(
            userId = "user-1",
            status = "suspended",
            updatedAtMillis = 123L,
        )

        val profile =
            repository.getProfile("user-1")

        assertEquals(
            "suspended",
            profile?.accountStatus,
        )
        assertEquals(
            123L,
            profile?.updatedAtMillis,
        )
    }


    @Test
    fun systemHistoryReturnsRecentTranslationsAcrossUsers() = runTest {
        val repository =
            InMemoryUserDataRepository()

        repository.recordTranslation(
            TranslationRecord(
                translationId = "older",
                userId = "user-1",
                createdAtMillis = 10L,
            )
        )
        repository.recordTranslation(
            TranslationRecord(
                translationId = "newer",
                userId = "user-2",
                createdAtMillis = 20L,
            )
        )

        val all =
            repository.getAllHistory(
                limit = 1,
            )

        assertEquals(1, all.size)
        assertEquals(
            "newer",
            all.single().translationId,
        )
    }

    @Test
    fun newFeedbackStartsPending() {
        assertEquals(
            "pending",
            FeedbackItem().status,
        )
    }

    @Test
    fun systemAnalyticsCanAggregateMultipleUsers() = runTest {
        val repository =
            InMemoryUserDataRepository()

        repository.recordTranslation(
            TranslationRecord(
                translationId = "t-1",
                userId = "user-1",
                inputType = InputType.TEXT.name,
            )
        )
        repository.recordTranslation(
            TranslationRecord(
                translationId = "t-2",
                userId = "user-2",
                inputType = InputType.IMAGE.name,
            )
        )

        val all = repository.getAllAnalytics()

        assertEquals(2, all.size)
        assertEquals(
            2L,
            all.sumOf { it.totalTranslations },
        )
        assertEquals(
            1L,
            all.sumOf { it.textTranslations },
        )
        assertEquals(
            1L,
            all.sumOf { it.imageTranslations },
        )
    }
}
