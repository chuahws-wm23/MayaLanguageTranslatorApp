# Expanded Classic Maya sign recognition — research pilot

The user-provided University of Bonn [ClassicMayan sign catalogue](https://classicmayan.org/portal/signcatalog?lang=en) contains **1,098 sign records** and **1,606 graph/allograph records** in the 2026-09-04 machine-readable TWKM dataset snapshot. **Those counts are catalogue coverage, not supported phone-photo AI labels.** Original Android Research v4 has 10 sign classes and stays unchanged.

The same public TWKM dataset already used by v4 provides a pinned graph image and `sign_no` label: `tadad/twkm-maya-glyphs` revision `73fe9da1d975a5f1e588c7d42c952dfda53cefd0`. Only rows explicitly marked **CC BY 4.0** are used by this pilot. Third-party photographs and separately copyrighted catalog materials are not imported.

## Objective

Assess whether **more than 10** sign identities can be separated from **individual catalogue drawings**, using only clean sign labels. Do not confuse a `graph_no` (concrete drawing variant) with the `sign_no` (identity) or a guessed English translation. A YOLO v18 bounding box contains a *block*, which may have multiple sign components; do not apply a single-sign classifier to a multi-sign block and call the output translation.

## What is implemented

- Full source **audit**: count distinct image hashes per sign and exclude wrong-licence/missing/corrupted rows and exact duplicate images; exclude identical image bytes if conflicting sign IDs.
- Experimental **multi-class expansion**: retain old 10 when eligible, and include additional sign IDs with at least 5 separately hashed reference drawings, up to 50 sign IDs by default. If too few labels meet the rule, the job fails explicitly rather than fabricating coverage.
- **Only training originals** populate the gallery. For each sign, one other image goes to validation and one to a research holdout. 40 *different unknown sign IDs* calibrate rejection; 40 further unknown sign IDs are reserved for a distinct test (or fewer, down to 30 if the dataset runs out).
- Compare three pixel/HOG visual-reference methods and an actual HOG/RBF-SVM classifier. Choose candidate by validation, tune an abstention rule on validation plus unknown calibration, evaluate a separate test once.
- Produce JSON audit/benchmark, proposed sign-ID manifest and compressed gallery under ignored `ml/catalogue_expansion/output/` (GitHub workflow artifacts only).

**Important:** independent *image hashes* are not independent monuments. Very similar original standardized drawings may still leak stylistic information across splits. Metrics on TWKM drawings can dramatically overestimate performance on carvings or phone photos.

## Run in GitHub Actions

Run [Evaluate larger TWKM sign inventory](../../.github/workflows/maya-catalogue-expansion.yml) on `fyp-development` (it also runs after code changes). Or from PowerShell:

```powershell
python -m pip install "datasets>=4,<5" Pillow numpy opencv-python-headless scikit-image scikit-learn
python -m unittest discover -s ml/catalogue_expansion -p "test_*.py"
python ml/catalogue_expansion/benchmark.py --min-originals 5 --max-signs 50
```

Review `output/catalogue_audit.json` first: it reports exactly how many signs have 2, 3, 4, 5, 6, 8 or 10 distinct source-image hashes. Then inspect `output/benchmark.json` for number of classes and test results. Do not copy this experimental gallery into the APK automatically.

## Release criteria

**The original Research v4 Android recognizer remains the default and has not been replaced.** A separate manually installed `TWKM 25 (research)` mode now exists for *experimental* isolated-sign tests, using ONNX visual features, SVM and training-only reference screening. This optional mode is NOT a validated release. See [Android installation and verified photo intake](../../docs/TWKM25_ANDROID_INSTALL_AND_NEW_DATA.md) and [exact 25 sign IDs](../../docs/TWKM25_EXACT_SIGN_IDS_AND_DATA_PLAN.md). Before any accuracy claim, establish independent source/artifact-grouped labelled photos, per-sign recall and macro F1, incorrect-unknown acceptance, on-device preprocessing parity and latency. An approximate catalogue match is not a verified translation.

For a fast FYP improvement, a separate manual searchable **reference catalogue** may expose much broader coverage while clearly not promising automatic photo recognition. If and only if a tested expanded visual model beats the existing 10-sign workflow on genuine independently labelled photos should automatic recognition move beyond Research v4.

## Attribution and rights

Original source: Text Database and Dictionary of Classic Mayan (TWKM), University of Bonn, standardized graph drawings by Christian Prager, [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). The [catalogue legal notice](https://classicmayan.org/portal/doc/246) excludes third-party copyrighted photographs from that licence. The Hugging Face snapshot is an independent interoperability publication; the live TWKM catalogue remains authoritative. Metadata may have different reuse rights. Clearly credit creators if using or distributing drawings. No third-party images are bundled here.
