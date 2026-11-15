# Expanded TWKM sign recognition — pretrained 25-class experiment

**Date:** 15 November 2026  
**Branch:** `fyp-development`  
**Experiment:** `ml/catalogue_expansion/pretrained_pilot.py`  
**Run:** https://github.com/chuahws-wm23/MayaLanguageTranslatorApp/actions/runs/43736632210  
**Android release:** **No change**; Research v4 still supports 10 sign identities.

## Why this was run

The first 25-class TWKM catalogue reference-matching pilot reached only 9/25
correct top-1 (36%) on the small source-hash-disjoint historical catalogue
cohort; its rejection screen accepted only 6/25 correct known signs and
incorrectly accepted 5/40 other sign identities.

The new candidate uses **ImageNet-pretrained frozen ResNet18/MobileNetV3-Small
vision features** extracted from normalized TWKM graph drawings. A
25-class **linear or RBF SVM** is fit on the existing 101 original training
drawings with three conservative views per original. The
model/backbone/C/kernel are chosen using three-fold training-original-grouped
cross-validation, **without selecting using the historical test**.

The repository's original 10 sign identities are included among the 25
eligible labels. The new classes have source-catalogue TWKM `sign_no` IDs,
not guessed Roboflow/MHD crosswalks or font-key mappings.

## Actual measured results

| Metric | 25-class pixel/HOG reference baseline | Pretrained MobileNetV3-Small + RBF SVM |
|---|---:|---:|
| Source-drawing classes | 25 | 25 |
| Correct top-1 on exposed catalogue cohort | 9/25 (36%) | **17/25 (68%)** |
| Macro F1 on exposed catalogue cohort | 0.296 | **0.59333** |
| Correct-and-accepted known | 6/25 | **12/25** |
| Wrongly accepted known as another sign | 3/25 | **2/25** |
| Separate unknown sign IDs rejected | 35/40 | **38/40** |
| Separate unknown sign IDs falsely accepted | 5/40 | **2/40** |

Pretrained model selection: **MobileNetV3-Small, RBF SVM C=10**,
training-original-grouped CV 51/101 top-1, macro F1=0.46205. The competing
ResNet18 and MobileNet variants and hyperparameters are in the action's
research results artifact. The rule is calibrated on the 25 known validation
drawings and 40 different unknown calibration sign identities.

The 17/25 and 38/40 figures are **research regression results** on previously
exposed catalogue data, **not** estimates of smartphone photo accuracy.
The 25-class trial is also not a direct apples-to-apples comparison with the
existing 10-class Android v4 classifier, whose known-test accuracy was
7/10 on a different label set.

## Interpretation

Frozen pretrained neural features help this small expanded catalogue set more
than raw pixel/HOG template matching. However:

1. Only 25 held-out catalogue drawings (one per sign) were scored, and
   their underlying source artifact independence is not established.
2. Research v4's 10-class phone recognizer and YOLO v18 block detector are
   separate. YOLO finds **blocks**; a block can contain several signs. The
   25-class experiment classifies isolated TWKM sign drawings.
3. Even at the selected abstention threshold, only 12/25 known examples were
   both correctly recognized **and accepted**, and 2/40 unknown sign drawings
   were falsely accepted.
4. A high neural relative score, or a nearest catalogue visual match, cannot
   verify the reading of an arbitrary unfamiliar sign.
5. Model weights were not fine-tuned for independent weathered stone,
   handwritten, blurry or photographed sign crops. It is not an epigraphic
   translation engine.

## Decision and next necessary work

**Do not replace `app/src/main/assets/maya_glyph_v4.onnx` or advertise 25
verified Android classes.** The 25-class feature experiment is a good
development baseline; no packaged Android inference asset is released.

Before enabling even an optional experimental multi-sign recognition mode:

- Collect independently sourced **single-sign** photographs or line
  drawings for the candidate TWKM sign IDs, including supported and
  unsupported signs. Verify identity and rights; group all images from
  one original artifact together, not by file hash alone.
- Test sign-level segmentation of YOLO block crops before passing them
  to a single-sign recognizer. Do not classify a multi-sign block as if
  it were an isolated sign.
- Repeat the benchmark on untouched source-group-separated real photographs
  with per-sign recall, correct-and-accepted rate and wrong-unknown
  acceptance (including confidence intervals).
- Only if validated, export the chosen feature extractor and SVM
  to Android-compatible ONNX/lookup assets, compare Python/Android inputs,
  benchmark device latency and keep the original v4 available as rollback.
- For immediate broader coverage, create a **manual sign reference search**
  using properly attributed TWKM catalogue drawings, without pretending
  all reference entries are automatic recognition classes.

## Resources and rights

- TWKM graph dataset: https://huggingface.co/datasets/tadad/twkm-maya-glyphs
- Live source catalogue: https://classicmayan.org/portal/signcatalog?lang=en
- Images include standardized TWKM graph drawings by Christian Prager
  (University of Bonn), CC BY 4.0 when explicitly marked. Third-party
  photographs or other metadata may have distinct terms.
- No new photos were supplied or fabricated; no user pictures committed.
