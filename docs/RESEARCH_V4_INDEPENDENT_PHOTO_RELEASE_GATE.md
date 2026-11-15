# Independent camera-photo reliability evaluation

**Current status: NO independent verified camera-photo dataset was supplied.**
Do not claim Research v4 or any experimental model is reliable on unknown
inscription photographs merely from TWKM or Kaggle synthetic tests.

The new photo-manifest audit (`dataset/scripts/validate_real_glyph_photos.py`)
ensures distinct original artifacts/drawings remain in a single split and
records source, rights, verification and known/unknown sign labels.

Once independently labelled local photographs are collected, run:

```powershell
python -m pip install numpy Pillow opencv-python-headless scikit-learn onnxruntime
python ml/research_v4_single/evaluate_heldout_photos.py `
  --manifest dataset/evaluation/real_single_glyph_photo_manifest_template.csv `
  --root dataset/local_verified_photos `
  --output dataset/local_verified_photos/evaluation
```

This evaluates the actual v4 ONNX weights, comparing the original and
shadow-corrected views in Python. A separate Android parity check is
still needed; no claim that the Python view is bit-identical to Kotlin.

Required metrics: **correct AND accepted** on known sign photos,
unknown-sign wrong acceptance, per-sign recall, macro F1, top-three
correctness, and Wilson 95% confidence intervals. These cannot be replaced
with a softmax percentage. Rejections of valid signs are failures of
correct identification.

Provisional gate for a "reliable" research release:
- >=100 *independent-source* known photos, >=10 per supported sign
- >=100 independent out-of-scope photos
- Wilson 95% LOWER bound of known correct-and-accepted >=80%
- Wilson 95% UPPER bound of wrong-accepted unknown <=5%
- macro F1 >=80%, every supported sign recall >=70%
- expert label review, verified photo rights, separate physical-phone
  inference parity, and no tuning on the final test set.

These are intentionally stringent proposed criteria. Even passing these
numbers on small non-independent data would not certify archaeological
translation, and the existing data does **not** pass this gate.

Keep local photos and labels out of Git commits until rights and
privacy are cleared. The script only processes locally accessible files.
