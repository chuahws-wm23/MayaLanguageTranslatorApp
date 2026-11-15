# Research v4: Python OpenCV single-glyph preprocessing benchmark

**Evaluation date:** 2026-11-15 (Malaysia)  
**GitHub Actions run:** https://github.com/chuahws-wm23/MayaLanguageTranslatorApp/actions/runs/43676472644  
**Research model:** unchanged `maya_glyph_v4.onnx` (MobileNetV3-Small, 10 TWKM sign classes).  
**Decision:** No replacement of Android preprocessing or ONNX weights.

## Implementation

The new scripts are:

- `ml/research_v4_single/opencv_single_glyph.py`: direct Python command-line recognition for a **single** image. Handles PNG/JPEG, EXIF orientation, transparent backgrounds, image size, Otsu-based glyph extraction, normalized 64px representation, and actual ONNX inference.
- `ml/research_v4_single/evaluate_opencv_single.py`: reproducible comparison of multiple preprocessing treatments against the same pinned TWKM validation originals and disjoint unsupported-sign IDs.
- `ml/research_v4_single/test_opencv_single.py`: regression tests for polarity, transparency, blank/tiny images, tensor dimensions, and real-ranked classifier scores.
- `.github/workflows/research-v4-opencv.yml`: automated execution and downloadable JSON/confusion CSV results.

The implementation **does not** hardcode glyph guesses or treat Kaggle font-key IDs as verified Maya readings. The same CNN runs on each image treatment. The Android project is intentionally unchanged pending stronger validation.

## Results

The following values describe the **paired acceptance** rule: a supported prediction is displayed only when the original CNN view and a second preprocessing view agree.

| Pairing with original input | Known validation correctly accepted /10 | Simulated-photo validation correctly accepted /30 | Unsupported signs wrongly accepted /80 |
|---|---:|---:|---:|
| Original + shadow correction (existing reference) | 8 | 22 | 79 |
| Original + CLAHE | 8 | 23 | 80 |
| Original + CLAHE + bilateral denoising | 8 | 23 | 80 |
| Original + adaptive thresholding | 8 | 20 | 75 |

**Predeclared selection gate:** preserve clean correct-accepted rate; gain at least 3/30 correctly accepted simulated-photo examples; and do not increase wrong acceptance of unsupported signs. None passed.

- SHA-separated source originals were used for the existing known validation, known exposed test, and disjoint unsupported-sign calibration/test groups.
- The synthetic-photo stress set consists of deterministic small rotations, gradients, blur and noise of the SAME validation originals; the 30 variations are **not** 30 independent sign drawings.
- The original v4 final catalogue test (one original per supported sign) was exposed during previous experiments; it cannot be called a new blind test.
- More sophisticated image processing does not automatically preserve crucial small differences between similar glyphs.
- Unknown or unsupported glyphs remain the major unresolved issue. Scores are relative to the model's ten supported classes, not calibrated likelihood of a correct reading.

## Capability and next experiment

**Currently possible:** perform offline ten-class single-glyph inference in Android using the prior pretrained model; experiment with alternative Python preprocessing; inspect cropped glyphs; display top-three model candidate scores and an attributed reference drawing.

**Not established:** improved independent real-camera recognition, reliable unknown-sign rejection, reading of arbitrary Maya signs, or full inscription translation.

The next meaningful training dataset must include source-verified **independently drawn** or photographed glyphs for each target TWKM sign and disjoint unfamiliar sign identities. Count drawings, not synthetic augmentations, as independent examples. Freeze a final test set before changing thresholds, then measure accuracy, precision, recall, F1 and known-correct/unknown-false-accept trade-offs on actual photos.

The experiment run contains downloadable `results.json`, `RESULTS.md`, per-sign confusion matrices, and precision/recall/F1 CSVs. Their metrics are from real test execution; no synthetic result is labelled as camera accuracy.
