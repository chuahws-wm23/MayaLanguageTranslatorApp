# Research v4 — Kaggle auxiliary glyph-shape experiment

**Date:** 15 November 2026  
**Project:** Maya Language Translator Android FYP  
**Status:** Experiment trained and evaluated; **NOT deployed** to the existing Research v4 Android ONNX recogniser.

## 1. What was done

1. Audited the user-provided Kaggle `archive.zip` (16,600 PNG images, 64×64 pixels, filled/outline styles).
2. Discarded blank image renders and exact pixel duplicates, restricted the experiment to the **80 font-key classes represented in both styles**, and excluded any pixel-hash label collisions. The experiment retained **10,120** unique, nonblank samples.
3. Identified an important labelling mismatch. The original Mayan Glyphs font author says their design derives from Thompson main signs **719–781**. Current Android Research v4 reads **ten TWKM sign numbers: 4, 32, 44, 122, 184, 187, 291, 507, 548, 1030**. There is no verified overlap or font-key→sign mapping in the ZIP.
4. Created an 80-row crosswalk CSV with **all mapping fields unverified/blank** rather than inventing readings.
5. Trained a **small 80-class convolutional image classifier**, within the Research v4 experimental workflow, to predict **font key labels such as `U_003A`**, not Maya words or English meanings.
6. Used SHA-256 of decoded image pixels to prevent **exact duplicates** crossing the mixed-font train/validation/test sets. Training used 7,084 unique images; validation 1,518; test 1,518. This does **not** prevent visually related variants of the same underlying glyph from appearing in multiple splits.
7. Evaluated the classifier on a separate deterministic synthetic photo-artifact test (blur, shadow/lighting gradient and noise) and on a much harder **train-fill / test-outline** font-style change.
8. Saved trained checkpoint, TorchScript inference artifact, reproducible training/evaluation code, image split manifest and metrics.

## 2. Verified experimental metrics

| Task (auxiliary font-key recognition) | Correct / total | Top-1 accuracy | Top-5 |
| --- | ---: | ---: | ---: |
| Pixel-unique held-out images from BOTH trained fonts | **1,518 / 1,518** | **100.00%** | 100.00% |
| Same held-out glyphs after simulated lighting/noise/blur | **1,495 / 1,518** | **98.48%** | 99.80% |
| Train on filled font ONLY; evaluate unseen outline style | **520 / 5,090** | **10.22%** | 25.27% |

**These numbers are NOT accuracy of Maya-English translation, real photographs or arbitrary Maya signs.** The first two tests still use glyph designs from the same font families included in training. The third shows a large drop under a genuine style change, even when the font keys are shared. This is why promoting this classifier into the Android translator would be misleading.

Random-chance baseline for 80 balanced categories: **1.25%**.

### Dataset quality warnings

- The original ZIP contains **500 blank images**, and **6,419 exact image duplicates** (counting the first instance of each as original).
- **1,929 / 3,320 = 58.1%** of the supplied `test/` images are pixel-identical to images in supplied `train/`. The supplied train/test split cannot support an independent accuracy claim.
- Font key labels are keyboard positions, not glyph readings. The exact font/dataset derived-asset redistribution rights remain to be cleared before a public model release.

## 3. How capable is the model?

**Current Android Research v4 (unchanged):**

- Works offline; accepts camera/gallery images and automatically attempts single-glyph crops or multi-block segmentation on approximately regular panels.
- Has ten named TWKM sign categories. It runs two lighting treatments and recommends review if the predicted classes disagree.
- May give tentative catalogued readings for a supported sign in a suitable image. The old **7/10** research result used TWKM catalogue drawings, not independent phone-camera images.
- Does not read glyphs outside its supported training categories reliably; agreement across lighting treatments does **not** prove recognition, and it cannot translate a full ancient Maya inscription to coherent English.

**New auxiliary Kaggle-font classifier (trained, NOT integrated into Android):**

- Accepts a single 64×64 or resized grayscale image and predicts one of **80 font-key IDs**, e.g. `U_0041`.
- Works very well on synthetic examples resembling its training fonts; simulated noise/blur did not greatly affect this narrow benchmark.
- Performs **poorly (10.22% top-1)** when the test font *outline* style is not present in training. Real inscriptions are a much larger domain shift.
- Cannot output verified Thompson/TWKM sign identities, Maya readings or English meanings from the available data.
- Has no validated unknown-sign rejection, full-block linguistic parsing, real-camera accuracy or manuscript/stone recognition score.

### Deployment decision

**Do NOT replace `app/src/main/assets/maya_glyph_v4.onnx` or claim 80 translated signs.** The new artifact predicts font-key classes without verified meanings and does not generalise sufficiently to a held-out font style. Existing Android v4 stays the best available *labelled* model for the app. No v5 was created.

## 4. Files supplied

- `train_research_v4_font_shapes.py` — repeatable cleaning, duplicate-safe splits, both-style training and harder cross-style evaluation.
- `infer_font_shapes.py` — test a new single glyph-font image with the checkpoint. Prints **font key guesses**, not translations.
- `font_to_thompson_crosswalk_unverified.csv` — list of 80 font-key classes with sign mappings explicitly unverified.
- `results/evaluation.json` — exact metrics and up to 30 error examples per test.
- `results/experiment_history.json`, `results/split_manifest.json`, `results/font_keys.json` — reproducible experiment records.
- `results/research_v4_aux_font_shapes.pt`, `results/research_v4_aux_font_shapes.torchscript.pt` — trained auxiliary weights (academic use only; rights not verified for redistribution).
- `training.log` — per-epoch training log.

**Example local inference:**

```bash
python infer_font_shapes.py --checkpoint results/research_v4_aux_font_shapes.pt --image single-glyph.png
```

Requires Python, PyTorch, NumPy and Pillow. It will output a key like `U_003A`, **not** the corresponding Maya reading.

## 5. What's needed for a genuinely better Android v4

1. Obtain source-verified glyph labels for the new images (font codepoint → Thompson sign ID → TWKM sign ID and reading/gloss), and clear image/derived-model reuse rights. A collection derived from Thompson **719–781** has no documented overlap with the current ten v4 classes.
2. Obtain multiple **independently photographed or drawn** examples of the exact TWKM classes you wish to support, from different visual sources; isolate all samples of the same drawing in one data split.
3. Fine-tune **Research v4 itself**, using verified sign labels and domain-aware augmentation, and evaluate on an untouched independent phone-photo test set as well as unsupported glyphs.
4. Only if results improve and Android tests pass, export new ONNX weights and update v4's class mappings, asset size/hash and acceptance tests, preserving a rollback point.

## Sources and permissions

- User-supplied Kaggle ZIP: https://www.kaggle.com/datasets/sayantanroy10121999/mayan-glyphs-dataset
- Font author's source note: https://www.dafont.com/mayan-glyphs.font (credits ikarusmedia / Rafael Saldaña; signs 719–781; author restricts rehosting the font).
- Existing v4 report in GitHub: `docs/GLYPH_V4_AVAILABLE_DATA.md`.
- This report does not redistribute original font images. Any public release of derived model assets should follow a separate permissions review.