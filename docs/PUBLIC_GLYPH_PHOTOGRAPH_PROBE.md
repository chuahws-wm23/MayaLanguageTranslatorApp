# Public real-photograph development probe

This probe uses five genuine artifact photographs and six inputs: one additional
operator close-up comes from the same Carlos Museum vessel photograph. Images
are downloaded only into androidTest assets; none enters the app's model or
reference bank. Original JPEG bytes are preserved. Source URLs, dimensions,
licenses and SHA-256 values are retained with each run.

The new workflow runs the production Android image loader and single-photo
ViewModel on an API 29 emulator. It also records the same automatic region
proposal chain used by Multiple mode. A separate existing illumination
normalizer is evaluated as a research control; it is NOT enabled in the app.
A green workflow checks decoding/model execution, not semantic correctness.

## Sources and rights

- Princeton University Art Museum, y1983-51, Gift of Gillett G. Griffin:
  [Pectoral/costume ornament in the form of the glyph Ajaw (lord)](https://artmuseum.princeton.edu/art/collections/objects/32863).
  The museum page expressly permits downloading the public-domain image.
- Gary Todd, via Wikimedia Commons, [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/):
  [Carlos Museum vessel, 7 Ahau](https://commons.wikimedia.org/wiki/File:Maya_Ceramic,_Late_Classic,_%227_Ahau%22_Glyph,_711-889_AD_(10452410093).jpg),
  [INAH ceramic glyph](https://commons.wikimedia.org/wiki/File:Classic_Maya_Ceramic_Glyph_(9756961171).jpg),
  [Palenque stucco head](https://commons.wikimedia.org/wiki/File:Classic_Maya_Stucco_Glyph_%22Head_Variant,%22_Forgotten_Temple,_Palenque_(9756977652).jpg),
  [Palenque inscription fragment](https://commons.wikimedia.org/wiki/File:Language_Glyph,_Palenque,_Late_Classic,_600-900_AD_01.jpg).

The Princeton museum title identifies AJAW; the Carlos photograph's title
identifies 7 Ahau. These are source-described readings, not expert-verified
TWKM IDs. Three other photographs have no verified sign labels; their guesses
must NOT be counted as correct or assigned UNKNOWN labels. A compatible reading
on a whole vessel does not demonstrate correct sign segmentation or translation.

The close-up rectangle [840,780,1860,1900] was selected before inference from
the 3888x2592 Carlos photograph. It is an operator diagnostic, not automatic
app cropping and not a new independent artifact. Full-frame and close-up
results stay separate. No transformations cross training/test splits because
this is an inspected development probe, not a held-out final test.

## Reproduce

With Pillow installed:

~~~sh
python ml/logogram_expansion/public_photo_probe.py
gradle connectedDebugAndroidTest \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.chuahws.mayalanguageapp.presentation.translation.PublicGlyphPhotoProbeTest
adb pull /sdcard/Android/data/com.chuahws.mayalanguageapp/files/screenshots/public-photo-development-report.json public-photo-report.json
python ml/logogram_expansion/public_photo_probe.py --summarize public-photo-report.json
~~~

Use a clean staging folder; the downloader refuses to overwrite an existing
batch. The dedicated GitHub workflow retains the exact inputs, source metadata,
ranked candidates, normalized masks and reports for 30 days. Original-photo SHA-256 values are pinned to the evaluated inputs; changed
source bytes stop staging and require provenance review. Each run also records
the derived close-up hash.

Read the actual report rather than treating a green test as accurate recognition.
No recognition percentage, confidence threshold, independent-source accuracy or
release approval is inferred from this small convenience sample. Artifact
lineage relative to the original catalogue drawings has not been expert-audited.

## Observed Android results — 17 November 2026

The [completed probe](https://github.com/chuahws-wm23/MayaLanguageTranslatorApp/actions/runs/44050289923) ran all six inputs through the production photo ViewModel with zero decoding/model-execution errors. That engineering success did **not** establish correct recognition:

| Input | Source-described reading | Production first candidate | Described reading in top five |
|---|---|---|---|
| Princeton shell pectoral | AJAW | NAAH / NAH | No |
| Carlos vessel, original photo | AJAW present in scene | CHAPAAT | No |
| Carlos AJAW close-up | AJAW | WA' | No |
| INAH ceramic glyph | Unverified | CH'EEN | Unscored |
| Palenque stucco head | Unverified | BALAM | Unscored |
| Palenque stone fragment | Unverified | IK' | Unscored |

Both original photographs with source-described readings missed that reading, and the operator close-up also missed it. The existing illumination correction did not recover AJAW in the first five for any of these three views. This is three observations of two objects, not three independent expert-labelled tests. No overall accuracy percentage is justified. The three unlabelled photographs were not assigned invented ground truth.

The automatic region chain proposed zero regions on all six inputs. Box labels are unavailable, so this is not a detection-accuracy score; automatic extraction on photographic carvings remains unestablished.

**Decision:** retain the production model and ranking. Do not enable the unsuccessful illumination-control experiment, add the test photos to the reference bank, or call the photographs correctly translated. No improvement in real-photo recognition has been demonstrated. Camera recognition is not ready to be presented as reliable. The next justified model work requires expert-labelled photographic training/validation data, separate artifact groups and an untouched final photo test, as specified in the independent-photo release gate.

The [full artifact](https://github.com/chuahws-wm23/MayaLanguageTranslatorApp/actions/runs/44050289923/artifacts/14313836708) contains the exact photographs, crop, hashes, normalized masks, complete candidate lists and runtime reports. A compact machine-readable snapshot is stored at ml/logogram_expansion/reports/public_photo_probe_2026_11_17.json.
