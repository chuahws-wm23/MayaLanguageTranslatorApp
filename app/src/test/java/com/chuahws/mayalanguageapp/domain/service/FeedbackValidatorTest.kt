package com.chuahws.mayalanguageapp.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedbackValidatorTest {

    @Test
    fun ratingIsRequired() {
        assertEquals(
            "Choose a star rating before submitting.",
            FeedbackValidator.validate(0, ""),
        )
        assertEquals(
            "Choose a star rating before submitting.",
            FeedbackValidator.validate(6, "Great app"),
        )
    }

    @Test
    fun commentCanBeEmptyOrWhitespace() {
        assertNull(FeedbackValidator.validate(5, ""))
        assertNull(FeedbackValidator.validate(2, "  "))
    }

    @Test
    fun optionalWrittenFeedbackIsAccepted() {
        assertNull(FeedbackValidator.validate(4, "Useful"))
    }

    @Test
    fun commentsCannotExceedLimit() {
        assertEquals(
            "Keep your feedback within 500 characters.",
            FeedbackValidator.validate(3, "a".repeat(501)),
        )
        assertNull(FeedbackValidator.validate(3, "a".repeat(500)))
    }
}
