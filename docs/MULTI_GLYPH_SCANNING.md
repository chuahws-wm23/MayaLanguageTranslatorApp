# Automatic multi-glyph panel scanning (experimental)

## New feature

Open **Photo translation > Maya glyph recognition**, choose Camera or Gallery,
then press **Scan all glyphs automatically**. The user does NOT need to crop
each individual glyph.

The app detects repeated row/column block boundaries, overlays numbered
regions on the photo and runs the selected on-device glyph recogniser for each.
Each detected block gets its own result and a button to inspect the original
region. Results can be copied as an explicitly UNVERIFIED candidate list.

Existing **Recognise one selected glyph** remains available for a single
sign or for refining an auto-detected region. Legacy photo History, Firebase,
login, dictionary and other app sections are unchanged.

## How it detects multiple symbols

- Decode and orient the image locally (existing glyph image loader).
- Scale a detection copy to a maximum side of 960 pixels.
- Convert it to grayscale, estimate an Otsu dark-pixel threshold.
- Build horizontal and vertical ink-density projections.
- Find repeated dark seams that are stronger than the centres of adjacent
  proposed blocks; compare arrangements with 2–10 repeated intervals.
- Demand a high proportion of repeated seams in both directions before
  declaring a multi-block panel. Return no boxes instead of making up
  unrelated shapes when the photo has no convincing grid structure.
- Crop each selected block, call the selected existing offline recogniser
  sequentially, and display its result in scan/visual order.
- Scan at most 48 regions to bound work on a phone.

The **example carved-panel photo** provided during development measures
466 x 658 pixels and has a visually regular approximately **4 x 7**
arrangement. A local numerical prototype identified those seam intervals,
and JVM regression tests exercise an idealized 4 x 7 panel, single-sign
outlines, blank images and damaged pixel arrays.

## Recognising signs vs recognising glyph blocks

**This is experimental automatic glyph-BLOCK localization with possible
per-block sign matches. It is NOT a validated full inscription translator.**
A block may contain several distinct Maya signs and complex reading order.
The prototype cannot yet parse syllabic compounds or reconstruct
grammatically correct English sentences.

- Model v1: five experimental categories; may return no match.
- Research v2: five closed-set categories; always guesses one of five.
- Research v3: five categories with experimental unknown-screening.
- Research v4: ten closed-set categories; always guesses one of ten.

All displayed English glosses are tentative source-catalogue labels, not
verified translations. The yellow overlay numbers follow top-to-bottom,
left-to-right **display** order and do not assert the reading order of
Maya inscriptions.

Carved relief, uneven lighting, photographs at perspective angles and
non-grid layouts may cause inaccurate separation and recognition.
The detector does not fake a whole-inscription result. If no meaningful
grid is detected it asks for a clearer straight-on photo and preserves
single-crop recognition.

## Device acceptance checks

1. Load the carved-panel sample through Gallery and tap Scan all glyphs;
   confirm multiple numbered boxes, not one guess for the entire photo.
2. Test a single glyph image. It should not be split into a fake panel.
3. Choose Research v3; confirm uncertain blocks remain unrecognized rather
   than being called verified English translations.
4. Choose Research v4; confirm ten-sign candidate guesses are visibly
   labelled UNVERIFIED.
5. Tap Inspect on any numbered region, then use single-glyph recognition.
6. Check that camera capture, rotation, gallery selection, Back, Firebase,
   history and offline operation remain intact.
7. Measure real-phone processing duration and whether the region detector
   consistently separates different carved-panel photographs.

Do **not** claim these smoke tests establish independent real-world
recognition accuracy. The existing reported 70% research v4 result was
on 10 TWKM catalogue originals, not carved full-page images.
