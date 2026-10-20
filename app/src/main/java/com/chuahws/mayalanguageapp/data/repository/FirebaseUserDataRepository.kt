package com.chuahws.mayalanguageapp.data.repository

import com.chuahws.mayalanguageapp.domain.model.FeedbackItem
import com.chuahws.mayalanguageapp.domain.model.InputType
import com.chuahws.mayalanguageapp.domain.model.SavedVocabularyItem
import com.chuahws.mayalanguageapp.domain.model.TranslationRecord
import com.chuahws.mayalanguageapp.domain.model.UsageAnalytics
import com.chuahws.mayalanguageapp.domain.repository.UserDataRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class FirebaseUserDataRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : UserDataRepository {

    override suspend fun recordTranslation(record: TranslationRecord) {
        firestore.collection("translations")
            .document(record.translationId)
            .set(record)
            .await()

        val analytics = mutableMapOf<String, Any>(
            "userId" to record.userId,
            "totalTranslations" to FieldValue.increment(1),
            "lastUpdatedMillis" to record.createdAtMillis,
        )

        when (record.inputType) {
            InputType.IMAGE.name ->
                analytics["imageTranslations"] = FieldValue.increment(1)

            InputType.VOICE.name ->
                analytics["voiceTranslations"] = FieldValue.increment(1)

            else ->
                analytics["textTranslations"] = FieldValue.increment(1)
        }

        firestore.collection("analytics")
            .document(record.userId)
            .set(analytics, SetOptions.merge())
            .await()
    }

    override suspend fun getHistory(
        userId: String,
        limit: Int,
    ): List<TranslationRecord> =
        firestore.collection("translations")
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(TranslationRecord::class.java) }
            .sortedByDescending { it.createdAtMillis }
            .take(limit)

    override suspend fun saveVocabulary(item: SavedVocabularyItem) {
        firestore.collection("savedVocabulary")
            .document(item.savedId)
            .set(item)
            .await()

        firestore.collection("analytics")
            .document(item.userId)
            .set(
                mapOf(
                    "userId" to item.userId,
                    "savedWords" to FieldValue.increment(1),
                    "lastUpdatedMillis" to item.savedAtMillis,
                ),
                SetOptions.merge(),
            )
            .await()
    }

    override suspend fun removeSavedVocabulary(savedId: String) {
        val ref = firestore.collection("savedVocabulary")
            .document(savedId)

        val item = ref.get().await()
            .toObject(SavedVocabularyItem::class.java)

        ref.delete().await()

        if (item != null) {
            firestore.collection("analytics")
                .document(item.userId)
                .set(
                    mapOf(
                        "userId" to item.userId,
                        "savedWords" to FieldValue.increment(-1),
                        "lastUpdatedMillis" to System.currentTimeMillis(),
                    ),
                    SetOptions.merge(),
                )
                .await()
        }
    }

    override suspend fun getSavedVocabulary(
        userId: String
    ): List<SavedVocabularyItem> =
        firestore.collection("savedVocabulary")
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents
            .mapNotNull {
                it.toObject(SavedVocabularyItem::class.java)
            }
            .sortedByDescending { it.savedAtMillis }

    override suspend fun submitFeedback(item: FeedbackItem) {
        firestore.collection("feedback")
            .document(item.feedbackId)
            .set(item)
            .await()
    }

    override suspend fun getFeedback(userId: String): List<FeedbackItem> =
        firestore.collection("feedback")
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(FeedbackItem::class.java) }
            .sortedByDescending { it.submittedAtMillis }

    override suspend fun getAnalytics(userId: String): UsageAnalytics =
        firestore.collection("analytics")
            .document(userId)
            .get()
            .await()
            .toObject(UsageAnalytics::class.java)
            ?: UsageAnalytics(userId = userId)

    override suspend fun getAllFeedback(): List<FeedbackItem> =
        firestore.collection("feedback")
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(FeedbackItem::class.java) }
            .sortedByDescending { it.submittedAtMillis }

    override suspend fun updateFeedbackStatus(
        feedbackId: String,
        status: String,
        reviewedAtMillis: Long,
    ) {
        firestore.collection("feedback")
            .document(feedbackId)
            .update(
                mapOf(
                    "status" to status,
                    "reviewedAtMillis" to reviewedAtMillis,
                )
            )
            .await()
    }
}
