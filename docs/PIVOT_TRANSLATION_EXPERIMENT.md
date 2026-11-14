# Spanish-Pivot Translation Experiment

## Purpose

The first direct Yucatec Maya ↔ English NMT experiments successfully learned some translation patterns but produced semantically incorrect outputs for several common phrases.

The next experiment therefore preserves the strongest available human-aligned corpus:

**Yucatec Maya ↔ Spanish (YUA-ES-CCC)**

English remains the user-facing language, but Spanish is used internally as a pivot.

## End-to-end routes

English → Spanish → Yucatec Maya

Yucatec Maya → Spanish → English

The Android user never needs to see the Spanish intermediate text.

## Why this is a useful comparison

The direct-English experiment relies heavily on machine-generated English targets. The pivot experiment instead trains the low-resource component on the original human Yucatec Maya ↔ Spanish alignment.

This gives the FYP a meaningful experimental comparison:

1. verified dictionary baseline
2. direct Yucatec Maya ↔ English NMT
3. Spanish-pivot NMT

## Evaluation

First evaluate the low-resource model directly on held-out YUA-ES pairs:

- Spanish → Yucatec Maya
- Yucatec Maya → Spanish

Then evaluate the complete English ↔ Yucatec Maya pivot route on the held-out human-direct English ↔ Yucatec subset.

Automatic metrics:

- ChrF++
- BLEU

Manual checks remain required for semantic correctness.

## Promotion rule

Do not integrate the pivot engine into Android merely because it produces fluent-looking text.

It should beat the direct model on the held-out human-direct test set and should produce more sensible outputs on the fixed manual sanity-check phrases before it becomes the NMT fallback in the app.
