package com.chuahws.mayalanguageapp.presentation.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.domain.model.FeedbackItem
import com.chuahws.mayalanguageapp.domain.model.SavedVocabularyItem
import com.chuahws.mayalanguageapp.domain.model.TranslationRecord
import com.chuahws.mayalanguageapp.domain.repository.DictionaryRepository
import com.chuahws.mayalanguageapp.domain.repository.UserDataRepository
import com.chuahws.mayalanguageapp.domain.repository.UserProfileRepository
import com.chuahws.mayalanguageapp.domain.service.TextNormalizer
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppDataViewModel(
    private val userDataRepository: UserDataRepository,
    private val userProfileRepository: UserProfileRepository,
    private val dictionaryRepository: DictionaryRepository,
) : ViewModel() {

    private val savingEntryIds = mutableSetOf<String>()
    private val removingSavedIds = mutableSetOf<String>()

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    fun loadForUser(userId: String) {
        if (
            _uiState.value.userId == userId &&
            _uiState.value.profile != null
        ) {
            return
        }

        _uiState.update {
            it.copy(userId = userId)
        }
        refresh()
    }

    fun clearUser() {
        _uiState.value = AppUiState()
    }

    fun refresh() {
        val userId = _uiState.value.userId ?: return
        if (_uiState.value.loading) return

        _uiState.update {
            it.copy(loading = true)
        }

        viewModelScope.launch {
            runCatching {
                coroutineScope {
                    val profile = async {
                        userProfileRepository.getProfile(userId)
                    }
                    val history = async {
                        userDataRepository.getHistory(userId)
                    }
                    val saved = async {
                        userDataRepository.getSavedVocabulary(userId)
                    }
                    val feedback = async {
                        userDataRepository.getFeedback(userId)
                    }
                    val analytics = async {
                        userDataRepository.getAnalytics(userId)
                    }

                    LoadedUserData(
                        profile = profile.await(),
                        history = history.await(),
                        saved = saved.await(),
                        feedback = feedback.await(),
                        analytics = analytics.await(),
                    )
                }
            }.onSuccess { loaded ->
                _uiState.update {
                    it.copy(
                        profile = loaded.profile,
                        history = loaded.history,
                        savedVocabulary = loaded.saved,
                        feedback = loaded.feedback,
                        analytics = loaded.analytics,
                        loading = false,
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        loading = false,
                        message =
                            "Could not refresh your data. Check your connection and try again.",
                    )
                }
            }
        }
    }

    fun loadDictionary(
        query: String = "",
    ) {
        if (_uiState.value.dictionaryLoading) return

        val cleanQuery = query.trim()
        loadDictionaryPage(
            query = cleanQuery,
            limit = DICTIONARY_PAGE_SIZE,
        )
    }

    fun loadMoreDictionary() {
        val state = _uiState.value

        if (
            state.dictionaryLoading ||
            !state.dictionaryHasMore
        ) {
            return
        }

        loadDictionaryPage(
            query = state.dictionaryQuery,
            limit =
                state.dictionaryLimit +
                    DICTIONARY_PAGE_SIZE,
        )
    }

    private fun loadDictionaryPage(
        query: String,
        limit: Int,
    ) {
        _uiState.update {
            it.copy(
                dictionaryLoading = true,
                dictionaryQuery = query,
            )
        }

        viewModelScope.launch {
            runCatching {
                if (query.isBlank()) {
                    dictionaryRepository
                        .browseEntries(limit + 1)
                } else {
                    dictionaryRepository
                        .searchEntries(
                            query = query,
                            limit = limit + 1,
                        )
                }
            }.onSuccess { entries ->
                val hasMore =
                    entries.size > limit

                _uiState.update {
                    it.copy(
                        dictionaryEntries =
                            entries.take(limit),
                        dictionaryLoading =
                            false,
                        dictionaryQuery =
                            query,
                        dictionaryLimit =
                            limit,
                        dictionaryHasMore =
                            hasMore,
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        dictionaryLoading = false,
                        message =
                            "Could not load the dictionary. Check your connection and try again.",
                    )
                }
            }
        }
    }

    fun saveEntry(entry: DictionaryEntry) {
        val userId = _uiState.value.userId ?: return

        if (!savingEntryIds.add(entry.entryId)) {
            return
        }

        if (
            _uiState.value.savedVocabulary.any {
                it.entryId == entry.entryId
            }
        ) {
            savingEntryIds.remove(entry.entryId)
            _uiState.update {
                it.copy(
                    message = "This word is already saved."
                )
            }
            return
        }

        viewModelScope.launch {
            val item = SavedVocabularyItem(
                savedId = "${userId}_${entry.entryId}",
                userId = userId,
                entryId = entry.entryId,
                mayaText = entry.mayaText,
                englishText = entry.englishText,
                pronunciation = entry.pronunciation,
                partOfSpeech = entry.partOfSpeech,
                savedAtMillis = System.currentTimeMillis(),
            )

            runCatching {
                userDataRepository.saveVocabulary(item)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        message = "Saved to vocabulary."
                    )
                }
                refresh()
            }.onFailure(::showError)

            savingEntryIds.remove(entry.entryId)
        }
    }

    fun removeSaved(item: SavedVocabularyItem) {
        if (!removingSavedIds.add(item.savedId)) {
            return
        }

        viewModelScope.launch {
            runCatching {
                userDataRepository.removeSavedVocabulary(
                    item.savedId
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        message =
                            "Removed from saved vocabulary."
                    )
                }
                refresh()
            }.onFailure(::showError)

            removingSavedIds.remove(item.savedId)
        }
    }

    fun deleteHistory(record: TranslationRecord) {
        val userId = _uiState.value.userId ?: return

        viewModelScope.launch {
            runCatching {
                userDataRepository.deleteTranslation(
                    translationId = record.translationId,
                    userId = userId,
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        history = it.history.filterNot { item ->
                            item.translationId ==
                                record.translationId
                        },
                        message =
                            "Translation removed from history.",
                    )
                }
            }.onFailure(::showError)
        }
    }

    fun updateDisplayName(displayName: String) {
        val profile = _uiState.value.profile ?: return
        val cleanName = displayName.trim()

        if (cleanName.length < 2) {
            _uiState.update {
                it.copy(
                    message =
                        "Enter at least two characters."
                )
            }
            return
        }

        viewModelScope.launch {
            val updated = profile.copy(
                displayName = cleanName,
                updatedAtMillis =
                    System.currentTimeMillis(),
            )

            runCatching {
                userProfileRepository.updateProfile(updated)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        profile = updated,
                        message = "Profile updated.",
                    )
                }
            }.onFailure(::showError)
        }
    }

    fun submitFeedback(
        email: String,
        rating: Int,
        comment: String,
    ) {
        if (_uiState.value.feedbackSubmitting) {
            return
        }

        val userId = _uiState.value.userId ?: return
        val cleanComment = comment.trim()

        if (rating !in 1..5) {
            _uiState.update {
                it.copy(
                    message =
                        "Choose a rating first."
                )
            }
            return
        }

        if (cleanComment.length < 4) {
            _uiState.update {
                it.copy(
                    message =
                        "Tell us a little more."
                )
            }
            return
        }

        _uiState.update {
            it.copy(feedbackSubmitting = true)
        }

        viewModelScope.launch {
            val item = FeedbackItem(
                feedbackId =
                    UUID.randomUUID().toString(),
                userId = userId,
                userEmail = email,
                rating = rating,
                comment = cleanComment,
                status = "pending",
                submittedAtMillis =
                    System.currentTimeMillis(),
            )

            runCatching {
                userDataRepository.submitFeedback(item)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        message =
                            "Thanks for the feedback.",
                        feedbackSubmissionVersion =
                            it.feedbackSubmissionVersion + 1,
                        feedbackSubmitting = false,
                    )
                }
                refresh()
            }.onFailure {
                _uiState.update { state ->
                    state.copy(
                        feedbackSubmitting = false,
                        message =
                            "Could not submit feedback. Please try again.",
                    )
                }
            }
        }
    }

    fun loadAdminData() {
        if (_uiState.value.profile?.role != "admin") {
            return
        }
        if (_uiState.value.adminLoading) return

        _uiState.update {
            it.copy(adminLoading = true)
        }

        viewModelScope.launch {
            runCatching {
                coroutineScope {
                    val users = async {
                        userProfileRepository.getAllProfiles()
                    }
                    val feedback = async {
                        userDataRepository.getAllFeedback()
                    }
                    val analytics = async {
                        userDataRepository.getAllAnalytics()
                    }
                    val translations = async {
                        userDataRepository.getAllHistory(
                            limit = 100,
                        )
                    }
                    val dictionary = async {
                        dictionaryRepository.browseEntries(200)
                    }

                    AdminData(
                        users = users.await(),
                        feedback = feedback.await(),
                        analytics = analytics.await(),
                        translations =
                            translations.await(),
                        dictionary = dictionary.await(),
                    )
                }
            }.onSuccess { adminData ->
                _uiState.update {
                    it.copy(
                        adminUsers = adminData.users,
                        adminFeedback = adminData.feedback,
                        adminAnalytics =
                            adminData.analytics,
                        adminTranslations =
                            adminData.translations,
                        adminDictionaryEntries =
                            adminData.dictionary,
                        adminLoading = false,
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        adminLoading = false,
                        message =
                            "Could not load administrator data. Check your connection and try again.",
                    )
                }
            }
        }
    }

    fun loadAdminFeedback() {
        if (_uiState.value.profile?.role != "admin") {
            return
        }

        viewModelScope.launch {
            runCatching {
                userDataRepository.getAllFeedback()
            }.onSuccess { items ->
                _uiState.update {
                    it.copy(
                        adminFeedback = items
                    )
                }
            }.onFailure(::showError)
        }
    }

    fun setUserAccountStatus(
        userId: String,
        status: String,
    ) {
        if (_uiState.value.profile?.role != "admin") {
            return
        }

        if (userId == _uiState.value.userId) {
            _uiState.update {
                it.copy(
                    message =
                        "You cannot suspend your own administrator account."
                )
            }
            return
        }

        val safeStatus =
            if (status == "suspended") {
                "suspended"
            } else {
                "active"
            }

        viewModelScope.launch {
            runCatching {
                userProfileRepository.updateAccountStatus(
                    userId = userId,
                    status = safeStatus,
                    updatedAtMillis =
                        System.currentTimeMillis(),
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        adminUsers =
                            it.adminUsers.map { profile ->
                                if (profile.userId == userId) {
                                    profile.copy(
                                        accountStatus = safeStatus,
                                        updatedAtMillis =
                                            System.currentTimeMillis(),
                                    )
                                } else {
                                    profile
                                }
                            },
                        message =
                            if (safeStatus == "active") {
                                "User account reactivated."
                            } else {
                                "User account suspended."
                            },
                    )
                }
            }.onFailure(::showError)
        }
    }

    fun searchAdminDictionary(query: String) {
        if (_uiState.value.profile?.role != "admin") {
            return
        }

        _uiState.update {
            it.copy(adminLoading = true)
        }

        viewModelScope.launch {
            runCatching {
                if (query.isBlank()) {
                    dictionaryRepository.browseEntries(200)
                } else {
                    dictionaryRepository.searchEntries(
                        query = query.trim(),
                        limit = 100,
                    )
                }
            }.onSuccess { entries ->
                _uiState.update {
                    it.copy(
                        adminDictionaryEntries = entries,
                        adminLoading = false,
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        adminLoading = false,
                        message =
                            "Could not search the dictionary.",
                    )
                }
            }
        }
    }

    fun saveAdminDictionaryEntry(
        existing: DictionaryEntry?,
        mayaText: String,
        englishText: String,
        partOfSpeech: String,
        pronunciation: String,
    ) {
        if (_uiState.value.profile?.role != "admin") {
            return
        }

        val cleanMaya = mayaText.trim()
        val cleanEnglish = englishText.trim()

        if (
            cleanMaya.isBlank() ||
            cleanEnglish.isBlank()
        ) {
            _uiState.update {
                it.copy(
                    message =
                        "Maya and English text are required."
                )
            }
            return
        }

        val englishNormalized =
            TextNormalizer.normalize(cleanEnglish)

        val entry = DictionaryEntry(
            entryId =
                existing?.entryId
                    ?.takeIf { it.isNotBlank() }
                    ?: UUID.randomUUID().toString(),
            mayaText = cleanMaya,
            mayaNormalized =
                TextNormalizer.normalize(cleanMaya),
            englishText = cleanEnglish,
            englishNormalized = englishNormalized,
            partOfSpeech =
                partOfSpeech.trim()
                    .takeIf { it.isNotBlank() },
            pronunciation =
                pronunciation.trim()
                    .takeIf { it.isNotBlank() },
            exampleSentence =
                existing?.exampleSentence,
            category = existing?.category,
            source =
                existing?.source
                    ?.takeIf { it.isNotBlank() }
                    ?: "Administrator entry",
            usageLabel = existing?.usageLabel,
            acceptedEnglishInputs =
                existing?.acceptedEnglishInputs
                    ?.takeIf { it.isNotEmpty() }
                    ?: listOf(englishNormalized),
            verified = true,
            active = true,
        )

        viewModelScope.launch {
            runCatching {
                dictionaryRepository.upsertEntry(entry)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        message =
                            if (existing == null) {
                                "Dictionary entry added."
                            } else {
                                "Dictionary entry updated."
                            }
                    )
                }
                searchAdminDictionary("")
            }.onFailure(::showError)
        }
    }

    fun importAdminDictionaryCsv(
        csvText: String,
    ) {
        if (_uiState.value.profile?.role != "admin") {
            return
        }

        if (csvText.isBlank()) {
            _uiState.update {
                it.copy(
                    message =
                        "The selected CSV file is empty.",
                )
            }
            return
        }

        val rows = parseCsv(csvText)
        if (rows.size < 2) {
            _uiState.update {
                it.copy(
                    message =
                        "The CSV file does not contain dictionary rows.",
                )
            }
            return
        }

        val header = rows.first()
            .map(::normaliseCsvHeader)

        fun columnIndex(
            vararg names: String,
        ): Int = names
            .asSequence()
            .map(::normaliseCsvHeader)
            .map(header::indexOf)
            .firstOrNull { it >= 0 }
            ?: -1

        val mayaIndex = columnIndex(
            "maya_text",
            "maya",
        )
        val englishIndex = columnIndex(
            "english_text",
            "english",
        )

        if (mayaIndex < 0 || englishIndex < 0) {
            _uiState.update {
                it.copy(
                    message =
                        "CSV must include maya_text and english_text columns.",
                )
            }
            return
        }

        val idIndex = columnIndex(
            "entry_id",
            "id",
        )
        val partOfSpeechIndex =
            columnIndex(
                "part_of_speech",
                "word_type",
            )
        val pronunciationIndex =
            columnIndex("pronunciation")
        val exampleIndex =
            columnIndex(
                "example_sentence",
                "example",
            )
        val categoryIndex =
            columnIndex("category")
        val sourceIndex =
            columnIndex("source")
        val usageIndex =
            columnIndex(
                "usage_label",
                "usage",
            )

        val existingIds =
            _uiState.value
                .adminDictionaryEntries
                .map { it.entryId }
                .filter(String::isNotBlank)
                .toMutableSet()

        val existingKeys =
            _uiState.value
                .adminDictionaryEntries
                .map(::dictionaryDuplicateKey)
                .toMutableSet()

        _uiState.update {
            it.copy(adminLoading = true)
        }

        viewModelScope.launch {
            var imported = 0
            var duplicates = 0
            var invalid = 0
            val duplicateRows =
                mutableListOf<Int>()
            val invalidRows =
                mutableListOf<Int>()

            rows.drop(1)
                .take(MAX_CSV_IMPORT_ROWS)
                .forEachIndexed {
                        rowIndex,
                        row,
                    ->
                    val csvRowNumber =
                        rowIndex + 2
                    fun valueAt(
                        index: Int,
                    ): String =
                        if (
                            index >= 0 &&
                            index < row.size
                        ) {
                            row[index].trim()
                        } else {
                            ""
                        }

                    val maya =
                        valueAt(mayaIndex)
                    val english =
                        valueAt(englishIndex)

                    if (
                        maya.isBlank() ||
                        english.isBlank()
                    ) {
                        invalid += 1
                        invalidRows +=
                            csvRowNumber
                        return@forEachIndexed
                    }

                    val entryId =
                        valueAt(idIndex)
                            .takeIf {
                                it.isNotBlank()
                            }
                            ?: UUID.randomUUID()
                                .toString()

                    val entry = DictionaryEntry(
                        entryId = entryId,
                        mayaText = maya,
                        mayaNormalized =
                            TextNormalizer.normalize(
                                maya
                            ),
                        englishText = english,
                        englishNormalized =
                            TextNormalizer.normalize(
                                english
                            ),
                        partOfSpeech =
                            valueAt(
                                partOfSpeechIndex
                            ).takeIf {
                                it.isNotBlank()
                            },
                        pronunciation =
                            valueAt(
                                pronunciationIndex
                            ).takeIf {
                                it.isNotBlank()
                            },
                        exampleSentence =
                            valueAt(
                                exampleIndex
                            ).takeIf {
                                it.isNotBlank()
                            },
                        category =
                            valueAt(
                                categoryIndex
                            ).takeIf {
                                it.isNotBlank()
                            },
                        source =
                            valueAt(
                                sourceIndex
                            ).takeIf {
                                it.isNotBlank()
                            }
                                ?: "Administrator import",
                        usageLabel =
                            valueAt(
                                usageIndex
                            ).takeIf {
                                it.isNotBlank()
                            },
                        acceptedEnglishInputs =
                            listOf(
                                TextNormalizer
                                    .normalize(
                                        english
                                    )
                            ),
                        verified = true,
                        active = true,
                    )

                    val duplicateKey =
                        dictionaryDuplicateKey(
                            entry
                        )

                    if (
                        entryId in existingIds ||
                        duplicateKey in
                            existingKeys
                    ) {
                        duplicates += 1
                        duplicateRows +=
                            csvRowNumber
                        return@forEachIndexed
                    }

                    val alreadyStored =
                        runCatching {
                            (
                                dictionaryRepository
                                    .searchEntries(
                                        maya,
                                        25,
                                    ) +
                                    dictionaryRepository
                                        .searchEntries(
                                            english,
                                            25,
                                        )
                                )
                                .distinctBy {
                                    it.entryId
                                }
                                .any {
                                    dictionaryDuplicateKey(
                                        it
                                    ) ==
                                        duplicateKey
                                }
                        }.getOrDefault(false)

                    if (alreadyStored) {
                        duplicates += 1
                        duplicateRows +=
                            csvRowNumber
                        return@forEachIndexed
                    }

                    runCatching {
                        dictionaryRepository
                            .upsertEntry(entry)
                    }.onSuccess {
                        imported += 1
                        existingIds += entryId
                        existingKeys +=
                            duplicateKey
                    }.onFailure {
                        invalid += 1
                        invalidRows +=
                            csvRowNumber
                    }
                }

            val refreshed =
                runCatching {
                    dictionaryRepository
                        .browseEntries(500)
                }.getOrElse {
                    _uiState.value
                        .adminDictionaryEntries
                }

            _uiState.update {
                it.copy(
                    adminDictionaryEntries =
                        refreshed,
                    adminLoading = false,
                    message =
                        buildString {
                            append(
                                "$imported imported, " +
                                    "$duplicates duplicate(s) skipped, " +
                                    "$invalid invalid row(s)."
                            )
                            if (
                                duplicateRows
                                    .isNotEmpty()
                            ) {
                                append(
                                    " Duplicate rows: " +
                                        duplicateRows
                                            .take(8)
                                            .joinToString(
                                                ", "
                                            ) +
                                        "."
                                )
                            }
                            if (
                                invalidRows
                                    .isNotEmpty()
                            ) {
                                append(
                                    " Invalid rows: " +
                                        invalidRows
                                            .take(8)
                                            .joinToString(
                                                ", "
                                            ) +
                                        "."
                                )
                            }
                        },
                )
            }
        }
    }

    fun deleteAdminDictionaryEntry(
        entry: DictionaryEntry,
    ) {
        if (_uiState.value.profile?.role != "admin") {
            return
        }

        viewModelScope.launch {
            runCatching {
                dictionaryRepository.deleteEntry(
                    entry.entryId
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        adminDictionaryEntries =
                            it.adminDictionaryEntries.filterNot { item ->
                                item.entryId == entry.entryId
                            },
                        message =
                            "Dictionary entry removed.",
                    )
                }
            }.onFailure(::showError)
        }
    }

    fun markFeedbackReviewed(item: FeedbackItem) {
        if (_uiState.value.profile?.role != "admin") {
            return
        }

        viewModelScope.launch {
            runCatching {
                userDataRepository.updateFeedbackStatus(
                    feedbackId = item.feedbackId,
                    status = "reviewed",
                    reviewedAtMillis =
                        System.currentTimeMillis(),
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        message =
                            "Feedback marked as reviewed."
                    )
                }
                loadAdminFeedback()
            }.onFailure(::showError)
        }
    }

    private fun parseCsv(
        csvText: String,
    ): List<List<String>> =
        csvText
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .lineSequence()
            .filter { it.isNotBlank() }
            .map(::parseCsvLine)
            .toList()

    private fun parseCsvLine(
        line: String,
    ): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0

        while (index < line.length) {
            val char = line[index]

            when {
                char == '"' &&
                    quoted &&
                    index + 1 < line.length &&
                    line[index + 1] == '"' -> {
                    current.append('"')
                    index += 1
                }

                char == '"' -> {
                    quoted = !quoted
                }

                char == ',' && !quoted -> {
                    values += current.toString()
                    current.clear()
                }

                else -> current.append(char)
            }

            index += 1
        }

        values += current.toString()
        return values
    }

    private fun normaliseCsvHeader(
        value: String,
    ): String =
        value.trim()
            .lowercase()
            .replace(" ", "_")
            .replace("-", "_")

    private fun dictionaryDuplicateKey(
        entry: DictionaryEntry,
    ): String =
        listOf(
            TextNormalizer.normalize(
                entry.mayaText
            ),
            TextNormalizer.normalize(
                entry.englishText
            ),
            entry.partOfSpeech
                .orEmpty()
                .trim()
                .lowercase(),
        ).joinToString("|")

    fun clearMessage() {
        _uiState.update {
            it.copy(message = null)
        }
    }

    private fun showError(error: Throwable) {
        _uiState.update {
            it.copy(
                loading = false,
                adminLoading = false,
                dictionaryLoading = false,
                message =
                    error.message
                        ?.takeIf { it.isNotBlank() }
                        ?: "Something went wrong. Please try again.",
            )
        }
    }

    private data class LoadedUserData(
        val profile:
            com.chuahws.mayalanguageapp.domain.model.UserProfile?,
        val history:
            List<com.chuahws.mayalanguageapp.domain.model.TranslationRecord>,
        val saved: List<SavedVocabularyItem>,
        val feedback: List<FeedbackItem>,
        val analytics:
            com.chuahws.mayalanguageapp.domain.model.UsageAnalytics,
    )

    private data class AdminData(
        val users:
            List<com.chuahws.mayalanguageapp.domain.model.UserProfile>,
        val feedback: List<FeedbackItem>,
        val analytics:
            List<com.chuahws.mayalanguageapp.domain.model.UsageAnalytics>,
        val translations:
            List<TranslationRecord>,
        val dictionary: List<DictionaryEntry>,
    )

    private companion object {
        const val MAX_CSV_IMPORT_ROWS = 2_000
        const val DICTIONARY_PAGE_SIZE = 100
    }
}
