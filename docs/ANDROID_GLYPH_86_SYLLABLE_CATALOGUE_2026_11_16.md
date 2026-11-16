# Maya FYP — Offline glyph catalogue expansion (86 syllables)

**Date:** 16 November 2026  
**Branch:** `fyp-development`

## What is genuinely expanded

Previously the shipped Research v4 image classifier could propose **10 TWKM sign identities**:
K'UH (god), K'INICH, SIIP, NAAH, TOK (cloud/mist), TZIMAH, HAAB,
K'AK' (fire), K'ABA', K'AWIIL.

Now the **Photo / Maya glyph recognition** screen also has a separately labelled
visual reference library covering:

- **86 source-proposed Maya syllabic values** (e.g. `ba`, `ku`, `ma`,
  `ch'a`, `tzi`) from pinned TWKM phonogram metadata;
- **382 distinct reference glyph drawings** explicitly marked
  **CC BY 4.0**, standardized by Christian Prager, TWKM / University of Bonn;
- the existing 10 Research v4 word/deity-name categories and their reference
  images, available in the same Browse panel.

**These are not 96 new trained recognition classes or 96 English
translations.** Syllabograms represent phonetic sounds. Source-proposed
readings need epigraphic confirmation and context. Some references may be
different graph variants of one sign.

## Where the data came from and why all chart pictures are excluded

- Hugging Face `tadad/twkm-maya-glyphs` pinned revision:
  `73fe9da1d975a5f1e588c7d42c952dfda53cefd0`.
- `decipherments` sign IDs were used only if `type=phonogram`,
  `sign_reference_resolved=true`, they matched the 86 chart syllable
  spellings exactly and had no conflicting phonogram values.
- The graphs were included only with explicitly recorded
  `drawing_license='CC BY 4.0'`. Identical source drawing hashes
  assigned contradictory syllables were excluded.
- The four user-uploaded syllabary charts and their 152 extracted derivatives
  were NOT embedded: their publication/redistribution rights are unresolved.

Developer-reproducible source:
`ml/syllabary_ml/build_android_phonogram_gallery.py`.

At build time two small assets are included automatically:

```
app/src/main/assets/maya_twkm_phonogram_index.json   # 382 referenced drawings
app/src/main/assets/maya_twkm_phonogram_bitmap.bin   # 382 x 64 x 64 packed bitmaps
```

Both are committed to `fyp-development`, so **you do not have to download
another dataset ZIP or install any new Python libraries**.

Attribution: TWKM Text Database and Dictionary of Classic Mayan,
University of Bonn. Standardized drawings by Christian Prager,
**CC BY 4.0**. https://classicmayan.org/portal/signcatalog

## How to use it in Android Studio

```powershell
git switch fyp-development
git pull origin fyp-development
```

Run the Android app. Open **Maya glyph recognition** (Photo / Camera).

- The new **Explore Maya glyphs** card appears at the top.
- Select **Browse Maya glyph reference catalogue** to explore 86 syllables,
  see representative drawings and inspect the 10 existing sign meanings.
- Choose a photo, crop ONE isolated glyph and select
  **Find similar glyph pictures (86 syllables)**. The app returns three
  nearest TWKM drawing examples with their proposed syllables and visual
  shape scores.
- For an image with several signs, open **Panel scanning tools** and
  choose **Find individual glyph components (research)** (requires locally
  installed `maya_signs_v11_seg.onnx`). The panel now returns up to three
  reference drawings beneath **each** detected component, without claiming
  a verified reading.
- You may still use **Recognise this single glyph** and Research v4,
  but **that classifier remains limited to 10 classes**. The visual
  gallery is a separate broad retrieval system.

## Accurate statements for your FYP report

- New displayed phonogram labels: 86 (source-proposed).
- Licensed individual standardized graph drawings: 382.
- Previously supported TWKM v4 recognition signs: 10 (unchanged).
- Automated glyph component segmentation: optional YOLO11n-seg v11,
  previously 85.83% mask mAP50 on only 22 held-out v11 images,
  which is NOT recognition accuracy.
- Visual matching uses canonicalized binary foreground overlap, not
  an automatically calibrated classifier. Its percent indicator is
  shape similarity, **not probability that a syllable/translation is correct**.
- Earlier cross-source research with more sophisticated MobileNet
  embeddings still produced low matching accuracy. This new feature
  is a **visual reference search** and has NOT been independently
  evaluated on real phone photographs.
- Not every glyph in a stone inscription is a syllabogram. A visual hit
  may be wrong and multiple signs can form one compound glyph block.

## Future expansion of English meanings (god, fire, mist, etc.)

The 86 syllables do NOT add 86 new English glosses. To expand meaningful
word-sign categories, create an independently reviewed logogram crosswalk
with verified readings, translations, sign identities, and rights-cleared
reference drawings, and benchmark additional classes separately. Do not
guess English glosses from a visual lookalike.

## Licences

TWKM drawings: Christian Prager / University of Bonn, CC BY 4.0.
Third-party artifact photographs, syllabary textbook charts, TWKM database
metadata, Ultralytics segmentation weights, and other material may have
additional reuse conditions. This implementation does not embed
unlicensed chart images or unrelated photos.
