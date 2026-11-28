# Roboflow v11: Manik' out-of-class failure and MR4 expansion probe

Date: 2026-11-15. **No change to Android v4 release model.**

## Why the user's photo is a useful failure case

- A real external JPEG named `Manik.jpg` was submitted (not included in GitHub for privacy/provenance reasons).
- The existing **21-code** HOG and CNN experimental classifiers both returned `ZZ1` as top prediction. This is *not evidence that the image belongs to ZZ1*: every image must be assigned one of the 21 classes.
- Learner's Maya Glyph Guide relates the `MANIK` calendar day sign to MHD `MR4` / `AV1` in a reading group. This is *context-dependent*: a similar hand sign may be read `chi` as a syllabogram without the day-sign cartouche. Primary material:
  - https://mayaglyphs.org/
  - https://mayaglyphs.org/CWL2html/chi.html
  - https://research.famsi.org/montgomery_dictionary/daysigns.php
- The v11 ZIP contains **16 MR4 training annotations from 6 distinct original source filenames**; **zero** annotated MR4 validation and official test instances; AV1 is not present. No proof that every MR4 crop includes a day cartouche.

## The attempted fixed-parameter 22-class research probe

Using the exact user dataset extracted into `/mnt/data/maya_v11_work`, add class `MR4` to the 21-code HOG RBF-SVM and keep its **previously chosen** HOG64, RBF `C=10` configuration; do not tune hyperparameters on this photo or held-out original drawings. Fit all 21 classes using their official training split; for MR4, fit 4 original sources and hold out two other MR4 original sources. User Manik photo is entirely **out of training**.

| Test cohort | 21-code baseline | 22-code probe |
|---|---:|---:|
| Existing valid (59 crops, old codes) | 37/59 (62.7%) | 37/59 (62.7%) |
| Existing test (34 crops, old codes) | 21/34 (61.8%) | 21/34 (61.8%) |
| MR4 2 held-out originals, 6 crops | not an available class | **0/6 top-1, 0/6 top-3** |
| User's Manik JPEG | ZZ1, not correct reading | **ZZ1, not MR4** |

The MR4 held-out originals were `MR401_CHIHJ_lex_l_t.png` and `PNG3BkD03.png`. Training originals were `CNCP1A01.png`, `CNCP1A08.png`, `CNCP1D04.png`, and `MR401_CHIHJ_lex_l.png`. **Important caveat:** `MR401_CHIHJ_lex_l.png` and `MR401_CHIHJ_lex_l_t.png` are visually similar variants and may represent the same underlying design despite distinct filename stems. Therefore, treating them as two independent sources is *optimistic*; even this optimistic test failed.

This is a narrow research probe, not evidence that training 22 classes cannot work with more independent sources. The 21-class validation and test were already exposed during model selection and are not independent field tests. Do not promote the 22-code model to Android or claim any new TWKM accuracy.

## Decision and next concrete work

1. **Stop using unrestricted photo uploads to grade the 21-class code model.** Its 21 outputs are forced-choice and include no 'other' class.
2. **Keep Android Research v4 ONNX** for its 10 supported TWKM signs. The v11 HOG/CNN experimental classifiers are not TWKM translators.
3. **Verify Roboflow code-to-TWKM mapping on individual images**, starting with data-rich related families such as `SG1` / K'UH and `XH2` / HAAB. Do not merge distinct glyph variants or reading groups blindly.
4. **Obtain genuinely different, provenance-tracked Manik' day-sign examples**, including a *visible cartouche* and equivalent glyph without cartouche as an intentional hard-negative; preserve source-artifact groups in train/validation/test. There are currently too few verified examples for a release claim.
5. **Run a strict source-disjoint benchmark** before and after adding verified samples. Include unsupported signs with measured false-acceptance rates. Retain v4 if gates are not met.
6. For the FYP demo, disclose supported vocabulary and display top candidates as **unverified possible readings**, offer manual dictionary lookup/correction, and avoid asserting a translation for unknown signs.

Data manifests and independent photo gates already exist at `dataset/scripts/validate_real_glyph_photos.py` and `ml/research_v4_single/evaluate_heldout_photos.py`. See `docs/ROBOFLOW_V11_SINGLE_GLYPH_DATA_AUDIT.md`.
