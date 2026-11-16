# FYP: scanned Maya glyphs in existing Favourites and History

**Branch:** `fyp-development`  
**Implementation:** 16 November 2026  
**Status:** Integrated with user account data; automatic Android CI and real-device checks are separate.

## User flow

1. Sign in, open **Scan** from the bottom bar or sidebar.
2. Use Gallery or Camera to select an image; crop one complete glyph.
3. Tap **Find matching English word signs**. On completion, the app runs
   the offline 135-sign / 366-drawing visual lookup and **records the scan
   to the existing History collection**. The card includes a small JPEG
   preview of the selected crop, its top-three proposed TWKM readings,
   English glosses, visual resemblance scores and creation time.
4. Tap **Add this glyph to favourites** to store an independent, user-owned
   copy in the existing Saved/Favourites collection. Tap again to remove it.
5. Tap **View History** or **View Favourites** from the result card.
   Both destinations can also be opened through Home / sidebar.
6. In History, filter by **Glyph scans**, tap a saved picture to open
   the candidate list and educational notes, and add/remove Favourite or
   delete the History entry.
7. In Favourites, filter by **Glyph scans**, search proposed readings/glosses,
   tap a picture to see the details, and remove the favourite if desired.
8. When using advanced Research-v4 recognition or optional multi-component
   scanning, tap **Save this scan to History** to capture that outcome.
   These advanced modes do not silently add an automatic record.

## Compatibility

The change reuses existing Firebase Firestore collections,
`translations` and `savedVocabulary`, using backward-compatible optional
fields in `TranslationRecord` and `SavedVocabularyItem`:

- `isGlyphScan`
- `glyphImageBase64`
- `glyphCandidates`
- `glyphScanKind`
- `glyphRegionCount`
- `glyphSourceScanId` on favourites.

No existing dictionary word is removed, renamed in Firestore, or converted.
Old History records have `isGlyphScan=false`, so they remain visible under
**Older records**. Saving a glyph does **not** pretend that it is a verified
Yucatec-Maya vocabulary entry.

Favourites use a deterministic ID based on the signed-in user and History
scan ID, preventing accidental duplicates for the same scan. Deleting a
History entry does not delete a separately saved favourite, and vice versa.

## Image and privacy limitations

- Each scan stores **only a small JPEG thumbnail** of the selected glyph
  crop, maximum width/height 360 pixels and at most 110 KB JPEG data.
  The full-resolution photograph remains on the user's device; it is
  **not** stored in Firestore by this feature.
- The preview is placed into the user's existing account History and,
  if favourited, into Saved. With Firebase configured, those previews
  are synced to the associated account; the current Firestore rules also
  grant authorized app administrators read access to History records.
  The app discloses that searches save previews to account History.
- When Firebase is not configured, the app uses development-only
  in-memory repositories; records do not persist across a full app restart.
- The photo may show a person or personal information. Users should crop
  to the glyph before choosing the search action.
- Neither the user image nor suggestions are automatically uploaded to
  model training. Recording matches does not verify the scholarly reading.

## Data accuracy

The extra `glyphCandidates` fields reflect the **experimental visual
search**. The visual-overlap percentage is not a calibrated probability of
translation correctness. For Research-v4 scans the app stores
`visualOverlap=0` to indicate that a shape-comparison score is unavailable.
This must never be displayed as **0% confidence**.
A correct reading cannot be inferred from a picture match alone.

## Technical structure

- `domain/model/GlyphScanRecord.kt`: bounded scan metadata, immutable
  candidate snapshot, History and Favourite record mapping.
- `integration/glyph/GlyphScanPreviewCodec.kt`: crop image into a bounded
  JPEG-on-white preview, with safe decode checks.
- `presentation/translation/EnglishMeaningExploreCard.kt`: record completed
  user-triggered visual matches.
- `presentation/translation/ImageTranslationScreen.kt`: History status,
  Favourite toggle, direct navigation, manual capture for advanced scanning.
- `presentation/app/AppDataViewModel.kt`: write History and Favourites
  using the existing per-user repository.
- `presentation/components/GlyphScanRecordCard.kt`: shared photo preview
  and source-linked results dialog for Saved and History.
- `presentation/saved/SavedScreen.kt`: joint saved vocabulary and glyph scans.
- `presentation/history/HistoryScreen.kt`: glyph-only and old-record filters,
  saved preview, source summary, favourite and delete actions.
- `data/repository/GlyphScanSavedHistoryTest.kt`: old/new coexistence,
  owner isolation, deterministic favourites and independent deletion tests.

## Phone QA checklist

- [ ] Search a single glyph → History contains one new glyph scan with
      photo preview, timestamp and top-three possible readings.
- [ ] Add favourite from Scan → Favourites contains the scanned glyph.
- [ ] From History, tap the scan → full detail dialog opens; toggle
      favourite → Saved reflects the correct state.
- [ ] Remove favourite → History remains.
- [ ] Favourite again, delete History → Favourites remains with photo.
- [ ] Saved word entries still filter, open, play pronunciation and delete.
- [ ] Old legacy History text/voice records remain under Older records.
- [ ] Query a different crop/source; no stale previous-photo result card.
- [ ] If using trained v11 ONNX, detect multiple glyphs, tap **Save this
      scan to History**, and confirm it shows one saved panel preview.
- [ ] Test no-network mode, Firebase disabled fallback, account switch,
      narrow 390px viewport, and large text.
- [ ] Ensure neither visual overlap nor research scores are labelled
      as reliable recognition accuracy.

## Build the new version

In Android Studio PowerShell:

```powershell
git switch fyp-development
git pull origin fyp-development
```

Rebuild and install. No new permissions, model files or Firebase collection
rules are required for this feature, assuming the existing per-user
`translations` and `savedVocabulary` collection rules are deployed.
