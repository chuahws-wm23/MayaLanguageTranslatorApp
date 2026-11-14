package com.chuahws.mayalanguageapp.domain.service

/**
 * A star rating is required. Written feedback is deliberately optional.
 * Keep this rule outside the UI so the ViewModel enforces it as well.
 */
object FeedbackValidator {
    const val MAX_COMMENT_LENGTH = 500

    fun validate(rating: Int, comment: String): String? = when {
        rating !in 1..5 -> "Choose a star rating before submitting."
        comment.trim().length > MAX_COMMENT_LENGTH ->
            "Keep your feedback within $MAX_COMMENT_LENGTH characters."
        else -> null
    }
}
