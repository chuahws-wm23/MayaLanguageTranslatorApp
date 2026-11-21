# Research v4 — Maya single-glyph model card and development audit

**Date:** 15 November 2026  
**Scope:** Android single-glyph recognition (the existing Research v4 model, not v5)  
**Release decision:** Preserve the existing v4 ONNX weights; no new candidate has demonstrated sufficient validation improvement.

## Architecture and supported labels

- MobileNetV3-Small CNN (10-class final classifier), run offline using ONNX Runtime on Android
- Same deployed asset: `app/src/main/assets/maya_glyph_v4.onnx`
- SHA-256: `b9bb6bcc816f9c7957792302d7a1e50aaee2a1ecf3d44b8ae9b9b493cf4efba9`
- Supported TWKM sign identities: **32, 184, 291, 4, 44, 507, 548, 122, 187 and 1030**
- Preprocessing: normalize orientation, user-guided crop, Otsu-style foreground mask, aspect-preserving 64px canonical glyph, and original/shadow-normalized two-view assessment
- **No validated open-set recognizer:** unsupported images can still be assigned a supported sign.

## Dataset compatibility

| Data | Legitimate use | Important limit |
|---|---|---|
| TWKM glyph graph catalogue (pinned HF revision `73fe9da1d975a5f1e588c7d42c952dfda53cefd0`) | Train/evaluate a classifier with known TWKM sign identities | Small number of distinct drawings for many sign classes; not independent camera photos |
| Kaggle Mayan Glyphs (user-supplied ZIP) | Auxiliary shape/font-key pretraining and unlabelled stroke/style statistics | `U_XXXX` classes are font keys, not verified TWKM identities/readings; numerous exact duplicates |
| Kaikki modern Yucatec dictionary | Separate text translation and lexical functions | Not labelled Classic Maya glyph images |

No false `U_XXXX -> TWKM -> English` mappings have been introduced.

## Actual evaluation of the deployed v4 ONNX classifier

TWKM originals were split by distinct source image hash before training. The
ten known 'final test' images were already exposed by prior experiments and
**are not a fresh independent test**.

| Metric | Known validation n=10 | Exposed known test n=10 |
|---|---:|---:|
| Top-1 correct | 8/10 (80%) | 7/10 (70%) |
| Top-3 includes correct sign | 8/10 (80%) | 7/10 (70%) |
| Macro precision | 0.700 | 0.550 |
| Macro recall | 0.800 | 0.700 |
| Macro F1 | 0.733 | 0.600 |

Confused source graph examples (test) included:
- TWKM sign **44 TOK** graph `44bb` -> predicted **187 K'ABA'**
- TWKM sign **507 TZIMAH** graph `507hh` -> predicted **4 NAAH**
- TWKM sign **1030 K'AWIIL** graph `1030ex` -> predicted **291 SIIP**

Evaluation and downloadable confusion CSVs:
https://github.com/chuahws-wm23/MayaLanguageTranslatorApp/actions/runs/43631175655

The 80 source-catalogue unsupported-sign examples received closed-set
predictions; some produced softmax scores above 99%, proving that **a high
neural softmax score is not proof that a Maya reading is correct**. Scores
shown in the UI are explicitly *uncalibrated relative model scores*, not
validated real-world confidence percentages.

## Model training experiments (candidate not deployed)

| Verified sign / synthetic style validation | Existing v4 | Candidate | Decision |
|---|---:|---:|---|
| Kaggle stroke-statistics augmentation: clean /30-style | 8/10; 21/30 | 8/10; 21/30 | No accuracy gain |
| Class-balanced hard-positive / hard-negative feature fine-tune | 8/10; 21/30 | 8/10; 21/30 | No accuracy gain |
| Kaggle font-key auxiliary pretraining then TWKM fine-tune | 8/10; 21/30 | 8/10; 19/30 | Worse robustness; reject |

The **hard-negative training** explicitly optimised visual-feature distances
for pairs of distinct training originals per sign and the closest rival signs.
Loss decreased, but the candidate did not improve classification on the
predeclared validation set. The two-stage Kaggle transfer model likewise did
not improve and was rejected.

Related experiments:
- https://github.com/chuahws-wm23/MayaLanguageTranslatorApp/actions/runs/43631503266
- https://github.com/chuahws-wm23/MayaLanguageTranslatorApp/actions/runs/43631956501
- https://github.com/chuahws-wm23/MayaLanguageTranslatorApp/actions/runs/43626942105

### Unknown-sign threshold research

A validation-selected logit-margin screen rejected 66 of 80 unsupported sign
drawings on the old catalogue test, but correctly identified and accepted
only 3 of 10 supported held-out drawings. **Not suitable for deployment**.
https://github.com/chuahws-wm23/MayaLanguageTranslatorApp/actions/runs/43628173799

## Android/UI work completed

- Primary action: choose/upload an image, preview/crop one complete glyph, **Recognise this single glyph**.
- Camera capture and Gallery; crop, rotation, busy indicator, invalid-image
  error messaging, and new-image selection.
- Legacy research model selector and multi-glyph panel tools preserved
  behind advanced toggles, reducing the main screen's visual clutter.
- Model predicts top 3 supported TWKM signs from real logits, with
  numerically stable softmax-relative percentages. Results are explicitly
  *unverified*. A standard/shadow-view disagreement shows **Unrecognized /
  needs review** instead of a confident English reading.
- A corresponding attributed TWKM reference drawing is shown **for
  comparison only**, never used in inference or lookup.
- Optional **Report incorrect / unknown glyph** allows selection of
  candidate/unknown plus free text and exports a local CSV.
  Each row is tagged `UNVERIFIED_USER_FEEDBACK`; there is **no automatic
  training or upload**, and no user photo is exported by that workflow.
- Existing auth/Firebase/dictionary/history/other screens unchanged.

## Honest capability statement

**Can do:** classify isolated images tentatively among ten supported TWKM
categories, return a ranked list, warn on some unstable/invalid inputs,
operate offline on device, and display a reference illustration.

**Cannot yet demonstrate:** reliable classification of arbitrary Classical
Maya glyphs, consistent rejection of unfamiliar symbols, high accuracy on
stone relief photographs or handwriting, phonetic glyph-compound parsing,
Classic Maya -> English sentence translation, or independent real-phone
accuracy. TWKM glosses depend on inscription context.

### Remaining acceptance work
1. Gather 3–5 *different independently sourced and source-verified*
   drawings for each of ten supported classes, plus varying camera photos
   and at least 30 independent unsupported signs.
2. Obtain explicit image rights, sign-ID provenance and expert verification
   of feedback. Do not use a different photo of the same original drawing
   as an independent test example.
3. Freeze an untouched source-group-separated final test; evaluate real-photo
   top1/top3, confusion matrix, macro precision/recall/F1, false acceptance,
   correct-and-accepted, and processing time on Android.
4. Train/tune from the development split only, deploy v4 weights **only if**
   the held-out final evaluation and on-device smoke tests show a genuine gain.

This audit makes **no fabricated accuracy-improvement claim**.
