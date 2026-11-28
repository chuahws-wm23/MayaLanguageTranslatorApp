# Real-photo generalization improvements and remaining limitations

Date: 2026-11-15 · Branch `fyp-development` · **Android Research v4 ONNX remains unchanged**.

## What was implemented in Android

- The photo-results card now includes **Not my glyph / unsupported sign**. This is an explicit **user action**, not a model-based unknown detector. It withdraws the visible possible meaning and disables copying that suggestion. The other model suggestions remain labeled as *unverified*.
- Correct-glyph review now lists **all ten actual TWKM categories**, including their catalogue reference images, rather than only the top three guesses. An Unknown / other option is first and selected by default.
- An **Export selected glyph crop (PNG)** action writes the user's current crop via Android's document chooser. The existing explicit CSV correction export continues to work. Both exports are local and user-initiated, never automatic upload/training.
- The CSV rejects arbitrary sign numbers to prevent accidental wrong-label entries, while still accepting the historical `UNKNOWN` and current `UNKNOWN_OR_OTHER` identifiers.
- JVM unit tests check available TWKM categories and correction input validation.

**This improves safety and creates a way to acquire independent training examples; it does NOT claim that the model now recognizes unsupported signs automatically.** Users must label/report honestly, and a qualified reviewer must check photo provenance and glyph identity before any exported item is used for supervised training.

## Model experiments from Roboflow v11

Source: user-supplied Roboflow Maya Epigraphy v11 COCO segmentation ZIP (CC BY 4.0).

The initial 21-class HOG classifier uses a single global 64px descriptor. A new source-grouped-CV-selected **two-view spatial HOG** concatenates global and interior HOG64 descriptors (image centre slice 14%–86% of each axis), then RBF SVM C=10.

| Research comparison | Existing global HOG | New spatial HOG |
|---|---:|---:|
| Train-only source-grouped CV correct (917 training instances) | 537/917 | **547/917** |
| Validation (59) correct | 37/59 | 37/59 |
| Previously exposed small test (34) correct | 21/34 | **22/34** |
| Previously exposed test top three | 26/34 | **28/34** |

One more correct glyph on a 34-item test does not establish a robust accuracy gain. Both models are still **closed-set 21-code classifiers**, *not* TWKM readers. External Manik' image remains classified incorrectly as ZZ1; Manik' is not one of their supported code categories. The new code-only research model is delivered as the conversation's `Maya_v11_Spatial_Research_Kit.zip`, which contains source-group metadata, 1,010 selected annotated crops, trained .joblib model, reproducible training script, example and tester. **Do not ship this model in Android or assign TWKM readings to the codes without a verified crosswalk.**

### Further controls tested; NOT shipped

- Naively expanding to all 377 dataset codes using nearest HOG exemplar matched 24/64 official test annotations (37.5%); the Manik' image still nearest-neighboured to ZZ1. Many codes have only one original drawing, and no proof of mapping to TWKM. Adding more forced-choice classes alone is not enough.
- A visual similarity rejection threshold rejects some unsupported glyphs but also rejects many correctly supported new variants. Manik' can score **0.8175 cosine similarity** to training examples of an *incorrect* class, so naive thresholds and model agreement alone cannot prove class membership.
- Previous dedicated unknown-sign detector incorrectly accepted 26/80 separate unsupported TWKM drawings; this failed the unknown-release gate.

## How to use the improved Android review flow

1. Update your checked-out `fyp-development` branch in Android Studio (first check for uncommitted edits).
2. Open **Image → Maya glyph recognition**, upload one isolated glyph and choose a complete crop.
3. For an incorrect result, tap **Not my glyph / unsupported sign**. This marks your personal review state; it does **not** train the AI.
4. Tap **Review / correct the glyph label**, select **Unknown / other** or any of the ten listed TWKM reference signs.
5. Export **selected glyph crop PNG** and **CSV**. Keep the pair together with a unique source/artifact identifier and image rights. Both remain **unverified**.
6. Move permitted samples to the gitignored `dataset/local_verified_photos/` directory, obtain specialist review, update the existing independent-photo manifest, and run the audit/evaluation scripts already in the repo. Do not merge source-overlapping train/test photos.

## What is still necessary to truly fix recognition

- New independently verified original drawings **of the same TWKM sign identities**, not just augmented duplicates. Prioritize #44 TOK, #187 K'ABA', #548 HAAB, and visually similar hard negatives.
- A verified MHD/Roboflow-code ↔ TWKM sign-instance crosswalk, with readings checked in context, not inferred from alphanumeric code similarity.
- Retain an unseen artifact/photo group for real-world release assessment, measure incorrect acceptances and per-class recall, and replace v4 only if a new model passes predefined thresholds.
- Consider a calibrated *abstention* architecture after enough data, rather than showing a forced answer as a fact. Simple top-1 softmax percentage is not calibrated confidence.

Reproducibility and earlier failures: [Roboflow data audit](ROBOFLOW_V11_SINGLE_GLYPH_DATA_AUDIT.md), [Manik failure](ROBOFLOW_V11_MANIK_FAILURE_AND_NEXT_STEPS.md), [Research v4 release gate](RESEARCH_V4_RELIABILITY_REFINEMENT_2026_11_15.md).
