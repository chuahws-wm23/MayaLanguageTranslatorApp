# Maya glyph dictionary — separate from photo scanning

Updated: 16 November 2026. Branch: `fyp-development`.

## New user navigation

The Photo / Scan page no longer contains the browseable word-sign list or
syllabary list. It is dedicated to camera/gallery input, one-sign cropping,
possible top-three visual matches, and optional multi-component segmentation.

A dedicated **Glyph Dictionary** page is accessible both ways:

- **Left sidebar:** Learn → Glyph dictionary.
- **Bottom navigation:** Dictionary.
- **Home:** Glyph dictionary shortcut (meanings and syllables).

The bottom navigation now highlights four primary destinations: Home, Scan,
Dictionary and Profile. Existing Saved and History screens remain accessible
from the left sidebar and home-screen shortcuts; their records are preserved.

## Dedicated dictionary tabs

1. **Word meanings (135):** TWKM word-sign gallery with source-linked English
   glosses, sign drawings, searches, and educational notes.
2. **Syllables (86):** TWKM reference drawings associated with source-proposed
   phonogram values, which represent sounds rather than English translations.
3. **Vocabulary:** Existing searchable/sortable vocabulary list, including its
   previous pagination and saved-entry action. This legacy collection is not
   deleted.

The dictionary is a separate destination (new
`presentation/dictionary/GlyphDictionaryHubScreen.kt`). It reuses the existing
`EnglishMeaningExploreCard` and `GlyphCataloguePanel` in browse-only mode.
The old `DictionaryScreen` is preserved under Vocabulary.

## Critical distinction

The catalogue's **135 documented English-glossed TWKM sign identities**
are *browseable reference records*, not 135 reliably recognized camera-photo
classes. The experimental automatic Research v4 neural model still predicts
only its 10 known categories. Shape matching may be wrong and is not calibrated
confidence. The new navigation does not alter any ONNX or Firebase assets.

## QA after checkout

- Open Scan from bottom bar and from left sidebar; no browseable word list
  should appear on the Scan page.
- Open Dictionary from either navigation location and from Home.
- Switch between the Word meanings, Syllables and Vocabulary tabs.
- Search for "fire", "AJAW" and "ba"; confirm descriptions and images.
- Confirm Vocabulary still loads old entries and can save an entry.
- Navigate Home, Saved and History via their shortcuts; no data is deleted.
- Test at 390px mobile width and dark mode; tabs should remain horizontally
  scrollable without cutting off the screen.
- Upload a glyph in Scan; confirm matching results still appear within the
  scan workflow, but the full lists remain only in Dictionary.
