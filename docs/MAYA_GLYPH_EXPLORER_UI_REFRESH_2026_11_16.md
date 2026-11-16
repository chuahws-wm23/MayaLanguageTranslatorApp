# Maya glyph explorer — mobile UI optimization (16 November 2026)

## Goal

Make the Photo / Scan screen lead with what a nontechnical user expects:
**choose an image → crop one sign → compare English-meaning reference pictures →
study possible Maya reading and historical context.** Preserve access to the
larger TWKM libraries, segmentation and previous research classifiers.

## New screen hierarchy

### Scan a picture (default)

1. **Choose an image** — Gallery and Camera are adjacent, large tap targets.
2. **Select one glyph** — interactive crop; Reset crop only appears when a crop is active.
3. **Find a possible English meaning** — primary action searches the 135 sign /
   366 reference-drawing TWKM English catalogue and displays top-three
   source-linked pictures. Users can expand **Explain this meaning** for
   historical and linguistic context. This is a visual search, NOT verified
   automatic translation.
4. **Multiple glyphs?** — when the optional
   `maya_signs_v11_seg.onnx` is installed, a direct action scans components.
   Each result is now a compact row with a thumbnail comparison available
   on expansion. **Inspect** takes the user back to the photo crop to inspect
   one part.
5. **Also compare syllable sounds** — optional, collapsed by default.
   This searches the separate 86-syllable, 382-drawing phonogram catalogue;
   a sound is not an English word meaning.
6. **Advanced AI models & research settings** — collapsed by default. Contains
   legacy 10-sign recognition, optional 25-sign research classifier, and
   dense-panel block/grid scanning controls. The old models have not changed.

### Explore signs (separate view)

Two compact choices:

- **Meanings (135)** — searchable English word-sign catalogue, source TWKM
  codes, images and educational explanations.
- **Syllables (86)** — searchable phonetic signs and their reference drawings.

No photo is required to use either library.

## UI design details

- Progressive disclosure hides model internals, grid sliders, and long lists
  from the default scan experience.
- Page uses existing Material 3 app styles, `AppGradientBackground`,
  shape and typography tokens, contrast-aware labels and >=48dp primary tap
  targets.
- Keeps native Activity Result photo selection and CameraX permission flows;
  no new permissions, backend calls, model weights, or private files.
- Top-three results remain explicitly **possible visual matches**, not
  calibrated confidence. Existing 10-class v4 scores are still model-relative.
- User can return to a region's crop from the multi-component list.
- Source attribution, reading limits and learning links remain available.

## Manual phone QA checklist

1. Open Scan on a fresh install without a photo. Verify only concise
   instructions and Gallery/Camera controls are visible.
2. Browse Meanings and Syllables; test searching for `fire`, `AJAW`,
   `ba` and changing library tabs.
3. Choose one clean glyph drawing. Crop it, run **Find matching English word
   signs**, inspect the three drawings and expand one explanatory note.
4. Replace the source image; confirm previous visual results disappear.
5. With the *optional* v11 ONNX installed, scan a multiple-sign picture. Verify
   compact regions, expandable visual references and **Inspect** navigation.
6. Without the ONNX asset, confirm existing photo and manual crop features
   remain usable; show no false segmentation result.
7. Expand Advanced tools, select each available model, and run the original
   experimental recognizer; verify research warnings remain.
8. Test a narrow ~390dp phone, dark mode, large text/font scaling, camera
   denied, image loading failure, and back navigation. Keep the image crop
   reachable without horizontal scrolling.

## Status

The UI edits are on `fyp-development` and should be tested with Android CI
and on a physical phone. **This redesign does not increase classifier
accuracy.** The 135-sign English visual lookup has low reported held-out
gallery performance and must not be sold as a validated image-to-English
translator. No historical context text is silently generated from an unknown
photo.
