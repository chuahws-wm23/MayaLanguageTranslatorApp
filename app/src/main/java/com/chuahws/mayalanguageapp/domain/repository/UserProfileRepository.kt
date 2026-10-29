package com.chuahws.mayalanguageapp.domain.repository

import com.chuahws.mayalanguageapp.domain.model.UserProfile

interface UserProfileRepository {
    suspend fun createProfile(profile: UserProfile)
    suspend fun getProfile(userId: String): UserProfile?
    suspend fun updateProfile(profile: UserProfile)

    suspend fun getAllProfiles(): List<UserProfile> =
        emptyList()

    suspend fun updateAccountStatus(
        userId: String,
        status: String,
        updatedAtMillis: Long,
    ) {
        val profile = getProfile(userId) ?: return
        updateProfile(
            profile.copy(
                accountStatus = status,
                updatedAtMillis = updatedAtMillis,
            )
        )
    }
}
