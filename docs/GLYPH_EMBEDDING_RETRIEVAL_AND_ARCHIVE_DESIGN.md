# Reference retrieval and archive design

## What the source actually contained

Work is based on `fyp-development` commit `22e9b21`. `main` is the older
text-translator starter. The default single-sign screen did **not** run its
neural models: it searched a binary-overlap gallery, discarded every result
unless overlap >=0.60 and the next-sign gap >=0.10, then displayed one candidate
as a translation. Thus small alignment differences could hide useful nearest
references, and a surviving shape match could look like a confirmed meaning.

The separate v4 fine-tuned MobileNet classifier covers only ten signs. Its
previous 7/10 catalogue holdout result does not establish camera accuracy. The
repository contains no independently verified phone-photo evaluation corpus.
The broader logogram catalogue has 366 drawings for 135 sign identities; the
phonogram catalogue has 382 drawings. These are not hundreds of labelled
independent photographs per class.

## Implemented changes

- Search reuses the **existing pretrained MobileNetV3-small feature ONNX**
  (`maya_mobilenet_v2.onnx`, 576 dimensions), without invoking its five-class
  SVM. No neural weights were retrained and no labels or meanings were invented.
- Precompute L2-normalized reference embeddings; normalize one query embedding
  and rank rows by cosine similarity. Keep the best actual drawing for each
  distinct sign (or syllable), with five neighbours in the single-sign UI.
- The runtime verifies model, metadata, reference order, artwork and feature
  hashes before searching. A mismatched gallery fails visibly, rather than
  silently attaching a feature row to the wrong reference image.
- Match the Python and Kotlin tensor contracts explicitly: canonical 64x64
  foreground mask, inversion to dark-on-light, float bilinear resize to 224,
  ImageNet normalization, NCHW. Android image canonicalization still uses
  Android bitmap resizing; photographic parity must be checked on a device.
- Identical normalized bitplanes are prioritized and labelled “Same normalized
  reference drawing.” That is a drawing-shape equality check, not proof of
  original-file equality, an inscription reading or archaeological identity.
- Other usable images get ranked **unconfirmed alternatives**, each with the
  corresponding image, TWKM identity, source-proposed gloss and similarity.
  Cosine is displayed as a number, never a fabricated confidence percentage.
- No unvalidated score threshold turns a neighbour into a confirmed identity.
  Blank/unusable foreground images return no candidate. Dense full panels
  prompt the user to use the existing multi-glyph tools or isolate a sign.
- Capture hints cover low contrast, darkness, low resolution and few sharp
  edges. They are heuristics, not validated blur or incompleteness detectors.
  Missing strokes cannot reliably be detected from the supplied data.
- Inference, feature loading and image processing stay on a worker dispatcher.
  Search jobs cancel when the photo/crop changes or the screen leaves, preventing
  obsolete results from being attached to a new image.
- The main scanner now shows Camera/Gallery, then only Single glyph and Multiple
  glyphs. Choosing a mode starts the comparison; manual cropping and research
  controls have been removed from this flow. Automatic preprocessing and
  detected-region extraction remain internal. History, favourites, dictionary,
  text and voice features remain available.
- Replace indigo/teal gradients with parchment, obsidian, muted jade and antique
  gold. Serif display typography, quieter surfaces, recent actual scan previews,
  ranked artwork cards and expandable historical notes create an archive identity.
  Light/dark palettes apply to the existing history, saved and detail screens.

## Measured development diagnostic

Run from the repository root:

```sh
python -m pip install -r ml/logogram_expansion/requirements-retrieval.txt
python ml/logogram_expansion/build_reference_embeddings.py
python ml/logogram_expansion/evaluate_reference_embeddings.py
```

The exported assets are checked in; Python is not required to run the app.
The diagnostic holds out one sorted graph variant per identity, searches only
other rows, and excludes binary-identical artwork from candidates. Both methods
use exactly the same query/gallery split. It compares **ungated ranking**, not
accepted/confirmed identifications. The logogram split follows the repository's
existing diagnostic. The phonogram comparison groups output by syllable to
match the production UI.

