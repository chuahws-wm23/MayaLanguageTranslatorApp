# Yucatec Maya ↔ English Neural Machine Translation

This directory contains the experimental neural machine translation (NMT) pipeline for the FYP.

The goal is **not** to train a Google-scale system from scratch. The experiment fine-tunes a multilingual sequence-to-sequence model for direct Yucatec Maya ↔ English translation.

## Why this pipeline exists

The Android app originally performs exact dictionary lookup. That is reliable for known entries, but it cannot generalize to unseen sentences. The NMT experiment adds a sentence-level fallback.

The intended final architecture is hybrid:

1. verified dictionary / fixed phrase match
2. neural translation fallback for unseen text
3. explicit user review for OCR-derived text

## Current experiment result

The first medium experiment used 5,000 mixed pairs for 2 epochs.

It reached approximately:

- validation ChrF++: 39.70
- English → Yucatec test ChrF++: 23.38
- Yucatec → English test ChrF++: 25.93

Manual sanity checks showed several semantically incorrect translations. Therefore this checkpoint is a **proof of concept**, not a final app model.

The next experiment uses two stages:

1. mixed human + synthetic training to learn broader translation patterns
2. human-direct refinement at a lower learning rate

## Training data

### GATITOS English ↔ Yucatec Maya

- 4,062 direct human pairs downloaded in the current experiment
- mostly words and short phrases
- CC BY 4.0
- loaded from `mabsoutwassim/smol`

### YUA-ES Communicative Contexts Corpus

- 14,332 Yucatec Maya ↔ Spanish everyday phrases
- 31 communicative contexts
- CC BY 4.0

The Spanish side is translated to English using `Helsinki-NLP/opus-mt-es-en`. Those rows are labelled `synthetic_english`.

## Two-stage training

After the normal corpus has been created:

```bash
python ml/scripts/build_human_refinement_corpus.py
```

This produces:

```text
ml/data/processed/human_train.csv
ml/data/processed/human_validation.csv
ml/data/processed/human_test.csv
```

### Stage 1 — mixed data

The current medium checkpoint can be used as the starting point for the next experiment. For a later full run, use the complete mixed training set.

### Stage 2 — human-direct refinement

Starting from the medium checkpoint:

```bash
python ml/train_m2m100.py \
  --train-file ml/data/processed/human_train.csv \
  --validation-file ml/data/processed/human_validation.csv \
  --model-name ml/artifacts/m2m100-yua-en-medium/best_model \
  --epochs 3 \
  --learning-rate 5e-6 \
  --output-dir ml/artifacts/m2m100-yua-en-human-refined
```

Evaluate only on the held-out human-direct test subset:

```bash
python ml/evaluate_model.py \
  --model-dir ml/artifacts/m2m100-yua-en-human-refined/best_model \
  --test-file ml/data/processed/human_test.csv
```

Then run the same qualitative test sentences:

```bash
python ml/manual_sanity_check.py \
  --model-dir ml/artifacts/m2m100-yua-en-human-refined/best_model
```

The refined checkpoint should only be promoted if both automatic metrics and qualitative correctness improve.

## Important methodology rule

The synthetic-English test subset must not be presented as a human gold-standard benchmark.

For the final dissertation, create a manually checked sentence-level English ↔ Yucatec Maya test set that is never used for training.

## Serving the trained model

After a checkpoint passes evaluation:

```bash
pip install -r ml/api/requirements.txt
MAYA_MODEL_DIR=ml/artifacts/m2m100-yua-en-human-refined/best_model \
uvicorn ml.api.main:app --host 0.0.0.0 --port 8000
```

The Android app should continue using verified dictionary matches first and call the NMT service only as a fallback for unseen text.
