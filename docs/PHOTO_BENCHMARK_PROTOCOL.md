# Photographic glyph baseline and optimisation protocol

## What is available now

Six inputs from five photographed objects are available through the pinned public-photo probe. Two originals have source-described AJAW readings; one operator close-up comes from the same Carlos vessel. Three other originals have no verified identities. These are **inspected development data**, not an untouched final test. No photograph currently has an expert-verified TWKM sign ID or exhaustive glyph-region boxes.

`dataset/evaluation/public_photo_development_review.csv` is a review queue, not training data. Sign IDs, reviewer names and boxes are intentionally blank. Source reading clues must not be converted automatically into sign labels. Every image has a source URL, licence, artifact group and pinned SHA. Original and crop stay in the same artifact group.

## Reproduce the current Android baseline

The **Public glyph photograph probe** workflow downloads the pinned source photos into ignored androidTest assets, runs the production Android image loader and recognition ViewModel, and records the same multiple-region proposal chain. It uploads exact inputs, raw predictions, model/manifest hashes, evaluated commit, a baseline JSON and an unlabelled review pack. The app and its reference gallery receive none of these photographs.

Locally, after staging and executing the existing probe:

```sh
python ml/logogram_expansion/public_photo_probe.py --run-android
python ml/logogram_expansion/photo_benchmark.py \
  --manifest app/src/androidTest/assets/public_photo_probe/manifest.json \
  --report public-photo-report.json --output public-photo-benchmark.json
python ml/logogram_expansion/photo_benchmark.py \
  --manifest app/src/androidTest/assets/public_photo_probe/manifest.json \
  --photo-root app/src/androidTest/assets/public_photo_probe \
  --output public-photo-review
```

The report recomputes reading compatibility at ranks 1, 3 and 5 from actual candidates rather than trusting stored hit flags. It separates original-photo and close-up slices. Unlabelled inputs remain unscored; no ground-truth boxes means detection precision/recall remain unavailable. Retrieval timing is an emulator diagnostic, not a phone performance guarantee. Green CI means successful execution and valid reporting, not correct translation.

## Expert-labelled benchmark

Use the existing `dataset/evaluation/real_single_glyph_photo_manifest_template.csv` and `prepare_independent_photo_test.py` for new, independently reviewed single-sign photographs. Include positive TWKM IDs, explicit reviewed UNKNOWN examples, reviewer identity, source rights and artifact groups. The audit rejects source groups shared across training/validation/test, exact pixel duplication and suspicious near-duplicates requiring review. No software audit substitutes for specialist verification.

Score real Android output with `summarize_android_photo_test.py` for top-1/3/5 retrieval, MRR@5, per-sign results, source-group means and runtime failures. Failed executions count as misses even if partial candidates remain. Duplicate image IDs and candidate identities are rejected. Unknown neighbour counts are not unknown-rejection accuracy.

For detection, supply a JSON with `format_version: 1` and an `images` list containing `id`, `sha256`, `source_group`, `reviewed_by`, `source_citation`, `exhaustive: true`, and `boxes` (normalized left, top, right, bottom). Use `--annotations regions.json` with the public baseline CLI. Missing annotations are excluded; an explicitly reviewed empty box list is a negative scene. Precision and recall at IoU 0.5 and 0.75 use one-to-one maximum-cardinality matching; duplicate predictions count as false positives. These metrics describe detected glyph regions, not correct sign identity or reading order.

## Model optimisation decision

1. Freeze dataset provenance and artifact-group splits before experimenting. Previously inspected public objects remain development material permanently.
2. Establish expert-labelled retrieval and detection baselines, including known misses and unknown examples. Show denominators, per-sign results and source diversity.
3. Compare preprocessing, photo/sketch embeddings and verified gallery expansion on training/development data. Fit thresholds only on validation artifacts. Retain baseline wins and losses instead of reporting only successful examples.
4. Run the chosen method once on untouched expert-labelled test artifacts. Measure unknown handling using an explicit abstention policy, not raw cosine similarity.
5. Promote a model only after evidence supports the change, then validate physical camera capture and live Firebase separately.

No model weights or production recognition threshold are changed by this tooling pass. A specialist must provide the missing labels; the current source titles cannot support an honest identification-accuracy percentage.
