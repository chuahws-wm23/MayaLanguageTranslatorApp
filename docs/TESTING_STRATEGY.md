# FYP Testing Strategy

Testing is split into **software correctness** and **linguistic evaluation**.
These are not the same thing and should be reported separately.

## 1. Automated software checks

GitHub Actions runs the Android unit-test suite on each development push.

Current automated coverage includes:

- text normalisation
- exact dictionary lookup
- no-match behaviour
- OCR Maya-text filtering
- Android compilation / unit-test execution

A passing software test proves that the implementation behaves as specified. It
does not prove that a Yucatec Maya translation is linguistically correct.

## 2. Dictionary data validation

The dataset pipeline checks:

- required fields
- empty Maya/English text
- deterministic IDs
- duplicate IDs
- duplicate translation/POS pairs
- Unicode NFC normalisation
- normalised lookup fields
- minimum expected dataset size

The separate ambiguity audit reports:

- Maya inputs with more than one English gloss
- English inputs with more than one Maya form
- dictionary metadata glosses such as "obsolete spelling of ..."

Ambiguity is expected in natural language, but it is important because the
current application repository returns one entry for a lookup.

## 3. Translation regression benchmark

The generated 200-case candidate file can be used to confirm that dictionary
entries remain retrievable in both directions after code/data changes.

Because these expected outputs originate from the same Kaikki-derived
dictionary, this is a **regression/coverage test**, not an independent accuracy
measurement.

## 4. Linguistic accuracy benchmark

For the FYP Results chapter, use a manually reviewed subset whose expected
answers are checked against an acceptable independent/reference source.

Recommended first benchmark:

- 50 cases total
- 25 Yucatec Maya → English
- 25 English → Yucatec Maya
- mix of nouns, verbs, adjectives and useful expressions
- include some ambiguous cases and document accepted alternatives
- exclude obvious dictionary metadata-only glosses unless the test explicitly
  concerns variant/obsolete forms

Record:

- Test ID
- Direction
- Input
- Expected accepted output(s)
- Actual app output
- Correct / acceptable alternative / incorrect / no match
- Verification source
- Notes

Report at least:

- exact/accepted accuracy
- dictionary coverage
- no-match rate
- error categories

## 5. Image OCR evaluation

Use a fixed set of labelled images with known ground-truth text.

Recommended conditions:

- clean screenshot / digital text
- printed text
- small font
- angled image
- low light
- blur
- mixed English + Maya text
- multi-line Maya text

Evaluate OCR separately from translation. For each image record:

- ground-truth Maya text
- OCR output
- corrected OCR output, if user editing is allowed
- final translation
- OCR correct / partially correct / incorrect
- translation correct / incorrect

This separation prevents a translation error from being incorrectly counted as
an OCR error.

## 6. Device-only tests

These cannot be completely validated by unit tests or CI and should be checked
on at least one emulator and preferably one physical Android phone:

- Camera permission allow/deny
- CameraX preview and photo capture
- Gallery replacement
- image orientation
- speech recogniser available/unavailable
- microphone cancellation
- TTS available/unavailable
- network unavailable during Firebase operations

## 7. Firebase/system tests

Verify:

- registration and login
- password reset
- profile update
- successful translation creates history
- Text/Image/Voice analytics counters
- save/remove vocabulary
- feedback submission
- admin feedback review
- one user cannot read another user's private records

Do not use Firebase Admin credentials inside the Android app or repository.
