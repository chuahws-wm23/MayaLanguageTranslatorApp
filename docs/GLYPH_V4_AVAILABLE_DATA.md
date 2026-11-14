# Available-dataset final-project milestone: Maya glyph research v4

This implementation intentionally uses **only the already-downloadable TWKM Maya
glyph dataset** at https://huggingface.co/datasets/tadad/twkm-maya-glyphs.
No embargoed MHD download, permission-gated codex data, external photo
collection, Python installation, or cloud inference service is required.

## What is added

An opt-in **Research v4 (10 signs)** mode under the existing
**Photo translation > Maya glyph recognition** screen. It runs the trained
fine-tuned MobileNetV3-Small ONNX model OFFLINE on Android 7.0+.

Supported source-catalogue sign readings:

| TWKM no. | Proposed reading | Catalogue gloss |
|---:|---|---|
| 32 | K'UH | God / deity |
| 184 | K'INICH | Resplendent / sunny |
| 291 | SIIP | Name of a deity |
| 4 | NAAH | House / first |
| 44 | TOK | Cloud / mist |
| 507 | TZIMAH | Gourd |
| 548 | HAAB | Year |
| 122 | K'AK' | Fire |
| 187 | K'ABA' | Name |
| 1030 | K'AWIIL | Name of a deity |

**Research v4 is closed-set**: it ALWAYS picks one of its ten signs. The
experimental v4 rejection filter correctly accepted only 1 of 10 known
holdout originals, so that filter is **not enabled** in this mode.
It should not be presented as a reliable unknown detector. Use the older
five-sign Research v3 mode when a screened/rejected result is desirable.

**Deer/CHIJ and Cacao/KAKAW are not supported by Research v4.** The separate
TinyGlyphNet v1 remains available for those experimental categories.

## Evaluation

- The model selection compared two methods via a fixed original-image
  validation set; 8/10 validation drawings were classified correctly by
  the selected fine-tuned network.
- Final original-image holdout: 7/10 correctly classified (70%).
- Known correct **and accepted** with the disabled v4 filter: 1/10 (10%).
- Unsupported catalogue signs rejected by that disabled filter: 75/80 (93.75%).
- Held-out known sample count is **only 10 drawings**, and all came from the
  same standardized TWKM catalogue. These numbers do **not** establish
  phone-camera accuracy, historical epigraphic accuracy, translation accuracy,
  or recognition of all Maya signs. See experiment
  `glyph-v4-expanded-training/ml/glyph_v4/outputs/evaluation.json`.

## Android Studio test

1. Ensure the working directory is clean: `git status`.
2. Switch to `fyp-development`, then `git pull --ff-only origin fyp-development`
   after this integration is merged.
3. Gradle sync; connect a device and Run.
4. Open Photo translation > Maya glyph recognition.
5. Tap **Research v4 (10 signs)**, choose an image, crop one isolated glyph,
   then tap Recognise glyph.
6. Verify the proposed TWKM sign number against a catalogue reference.
   The app will mark every v4 reading **unverified**.

Use the original-image test pack under
`glyph-v4-expanded-training/ml/glyph_v4/heldout_tests` to repeat the
published v4 test on Android. It does NOT represent a new independent test.

## Features left intact

Model v1, Research v2, Research v3, legacy text-photo History replay,
Firebase, authentication, dictionary, vocabulary and ordinary text/voice
translation are preserved.

The app does not automatically upload, store as verified, or merge
experimental glyph guesses into the modern-Yucatec translation database.

## Dataset attribution

TWKM / University of Bonn, Christian Prager standardized graph drawings,
CC BY 4.0. The broader catalogue metadata has different reuse restrictions.
Source labels are catalogued hypotheses and may be phonographic or logographic
and depend on inscription context.

This is a bounded undergraduate FYP prototype, not a full Classic Maya
glyph-block segmentation, transliteration or inscription translator.
