# Android runtime validation and independent-photo evaluation

The existing catalogue drawings are sufficient to exercise decoding, model
loading, tensor preparation, retrieval, reference-image identity and UI state.
They are **not** independent camera photographs and do not establish real-photo
recognition accuracy. No expert-labelled independent photos were supplied.

## Reference workflow checks

The `glyph-runtime` job in `.github/workflows/android.yml` runs
`GlyphReferenceWorkflowTest` on Android API 29 x86_64. It uses the real bundled
ONNX model and galleries, not a mocked inference service:

1. Save licensed TWKM reference artwork as PNG, open it through the app's
   FileProvider and production `GlyphImageLoader`, then search the actual model.
2. Verify three known reference drawings retrieve the same catalogue graph and
   five distinct sign candidates, with finite similarity scores.
3. Exercise the comparison UI: run a search, verify five reference cards,
   switch light/dark themes, change the selected image and check old cards clear.
4. Submit a blank photo and verify it yields no candidates and helpful feedback.
5. Save light/dark screenshots and instrumentation reports as
   `GlyphReferenceRuntimeChecks` in the Actions run.

Run these checks on a connected local Android device/emulator:

```sh
gradle connectedDebugAndroidTest \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.chuahws.mayalanguageapp.presentation.translation.GlyphReferenceWorkflowTest
```

This is a runtime/component workflow test, **not** an automated camera permission,
external gallery-picker or full account-navigation test. The actual camera and
manufacturer-specific content providers still need physical-device smoke tests.

## When expert-labelled photos become available

Use `dataset/evaluation/real_single_glyph_photo_manifest_template.csv`:

```csv
image_path,twkm_sign,source_group,source_url,license,verified_by,split
```

Supply actual expert-reviewed labels and provenance, not generated predictions.
`UNKNOWN` means explicitly outside the shipped English-logogram catalogue.
The allowed known IDs are read from the actual shipped index, not manually
expanded without source evidence. `source_group` must identify the original
artifact/drawing; different photos/crops of one original stay in one split.

```sh
python -m pip install -r ml/logogram_expansion/requirements-retrieval.txt
python ml/logogram_expansion/prepare_independent_photo_test.py \
  --manifest /path/to/verified-manifest.csv --root /path/to/photos
```

The script audits all splits for provenance and leakage. Errors and duplicate
review warnings stop staging. Only **test** photos are copied into the ignored
`app/src/androidTest/assets/independent_photos/` folder. It refuses to overwrite
an existing batch. Source images are preserved. Keep staged photos, the test
APK containing them and reports private unless owners approve distribution;
they are not committed or sent to GitHub by this workflow.

Then run the actual Android evaluator. Keep the APK installed after the run so
the report remains available for `adb` extraction:

```sh
gradle connectedDebugAndroidTest \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.chuahws.mayalanguageapp.presentation.translation.IndependentGlyphPhotoTest
adb exec-out run-as com.chuahws.mayalanguageapp \
  cat files/reference-photo-report.json > /path/to/local-android-report.json
python ml/logogram_expansion/summarize_android_photo_test.py \
  /path/to/local-android-report.json --output /path/to/local-summary.json
```

The Android runner verifies staged image and catalogue hashes, loads the photos
through the production EXIF-aware image loader, runs actual on-device ONNX
retrieval, and records ranked graph identities and similarities. Decode/model
errors are recorded as empty results, not removed from the denominator.

The summary reports known top-1/top-3/top-5 retrieval, MRR@5, per-sign results,
image errors and source-group-weighted top-1. Unknown photos yielding neighbours
are counted separately. They are **not** claimed as correct rejections or false
accepted identifications: the app provides unconfirmed alternatives and has no
calibrated confirmation rule. No final-test threshold tuning is performed.

With no staged batch the independent-photo test is skipped with an explicit
reason. The summary tool rejects an empty report rather than inventing accuracy.
Readiness gaps remain visible and `release_approved` stays false. A separate
expert review, sufficiently large independent dataset, validation-only threshold
selection and broader device coverage remain necessary for a reliability claim.

## Multi-glyph reliability follow-up

The shipped APK has no optional v11/v18 detector assets. Automatic scanning now
tries a conservative whitespace/XY-cut proposal method for separated drawings
before the older dark-seam fallback. It supports single rows, single columns
and 2x2 layouts without requiring dark grid lines. It preserves very low-detail separated regions as one reviewable crop and
uses spatial size rather than ink mass to avoid losing thin-stroked signs.
It refuses blank images,
very small detached marks and dark-background inputs rather than treating those
as reliable multiple-sign detections. Connected/overlapping carvings still need
a trained detector or manual review. The detector finds layout regions, not
verified linguistic signs or Maya reading order.

Every result displays its actual uploaded crop and an Inspect / correct crop
action that opens that region in the existing single-sign crop editor. A region
with further separated shapes is withheld from automatic single-sign comparison
and asks for inspection. Other regions receive five unconfirmed alternatives,
with capture-quality feedback. A visually similar reference is not a confirmed
reading, even when the detector found a plausible boundary.

Device tests cover constructed 2x2 and single-row panels of source-labelled
reference drawings, per-region reference identity, and the correspondence
between an Inspect action and its crop. A separate development diagnostic (inspected during method selection) processes one
reference for each of all 135 logogram identities in constructed panels. It
records region proposals, matches at IoU >=0.5, and correct detected-and-top-1 /
top-5 retrieval, counting missed regions as failures. Its JSON is included with
the runtime screenshots. This is a source-reference layout diagnostic, not
independent photo detection accuracy or an accuracy guarantee.
