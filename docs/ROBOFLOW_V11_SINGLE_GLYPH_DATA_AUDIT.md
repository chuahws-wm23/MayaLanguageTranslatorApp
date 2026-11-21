# Roboflow Maya Glyphs v11 — audited user dataset and code-only research baseline

**Date:** 2026-11-15  
**Input:** user-provided `Maya_Glyphs_v11.zip`  
**Upstream:** https://universe.roboflow.com/maya-epigraphy/maya-glyphs-segmentation-model-behds/dataset/11  
**Usage notice:** user ZIP `README.dataset.txt` and COCO license metadata state **CC BY 4.0**; credit Maya Epigraphy / Roboflow.

## ZIP verified

| Split | Images | Segmentation polygons | Unique source names |
|---|---:|---:|---:|
| Train | 2307 | 4046 | 756 |
| Validation | 74 | 184 | 74 |
| Test | 22 | 64 | 22 |
| **Total** | **2403** | **4294** | **852** |

- 377 actual alphanumeric glyph-code categories (378 COCO category records including the unused parent `maya-glyphs`).
- Each annotation has a polygon and bounding box. Each JPEG is 384×384 and the training portion contains RF flip-augmented variants.
- No exact original-source **filenames** or identical JPEG byte hashes overlap between train/valid/test. This cannot rule out equivalent physical drawings under different names.
- 156 categories have only **one** unique training source image; 145 have at least three. **Do not treat 2,307 augmented training JPGs as 2,307 independent examples**.
- 21 categories meet a minimal pilot criterion of >=4 independent training sources plus >=1 validation and >=1 test annotation. The pilot data contains 917 training annotations (**194 unique training source names**), 59 validation annotations and 34 test annotations.
- The ZIP does **not** contain a validated Roboflow glyph-code -> exact TWKM sign-number map. Do not use the 377 class codes as TWKM integers.

## Actual pilot trained on extracted 160px glyph crops

Code classes: `001, 009, 1BA, 1G1, 1SC, 2S2, 32A, 3M2, AA1, HE7, SG1, XH2, XQ3, YM1, ZA1, ZA2, ZB1, ZC1, ZH1, ZU1, ZZ1`.

| Model | Validation top-1 | Validation top-3 | Test top-1 | Test top-3 | Test macro F1 |
|---|---:|---:|---:|---:|---:|
| HOG + RBF-SVM | 37/59 (62.7%) | 48/59 (81.4%) | **21/34 (61.8%)** | 26/34 (76.5%) | 0.532 |
| Custom 96px 4-block CNN | 32/59 (54.2%) | 45/59 (76.3%) | 18/34 (52.9%) | 23/34 (67.6%) | 0.452 |

SVM hyperparameters were selected using **train-only source-grouped 3-fold cross-validation**. CNN checkpoint was selected on validation, and the reserved test was evaluated afterward. Both classifiers output only **Roboflow code categories**, not verified Maya readings or English translations. **34 test instances cannot establish dependable real-world recognition**.

The stronger research model is the HOG+SVM baseline, but it should **not** replace the Android TWKM Research v4 recognizer.

## Promising MHD/TWKM cross-reference — NOT ready to train from

The Roboflow class names resemble MHD sign classification codes. The **preliminary** Learner's Maya Glyph Guide by Sim Lee and John Pedersen provides MHD/TWKM correspondences:
https://mayaglyphs.org/concCMGG.html
https://mayaglyphs.org/

Possible **reading-group** associations include:
- TWKM #44 TOK: MHD `AB4`, `XV3`, `ZBC` (only 1/0/2 distinct training sources respectively in this ZIP).
- TWKM #32 K'UH: `PJF`, `SG1`, `SJ5` (1/13/0 train sources).
- TWKM #548 HAAB: `SB5`, `SS1`, `SS8`, `XH2` (5/1/1/11 train sources).
- TWKM #122 K'AK': `AB3`, `ZBB` (2/3 train sources).
- Other reading-group candidates are documented in the audit kit's `provisional_MHD_TWKM_reading_candidates_NEEDS_REVIEW.csv`.

**Caution:** Matching a *reading* or a broader Thompson-number group is not proof that every segmented Roboflow crop represents the **specific** TWKM sign-number variant used in Research v4. Concordance entries can relate multiple sign numbers and variants. The Roboflow filenames containing `TOK-` are primarily class `1C1`, which the concordance associates with **TOOK'** rather than automatically with TWKM #44 TOK. Each crop needs epigraphic and provenance checking **before** adding it to the TWKM supervised training set.

## Artifacts and next step

For review, extracted all 4,294 annotated regions into 160px grayscale glyph crops without stretching sign proportions. Created class-frequency, source-group, filename-reading-hint and candidate crosswalk CSVs, contact sheets, trained SVM/CNN checkpoints, and Python re-training/testing scripts. These binary/artwork artifacts are distributed as **conversation ZIP attachments**, not committed to this repository.

Preserve existing working Android v4 ONNX weights. Next: verify select MHD/TWKM mappings (prioritise `ZBC` for TOK) at the *individual source drawing* level with an epigraphy reference; preserve source-original train/validation/test separation, then retrain only with verified labels and test on truly independent photographs. Do not inflate accuracy by training on the previously exposed TWKM TOK test sample.
