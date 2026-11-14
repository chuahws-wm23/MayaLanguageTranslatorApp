# Maya glyph v3 — experimental unknown-sign screening

## What was actually built

**Research v3** is an additional opt-in mode in the existing camera/gallery/crop screen.
It reuses the **same trained MobileNetV3-Small + RBF SVM ONNX weights as Research v2**, plus a small ~88 KB embedding gallery of 38 original TWKM drawings for a class-specific nearest-neighbour similarity and competing-class margin check.

**This is a new rejection strategy, not a newly fine-tuned MobileNet backbone.**
No cloud API, Python or external storage is required to run it on Android.

**Model v1 and Research v2 are preserved and can be selected independently.**
Research v3 covers the same FIVE proposed sign readings as Research v2: K'UH (32), K'INICH (184), SIIP (291), NAAH (4) and TOK (44). **CHIJ/Deer and KAKAW/Cacao are not recognised by v3**.

### Reported experiment

Evidence: `glyph-v3-open-set-experiment/ml/glyph_v3/outputs/evaluation.json`.

- Five-fold **out-of-fold original sign classification:** 35/38 (=92.1%) without rejection (closed set; same TWKM source).
- **Correct and accepted original drawings:** 24/38 (=63.2%) with inner-fold calibrated screening.
- **Unknown sign rejection:** average 90.6% over five fold models evaluating the SAME 100 TWKM original drawings, coming from 50 unknown sign IDs disjoint from 50 sign IDs used to tune rejection thresholds. This is **not 500 independent images**.
- Exploratory balanced correct-known/unknown-reject mean: 76.88%.
- The shipped provisional thresholds are **0.89 nearest-cosine similarity** and **0.05 cosine margin**. These thresholds were derived from the same small catalogue experiment, so the production classifier using all 38 originals has not been independently scored.

**Important methodological limitations:** These are standardised glyphs from one catalogue, not an externally sourced handwriting/photo dataset. Training/validation/test originals are separated using image SHA and sign identity, but the catalogue format is shared. Sign readings and English glosses need specialist review. Do NOT report these results as phone photo recognition accuracy or as archaeological translation accuracy.

### Why v3 rejects

V2 must choose one of five classes even for an unrelated drawing. V3 also compares the image's MobileNet feature vector to a reference gallery: it only shows a possible meaning when the predicted class matches an example strongly enough and is separated from the competing classes. If not, it shows **No reliable supported match** and does not display an English word as a translation.

This is not a guaranteed unknown detector. Previously unseen writing styles, damaged signs, low-light photos, very different glyph variants and cropped images can still be misclassified or rejected.

### Android implementation and build

- `GlyphV3Screening.kt`: class-specific cosine and margin calculations, checksum-verified gallery loader; built-in pure-JVM unit tests.
- `MayaGlyphRecognizerV2.kt`: shared ONNX backbone/SVM inference with a screened v3 return type; no duplicated neural weights.
- `GlyphRecognitionViewModel.kt`: separate result and model mode; cancels or discards stale results during crop/model change.
- `ImageTranslationScreen.kt`: added **Research v3 (screened)** selector, distinct reject/possible-result UI and unverified disclaimer.
- `app/src/main/assets/maya_gallery_v3.bin`: float32 reference features extracted from original TWKM drawings; **no source glyph photos shipped in the APK**.
- Existing `maya_mobilenet_v2.onnx`, `maya_svm_v2.onnx`, `maya_glyph_v1.mgl` unchanged.
- Android 7.0+ required due to ONNX Runtime.

New v3 tests check strong accepted matches, low similarity reject, ambiguous reject, invalid class and malformed feature vector. Additional on-device acceptance needed on a real phone and with photos distinct from the training catalogue.

## Next real-world tests

Collect images from independently licensed sources or user-made photographs with **expert-checked sign labels**. Keep source, attribution, expected sign ID, photographer/drawing variation, device and an unused test split. Do not copy FAMSI drawings into public datasets without checking their separate publication permission requirements. The Idiap Maya Codex dataset is access-controlled and cannot be treated as a freely downloadable benchmark.

Only after independent photographs or drawings are evaluated can real-world accuracy and unknown false-accept rates be claimed.

TWKM source: `tadad/twkm-maya-glyphs`, pinned revision `73fe9da1d975a5f1e588c7d42c952dfda53cefd0`; standardized drawings by Christian Prager, University of Bonn / TWKM, CC BY 4.0. The catalogue's broader metadata has separate reuse terms.
