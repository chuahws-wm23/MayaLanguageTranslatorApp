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
