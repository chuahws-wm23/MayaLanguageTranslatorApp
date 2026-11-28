# Research v4 — user-supplied Kaggle Mayan Glyphs dataset audit

Source: https://www.kaggle.com/datasets/sayantanroy10121999/mayan-glyphs-dataset

**Decision:** Suitable for a cautious *visual-feature* experiment, but NOT yet suitable to replace `maya_glyph_v4.onnx` or extend its translations. Keep the Android model labelled **Research v4**; do not create v5.

## Verified ZIP audit

These statistics are from the actual user-uploaded `archive.zip`, not extrapolated from the accompanying paper.

| Finding | Count |
| --- | ---: |
| PNG images (64 x 64 grayscale) | 16,600 |
| Filled-style label folders | 84 |
| Outline-style label folders | 82 |
| Unique `U_XXXX` font-key folders across styles | 85 |
| Shared nonblank glyph-key classes across both styles | 80 |
| Completely blank rendered images | 500 |
| Pixel-identical duplicates beyond the first copy | 6,419 |
| Test images pixel-identical to a training image | 1,929 of 3,320 (58.1%) |

Blank font-key folders: filled `U_005C`, `U_007C`, `U_007E`; outlined `U_005E`, `U_00C4`. Each contains 80 training and 20 test PNGs.

The original 80/20 train/test folders are NOT an independent generalisation test because of pixel-identical leakage. The source images are generated font glyphs with transformations; they are not a representative independent photo collection.

## Labelling and licence blocker

`U_0041` means the font codepoint for key 'A' (not a verified Maya sign, a TWKM number or an English translation). The two style names trace to the Mayan Glyphs dingbat font published by ikarusmedia / Rafael Saldaña, derived from Thompson's catalogue: https://www.dafont.com/mayan-glyphs.font.

The ZIP provides no verified `font_key -> Thompson_id -> TWKM_sign -> reading/gloss` crosswalk. Consequently, training a sign-to-English head directly on `U_XXXX` would silently invent readings. The archive contains only PNG files and no licence manifest. The publication's displayed licence does not by itself establish the independently applicable dataset/font licence. Check source rights before redistributing images or models derived from these images.

## Safe continuation of Research v4

1. Keep the deployed 10-class ONNX model and its integrity SHA unchanged until a better evaluated candidate exists.
2. Use `dataset/scripts/audit_kaggle_font_dataset.py` on the local Kaggle ZIP. Exclude completely blank images and de-duplicate decoded pixels before internal experiments.
3. Obtain a source-checked font-key-to-Thompson-to-TWKM crosswalk before attaching proposed readings or English glosses.
4. If rights allow, trial visual-feature pretraining on *nonblank* Kaggle glyphs, then separately fine-tune on validated TWKM-labeled glyphs, within the **existing Research v4** experiment.
5. Set aside source-separated real photographed glyphs as a test set before tuning. Report both correct accepted signs and incorrect matches on unsupported signs; test multi-block cropping independently.
6. Only replace `maya_glyph_v4.onnx` and update its class mappings after a demonstrable independent improvement, Android unit and device tests, and rollback plan.

**Do not report the provided Kaggle train/test results as phone-camera or Maya-English translation accuracy.** Modern Yucatec Maya language translation and Classical Maya inscription reading remain different tasks.

Audit provenance: original ZIP provided directly by the project owner; duplicate detection by SHA-256 of decoded 64x64 grayscale pixel arrays.
