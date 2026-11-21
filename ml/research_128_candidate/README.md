# Research-only 128×128 single-glyph models

This is a distinct experimental model comparison, not a replacement for the working Android Research v4.

It trains two MobileNetV3-Small CNNs at **128×128**: a TWKM-only control and a two-stage model pretrained on 80 Kaggle **font-key** classes and fine-tuned on the **ten TWKM sign identities**. The Kaggle font labels (U_XXXX) do NOT identify Maya readings, TWKM sign numbers, or English translations. Both 128px models are initialized with the existing pinned v4 vision weights, then further trained using TWKM source-hash-separated original drawings.

The exact Kaggle source ZIP was audited earlier: 16,600 PNG files, multiple exact duplicates and blank samples. Only nonblank, distinct-pixel fonts shared between the filled and outline styles are used for auxiliary pretraining. Source original TWKM images are processed directly to retain more detail instead of enlarging a previously reduced 64×64 shape.

## Repeat experiment

Run the GitHub Actions workflow `.github/workflows/maya-128-kaggle-twkm.yml` or, with its research dependencies installed:

```bash
python ml/research_128_candidate/train_128.py --kaggle-dir /path/to/kaggle_directory --output ml/research_128_candidate/out
```

The workflow exports research-only `*.pt`, `*.onnx`, per-sign confusion CSVs and `evaluation.json`. It verifies ONNX/PyTorch numerical parity. No trained model is deployed automatically.

### Evaluation limitations

The validation set has just one TWKM source original per sign; the ten historical held-out examples have already been exposed in previous v4 work. Synthetic phone-like shadow/noise/blur is not the same as a properly annotated phone-photo benchmark. An apparent improvement here is **not** a proven generalisation gain. Real sign identity accuracy and unknown-sign false acceptance require a new source-independent test set.

The Kaggle publisher's accompanying article displays a CC BY-NC-ND label; third-party training-data and model-weight redistribution permissions need review. These artifacts are for non-commercial experimental FYP work, not a standalone public model release.
