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

## Measured current baseline

[Run 44123424781](https://github.com/chuahws-wm23/MayaLanguageTranslatorApp/actions/runs/44123424781) evaluated commit `762f4d6bd7adab56687d73645a07bec4d6851677` with the production Android pipeline. The machine-readable result is `ml/logogram_expansion/reports/public_photo_benchmark_baseline.json`.

| Slice | Inputs / artifact groups | Source reading in top 1 / 3 / 5 |
| --- | --- | --- |
| Source-described original photographs | 2 / 2 | 0 / 0 / 0 |
| Operator close-up, sharing one original artifact | 1 / 1 | 0 / 0 / 0 |
| Unlabelled original photographs | 3 / 3 | Unscored |

All six inputs decoded/executed without runtime errors. The detector proposed zero regions for every input; no expert boxes are available, so no detection accuracy is reported. The illumination control also failed to recover the described reading in the top five. There is no demonstrated improvement over the earlier development probe.

The emulator retrieval round-trip ranged from about 51 to 203 ms (median about 51 ms, six inputs). This includes 25 ms busy-state polling and is **not pure inference latency**, camera latency, or a physical-device performance guarantee. Do not use this tiny run to select a speed/accuracy trade-off.

Decision: preserve the production model, weights, reference bank and thresholds. The concrete next data task is specialist review of the queue and collection of additional uninspected artifacts for a final test. The reviewed public objects can help development but cannot be relabelled as an untouched benchmark after inspection. Real-photo reliability remains unestablished.

The committed input-manifest and runtime snapshots retain source metadata and complete production/control candidate lists, with binary mask previews omitted. Recompute the committed baseline without downloading photos:

```sh
python ml/logogram_expansion/photo_benchmark.py \
  --manifest ml/logogram_expansion/reports/public_photo_baseline_inputs.json \
  --report ml/logogram_expansion/reports/public_photo_baseline_runtime.json \
  --output /tmp/public-photo-baseline.json
```

## Scanner recovery and cutoff comparison

The scanner offers **Try another photo** and **Browse dictionary** after an unreadable image, an unsuccessful region scan, or a result below the display cutoff. Dictionary guidance points to **Word signs → Find by visible clues**. A readable single glyph with a low score gets a separate message from a photo the app cannot read.

A replay of the six stored production candidate lists gives the following display counts using a strict greater-than cutoff. These counts measure how often a suggestion would appear in this development sample.

| Similarity cutoff | Photos with a displayed suggestion |
| --- | ---: |
| 75% | 5 / 6 |
| 80% | 3 / 6 |
| 85% | 1 / 6 |
| 90% | 0 / 6 |
| 95% | 0 / 6 |

The one suggestion above 85% comes from an unlabelled photo. The source-described AJAW originals and close-up had top scores of 78.91%, 72.92%, and 84.26%, respectively. The gap between first and second candidates ranged from 0.18 to 4.25 percentage points across the six photos. The cutoff and gap cannot be validated with these cases because there are no expert sign labels or verified unknown examples. The three AJAW cases also lacked an AJAW-compatible reference in their top five, so a lower cutoff cannot recover that reading from these rankings.

For the next training dataset, have a reviewer label exact sign IDs and visible glyph boundaries, and record explicit out-of-catalogue examples separately. Keep all views and crops of each object together in the split. Use the validation objects to compare additional variants, field-photo training, and acceptance rules, then report performance on held-out objects.

## Check for close alternatives

The scanner now requires a two-point similarity lead over the next distinct sign when proposing a ranked reference. Exact drawing matches still use the existing display cutoff. The rule also applies when saving new scan results for History and PDF export.

In a development diagnostic using the bundled logogram embeddings, each drawing was queried after excluding byte-identical masks from the gallery. Across 366 queries with a same-sign drawing remaining, the 85% rule displayed 346 suggestions, including 104 correct sign IDs. Adding the two-point gap displayed 131 suggestions, including 66 correct sign IDs. The gap withheld 177 of the previously displayed wrong suggestions and 38 of the previously displayed correct suggestions. All five held-out TWKM #122 drawings had the wrong sign at rank one and a gap below two points.

These are same-catalogue drawing results. The exact fire redraw reported by the user and independently labelled photographs still need evaluation. Weights and gallery contents were kept at the baseline after a simple bitmap-overlap blend reduced overall ranking accuracy in this diagnostic.
