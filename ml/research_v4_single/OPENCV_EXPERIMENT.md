# Research v4 Python/OpenCV + CNN single-glyph experiment

## What this code does

- Uses the existing **Research v4 ten-sign MobileNetV3-Small ONNX CNN** and
  the already pinned TWKM source images.
- Converts a selected JPG/PNG to grayscale with correct EXIF orientation and
  transparent-background handling; finds one dark glyph, crops without
  changing aspect ratio, and normalizes to the 64px training representation.
- Compares **original**, CLAHE local contrast, CLAHE + bilateral denoising,
  adaptive threshold and large-scale shadow correction.
- Does NOT claim that any OpenCV filter automatically improves recognition.
  Stroke removal or excessive thresholding can destroy fine sign details.
- Tests known sign IDs, confusing classes and **unsupported sign IDs** using
  the same input images for every treatment.
- Evaluates real original TWKM images and fixed simulated photo conditions.
  Synthetic perturbations are not independent photographed inscriptions.
- Applies a **predeclared validation-only** selection gate and leaves the
  Android model unchanged regardless of the result: Kotlin parity plus
  an independent labelled photo set are needed for a safe deployment.

## Run a real single image (Python on laptop)

    pip install opencv-python-headless numpy Pillow onnxruntime
    python ml/research_v4_single/opencv_single_glyph.py \
      --image /path/to/single_glyph.jpg \
      --treatment original \
      --debug-output /path/to/canonical.png

The default expects the same trained model at
\`app/src/main/assets/maya_glyph_v4.onnx\`. The script verifies its SHA256.
Alternative \`--treatment\` modes: \`clahe\`, \`clahe_bilateral\`,
\`adaptive\`, \`shadow\`. Returned top-three scores are *relative to the ten
supported classes*, not calibrated likelihoods of correctness.

## Reproduce measured comparison

Use \`.github/workflows/research-v4-opencv.yml\` for all dependencies, the
pinned TWKM version and exact JSON/CSV/Markdown metrics. A local copy with
the prior research loaders installed also works:

    python ml/research_v4_single/test_opencv_single.py -v
    python ml/research_v4_single/evaluate_opencv_single.py

Data selection is by source original SHA, not random augmented-image rows.
The already-exposed v4 known test image pack is used for descriptive
regression only and is not a new independent estimate of recognition.

**Important limitations:** This is a research prototype for *isolated* signs;
the deployed v4 knows only ten TWKM sign categories. No OpenCV transform,
softmax score, or catalogue gloss establishes an authentic full inscription
translation or real-camera recognition accuracy.
