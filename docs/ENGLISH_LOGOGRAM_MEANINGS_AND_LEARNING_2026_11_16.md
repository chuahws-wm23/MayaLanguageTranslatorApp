# Maya FYP: English logogram meanings and educational result cards

**Updated:** 16 November 2026  
**Branch:** `fyp-development`

## Changes implemented

The user wants an app that compares uploaded Maya glyph imagery against a
reference catalogue, shows potential English meanings rather than only
syllables, and lets learners understand those meanings.

The Android Photo / Maya glyph screen now has a separately labelled
**English meanings & Maya history** gallery. It includes:

- **135 source-linked TWKM logogram sign identities** with English glosses;
- **366 rights-cleared standardized graph drawings** (Christian Prager,
  University of Bonn, records explicitly marked CC BY 4.0);
- English text search, representative visual drawings, TWKM reading,
  scholarly evidence level, and explanation panels;
- **Find possible English meanings** for one selected/cropped glyph:
  three closest word-sign drawings and their source-proposed readings;
- optional multi-component v11 segmentation showing possible word signs
  and syllable lookalikes for each individual region;
- a separate **Learn about this glyph** panel after existing Research v4
  recognition, including English meaning, explanation, historical context,
  interesting fact/caution and a link to original sources.

The previous **86 syllables / 382 reference drawings** and
**10-class TWKM Research v4 recognizer** remain. The new English
catalogue is different from syllabary sounds.

## How the 135 English categories were obtained

Pinned dataset: `tadad/twkm-maya-glyphs`
revision `73fe9da1d975a5f1e588c7d42c952dfda53cefd0`.

1. Join source `decipherments` rows to graph drawings by exact TWKM
   `sign_no` only when `type=logogram` and
   `sign_reference_resolved=true`.
2. Retain logogram readings with source decipherment confidence levels
   **1–3** out of 8 (1 = strongest, 8 = most speculative). This is
   **scholarly rating of a proposed reading**, not photo-classifier confidence.
3. Retain English translations published in `graphs.translation`,
   only when the licensed drawings for that sign have **one consistent
   source English gloss**.
4. Require at least **2 distinct source-image SHA256 values** for a sign;
   drop graph drawings with blank/ambiguous labels or images assigned
   conflicting signs/meanings.
5. Include only records explicitly marked `drawing_license='CC BY 4.0'`.
6. Keep source sign identity, proposed readings and gloss, scholarly
   evidence level, graph number and attribution in the Android index.

The audit found 411 source logogram identities with English text and
licensed drawings prior to these conservative filters. The release
catalogue subset has **135 signs / 366 drawings**, including examples
AJAW (lord/ruler), YOP (leaf), CHAK (red), K'AK' (fire), HUL (to arrive
here), MIH (nothing/zero), and TOK (cloud/mist).

Source files:

- `ml/logogram_expansion/audit_meanings.py`
- `ml/logogram_expansion/build_android_meaning_gallery.py`
- `app/src/main/assets/maya_twkm_logogram_index.json`
- `app/src/main/assets/maya_twkm_logogram_bitmap.bin`

The source-image and catalogue metadata rights should be reviewed before
publishing commercial packages. Uploaded school syllabary chart artwork
was not included.

## Two separate operations: recognition vs reference search

| Function | Current scope | Accuracy statement |
|---|---|---|
| Research v4 ONNX AI classifier | **10 trained sign categories** with English glosses | Catalog-only research; not measured on independent photos |
| 25-sign ONNX research classifier | Optional sign-ID model | Not new verified English meanings |
| New English logogram image search | **135 documented sign identities / 366 drawings** | Top-3 shape similarity, **not** a trained 135-class classifier |
| Syllabary image search | 86 syllable values / 382 drawings | Sounds, not English translations |
| v11 instance segmentation | Approximate components of multi-sign imagery | 85.83% mask mAP50 on only 22 v11 test images; not reading accuracy |

Do NOT describe 135 source-backed meanings as 135 accurately classified
photo categories, or call a 74% visual-overlap score a 74% probability of
correct interpretation. The TWKM scholarly level and the recognition
uncertainty must remain visibly distinct. There is no claim that the app
now translates 135 distinct signs in unassisted camera images.

## English result education example

