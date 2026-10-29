package com.chuahws.mayalanguageapp.data.repository

import com.chuahws.mayalanguageapp.domain.model.UserProfile
import com.chuahws.mayalanguageapp.domain.repository.UserProfileRepository

class InMemoryUserProfileRepository : UserProfileRepository {
    private val profiles = mutableMapOf<String, UserProfile>()

    override suspend fun createProfile(profile: UserProfile) {
        profiles[profile.userId] = profile
    }

    override suspend fun getProfile(userId: String): UserProfile? =
        profiles[userId]

    override suspend fun updateProfile(profile: UserProfile) {
        profiles[profile.userId] = profile
    }

    override suspend fun getAllProfiles(): List<UserProfile> =
        profiles.values
            .sortedBy {
                it.displayName.ifBlank { it.email }.lowercase()
            }

    override suspend fun updateAccountStatus(
        userId: String,
        status: String,
        updatedAtMillis: Long,
    ) {
        profiles[userId]?.let { profile ->
            profiles[userId] = profile.copy(
                accountStatus = status,
                updatedAtMillis = updatedAtMillis,
            )
        }
    }
}
