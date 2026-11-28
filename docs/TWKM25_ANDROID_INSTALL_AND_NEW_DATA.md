# Opt-in 25-sign Android research and adding verified photos

## Current result and limitations

The **default** Android Research v4 recognizer remains 10 TWKM sign
identities. The new `TWKM 25 (research)` option only activates when you
**manually install** four version-matched assets. The source-graph cohort
used in earlier development scored 17/25 correct top-1, 12/25 correctly
recognized and accepted, with 38/40 other catalogue sign IDs rejected.
These are small previously exposed catalogue-image tests, **not real phone
accuracy**, open-vocabulary glyph recognition, or English translation.

The source-coded 25-sign mode only returns `Possible catalogue sign #N`
or `Not identified / needs review` for an individual glyph. It does
**not** give unverified English glosses or guess how to read compound
glyph blocks. The original YOLO v18 detector is separate and can continue
finding glyph **blocks**. A block may contain several signs.

## Install tested research model on Windows (Android Studio PowerShell)

1. Update the GitHub checkout after saving your local changes:

   ```powershell
   git status --short
   git switch fyp-development
   git pull --ff-only origin fyp-development
   ```

2. Download the GitHub Actions ZIP artifact called
   [Maya-TWKM25-Android-Research-Assets](https://github.com/chuahws-wm23/MayaLanguageTranslatorApp/actions/runs/43738491329/artifacts/14171148524)
   from the successful `Export opt-in 25-sign Android research model` run.
   GitHub Actions artifacts expire after their retention window; rerun the
   workflow on the same source commit when needed.
3. Extract the ZIP on your laptop, for example into `$env:USERPROFILE\twkm25`:

   ```powershell
   Expand-Archive "$env:USERPROFILE\Downloads\Maya-TWKM25-Android-Research-Assets.zip" "$env:USERPROFILE\twkm25" -Force
   Get-ChildItem "$env:USERPROFILE\twkm25" -Recurse -File -Filter "maya_twkm25_*" |
       Copy-Item -Destination ".\app\src\main\assets" -Force
   ```

   Four required files:

   - `maya_twkm25_feature.onnx` (pretrained visual features)
   - `maya_twkm25_svm.onnx` (25-class SVM classifier)
   - `maya_twkm25_gallery.bin` (training-only similarity reference bank)
   - `maya_twkm25_metadata.json` (TWKM sign IDs, calibrated research rule,
     checksums, provenance, sample counts, and parity metrics)

4. Gradle Sync, rebuild, run on emulator/phone. Go to **Image → Maya glyph
   recognition → Change research model (advanced) → TWKM 25 (research)**.
   Upload ONE isolated sign with a small margin, crop and select
   **Recognise this single glyph**. Compare proposed sign ID with a trusted
   sign reference. Do not count a compound block as a correctly read sign.
5. Compare Android and Python predictions of the **same single-sign crop**.
   Record actual on-phone latency, rejected unknowns and wrong accepted
   signs; report errors. The app has never been claimed independently
   accurate on real phone photographs.

The experimental assets are intentionally `.gitignore`d and are **not**
embedded in normal GitHub source or regular debug builds. If any asset
is missing or corrupt, the app shows a setup error; the default v4 mode
is unaffected.

## Adding new labelled photos, then retraining

The trainer can now **refit the SVM** while keeping MobileNetV3 frozen,
using existing TWKM originals PLUS your new verified photos. You do **not**
need to retrain the entire CNN from scratch.

1. Prepare one **isolated SIGN per photo**, not an entire inscription,
   and save images under `dataset/local_verified_photos/` (ignored by Git).
   Prefer genuinely different artifacts and carvers; avoid copies of the
   same TWKM catalogue illustration.
2. Create a CSV by copying
   `dataset/evaluation/real_single_glyph_photo_manifest_template.csv`,
   with these fields:

   | Field | Example |
   |---|---|
   | image_path | 32/artifact_a.png |
   | twkm_sign | 32 (or literal UNKNOWN if independently verified as outside the 25 supported IDs) |
   | source_group | museum-object-123 (all crops/photos of the SAME object have the SAME group) |
   | source_url | real artifact/source URL |
   | license | CC BY 4.0 or documented explicit research permission |
   | verified_by | actual person who verified sign identity; never fabricate expert approval |
   | split | train, validation or test |

   A `train` source group **must not** also appear under validation/test.
   Keep the test groups untouched until evaluation. An image that merely
   looks like sign 32 is NOT verified sign 32.
3. Keep a copy of the model ZIP's `maya_twkm25_metadata.json` in a local
   folder, e.g. `ml/catalogue_expansion/android_export`. The audit
   uses its **exact 25 TWKM sign IDs**, not the old 10-sign default.

   ```powershell
   python -m pip install Pillow numpy opencv-python-headless scikit-image scikit-learn
   python dataset/scripts/validate_real_glyph_photos.py `
     --manifest dataset/evaluation/real_single_glyph_photo_manifest_template.csv `
     --root dataset/local_verified_photos `
     --model-metadata ml/catalogue_expansion/android_export/maya_twkm25_metadata.json
   ```

   Fix any errors and manually inspect near-duplicate warnings.
   Insufficient sample counts show as research readiness gaps; this is
   expected during initial collection.
4. **Refit with the verified training photos**. Install the CPU or CUDA
   compatible PyTorch/TorchVision pair for your machine (don't break a
   working GPU environment). Then:

   ```powershell
   python -m pip install "datasets>=4,<5" onnx onnxruntime onnxscript skl2onnx
   python ml/catalogue_expansion/export_android_candidate.py `
     --photo-manifest dataset/evaluation/real_single_glyph_photo_manifest_template.csv `
     --photo-root dataset/local_verified_photos `
     --output ml/catalogue_expansion/android_export_with_photos
   ```

   The script audits each image and rejects invalid sign labels, missing
   metadata, cross-split source-group leakage, duplicate SHA examples,
   damaged images, and empty training data. Only verified
   `train` photos enter the SVM and training-only gallery. Any new
   `validation`/`test` photos are **evaluation only**.
5. Inspect the new `maya_twkm25_metadata.json` for new known-photo
   accuracy, unknown rejection and ONNX parity. Compare against the
   baseline and do not assume extra training always helps. If results
   are credible, copy all four **new matching files** into Android assets
   together and rebuild. Retain the older package as a rollback point.

## What still requires research

- Reliable extraction of **individual signs from detected compound blocks**.
- More verified distinct training/validation/test photos for every sign.
- Accuracy on untouched, source-artifact-disjoint real-world photographs;
  per-class precision/recall/F1 and known-sign correct-and-accepted rate.
- Explicit incorrectly-accepted unsupported-sign rate and confidence
  intervals, on-device preprocess/ONNX parity and user testing.
- Expert verification of sign-to-reading mappings and image reuse rights.
- Further expansion beyond 25 only when enough independent labelled
  examples support additional sign classes.

**Do not report the 68% catalogue metric as 68% translation accuracy.**
The app is a bounded educational research prototype, not a complete
Classic Maya inscription translator.
