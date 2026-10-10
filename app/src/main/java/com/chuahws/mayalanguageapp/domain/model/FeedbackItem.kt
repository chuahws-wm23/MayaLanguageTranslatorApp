package com.chuahws.mayalanguageapp.domain.model

data class FeedbackItem(
    val feedbackId: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val rating: Int = 0,
    val comment: String = "",
    val status: String = "new",
    val submittedAtMillis: Long = 0L,
    val reviewedAtMillis: Long? = null,
)
