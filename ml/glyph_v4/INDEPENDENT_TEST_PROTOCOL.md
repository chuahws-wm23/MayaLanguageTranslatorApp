# V4 next dataset: independent validation and test collection

## Why more labelled originals are needed

v4 correctly classified 7/10 TWKM held-out originals but only displayed a
correct result for 1/10 when its unknown-sign screen was active. Tweaking
that screen using the same ten exposed test examples would be overfitting.

**Do not use the v4 training/testing drawings again as a new final evaluation
set.** The v4 splits and SHA-256 source-image IDs are stored in
outputs/split_manifest.json.

## What to collect

Seek independently sourced, correctly labelled examples of each supported sign:
K'UH, K'INICH, SIIP, NAAH, TOK, TZIMAH, HAAB, K'AK', K'ABA', K'AWIIL.

A practical first pilot:

- **Known-sign photographs:** 5 independently drawn/source-verified variants
  for each sign, with 2–3 photos per drawing using different lighting and angles.
  Count these as 50 independent sign *drawings*, not 100–150 independent shapes.
- **Unsupported signs:** 30 or more source-verified images spanning signs not
  in the supported 10 categories. Count the distinct sign identities.
- Keep a written attribution/copyright record; do not collect watermarked stock
  pictures as reusable training assets.
- Avoid photographing any file copied from the TWKM training/validation/test
  set for an independent morphology test. Such copies can be used ONLY for a
  separate camera-versus-clean-image processing test.

## Required record for each image

Record: file name, sign number (or unsupported), proposed reading, verified
source URL or bibliographic reference, artist/photographer, original drawing
ID, source/usage license, device, lighting, photograph date, and intended
partition (development or final test).

Use source- and drawing-group-separated splits. Different photos of the
**same underlying drawing must remain in the same partition**.

## Two different evaluations

1. **Known-sign accuracy** — for each supported independent drawing, compare
   top-1 suggested sign against a checked sign label; also count whether v4
   accepted or rejected the correct result.
2. **Unsupported-sign rejection** — count unsupported sign identities and
   how many the model correctly rejects, rather than just assigning a
   plausible-looking supported sign.

Report counts for a test set fixed before training or threshold selection.
Report false acceptance and false rejection alongside top-1 accuracy.
Do not treat softmax or cosine values as calibrated true probability.

## Release criterion

At least 50% **correct and accepted** on independent original drawings,
reasonably low false acceptance of unsupported signs, and successful phone
tests of camera/gallery crop, rotation, latency and memory.
The previous 50% closed-set top-1 goal alone is insufficient.

If these conditions fail, preserve the working v3 model and retrain.
If they pass, consider v4 as the final candidate and verify on physical
Android devices before moving it into the main app flow.
