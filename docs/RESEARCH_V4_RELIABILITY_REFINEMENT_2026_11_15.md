# Research v4 Maya glyph recognition: iterative refinement and reliability decision

**Date:** 15 November 2026
**Branch:** `fyp-development`
**Current Android release:** unchanged v4 MobileNetV3-Small ONNX weights, 10 TWKM sign categories.

## Finding: original labelled image scarcity is the core blocker

The pinned TWKM source split has **45** labelled original drawings for
training, **10** separate validation drawings and **10 previously exposed**
catalogue test drawings. Specifically:

| TWKM sign | Total distinct graph drawings | Training originals |
|---|---:|---:|
| #44 TOK | 6 | 4 |
| #187 K'ABA' | 5 | 3 |
| #548 HAAB | 6 | 4 |

This limited training variation cannot establish reliable fine-grained
recognition from independent carvings or phone photos, regardless of how
many rotations/duplicates of each original are generated.

The Kaggle font dataset has 16,600 files but many exact duplicates,
font-key labels rather than true Maya sign identities, and no verified
crosswalk mapping to TWKM. It is not legitimate to label Kaggle U_XXXX
keys as TWKM #44, #187, etc. without an expert-confirmed mapping.

## Actual model comparisons

Numbers below come from source-original-disjoint TWKM splits. The
original **ten-image known test set has already been inspected in earlier
development**; it is NOT a blind independent external test.

