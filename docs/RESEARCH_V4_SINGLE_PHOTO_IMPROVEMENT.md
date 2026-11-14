# Research v4 — single-sign photo reliability, 15 November 2026

## Scope
Only the **single glyph** interface and v4 photo input handling were updated.
The ten trained TWKM categories and the ONNX weights are **unchanged**.
Existing multi-glyph features are preserved but moved behind an optional
panel-tools button to avoid confusing them with the requested single-sign test.

## Why all existing datasets cannot be concatenated as one labelled set
- **TWKM** contains visually labelled graphs and proposed readings. It is the
  only source presently verified for the ten sign identities in the Android v4
  classifier.
- **Kaggle Mayan Glyphs** contains 80 usable font *keyboard-key* labels, but
  no verified font-key-to-TWKM crosswalk. A generic font-key classification
  pretraining experiment exists; its 100% synthetic accuracy is NOT
  glyph-to-English accuracy, and its held-out font-style test was only 10.2%.
- **Kaikki / modern Yucatec dictionary** is text/lexicon data, not labelled
  images of Classical Maya glyphs.
Training the ten-class head using any unrelated font-key or dictionary label
would create false readings. Do not treat a coincidental Thompson/TWKM number
as a validated mapping: the catalogues use different numbering schemes.

## Changes
- Added a third (median-denoised) **image preprocessing view** for Research v4
  single-glyph recognition. The trained ten-class model runs on each view;
  the original guess must agree with at least one other view.
- The result is still an experimental *possible match*, never a verified
  reading. Agreement does not detect truly unsupported glyphs or increase
  the trained class count.
- Promoted **Recognise this single glyph** to the primary photo action.
  Grouped the automatic/assisted panel scanners into optional tools.
- Added unit tests for median noise reduction and three-view consensus.

## Acceptance tests to run on actual Android devices
1. Use a clean, correctly labelled held-out TWKM image to verify the predicted
   sign number; repeat with a photo of that same drawing under shadow/noise.
2. Use different independently labelled real drawings of supported signs.
   Count correct / total, uncertain / total, and incorrect accepted / total.
3. Use signs outside the ten classes, blank/noise, arbitrary symbols and
   full inscriptions. Record false accepted guesses.
4. Check latency, repeated scans, gallery/camera, rotation and offline use.
5. Keep each underlying source drawing entirely in one train/test partition.

**No new claimed recognition accuracy** can be stated until labelled independent
photographs are evaluated. The prior 7/10 is the old catalogue-only figure.
Research v4 remains unchanged in version and trained coverage.