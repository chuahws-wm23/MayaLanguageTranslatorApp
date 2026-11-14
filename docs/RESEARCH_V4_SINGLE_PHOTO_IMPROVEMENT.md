# Research v4 single-glyph focus — measured results, 15 Nov 2026

## Change that remains in the Android app

- **Recognise this single glyph** is now the primary photo action.
- The legacy automatic/grid panel functions are accessible behind
  **Panel scanning tools (optional)**. No multi-block recognition change.
- The same Research v4 10-sign ONNX weights, SHA256, sign-to-gloss map and
  original + shadow-corrected photo views remain deployed.

## Three-view proposal was evaluated and rejected

A third median-denoised photo view was trialled but **reverted** after a
source-faithful TWKM evaluation showed no gain in correctly accepted
supported signs and more false acceptance of unknown signs.

The evaluation ran the actual ONNX weights in Python with an approximation
of Android preprocessing on the original TWKM drawing pixels. These figures
are NOT independent phone photograph accuracy.

| Task | Original two-view | Experimental three-view |
| --- | --- | --- |
| Clean supported validation, correct accepted | 8/10 | 8/10 |
| Simulated-photo validation, correct accepted | 24/30 | 24/30 |
| Exposed original supported holdout, correct accepted | 7/10 | 7/10 |
| Unknown sign IDs accepted incorrectly | 77/80 | **80/80** |
| Unknown sign IDs rejected | 3/80 | 0/80 |

The extra view increased false positives, so it was not kept.

Research run:
https://github.com/chuahws-wm23/MayaLanguageTranslatorApp/actions/runs/43627701899

## Training experiment with datasets

Reproducible same-v4 retraining uses the pinned TWKM drawing IDs for
supervised learning and the Kaggle font images ONLY for unlabelled ink
coverage/style statistics. There is no verified U_XXXX font-key-to-TWKM
crosswalk. Modern Yucatec/Kaikki dictionary text cannot act as labelled
Classic Maya glyph images.

- Existing v4 on 10 known validation originals: **8/10**.
- New style-augmented v4 candidate: **8/10**.
- Existing v4 on 30 synthetic variations: **21/30**.
- New candidate on 30 synthetic variations: **21/30**.

As no validated gain occurred, the trained candidate was rejected and
the original Android model weights were kept.

Training run:
https://github.com/chuahws-wm23/MayaLanguageTranslatorApp/actions/runs/43626942105

## Scientific limits

The current v4 classifier supports only ten TWKM sign classes. Neither
two-view agreement nor an attractive-looking English gloss proves a
correct identification. The 77/80 wrongly accepted unknown-sign
result shows the old classifier has poor open-set rejection.

Meaningful gains require (1) independently labelled real photos for
each target sign and extra unknown signs, (2) calibration for false
acceptance, and (3) an untouched independent camera test before a
release. We cannot claim that this update increases independent
real-photo accuracy.
