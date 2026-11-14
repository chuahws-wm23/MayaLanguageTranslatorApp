# Maya glyph recognition v2 — exploratory training experiment

**Status:** Trained research classifier on an isolated experiment branch. **Not deployed to Android.**

## Measured results

- First compact HOG/SVM/1-NN experiment: **2/9 correct (22.2%)** on nine held-out, distinct TWKM catalogue drawings, with method selection on only five validation drawings.
- Pretrained MobileNetV3-Small feature extractor plus trained RBF SVM: **35/38 (92.11%)** five-fold stratified cross-validation on original TWKM sign drawings; macro-F1 **0.9099**.
- Frozen ResNet18 plus RBF SVM: **34/38 (89.47%)** under the same five-fold methodology.
- These percentages use **different evaluation protocols**; they are not an apples-to-apples accuracy improvement claim. Methods were compared using cross-validation, so the best selected result has model-selection optimism.
- **No independent phone-camera accuracy has been measured.** Unknown glyph rejection, damaged carvings, user drawings, multi-sign blocks and unrestricted Mayan-to-English translation have NOT been validated.

The five sign categories were fixed from the pinned dataset availability audit before model training:

| TWKM sign no. | Proposed sign reading | Source catalogue gloss | Unique originals available |
|---|---|---|---:|
| 32 | K'UH | god / deity | 9 after cross-sign collision filtering |
| 184 | K'INICH | resplendent / sunny | 8 |
| 291 | SIIP | name of deity | 8 |
| 4 | NAAH | first / house | 7 |
| 44 | TOK | cloud / mist | 6 |

**The five trained classes do not include CHIJ (Deer) or KAKAW (Cacao)** because those concepts lacked enough independently labelled original drawings in this data source to support the same test. The Android app's old TinyGlyphNet v1 has not been replaced.

## Files

- `outputs/transfer/mobilenet_v3_small_backbone.pt` — pretrained MobileNetV3-Small backbone, **3.8 MB**.
- `outputs/transfer/mobilenet_v3_small_head.joblib` — trained SVM classifier, **285 KB**.
- `outputs/transfer/evaluation.json` — all six cross-validated comparisons, confusion matrices, and per-original predictions.
- `outputs/split_manifest.json` — original-drawing train/val/test assignment for the HOG baseline.
- `outputs/evaluation.json` — first HOG benchmark and its limitations.
- `audit.json` — catalogue variant counts.
- `train.py`, `train_transfer.py` — code to reproduce the two experiments.
- `predict_transfer.py` — offline demo to rank a single tightly cropped glyph; its label is always marked **unverified**.

## Try on Windows (from repository root)

Use Python 3.11. Install the CPU-only model dependencies and image tooling:

```powershell
python -m pip install torch torchvision --index-url https://download.pytorch.org/whl/cpu
python -m pip install "datasets>=4.0,<5" "pillow>=11,<13" scikit-learn scikit-image opencv-python-headless joblib
python ml/glyph_v2/predict_transfer.py "C:\path\to\one_glyph.png"
```

This demo is a **closed-set classifier**: it always chooses one of the five trained categories, even when the drawing is unknown. It must not be used as an authoritative translation; a reliable unknown-glyph rejection mechanism is still needed before mobile deployment.

The research model weights **do not require a runtime network connection**. The downloaded Python libraries are for PC testing/training, not a proposal to install Python on Android.

## Reproducible training and evaluation

Source: [TWKM Maya Glyphs on Hugging Face](https://huggingface.co/datasets/tadad/twkm-maya-glyphs), snapshot revision
`73fe9da1d975a5f1e588c7d42c952dfda53cefd0`, underlying TWKM catalogue retrieved 2026-09-04.

All source originals are grouped by **SHA-256**. Cross-sign exact-image collisions are removed. For the five-fold comparison, augmentations are grouped with their original, so no transformed copy of the tested drawing appears in that test fold. The benchmark covers **standardised catalogue drawings only** and has no independent archaeological or phone-photo test set.

**Credits:** The standardised glyph drawings are credited to Christian Prager, Text Database and Dictionary of Classic Mayan, University of Bonn (TWKM), CC BY 4.0. The catalogue's larger metadata has different/unspecified reuse terms; consult the dataset's RIGHTS.md and TWKM.

The independent 2018 paper *How to Tell Ancient Signs Apart? Recognizing and Visualizing Maya Glyphs with CNNs* provides background for pretrained-feature and sketch-specific approaches. Its published accuracy figures use **different data and cannot be used as this model's score**.

**Next before production:** Obtain independent handwriting/camera examples for target signs (especially CHIJ and KAKAW), validate meanings with a Maya epigraphy reference, evaluate unsupported-glyph rejection and latency on phone, then test a quantized Android inference implementation. Do not swap the v1 production model with this experiment yet.
