# Wider TWKM catalogue negative-sign research — measured results

**Date:** 15 November 2026  
**Dataset:** [tadad/twkm-maya-glyphs](https://huggingface.co/datasets/tadad/twkm-maya-glyphs)  
**Pinned revision:** `73fe9da1d975a5f1e588c7d42c952dfda53cefd0`  
**Code:** `ml/catalogue_expansion/twkm_background_screen.py`  
**Reproducible GitHub Actions run:** https://github.com/chuahws-wm23/MayaLanguageTranslatorApp/actions/runs/43741377666  
**Artifact:** https://github.com/chuahws-wm23/MayaLanguageTranslatorApp/actions/runs/43741377666/artifacts/14171569924

## Objective

Investigate whether using the remaining documented TWKM sign identities as
**known negative examples**, rather than manufacturing additional positive
classes from one drawing each, reduces the current 25-sign prototype's false
recognition of unsupported signs.

## Input provenance and split

- Original 25 TWKM sign categories: 101 training drawings, 25 validation and
  25 previously exposed historical test drawings.
- Additional negative training material: **979 other TWKM sign identities**,
  one graph drawing per identity, all explicitly different from the 25 known
  labels and from the reserved unknown calibration/test sign IDs.
- Forty further *untrained* TWKM sign identities form the unknown calibration
  set; another **40 unseen sign identities** form the unknown regression set.
- TWKM snapshot yielded 1,084 image-usable sign identities after licensing
  and image cleaning in this pipeline. This is NOT the total public
  catalogue sign count (1,098).
- Images are distinct at hash/sign-ID level, but similar allographs may share
  an underlying source artefact. These drawing results are NOT camera results.

The base model remains **MobileNetV3-Small frozen pretrained features with a
25-sign RBF SVM** trained on verified TWKM catalogue signs. A separate
known-versus-other SVM is trained on the 25 known training embeddings and the
979 additional-sign embeddings. Its cutoff is selected using only the known
validation and unknown calibration sets.

## Measured comparison

| Metric | Baseline 25-sign model | With full-catalogue negative screen |
|---|---:|---:|
| Known validation correct-and-accepted | 13/25 | 12/25 |
| Unknown calibration falsely accepted | 3/40 | 1/40 |
| Historical known catalogue top-1 | 17/25 | 17/25 |
| Historical known correct-and-accepted | 12/25 | 12/25 |
| Historical known wrongly accepted as different sign | 2/25 | 2/25 |
| Disjoint unknown test falsely accepted | 2/40 | **1/40** |
| Disjoint unknown test correctly rejected | 38/40 | **39/40** |

The selected candidate used binary RBF SVM C=10 and a validation-chosen
decision cutoff approximately -0.851497. It reduced unknown acceptance on
the 40 example regression cohort, with no loss in correctly accepted
historical known examples. This is only **one** additional rejection over
40 test signs, far too small to establish statistical significance or
phone-photo reliability.

## Interpretation and next decision

The complete pinned Hugging Face dataset is already used by the research
training pipeline. It **does** improve the number and diversity of known
negative examples; it **does not** magically supply multiple independent
training photographs for every one of the 1,098 catalogue sign records.

The expanded negative screen is **research-only** and was not packaged into
the Android app, and the existing 10-class v4 default and optional 25-sign
Android research model remain unchanged.

Needed before adopting as app behavior:

1. Gather independent photographs of single signs and physically distinct
   source artefacts; verify the TWKM sign ID for known signs and certify
   unsupported signs as truly out-of-set.
2. Repeat correct-and-accepted/false-accepted measurements on an untouched
   source-group-disjoint camera test, including uncertainty intervals.
3. Compare Android-versus-Python feature extraction, unknown screen, and
   per-image latency before enabling the binary novelty model.
4. Retain experimental display ("Possible TWKM sign #N") and always allow
   "Not identified"; do not claim Classic Maya sentence translation.

## Credits and rights

TWKM standardized graph drawings: Christian Prager / University of Bonn,
when explicitly CC BY 4.0, included in the independently packaged
Hugging Face snapshot. Other metadata and third-party photographs have
distinct rights. Graph variants and readings may change with scholarship.
