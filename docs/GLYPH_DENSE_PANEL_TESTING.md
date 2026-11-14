# Dense inscription screenshots — 15 November 2026

## Reproduced user-facing failures

A multi-column carved stone tablet and two dense black-and-white inscription
pictures failed strict automatic multi-block detection. The same full-page
inscription also received a misleading single-sign v4 guess (K'UH/#32).
These are screenshots, NOT gold-labelled test images; no new recognition
accuracy figure can be claimed.

## Fix on fyp-development

- Added **Grid-assisted scan (dense panels)**: drag ONE crop over the full
  panel, choose 2–12 rows and columns, and scan approximate cells (up to 144).
  This is a fallback when image projection seams are too irregular to detect.
- Automatic scanning still uses conservative boundaries, but now respects
  the image's selected crop when one is set.
- Added a conservative high-complexity warning to the **full-image single
  glyph** v4 action, reducing misleading whole-page guesses.
- Approximate user-selected grid results are labelled clearly. Neither a
  grid cell nor an English gloss is guaranteed to be a verified Maya sign.
- Existing v4 weights, 10 class labels, Firebase, navigation and gallery
  handling are unchanged.

## Phone checks

1. With the carved tablet image, select the panel bounds once, count visible
   rows/columns and run Grid-assisted scan. Confirm numbered boxes; adjust
   counts or crop if any border crosses a sign.
2. Repeat with the two black-and-white dense panels.
3. Press Recognise one selected glyph with the entire line-art inscription
   (without cropping) and verify the warning instead of the old single-sign
   guess.
4. Crop one known TWKM glyph and verify single-glyph recognition still works.
5. Try automatic Scan all on an originally supported regular 4x7 panel.
6. Check memory/latency if scanning 100+ grid cells on an actual phone.

**Research v4 remains a 10-category tentative classifier.** A grid may be
segmented into many cells, but those cells can contain several compound
signs. This is not full inscription reading or Maya-English translation.
