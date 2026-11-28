package com.chuahws.mayalanguageapp.presentation.app

import com.chuahws.mayalanguageapp.data.repository.*
import com.chuahws.mayalanguageapp.domain.model.*
import com.chuahws.mayalanguageapp.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class AppDataValidationTest {
    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private fun vm(data: UserDataRepository = InMemoryUserDataRepository(), profiles: UserProfileRepository = InMemoryUserProfileRepository()) =
        AppDataViewModel(data, profiles, InMemoryDictionaryRepository(emptyList()))

    @Test fun switchingAccountsClearsPreviousDataBeforeNewLoad() = runTest {
        val profiles = InMemoryUserProfileRepository()
        profiles.createProfile(UserProfile(userId = "a", displayName = "Alice"))
        profiles.createProfile(UserProfile(userId = "b", displayName = "Bob"))
        val data = InMemoryUserDataRepository()
        data.recordTranslation(TranslationRecord(translationId = "a-scan", userId = "a", glyphScanRecord = true))
        val model = vm(data, profiles)
        model.loadForUser("a"); advanceUntilIdle()
        assertEquals("Alice", model.uiState.value.profile?.displayName)
        model.loadForUser("b")
        assertNull(model.uiState.value.profile)
        assertTrue(model.uiState.value.history.isEmpty())
        advanceUntilIdle()
        assertEquals("Bob", model.uiState.value.profile?.displayName)
        model.clearUser()
    }

    @Test fun delayedRefreshCannotRepopulateSignedOutState() = runTest {
        val gate = CompletableDeferred<Unit>()
        val profiles = object : UserProfileRepository by InMemoryUserProfileRepository() {
            override suspend fun getProfile(userId: String): UserProfile = withContext(NonCancellable) {
                gate.await(); UserProfile(userId = userId, displayName = "Old account")
            }
        }
        val model = vm(profiles = profiles)
        model.loadForUser("a"); runCurrent()
        model.clearUser(); gate.complete(Unit); advanceUntilIdle()
        assertEquals(AppUiState(), model.uiState.value)
    }

    @Test fun savedAndHistoryMutationsRejectRecordsFromAnotherAccount() = runTest {
        var calls = 0
        val data = object : UserDataRepository by InMemoryUserDataRepository() {
            override suspend fun removeSavedVocabulary(savedId: String) { calls++ }
            override suspend fun deleteTranslation(translationId: String, userId: String) { calls++ }
        }
        val model = vm(data)
        model.loadForUser("a"); advanceUntilIdle()
        model.removeSaved(SavedVocabularyItem(savedId = "b-saved", userId = "b"))
        model.deleteHistory(TranslationRecord(translationId = "b-scan", userId = "b"))
        advanceUntilIdle()
        assertEquals(0, calls)
        model.clearUser()
    }

    @Test fun feedbackValidatesRatingBlocksDuplicateSubmitsAndAllowsRetryAfterFailure() = runTest {
        var calls = 0
        var fail = true
        val gate = CompletableDeferred<Unit>()
        val data = object : UserDataRepository by InMemoryUserDataRepository() {
            override suspend fun submitFeedback(item: FeedbackItem) {
                calls++; gate.await()
                if (fail) error("offline")
            }
        }
        val model = vm(data)
        model.loadForUser("a"); advanceUntilIdle()
        model.submitFeedback("a@example.com", 0, ""); advanceUntilIdle()
        assertEquals(0, calls)
        model.submitFeedback("a@example.com", 4, "")
        model.submitFeedback("a@example.com", 4, "")
        runCurrent(); assertEquals(1, calls)
        gate.complete(Unit); advanceUntilIdle()
        assertFalse(model.uiState.value.feedbackSubmitting)
        assertEquals(0L, model.uiState.value.feedbackSubmissionVersion)
        fail = false
        model.submitFeedback("a@example.com", 4, ""); advanceUntilIdle()
        assertEquals(2, calls)
        assertEquals(1L, model.uiState.value.feedbackSubmissionVersion)
        model.clearUser()
    }

    @Test fun deletingHistoryRefreshesActivityTotals() = runTest {
        val data = InMemoryUserDataRepository()
        val record = TranslationRecord(translationId = "scan", userId = "a", glyphScanRecord = true)
        data.recordTranslation(record)
        val model = vm(data)
        model.loadForUser("a"); advanceUntilIdle()
        assertEquals(1L, model.uiState.value.analytics.totalTranslations)
        model.deleteHistory(record); advanceUntilIdle()
        assertTrue(model.uiState.value.history.isEmpty())
        assertEquals(0L, model.uiState.value.analytics.totalTranslations)
        model.clearUser()
    }

    @Test fun profileRejectsBlankNameAndTrimsValidName() = runTest {
        val profiles = InMemoryUserProfileRepository()
        profiles.createProfile(UserProfile(userId = "a", displayName = "Alice"))
        val model = vm(profiles = profiles)
        model.loadForUser("a"); advanceUntilIdle()
        model.updateDisplayName("  "); advanceUntilIdle()
        assertEquals("Alice", profiles.getProfile("a")?.displayName)
        model.updateDisplayName("  Explorer  "); advanceUntilIdle()
        assertEquals("Explorer", profiles.getProfile("a")?.displayName)
        model.clearUser()
    }
}
