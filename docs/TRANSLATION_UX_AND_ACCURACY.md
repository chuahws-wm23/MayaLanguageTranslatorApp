# Translation UX and Reliability Improvements

Branch: `fyp-development`

## User-facing behaviour

- An incomplete OCR/text/voice translation no longer displays developer
  tokens such as `[unknown: word]` or
  `[No reliable phrase translation available]`.
- A dash (`—`) marks a word or sentence that cannot be translated reliably;
  the guidance card lists original unrecognised words separately.
- The result is labelled **Partial dictionary matches** and offers an
  explanation instead of presenting a guessed full translation.
- When a dictionary expression has multiple distinct meanings/forms, the
  translator avoids silently selecting a first result. It presents multiple
  meanings for exact inputs and marks ambiguous spans for review in sentences.
- For a spelling error with a close dictionary match, the app may suggest
  the original dictionary word. The user explicitly taps a suggestion,
  reviews the corrected OCR/input field, then presses Translate again.
  Suggestions never automatically change the original input.
- Incomplete results do not trigger pronunciation, Copy, saved history,
  or translation analytics. Complete matched dictionary segments may be
  displayed as **Dictionary-based result** and still need grammar review.

## Quality improvements to the translation engine

1. Normalise punctuation between OCR words as a separator instead of
   joining separate words.
2. Prioritise `verified` entries *before* deduplicating translations.
3. Use longest supported phrase first, while avoiding arbitrary choice
   among multiple valid dictionary senses.
4. Reject low-coverage sentence glosses instead of presenting a single
   known word as the translation of the full sentence.
5. Cache repeated lookup keys during each translation to reduce redundant
   Firestore reads.
6. Use conservative dictionary-backed edit-distance suggestions. These
   are spelling candidates, not automatic translations.

## Regression examples

| Input | Expected |
| --- | --- |
| `water mystery` (English to Maya) | Partial output `jaʼ —` and warning that `mystery` is not found |
| `water mystery impossible` | No reliable translation (do not present a fake sentence translation) |
| `watwr` | No automatic translation; suggest dictionary spelling `water` where available |
| `water,house` | Keep words separate; use existing `water` and `house` entries |
| `broom water` with two different meanings of `broom` | Mark the ambiguous span and show the known word separately |

The regression tests are implemented in `TranslationServiceTest.kt` and
`TextNormalizerTest.kt` and run through Android GitHub Actions.

## FYP evaluation warning

These changes improve safety, explainability, matching and error handling.
They **do not establish improved linguistic translation accuracy**.
The current production system remains dictionary/phrase based, not a
general neural translation model. Output from matched individual words
may not be grammatically equivalent to a natural sentence.

For Project II, evaluate lexical/phrase quality against a human-verified
independent benchmark (for example, the 50-case plan in
`docs/TESTING_STRATEGY.md`) and evaluate OCR character error rate
separately using `docs/OCR_EVALUATION.md`.

On a physical Android device, additionally test suggestion replacement,
microphone/camera permissions, word ambiguity, long OCR inputs and
response times. Performance targets from the report are not claimed
until measured.