| Development task | Queries | Old overlap top 1 | Embedding top 1 | Old top 5 | Embedding top 5 |
|---|---:|---:|---:|---:|---:|
| Logogram identity | 135 | 10/135 (7.41%) | 42/135 (31.11%) | 19/135 (14.07%) | 55/135 (40.74%) |
| Phonogram syllable | 71 | 10/71 (14.08%) | 19/71 (26.76%) | 19/71 (26.76%) | 29/71 (40.85%) |

Macro precision / recall / F1:

| Task | Old overlap | Embeddings |
|---|---|---|
| Logogram | .0605 / .0741 / .0638 | .2317 / .3111 / .2545 |
| Phonogram | .1042 / .1389 / .1157 | .2112 / .2603 / .2247 |

Full per-query ranks, labels and confusion matrices are in
`ml/logogram_expansion/reports/`. Errors remain substantial. This diagnostic was
inspected during method selection: **it is development evidence, not a blind
final test, independent artifact test, camera test or translation-accuracy claim**.
The proposed one-pixel tolerant overlap approach was also explored and did not
improve logogram top-1 ranking; it was not deployed.

## Build and verification

Use Android Studio with JDK 17, Android SDK 35 and Gradle 8.11.1. Firebase
configuration is optional for the existing development repositories.

```sh
gradle testDebugUnitTest assembleDebug
```

Validation in this workspace: `testDebugUnitTest assembleDebug` passed;
168 JVM tests, zero failures/errors/skips. This includes embedding
normalization/polarity, artwork-row ranking, distinct identities, uncertainty
alternatives, and capture-hint tests.

Install `app/build/outputs/apk/debug/app-debug.apk` on Android 7.0+.
Open Scan, choose a photo, then Single glyph or Multiple glyphs. A normalized
reference equality shows that reference; otherwise an explicit “No exact match
found” message precedes up to five nearest unconfirmed alternatives. Blank
images do not receive invented neighbours. Multiple mode outlines detected
regions and displays one selected region's results at a time. Check a second
photo, both modes, blank/dense images, history and favourites on a device.

## Dictionary pagination and original artwork

Word signs and syllables display at most ten entries per page, with previous,
next and editable page-number controls. Search/category changes reset the page.
Word-sign headings use the Maya reading, followed by the English gloss. Sorting
follows a Romanized reading convention, not a claimed alphabet of hieroglyphs:
`a, b, ch, ch', e, h, i, j, k, k', l, m, n, o, p, p', s, t, t', tz, tz', u, w, x, y`.
Typography is normalized and `ts` spellings collate with `tz`.

Dictionary and nearest-reference images use 683 original licensed drawings,
512 pixels on their longest side, instead of enlarging 64-pixel binary masks.
Original files remain unchanged. The extra display pack adds about 30 MB of
assets; it does not improve or alter model inference. The 64-pixel matching
assets and their embeddings remain unchanged. Display images load on a worker
thread into a bounded 12 MiB cache, with the existing binary drawing as fallback.

