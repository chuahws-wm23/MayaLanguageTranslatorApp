package com.chuahws.mayalanguageapp.data.repository

import com.chuahws.mayalanguageapp.domain.model.UserProfile
import com.chuahws.mayalanguageapp.domain.repository.UserProfileRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirestoreUserProfileRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : UserProfileRepository {

    private val users = firestore.collection("users")

    override suspend fun createProfile(profile: UserProfile) {
        users.document(profile.userId).set(profile).await()
    }

    override suspend fun getProfile(userId: String): UserProfile? =
        users.document(userId)
            .get()
            .await()
            .toObject(UserProfile::class.java)
}
