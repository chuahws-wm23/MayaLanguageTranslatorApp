# TinyGlyphNet v1 Android integration

## Scope and entry point

Home > Photo translation (or the same sidebar item) now opens **Maya glyph recognition**.
Camera/Gallery > drag a rectangle around one complete glyph > optionally Rotate > Recognise glyph.
The existing account, Firebase setup, dictionary, typed/voice translation and saved vocabulary paths are unchanged.
Older photo History items are reopened as their original text, not interpreted as glyphs.

New glyph scans deliberately are not written into the modern-Yucatec dictionary/history schema or labelled verified.
The result can be copied with its experimental disclaimer. Glyph-specific persistence needs a separate schema and review workflow.

## Small offline runtime

- The exact supplied `maya_glyph_v1.mgl` asset is 29,420 bytes.
- SHA-256: `d4ed697fbb7dd3552f6efac0e58e6c29e872b9b60832dba8d5f6a19bd94a4094`.
- Four convolution layers and two dense layers. INT8 stored weights; floating-point activations/accumulation in Kotlin.
- No added Python, PyTorch, TFLite, model-download or training-image dependency.
- Model is loaded once per process, checked for size, checksum and architecture, and reused.
- Image decode is dispatched to IO. Crop/preprocessing/inference run on Default, not the main thread.
- Preview images are downsampled, EXIF orientation is applied, transparent input is flattened to white.
- Temporary captures created by this screen are removed after decoding. Gallery files are never deleted.
- Leaving the scanner releases its preview/results; orientation recreation may require choosing the image again.

## Interpretation and provenance

Classes: DEER/CHIJ (TWKM 796st), HAWK/MUWAAN (748st), JAGUAR/HIX (524st), STAR/EK' (510bt), KAKAW, OTHER.
Labels are inherited from the supplied experimental package, not independently verified here.
KAKAW comes from the user's earlier cacao-labelled test image and is a whole-block example, not a single logogram.
Do not use that same example to claim unseen-image accuracy.

`POSSIBLE_MATCH_UNVERIFIED` requires a non-OTHER top score >= 0.90 and a top-two margin >= 0.25.
Otherwise the UI shows no reliable supported match. These are preliminary gates, not calibrated open-set detection.
No score is displayed as a confidence/accuracy percentage. A strong score can still be wrong.
Each positive class had only one original drawing. No new training is claimed in this integration.
The earlier 96.44% diagnostic used synthetic transformations and is not independent in-scope accuracy.

## Checks and manual acceptance

JVM tests cover model integrity, parsing rejection, deterministic finite inference, gating and crop bounds.
Local JVM checks are software diagnostics only, not Android-device accuracy tests.

On a real phone test: portrait and landscape capture; Gallery; denied camera permission; select/cancel;
crop at image boundaries and after rotation; blank/photo/unsupported drawings; large font; Back; screen exit while busy;
old text-photo History replay; login, saved words and ordinary dictionary operations.
Record outcomes for independent drawings separately from tests of model training references.

Expected limits: single isolated line drawing only; no handwriting/stone-carving/full-inscription guarantee;
unknown false positives remain possible; crop UI/accessibility and device latency require physical-device testing.
