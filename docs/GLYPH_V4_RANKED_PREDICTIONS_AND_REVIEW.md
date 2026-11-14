# Research v4 — ranked predictions and review workflow

## Android implementation
- The ONNX v4 weights and supported ten TWKM sign IDs are **unchanged**.
- The v4 logits are converted to a numerically stable ten-class softmax, with
  a ranked top-three list. Softmax scores are **relative model scores**, NOT
  a calibrated probability of a correct archaeological glyph reading.
- Single-glyph scans show candidate reading, tentative catalogue gloss,
  ranked alternatives, clear uncertainty / unsupported-sign notes.
- A mismatch between standard and shadow-corrected predictions continues
  to show **Unrecognized / needs review**; stable agreement does NOT imply
  an unfamiliar glyph is supported.
- The review dialog allows the user to select a displayed candidate or
  "Unknown / other", and add a source identifier and a correction note.
  It exports a CSV through Android's system file picker; no photo, identity
  or private user details are uploaded automatically. Reports are explicitly
  `UNVERIFIED_USER_FEEDBACK`, never automatically used for training.
- Added a "Choose another image" button within the result card.
- Existing auth, Firebase data, dictionary, history, camera, and optional
  multi-block functionality remain unchanged.

## Accuracy limitations
The present source-faithful TWKM test identified only 7/10 held-out known
signs; known input confidence is not calibrated. Prior v4 open-set threshold
trials accepted too few known signs, so **no unvalidated confidence threshold
or automatic unknown detector is claimed**. Softmax scores are not evidence
of correct Maya-to-English translation.

Before introducing a threshold, evaluate its false-accept and false-reject
rates on independent labelled real-camera photos (not recoloured copies of
TWKM source images). Preserve provenance and artist licensing for training.
