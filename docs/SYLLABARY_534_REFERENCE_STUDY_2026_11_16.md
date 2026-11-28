# Syllabary visual recognition: source-joined TWKM expansion and local private gallery

**Date:** 16 November 2026  
**Status:** Completed reproducible research; **not deployed to Android**  
**Current source branch:** `fyp-development`

## Goal and safety boundary

The existing Android project supports v18 YOLO glyph-BLOCK detection, v4 ten-TWKM-sign recognition, and optional TWKM25 research classification. The four user-supplied syllabary charts are **different labels and artwork** from TWKM sign numbers, and a glyph block can contain more than one sign. It is unsafe to treat an entire YOLO block as one syllable or to map arbitrary Roboflow codes to TWKM phonograms.

A separate local-only prototype now provides **top-three visual similarity suggestions** from reference drawings. These are unverified suggestions, not an accurate or automatic reading of any unknown Maya inscription.

## Actual data work

1. The original chart study contained 86 syllable labels and 152 crop candidates from four user-supplied charts. These have incomplete or uncertain annotations and uncertain redistribution rights.
2. The pinned Hugging Face TWKM dataset `tadad/twkm-maya-glyphs` revision `73fe9da1d975a5f1e588c7d42c952dfda53cefd0` includes 207 proposed phonogram decipherment rows.
3. Joined only `decipherments` where `type=phonogram`, `sign_reference_resolved=true` and the source-proposed value **exactly matched** one of the 86 chart syllable spellings after apostrophe normalization. Excluded conflicting phonogram values for the same sign ID. Joined to `graphs` by `sign_no` and retained only drawing rows explicitly marked CC BY 4.0, skipping duplicate/conflicting pixels.
4. This yielded **382 additional TWKM standardized drawings** across the 86 syllable values, making a combined private research gallery of **534** candidate reference illustrations.

**Do not claim 534 independent photographs.** The TWKM rows are standardized drawings; graph variants may share source artifacts. The chart sources also share one publication. TWKM decipherments are proposed scholarly readings requiring contextual review.

## Reproducible evaluation (same 45 heldout chart alternate drawings)

For every comparison, the first alternate chart drawing for each of 45 labels was **excluded from the training/reference gallery**. Reference images from the other chart positions share the same underlying book, so this is only a source-related exploratory comparison.

| Method | Top-1 correct | Top-3 includes chart syllable |
|---|---:|---:|
| Chart-only pixel/HOG | 9/45 (20.0%) | 14/45 (31.1%) |
| Chart + TWKM pixel/HOG | 9/45 (20.0%) | **17/45 (37.8%)** |
| Chart + TWKM frozen MobileNetV3 and HOG/pixel hybrid | **13/45 (28.9%)** | 16/45 (35.6%) |

Same-weight hybrid score = 25% cosine similarity of frozen ImageNet MobileNetV3-Small embeddings + 75% previous 40%-pixel/60%-HOG similarity. This was an exploratory method comparison, **not validated scoring calibration**.

**Different-source check:** Querying all 86 primary chart drawings against only the TWKM graph drawings using the MobileNetV3 hybrid gave 11/86 top-one and 22/86 top-three. There is severe source-style generalization weakness.

**Supervised fine-tuning was tried and rejected:** For 20 syllables chosen solely by having at least six TWKM reference drawings, trained a 20-way MobileNetV3 classifier on 210 source-related inputs and validated on separate TWKM graph drawings. It obtained only **4/15 top-one and 8/15 top-three** on held-out chart variants; it was not adopted.

The discrepancy with earlier 5/45 chart-only scores is from a **different gallery exclusion protocol**. Only numbers in the same table may be compared directly. None is independent field-photo recognition accuracy, or a guarantee of Maya translation.

## Deliverables and Android decision

- Public, **code-only** dataset audit: `ml/syllabary_ml/twkm_phonograms_audit.py`.
- Explicitly CC BY TWKM reference exporter: `ml/syllabary_ml/export_twkm_licensed_phonograms.py`.
- Reproducible GitHub Actions:
  - [phonogram audit](https://github.com/chuahws-wm23/MayaLanguageTranslatorApp/actions/runs/43760231890)
  - [CC BY reference exporter](https://github.com/chuahws-wm23/MayaLanguageTranslatorApp/actions/runs/43760434741)
- **Private** local study includes a 534-reference index, original and training-only galleries, heldout alternate chart crops, top-three image search CLI, source attribution, and evaluated reports. This package must not be uploaded to the public repository while rights of syllabary chart artwork are unconfirmed.

**Model status:** The top-1 result is **far below the requested 60–70%**. The running Android v4/v18/TWKM25 models remain unchanged. Do not publish syllabary matching as a reliable translation module.

## Practical next engineering actions

1. Obtain independently labelled, genuinely new source drawings and real carved sign photographs, with enough samples per syllable.
2. Set aside artifact/source-group-separated untouched test images; measure macro F1, top-1, top-3, correct accepted and false accepted unknowns.
3. Develop individual-sign segmentation inside YOLO glyph blocks; block detection itself does not resolve syllabogram components.
4. Check on-device preprocessing parity, latency and RAM before attempting integration of any improved model.
5. If chart-image reuse rights are cleared, an **opt-in, locally installed visual-reference gallery** may be exposed as unverified image search before reliable automatic transliteration is established.

## Attribution

TWKM standardized graph drawings: Christian Prager, Text Database and Dictionary of Classic Mayan, University of Bonn, explicitly marked CC BY 4.0. See https://classicmayan.org/portal/signcatalog and https://huggingface.co/datasets/tadad/twkm-maya-glyphs. Other metadata has separate reuse conditions.

The chart source is the Maya Archaeologist educational page reproducing Kettunen and Helmke (2014), pp. 74–77; redistribution permission for chart-derived images is not established.
