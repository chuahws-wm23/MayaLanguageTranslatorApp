package com.chuahws.mayalanguageapp.data.repository

import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.domain.repository.DictionaryRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirestoreDictionaryRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : DictionaryRepository {

    private val collection = firestore.collection("dictionary")

    override suspend fun findByMaya(
        normalizedText: String,
    ): DictionaryEntry? =
        findAllByMaya(normalizedText).firstOrNull()

    override suspend fun findByEnglish(
        normalizedText: String,
    ): DictionaryEntry? =
        findAllByEnglish(normalizedText).firstOrNull()

    override suspend fun findAllByMaya(
        normalizedText: String,
    ): List<DictionaryEntry> =
        collection
            .whereEqualTo("mayaNormalized", normalizedText)
            .whereEqualTo("active", true)
            .get()
            .await()
            .documents
            .mapNotNull {
                it.toObject(DictionaryEntry::class.java)
            }
            .sortedWith(
                compareByDescending<DictionaryEntry> { it.verified }
                    .thenBy { it.englishText }
            )

    override suspend fun findAllByEnglish(
        normalizedText: String,
    ): List<DictionaryEntry> =
        collection
            .whereEqualTo("englishNormalized", normalizedText)
            .whereEqualTo("active", true)
            .get()
            .await()
            .documents
            .mapNotNull {
                it.toObject(DictionaryEntry::class.java)
            }
            .sortedWith(
                compareByDescending<DictionaryEntry> { it.verified }
                    .thenBy { it.mayaText }
            )
}