| Experiment | Internal training-only CV | Known validation | Previously exposed known test | TOK test correctly classified? |
|---|---|---|---|---|
| Existing Android Research v4 MobileNetV3 | n/a | **8/10** | **7/10** | No (#187 instead of #44) |
| 128px TWKM-only CNN | n/a | 6/10 | 4/10 | No |
| 128px Kaggle→TWKM CNN | n/a | 6/10 | 5/10 | No (#548 instead of #44) |
| HOG shape SVM | 11/45 | 1/10 | 2/10 | No |
| Frozen ImageNet EfficientNet-B0 + SVM | 27/45 | 8/10 | 6/10 | No (#187) |
| Frozen DINOv2-Small + SVM | 28/45 | 8/10 | 6/10 | No (#291) |

The HOG hyperparameters, ImageNet backbone/representation, and DINOv2
hyperparameters were selected with **training-original-grouped CV**, not
by repeatedly fitting to the historical TOK test.

None of these candidates establishes improvement over v4 on the small
available validation set. Larger architectures or 128px upsampling alone
did not solve the problem.

Research runs:
- 128px two-stage training:
  https://github.com/chuahws-wm23/MayaLanguageTranslatorApp/actions/runs/43680463041
- Shape HOG SVM:
  https://github.com/chuahws-wm23/MayaLanguageTranslatorApp/actions/runs/43686743032
- Frozen ResNet18/EfficientNet:
  https://github.com/chuahws-wm23/MayaLanguageTranslatorApp/actions/runs/43686905471
- Frozen DINOv2-Small:
  https://github.com/chuahws-wm23/MayaLanguageTranslatorApp/actions/runs/43687040015

### Unknown sign research

An explicit detector used **45 supported-sign training originals**
plus **50 unsupported drawings from 25 sign identities** for fitting,
then validated on **10 known originals and 30 unrelated drawings from
15 other unknown sign identities**. It was finally tested on the ten
previously exposed known drawings and **80 unknown drawings from 40
further sign identities**.

| Metric | Result |
|---|---:|
| Validation known correct-and-accepted | 8/10 |
| Unknown validation rejected | 20/30 |
| Previously exposed known test correct-and-accepted | 6/10 |
| Further unknown sign identities rejected | 54/80 (67.5%) |
| Further unknown signs accepted incorrectly | **26/80 (32.5%)** |

This failed the provisional unknown-sign reliability gate. **Do not
deploy** the research detector. Existing v4 remains an experimental
possible-match system, not an open-vocabulary sign translator.

Unknown-sign experiment:
https://github.com/chuahws-wm23/MayaLanguageTranslatorApp/actions/runs/43687406631

## Software delivered in this round

- Reproducible scripts and GitHub workflows for original-grouped
  HOG, ImageNet EfficientNet/ResNet, and DINOv2 few-shot comparisons.
- New test of a classifier for supported versus unsupported TWKM sign IDs,
  including identity-disjoint negative examples, meaningful correct-
  accepted and false-accepted metrics.
- A **local verified real-photo manifest** with path, source/origin
  grouping, licensing, specialist label verification and train/val/test
  split, with duplicate-image / train-test leakage checks:
  `dataset/scripts/validate_real_glyph_photos.py`.
- Independent-photo ONNX evaluation:
  `ml/research_v4_single/evaluate_heldout_photos.py`,
  producing per-sign precision/recall/F1, correct-and-accepted,
  wrong-accepted, unknown rejection, top-three results, and 95%
  Wilson intervals. Python views approximate Android, not binary parity.
- Source-groups and real photos stay **local**, with
  `dataset/local_verified_photos/` ignored by Git.
- Synthetic-code regression tests of both data audit and scoring logic
  passed in Actions. No new live camera photographs were provided,
  so none have been fabricated as an accuracy dataset.

## Proposed release gate (not yet achieved)

Before calling the system "reliable" we need *independently sourced,
correctly identified individual glyph photos* and negative examples:

1. >=100 independent known-sign test photographs, >=10 for each of
   the ten supported signs; >=100 independent unsupported-sign
   photographs, grouped by underlying original artifact/drawing.
2. Lower 95% Wilson confidence bound for **correct-and-accepted**
   known-sign photos >=80%.
3. Upper 95% Wilson bound for incorrectly accepted unsupported signs
   <=5%.
4. Known-sign macro F1 >=.80, each sign recall >=.70.
5. Check Android preprocessing/model parity, app stability and inference
   latency on a phone. No test-set tuning.

These are conservative project acceptance targets, not a claim of a
certified archaeological reader. A larger new dataset could allow a
different threshold choice, but it must be assessed on independently
labelled samples.

## Potential next independent data source (not used yet)

Roboflow Universe's *Maya Glyphs segmentation model* by Maya Epigraphy:
https://universe.roboflow.com/maya-epigraphy/maya-glyphs-segmentation-model-behds

At inspection it listed **852 project images**, **377 class labels** and
a version with **2,403 images including augmentations**. It advertises
CC BY 4.0. These are *instance segmentation annotations*, not
automatically validated TWKM IDs; all class meanings and per-source
licensing must be checked. The project's version includes flips and
other augmentation, so its 2,403 files are NOT 2,403 independent source
drawings. A dataset download may require an account or API access;
**it was not imported or used in the training above**.

The Maya Hieroglyphic Text and Image Archive (Bonn) offers additional
artifacts/photographs, but individual sign crop labels and usage rights
need separate verification:
https://classicmayan.ulb.uni-bonn.de/

The Idrip/Idiap codical collection of 9,000+ segmented glyphs was
described in research literature, but a compatible downloadable labelled
version and verified number-system crosswalk were not established.

## Practical next action

Add **verified independent** TOK, K'ABA', HAAB and other signed photos
to `dataset/local_verified_photos/` and fill
`dataset/evaluation/real_single_glyph_photo_manifest_template.csv`.
Preserve the underlying source-group when photographing the same drawing
again. Then run:

```powershell
python -m pip install Pillow numpy opencv-python-headless scikit-learn onnxruntime
python dataset/scripts/validate_real_glyph_photos.py --manifest dataset/evaluation/real_single_glyph_photo_manifest_template.csv --root dataset/local_verified_photos
python ml/research_v4_single/evaluate_heldout_photos.py --manifest dataset/evaluation/real_single_glyph_photo_manifest_template.csv --root dataset/local_verified_photos
```

A blank template is not a dataset and will correctly report missing
labels. Do not misrepresent augmented photos of the same source as
independent evidence.

**Release decision:** keep the 10-sign v4 Android ONNX weights unchanged.
No training candidate, no unsupported-sign detector and no general
camera-photo glyph reader has passed the empirical reliability gate.
