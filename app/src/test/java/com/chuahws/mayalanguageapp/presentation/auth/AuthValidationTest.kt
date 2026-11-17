package com.chuahws.mayalanguageapp.presentation.auth

import com.chuahws.mayalanguageapp.data.repository.InMemoryUserProfileRepository
import com.chuahws.mayalanguageapp.domain.model.AuthUser
import com.chuahws.mayalanguageapp.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class AuthValidationTest {
    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private class Auth : AuthRepository {
        var resets = mutableListOf<String>()
        var registrations = 0
        override fun currentUser(): AuthUser? = null
        override suspend fun register(displayName: String, email: String, password: String): AuthUser {
            registrations++; return AuthUser(userId = "a", email = email)
        }
        override suspend fun login(email: String, password: String) = AuthUser(userId = "a", email = email)
        override suspend fun sendPasswordReset(email: String) { resets.add(email) }
        override fun signOut() {}
    }
    @Test fun malformedEmailsNeverRequestPasswordReset() = runTest {
        val auth = Auth(); val model = AuthViewModel(auth, InMemoryUserProfileRepository())
        listOf("", "a@@example.com", "a b@example.com", "a@.com", "a@example..com", "a@-example.com", ".a@example.com").forEach {
            model.updateEmail(it); model.sendPasswordReset(); advanceUntilIdle()
            assertNotNull(model.uiState.value.message)
        }
        assertTrue(auth.resets.isEmpty())
    }
    @Test fun resetTrimsValidEmailAndBlocksRepeatedClickWhileLoading() = runTest {
        val auth = Auth(); val model = AuthViewModel(auth, InMemoryUserProfileRepository())
        model.updateEmail("  learner+glyph@example.com  ")
        model.sendPasswordReset(); model.sendPasswordReset(); advanceUntilIdle()
        assertEquals(listOf("learner+glyph@example.com"), auth.resets)
        assertTrue(model.uiState.value.passwordResetSent)
    }
    @Test fun registrationRequiresNamePasswordAndMatchingConfirmation() = runTest {
        val auth = Auth(); val model = AuthViewModel(auth, InMemoryUserProfileRepository())
        model.updateEmail("a@example.com"); model.register(); advanceUntilIdle()
        model.updateDisplayName("Explorer"); model.updatePassword("123"); model.updateConfirmPassword("123")
        model.register(); advanceUntilIdle()
        model.updatePassword("123456"); model.register(); advanceUntilIdle()
        assertEquals(0, auth.registrations)
        model.updateConfirmPassword("123456"); model.register(); advanceUntilIdle()
        assertEquals(1, auth.registrations)
        assertTrue(model.uiState.value.registrationCompleted)
        assertNull(model.uiState.value.currentUser)
    }
}