Source: [tadad/twkm-maya-glyphs](https://huggingface.co/datasets/tadad/twkm-maya-glyphs),
revision `73fe9da1d975a5f1e588c7d42c952dfda53cefd0`, graph drawings by Christian
Prager / TWKM, University of Bonn, CC BY 4.0. Attribution, individual source URLs,
SHA-256 and dimensions are recorded in `assets/reference_art/index.json`.
Rebuild with Python, `pyarrow` and `Pillow` installed:

```sh
python ml/logogram_expansion/build_reference_art.py --parquet /path/to/graphs/train-00000-of-00001.parquet
```

The builder verifies each original against the existing catalogue source SHA
and license; runtime checks each displayed file's SHA and dimensions. No pixels
were synthesized to claim a higher source resolution.

## Interactive region review

In Multiple glyphs mode, tap a detected box or its numbered chip to select a
region. The highlighted box, selected chip and preview share the same selection.
Numbered chips live in the “Glyph by glyph” result card, replacing the lower
arrow navigator; the photo no longer repeats the chip row. Chips provide keyboard and screen-reader
access; the photo exposes the selected region as its accessibility state.
Only the selected region's comparisons are rendered. There is no manual crop UI.

A shared aspect-fit transform drives both drawing and hit testing. Taps in
letterboxing or gaps do not select unrelated boxes; nested boxes choose the
smallest containing region. Unit tests cover landscape/portrait transforms,
empty gutters, overlaps and invalid coordinates. Android instrumentation loads
an actual constructed reference panel, runs detection and checks box taps,
chip selection, the absence of duplicate arrow controls and blank-photo recovery.

Detected-region display previews retain up to 512 pixels on their longest side
instead of 128, without upscaling small source images or changing inference.
Unreadable single-glyph photos receive actionable lighting/focus tips and no
invented neighbours. Resolution hints use the loaded source dimensions rather
than the downsampled analysis preview, so wide high-resolution photos are not
incorrectly called small. These hints remain heuristics, not measured blur,
unknown-sign or recognition-confidence classifiers.

## What is needed for reliable recognition

Collect licensed, expert-verified **independent original** drawings and camera
photographs, including visually similar signs, unsupported signs, non-glyphs,
multiple-sign blocks and bad captures. Record artifact/drawing lineage, device,
lighting and a bounding box for each single sign. Split by original physical
artifact/drawing before augmentation; multiple crops/augmentations of the same
original must never cross splits. Audit conflicting labels and duplicate images.

Start with >=10 independent test photographs for each of the ten v4 signs and
>=100 out-of-scope photographs, as in the existing independent-photo release
gate. Expanding to all 135 logograms needs balanced source-diverse training and
validation examples for those signs, beyond the current 2–few reference drawings
per identity. Use class-balanced sampling, modest rotation/scale/illumination
and camera-noise augmentation on **training only**, early stopping and a
source-separated validation set to compare fine-tuned MobileNet against larger
pretrained embeddings. Keep the final photo test untouched.

Select similarity and best-vs-runner-up thresholds on validation **known and
unknown** queries; report correct-and-accepted rate, wrong acceptance of unknowns,
per-sign recall, precision/F1, top-k retrieval and confusion with confidence
intervals on the held-out test. Include hard lookalike pairs. Until those data
exist, retain alternatives-only output and do not claim calibrated confidence.
See `RESEARCH_V4_INDEPENDENT_PHOTO_RELEASE_GATE.md` and the existing verified-photo
manifest validator for the project's independent-source evaluation requirements.

## Android runtime and photo-test follow-up

The reference-only instrumentation workflow and optional audited real-photo
evaluation runner are documented in
[Android runtime and photo evaluation](GLYPH_ANDROID_RUNTIME_AND_PHOTO_EVALUATION.md).
No independent photo test results are available; no expert-labelled batch was
supplied. The included staging/summary fixtures test tooling, not accuracy.

## History and profile follow-up

History shares the ten-entry pagination rules used by the dictionary. Its sticky
search area shows entry ranges and page controls, resets to page one for a new
search and clamps the current page after deletions. Scan pictures, proposed
meanings, dates, favourites and deletion confirmation remain available.

The Profile page uses an archive identity card, account facts and grouped
account/preferences sections. Editing, photo selection, password recovery,
admin visibility and sign-out keep their existing callbacks. Profile pictures
use bounded, EXIF-aware decoding and a readable placeholder in both themes.
The About section distinguishes glyph reference suggestions from the modern
Yucatec text/voice dictionary and describes photographic recognition as
experimental. No recognition weights or reference data changed in this UI update.
