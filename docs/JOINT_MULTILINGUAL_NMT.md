# Joint Multilingual NMT Experiment

## Goal

Train one model on both direct human English ↔ Yucatec Maya data and the
larger original human Spanish ↔ Yucatec Maya corpus.

Unlike the pivot experiment, Spanish is not used as an intermediate translation
during inference. It is auxiliary supervision during training.

The model learns four prompted tasks:

- English → Yucatec Maya
- Yucatec Maya → English
- Spanish → Yucatec Maya
- Yucatec Maya → Spanish

The Android app remains English ↔ Yucatec Maya for the user.

## Why this experiment

The direct model had limited sentence-level English data.
The Spanish-pivot system compounded errors across two separate translation
stages.

Joint training tries to learn more Yucatec Maya structure from the 14k
human Maya-Spanish pairs without forcing English input through Spanish at
runtime.

## First controlled run

```bash
python ml/train_joint_multitask.py \
  --max-english-train-samples 2500 \
  --max-spanish-train-samples 5000 \
  --max-english-eval-samples 400 \
  --max-spanish-eval-samples 500 \
  --english-repeat 2 \
  --validation-english-repeat 2 \
  --epochs 2 \
  --output-dir ml/artifacts/m2m100-yua-joint-medium
```

Evaluate all four directions:

```bash
python ml/evaluate_joint_multitask.py \
  --model-dir ml/artifacts/m2m100-yua-joint-medium/best_model
```

Run manual English↔Yucatec checks:

```bash
python ml/manual_joint_check.py \
  --model-dir ml/artifacts/m2m100-yua-joint-medium/best_model
```

## Baselines

Direct human-refined model:
- English → Yucatec ChrF++: 15.34
- Yucatec → English ChrF++: 20.32

Spanish-pivot system:
- English → Yucatec ChrF++: 9.94
- Yucatec → English ChrF++: 12.24

The joint model should beat the direct human baseline on the same held-out
human English test and also improve qualitative sentence correctness before it
is considered for Android integration.
