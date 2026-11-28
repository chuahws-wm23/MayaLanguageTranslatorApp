# Roboflow v11 — conservative gallery lookup (not a translator)

This is a reference matcher, not a new trained AI classifier. It uses the
**actual COCO segmented glyph crops** and returns the corresponding
**Roboflow glyph code** (e.g. `32A`) or `no_reliable_match`.

Roboflow codes are **NOT verified TWKM sign numbers/readings**. The system
does not invent English glosses. Thresholds are strict experimental starting
values (.97 nearest pixel cosine and .06 margin over another class), **not**
calibrated confidence or proof that unknown signs are rejected safely.

## How to run in Android Studio's PowerShell Terminal

From `C:\Users\cws\AndroidStudioProjects\MayaLanguageTranslatorApp`:

```powershell
python -m pip install numpy pillow opencv-python-headless
python .\ml\research_roboflow_v11\reference_matcher.py --dataset ".\Maya_Glyphs_v11.zip" --build --index ".\maya_roboflow_v11_gallery.npz"
python .\ml\research_roboflow_v11\reference_matcher.py --image "$env:USERPROFILE\Downloads\Manik.jpg" --index ".\maya_roboflow_v11_gallery.npz"
python .\ml\research_roboflow_v11\reference_matcher.py --dataset ".\Maya_Glyphs_v11.zip" --index ".\maya_roboflow_v11_gallery.npz" --evaluate
```

**If the ZIP file is elsewhere**, substitute its actual path. An extracted
COCO Segmentation directory containing
`train/_annotations.coco.json`, `valid/_annotations.coco.json`
and `test/_annotations.coco.json` also works.

The default gallery uses **training annotations only** so official
validation/test crops remain available for evaluation. After obtaining
honest held-out results, you can optionally create a **full catalogue
gallery** from all available v11 image splits:

```powershell
python .\ml\research_roboflow_v11\reference_matcher.py --dataset ".\Maya_Glyphs_v11.zip" --build --include-all --index ".\maya_roboflow_v11_full_gallery.npz"
```

That full reference gallery is for **visual lookup only**: no valid accuracy
claim can be made by testing against gallery images that have already been
included. Duplicate canonical pixels are deduplicated, and identical glyph
pixels assigned different codes are excluded to reduce ambiguous reference
entries.

This uses COCO bounding boxes and polygon masks to remove neighboring signs
before canonicalising each crop. Its ability to handle handwritten, carved,
photographed or different graphical variants is not established.

### Crucial FYP integration distinction

- v18 **glyph-block detection** produces bounding boxes around blocks.
- This v11 tool produces the nearest *Roboflow glyph code* for a **single**
  crop and may abstain. A block may contain multiple signs.
- The existing Research v4 Android neural network supports ten
  **verified TWKM** sign categories but still has limited real-world
  generalisation.
- There is **no validated code-to-TWKM crosswalk** for the 377 Roboflow
  codes. Do not feed them into the Android translation dictionary yet.

Roboflow v11 image credits/license: Maya Epigraphy via Roboflow,
**CC BY 4.0**, subject to individual source provenance.
