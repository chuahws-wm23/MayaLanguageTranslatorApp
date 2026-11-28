package com.chuahws.mayalanguageapp.data.repository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
class DevelopmentResetTest {
    @Test fun developmentResetDoesNotPretendToSendEmail() = runTest {
        val error = runCatching { DevelopmentAuthRepository().sendPasswordReset("test@example.test") }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains("unavailable in development mode"))
    }
}
