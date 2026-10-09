package com.chuahws.mayalanguageapp.data.repository

import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.domain.repository.DictionaryRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirestoreDictionaryRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : DictionaryRepository {

    private val collection = firestore.collection("dictionary")

    override suspend fun findByMaya(normalizedText: String): DictionaryEntry? =
        collection
            .whereEqualTo("mayaNormalized", normalizedText)
            .whereEqualTo("active", true)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
            ?.toObject(DictionaryEntry::class.java)

    override suspend fun findByEnglish(normalizedText: String): DictionaryEntry? =
        collection
            .whereEqualTo("englishNormalized", normalizedText)
            .whereEqualTo("active", true)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
            ?.toObject(DictionaryEntry::class.java)
}
