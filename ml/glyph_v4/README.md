# Model v4 — Expanded Maya Glyph Recognition Experiment

**Goal:** Classify ten TWKM sign classes, measure performance on drawings
excluded from training, and evaluate unsupported-sign rejection. These are not
general Maya-English translation or independently measured phone-photo accuracy.

## Dataset selection

Dataset: https://huggingface.co/datasets/tadad/twkm-maya-glyphs
Pinned snapshot: 73fe9da1d975a5f1e588c7d42c952dfda53cefd0.

The dataset includes many signs with only a single drawing. We select ten
catalogued signs with at least five distinct image originals:

| Sign no. | Reading | Catalogue gloss | Distinct images in audit |
|---|---|---|---:|
| 32 | K'UH | God / deity | 10 |
| 184 | K'INICH | Resplendent / sunny | 9 |
| 291 | SIIP | Deity name | 8 |
| 4 | NAAH | House / first | 7 |
| 44 | TOK | Cloud / mist | 6 |
| 507 | TZIMAH | Gourd | 6 |
| 548 | HAAB | Year | 6 |
| 122 | K'AK' | Fire | 5 |
| 187 | K'ABA' | Name | 5 |
| 1030 | K'AWIIL | Deity name | 5 |

These are **source-catalogue proposed labels**, not independently
expert-verified readings. Sign readings depend on the surrounding inscription.

## Evaluation safeguards

1. Use SHA-256 to identify originals; remove exact-image cross-sign collisions.
2. Set aside one validation and one test original per sign, before training.
3. Augment **training originals only**; never augment an excluded test image into training.
4. Compare pretrained MobileNetV3+RBF SVM and fine-tuned MobileNetV3, selecting
   only through validation results. Evaluate the held-out test once.
5. Reserve disjoint unsupported-sign IDs for rejection threshold calibration
   and unknown-sign final tests.
6. Report top-1 closed-set accuracy, correct-and-accepted known examples, and
   unknown-glyph rejection separately.

A predeclared experimental acceptance gate is **at least 50%** top-1 on
ten held-out known originals and **75%** rejection on 80 unsupported original
drawings, using a threshold selected on validation data only.

Even if this gate passes, the 10-image known test set is very small and all
images share the same TWKM source. No phone-photo or handwriting accuracy
can be claimed without an independent external dataset.

## Artifacts and reproducibility

GitHub Action: .github/workflows/glyph-v4-train.yml
Training code: ml/glyph_v4/train_v4.py
Outputs: ml/glyph_v4/outputs/evaluation.json, split_manifest.json,
metadata.json and selected model weights/classifier.

The selected v4 classifier will be converted to Android ONNX only if
the research test results justify an **experimental** mobile mode.
The previous v1/v2/v3 Android modes remain untouched in the meantime.
