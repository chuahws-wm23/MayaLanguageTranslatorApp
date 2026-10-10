package com.chuahws.mayalanguageapp.domain.repository

import com.chuahws.mayalanguageapp.domain.model.UserProfile

interface UserProfileRepository {
    suspend fun createProfile(profile: UserProfile)
    suspend fun getProfile(userId: String): UserProfile?
}