**HAAB — Year (TWKM #548)**

- **Meaning:** The sign refers to year.
- **How it connects to Maya culture:** The Maya Haab calendar has
  18 months of 20 days plus one five-day period, for 365 days.
- **Important note:** A correct English gloss for an individual sign is
  not a complete reading of its inscription.
- **Source:** Smithsonian National Museum of the American Indian
  (https://maya.nmai.si.edu/calendar/calendar-system).

For K'AWIIL and SIIP, the text says **deity name**, never inventing a
literal English replacement. For K'INICH, context explains solar-linked
titles and names.

Educational implementation:

- `integration/glyph/GlyphLearningNotes.kt`
- `presentation/translation/GlyphLearningDetails.kt`
- `presentation/translation/EnglishMeaningExploreCard.kt`

## Steps to update Android Studio

```powershell
git switch fyp-development
git pull origin fyp-development
```

Rebuild and launch the app, open Maya glyph recognition, and:

1. Browse **English meanings & Maya history**;
2. Search a word or reading (e.g. `fire`, `lord`, `AJAW`);
3. Expand **Meaning & learning notes** to read context;
4. Upload a clear photo and select one sign;
5. Select **Find possible English meanings** to see top three visual
   candidate drawings and unverified possible English glosses;
6. If the v11 ONNX segmentation model is installed, use
   **Find individual glyph components (research)** for multiple glyphs.

The old Research v4 **Recognise this single glyph** action remains
available for the original 10 trained classes, and includes new
**Learn about this glyph** explanations.

## Technical and FYP caveats

- Maya writing is logosyllabic: a word-sign can carry meaning whereas
  a syllabic sign represents sound. The same pictured block can have
  several components and the meaning depends on context.
- Visual matching currently uses centred foreground binary shape
  overlap; this is not robustly validated against shadows, stone
  texture, scale, artistic variation or ambiguous glyphs.
- Source confidence and visual appearance are both separate from the
  actual correctness of translation.
- To expand **neural English classifications**, collect labelled,
  independent examples across the 135 identities, assign artifact-group
  train/validation/test partitions, train a multi-class recognizer,
  calibrate abstention and measure real-photo top-1/top-3 and false
  acceptance. Without this data, replacing the existing 10-class model
  would overstate accuracy.

## Attribution and further learning

- University of Bonn, TWKM:
  https://classicmayan.org/portal/signcatalog
- Pinned TWKM preservation dataset (independent snapshot):
  https://huggingface.co/datasets/tadad/twkm-maya-glyphs
- Smithsonian NMAI / Living Maya Time:
  https://maya.nmai.si.edu/calendar/reading-calendar-glyphs
  and https://maya.nmai.si.edu/calendar/calendar-system
- Helmke & Vepretskii (2022), names and K'awiil in dynastic titles:
  https://www.mesoweb.com/pari/publications/journal/2204/Helmke-Vepretskii.2022.pdf

TWKM standardized drawing artist: Christian Prager, CC BY 4.0; source
decipherment glosses are scholarly interpretations, not static truths.


## Measured image-matching and larger neural-classifier research

The new shape-search gallery was tested on **135 held-out TWKM graph
variants** (one variant per class; training retains other graph variants
of the same sign). Results: **10/135 (7.4%) top-one**, **18/135 (13.3%)
top-three**. These are source-related drawings, not independent artifact
photographs. The photo-search UI prominently warns users that it does
NOT reliably identify English meanings.

To investigate a real **expanded recognition classifier**, a separate
frozen-pretrained MobileNetV3-Small + logistic-regression pilot trained
on the most supported logogram classes. Two class-count configurations
were declared in advance, selected by reference-image count, not by
held-out success:

| Research model | Distinct training drawings | Held-out graph variants | Top-1 | Top-3 |
|---|---:|---:|---:|---:|
| 20 English logogram classes | 85 | 20 | 7/20 (35.0%) | 8/20 (40.0%) |
| 30 English logogram classes | 105 | 30 | 10/30 (33.3%) | 11/30 (36.7%) |

None approaches 60–70%. The samples come from the same catalogue and
may be related allographs, so even these figures are NOT camera-photo
accuracy. The expanded neural classifier was **deliberately not deployed**,
and the original 10-class model is unchanged.

- Full shape-search comparison:
  https://github.com/chuahws-wm23/MayaLanguageTranslatorApp/actions/runs/43842014568
- Expanded neural classifier pilot:
  https://github.com/chuahws-wm23/MayaLanguageTranslatorApp/actions/runs/43842579349

The user-facing learning catalogue and expert-linked English glossary are
valuable despite the recognition limit. The next AI improvement requires
a manually reviewed, artifact-independent, labelled camera-photo set per
target logogram before replacing the 10-class model.
