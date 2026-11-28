# Research v4 Kaggle font-shape experiment

This directory contains reproducible local training/inference code for an 80-class **font-key-only** auxiliary classifier. It is NOT a Maya inscription translator or an Android-deployed ONNX model. Do not label U_XXXX glyph keys as Maya words.

## Reproduce

```bash
python -m pip install -r ml/research_v4_kaggle/requirements.txt
python ml/research_v4_kaggle/train_research_v4_font_shapes.py --zip archive.zip --out experiment_out --epochs 16 --style-epochs 9 --seed 2026
python ml/research_v4_kaggle/infer_font_shapes.py --checkpoint experiment_out/research_v4_aux_font_shapes.pt --image sample.png
```

Read [`docs/RESEARCH_V4_KAGGLE_EXPERIMENT_RESULTS.md`](../../docs/RESEARCH_V4_KAGGLE_EXPERIMENT_RESULTS.md) for the validated local results, dataset limitations, and non-deployment decision.

The user's Kaggle ZIP and its derived model weights are **not committed**. Verify third-party rights and identity mapping before any public weight distribution or Android integration.
