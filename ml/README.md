# Yucatec Maya ↔ English Neural Machine Translation

This directory contains the experimental neural machine translation (NMT) pipeline for the FYP.

The goal is **not** to train a Google-scale system from scratch. The experiment fine-tunes a multilingual sequence-to-sequence model for direct Yucatec Maya ↔ English translation.

## Why this pipeline exists

The Android app originally performs exact dictionary lookup. That is reliable for known entries, but it cannot generalize to unseen sentences. The NMT experiment adds a sentence-level fallback.

The intended final architecture is hybrid:

1. verified dictionary / fixed phrase match
2. neural translation fallback for unseen text
3. explicit user review for OCR-derived text

## Research basis

The default baseline is `facebook/m2m100_418M`. Rangel and Kobayashi (AmericasNLP 2024) reported substantially stronger Yucatec Maya–Spanish results with M2M100 than with their T5-based baseline. Their cleaned Yucatec Maya–Spanish corpus contained 13,528 pairs and their best Yucatec system reported ChrF++ above 50.

This project does **not** copy their training code or redistribute their data. Their work is used as methodological evidence for choosing a translation-focused multilingual baseline.

## Training data

The pipeline deliberately separates human and synthetic supervision.

### 1. GATITOS English ↔ Yucatec Maya

- direct human translation from English
- approximately 4,000 short items
- mostly words and short phrases rather than full sentences
- CC BY 4.0
- loaded from `mabsoutwassim/smol` on Hugging Face

This gives high-quality direct English ↔ Yucatec supervision.

### 2. YUA-ES Communicative Contexts Corpus

- 14,332 Yucatec Maya ↔ Spanish everyday phrase pairs
- 31 communicative contexts
- CC BY 4.0
- Yucatec side is human-created / curated

To build a larger direct English ↔ Yucatec experimental corpus, the Spanish side is translated to English using `Helsinki-NLP/opus-mt-es-en`.

Those English sentences are marked **synthetic_english**. They must not be described as human ground truth.

## Quick start in Google Colab

Use a GPU runtime.

```bash
git clone https://github.com/chuahws-wm23/MayaLanguageTranslatorApp.git
cd MayaLanguageTranslatorApp
git checkout fyp-development

pip install -r ml/requirements.txt

python ml/scripts/download_gatitos.py
python ml/scripts/download_yua_es_ccc.py
python ml/scripts/synthesize_english.py
python ml/scripts/build_training_corpus.py
```

Run a small smoke test first:

```bash
python ml/train_m2m100.py \
  --max-train-samples 1000 \
  --max-eval-samples 200 \
  --epochs 1 \
  --output-dir ml/artifacts/m2m100-yua-en-smoke
```

Then train the main experiment:

```bash
python ml/train_m2m100.py \
  --epochs 5 \
  --output-dir ml/artifacts/m2m100-yua-en
```

Evaluate:

```bash
python ml/evaluate_model.py \
  --model-dir ml/artifacts/m2m100-yua-en \
  --test-file ml/data/processed/test.csv
```

## Metrics

The evaluator reports:

- ChrF++ (ChrF with word order 2)
- BLEU

It reports the two translation directions separately and also separates human-direct GATITOS examples from synthetic-English examples when both are present.

## Important methodology rule

Do **not** report the synthetic English test subset as a fully human gold-standard English evaluation set.

For the final FYP evaluation, create a manually checked English ↔ Yucatec sentence test set that is never used for training.

## Serving the trained model

After training:

```bash
pip install -r ml/api/requirements.txt
MAYA_MODEL_DIR=ml/artifacts/m2m100-yua-en \
uvicorn ml.api.main:app --host 0.0.0.0 --port 8000
```

The Android app can later call this API only after the model reaches an acceptable evaluation score.
