# Research v4 single-sign data-compatible experiment

Runs an optional **Research v4** candidate fine-tuning experiment; never
creates a new numbered model or replaces the Android asset automatically.

- **TWKM**: only reliable supervised labels for the current ten-class sign head.
  Preserve its original/source-hash-separated training, validation and test
  images. Do not train on the exposed holdout.
- **Kaggle Mayan Glyphs**: images are font-key glyphs with no verified
  font-key -> Thompson -> TWKM -> English mapping. The code uses their
  brightness-normalised **stroke coverage statistics** to modulate morphological
  TWKM training augmentation, not as ten-class labels.
- **Kaikki/Yucatec dictionary**: modern language text data, not Classical
  Maya sign photographs. It cannot be used as an image classification label.

GitHub workflow: `.github/workflows/research-v4-single-train.yml`. It
downloads the public Kaggle dataset and the pinned public TWKM graph
catalogue, then imports the old v4 *training-only* weights and fixed
sign splits. The old sign head is preserved; fine-tuning happens only
on the verified ten classes.

Run locally in a full research environment:

```bash
python ml/research_v4_single/retrain_v4.py \
  --kaggle-dir /path/to/unzipped/archive \
  --output ml/research_v4_single/output
```

Evaluate selection using known *validation originals* and separate
deterministic synthetic degradation. A candidate is only generated if
clean sign-ID validation does not drop and synthetic test performance
rises by at least two of thirty trial images. This does **not** prove
real-world accuracy. The script intentionally does **not** replace
`app/src/main/assets/maya_glyph_v4.onnx`; independent labelled camera
images and a separate device test are required before deploying a
new weight file under Research v4. Kaggle images/labels are not
redistributed, and no unverified Maya translation mapping is created.
