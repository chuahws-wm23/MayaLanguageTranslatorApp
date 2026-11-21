# Neural Translation Experiment

## Objective

Develop and evaluate a direct bidirectional neural machine translation model for modern Yucatec Maya ↔ English.

This is an experimental extension of the existing dictionary-based Translation Service. It should not replace the verified dictionary until evaluation shows that the generated translations are acceptable.

## Baseline model

The baseline uses `facebook/m2m100_418M`.

Rangel and Kobayashi (2024) trained M2M100 on Yucatec Maya–Spanish data and reported materially better results than their T5-based experiments. Their paper reports 13,528 cleaned Yucatec Maya–Spanish pairs and a Yucatec test result around ChrF++ 51.5 for the M2M100 Mayan experiment.

Source:
https://aclanthology.org/2024.americasnlp-1.16/

Research code:
https://github.com/RIKEN-DKO/iikim_translator

## Current experimental results

A 5,000-pair, 2-epoch mixed-data experiment reached:

- validation ChrF++ ≈ 39.70
- English → Yucatec overall test ChrF++ ≈ 23.38
- Yucatec → English overall test ChrF++ ≈ 25.93
- English → Yucatec human-direct ChrF++ ≈ 13.58
- Yucatec → English human-direct ChrF++ ≈ 19.13

Manual sentence testing revealed semantic errors on several common phrases. This means the model is learning Yucatec-like generation patterns, but the mixed-data checkpoint is not sufficiently reliable for production translation.

## Why the next experiment changes

The prepared training set is dominated by synthetic-English rows. In the current split:

- human-direct training pairs: 3,249
- synthetic-English training pairs: 11,145

The model performs better on the synthetic subset than on human-direct examples. The next experiment therefore uses **human-direct refinement** rather than immediately spending several hours on a larger mixed-data run.

## Two-stage training design

### Stage 1 — broad mixed-data training

Use human-direct and synthetic-English pairs together so the model learns broader Yucatec Maya ↔ English generation patterns.

### Stage 2 — human-direct refinement

Continue fine-tuning from the Stage 1 checkpoint using only the human-direct subset and a smaller learning rate.

Current refinement settings:

- training pairs: 3,249 human-direct
- validation pairs: 425 human-direct
- held-out test pairs: 388 human-direct
- epochs: 3
- learning rate: 5e-6

The held-out human test set remains excluded from refinement training.

## Evaluation

The pipeline reports BLEU and ChrF++.

Automatic metrics must be complemented with manual sentence checks because an NMT model can obtain a non-trivial corpus score while still producing semantically incorrect translations for individual phrases.

The final dissertation should additionally use a manually verified sentence-level English ↔ Yucatec test set that is never used during training.

## Proposed final app architecture

1. User supplies typed text, OCR text, or speech-recognized text.
2. Text is normalized.
3. Translation Service checks a verified dictionary/phrase table first.
4. If there is no reliable exact match, the Translation Service calls the evaluated neural translation model.
5. The neural result is clearly treated as generated output.
6. History and analytics record which engine produced the translation.

This keeps deterministic translations for verified phrases while allowing model-based fallback for unseen sentences.
