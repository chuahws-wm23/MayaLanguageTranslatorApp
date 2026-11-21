package com.chuahws.mayalanguageapp.data.repository

import com.chuahws.mayalanguageapp.domain.model.GlyphScanCandidate
import com.chuahws.mayalanguageapp.domain.model.GlyphScanCapture
import com.chuahws.mayalanguageapp.domain.model.GlyphScanRecords
import com.chuahws.mayalanguageapp.domain.model.SavedVocabularyItem
import com.chuahws.mayalanguageapp.domain.model.TranslationRecord
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scan workflow reuses BOTH existing user-data collections, so old records
 * and saved words survive a change to photo-only recognition.
 */
class GlyphScanSavedHistoryTest {
    private val candidate = GlyphScanCandidate(
        twkmSign=122,
        reading="K'AK'",
        englishGloss="fire",
        visualOverlap=.54,
        scholarlyLevel=1,
    )

    private fun scan(user: String = "a", id: String = "scan1"): TranslationRecord =
        GlyphScanRecords.makeHistory(
            userId=user, id=id, millis=1234,
            capture=GlyphScanCapture(
                previewBase64="preview-jpeg-encoded",
                candidates=listOf(candidate),
            ),
        )

    @Test fun oneScanPersistsWithImageAndThreeFieldsInExistingHistory() = runTest {
        val repo=InMemoryUserDataRepository()
        val record=scan()
        repo.recordTranslation(record)
        assertEquals(record,repo.getHistory("a").single())
        assertEquals(1,record.glyphCandidates.size)
        assertEquals("preview-jpeg-encoded",record.glyphImageBase64)
        assertTrue(record.glyphScanRecord)
        assertTrue(record.translatedText.contains("unverified"))
        assertTrue(repo.getHistory("b").isEmpty())
    }

    @Test fun favouritingCreatesOneDeterministicSavedScan() = runTest {
        val repo=InMemoryUserDataRepository()
        val original=scan()
        repo.recordTranslation(original)
        val favorite=GlyphScanRecords.makeFavorite(original,2345)
        repo.saveVocabulary(favorite)
        repo.saveVocabulary(favorite)
        assertEquals(1,repo.getSavedVocabulary("a").size)
        assertEquals("scan1",favorite.glyphSourceScanId)
        assertEquals("fire",favorite.englishText)
        assertEquals("preview-jpeg-encoded",favorite.glyphImageBase64)
        assertTrue(favorite.glyphScanRecord)
        assertTrue(repo.getSavedVocabulary("b").isEmpty())
    }

    @Test fun deletingHistoryDoesNotEraseASeparateFavourite() = runTest {
        val repo=InMemoryUserDataRepository()
        val original=scan()
        repo.recordTranslation(original)
        val favorite=GlyphScanRecords.makeFavorite(original,2345)
        repo.saveVocabulary(favorite)
        repo.deleteTranslation(original.translationId, "a")
        assertTrue(repo.getHistory("a").isEmpty())
        assertEquals(favorite,repo.getSavedVocabulary("a").single())
        repo.removeSavedVocabulary(favorite.savedId)
        assertTrue(repo.getSavedVocabulary("a").isEmpty())
    }

    @Test fun existingWordsAndOldHistoryRemainUnaffected() = runTest {
        val repo=InMemoryUserDataRepository()
        val old=TranslationRecord(translationId="old",userId="a",sourceText="water")
        val word=SavedVocabularyItem(savedId="word",userId="a",mayaText="ha'")
        repo.recordTranslation(old)
        repo.saveVocabulary(word)
        repo.recordTranslation(scan())
        repo.saveVocabulary(GlyphScanRecords.makeFavorite(scan(),2345))
        assertEquals(2,repo.getHistory("a").size)
        assertEquals(2,repo.getSavedVocabulary("a").size)
        assertFalse(old.glyphScanRecord)
        assertFalse(word.glyphScanRecord)
    }

    @Test fun captureCannotContainAnOversizedImageOrTooManyRegions() {
        try {
            GlyphScanCapture(previewBase64="x".repeat(160_001))
            throw AssertionError("Oversized Base64 was accepted")
        } catch (_: IllegalArgumentException) { }
        try {
            GlyphScanCapture(regionCount=65)
            throw AssertionError("Oversized region count was accepted")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun differentScanIdsProduceIndependentFavorites() {
        val a=GlyphScanRecords.makeFavorite(scan(id="one"),2345)
        val b=GlyphScanRecords.makeFavorite(scan(id="two"),2345)
        assertTrue(a.savedId != b.savedId)
    }
}
