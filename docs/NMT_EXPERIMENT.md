# Neural Translation Experiment

## Objective

Develop and evaluate a direct bidirectional neural machine translation model for modern Yucatec Maya ↔ English.

This is an experimental extension of the existing dictionary-based Translation Service. It should not replace the verified dictionary until evaluation shows that the generated translations are acceptable.

## Baseline model

The first baseline uses `facebook/m2m100_418M`.

Rangel and Kobayashi (2024) trained M2M100 on Yucatec Maya–Spanish data and reported materially better results than their T5-based experiments. Their paper reports 13,528 cleaned Yucatec Maya–Spanish pairs and a Yucatec test result around ChrF++ 51.5 for the M2M100 Mayan experiment.

Source:
https://aclanthology.org/2024.americasnlp-1.16/

Research code:
https://github.com/RIKEN-DKO/iikim_translator

## Direct English data

SMOL/GATITOS provides approximately 4,000 direct professional/human-translated English ↔ Yucatec Maya items under CC BY 4.0.

GATITOS is primarily lexical and short-phrase data. It is useful for vocabulary coverage but cannot by itself justify claims of strong sentence-level translation.

Source:
https://huggingface.co/datasets/mabsoutwassim/smol

## Everyday phrase data

YUA-ES-CCC provides 14,332 aligned Yucatec Maya ↔ Spanish everyday phrases across 31 communicative contexts under CC BY 4.0.

Source repository:
https://github.com/alemol/yua-es-ccc

DOI:
https://doi.org/10.5281/zenodo.20631792

For this FYP experiment, its Spanish translations are converted to English using a Spanish→English NMT model. The resulting English targets are **synthetic** and are labelled as such in every processed row.

## Evaluation design

The pipeline reports BLEU and ChrF++.

Two subsets must be distinguished:

1. **human_direct** — direct English ↔ Yucatec GATITOS pairs
2. **synthetic_english** — Yucatec phrases paired with machine-translated English derived from the Spanish side of YUA-ES-CCC

The final dissertation should not present the synthetic-English subset as a human gold-standard English benchmark.

A manually verified sentence-level English ↔ Yucatec test set should be created before the final evaluation.

## Proposed final app architecture

1. User supplies typed text, OCR text, or speech-recognized text.
2. Text is normalized.
3. Translation Service checks a verified dictionary/phrase table first.
4. If there is no reliable exact match, the Translation Service calls the neural translation model.
5. The neural result is shown as a generated translation and can be reviewed before saving.
6. History and analytics record which engine produced the output.

This keeps deterministic translations for verified phrases while allowing generalization to unseen sentences.
