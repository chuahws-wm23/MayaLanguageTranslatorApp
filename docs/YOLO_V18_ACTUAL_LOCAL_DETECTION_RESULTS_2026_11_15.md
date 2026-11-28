# Roboflow v18 — measured local YOLO11n glyph-block detector

**Reported:** 15 November 2026 from the user's locally generated `ml/blocks_v18/output/evaluation.json`.

**Status:** Training, validation, test, and ONNX export completed on the user's RTX 4050 Laptop GPU. Provisional detection evaluation gate passed. **Phone and visual box testing are still required before claiming Android detection works with this model.** The ONNX binary remains only on the user's computer; it has not been committed to GitHub.

## Dataset and annotation audit

Source: Maya Epigraphy, Roboflow Maya Glyph Blocks Detection v18, prepared as YOLO detection boxes under `C:\Users\cws\mb18_boxes`.

| Split | Images | Labeled glyph-block boxes | Invalid box rows |
|---|---:|---:|---:|
| Train | 320 | 8,602 | 0 |
| Validation | 67 | 1,888 | 0 |
| Test | 44 | 1,097 | 0 |
| **Total** | **431** | **11,587** | **0** |

SHA256-identical images and Roboflow source-filename stems do not overlap across train/validation/test splits. **This does not establish independent source monuments/drawings:** the same original artwork may be represented under different filenames.

## Training setup

- Backbone: Ultralytics **YOLO11n**, one class `glyph-blocks`.
- Input resolution: **512 × 512**.
- User command: `python .\ml\blocks_v18\train_export.py --dataset "$env:USERPROFILE\mb18_boxes" --epochs 45 --batch 8 --device auto`.
- GPU: NVIDIA GeForce RTX 4050 Laptop GPU.
- `torchvision_cuda_nms_cpu_fallback=true`: CUDA training works, but installed TorchVision lacks CUDA NMS, so only NMS postprocessing uses CPU.
- Test was evaluated once after validation-selected model training. These numbers are specific to this split and do **not** measure glyph reading or translation.

## Actual measured detection metrics

| Metric | Validation (67 images) | Test (44 images) |
|---|---:|---:|
| mAP@50 | 0.89045 | **0.78510** |
| mAP@50–95 | 0.65729 | **0.57286** |
| Precision | 0.92148 | **0.88148** |
| Recall | 0.82892 | **0.72546** |

The trainer's provisional gate required validation mAP50 >=0.60, test mAP50 >=0.60 and test recall >=0.60; `gate_passed=true`. This is **only a preliminary numerical acceptance gate**. There is visible generalization degradation between validation and test, and the test recall means some annotated blocks are missed.

Do not treat `mAP50=0.7851` as `78.51% glyph translation accuracy`. The reported precision/recall are object-detection metrics at the evaluator's operating point, not guarantees at the Android decoder's fixed confidence threshold.

## Exported file (user machine only)

```json
{
  "model_path": "ml/blocks_v18/output/maya_blocks_v18.onnx",
  "model_sha256": "d304cbda9e4194211ed0b1a7b862c53942acce37eb7736436ab5cb93cc97c53f",
  "bytes": 10563594,
  "onnx_input": "images",
  "onnx_output": "output0",
  "onnx_shape": [1,5,5376]
}
```

This raw one-class YOLO11 output is compatible *in shape* with the Android decoder at `GlyphV18Postprocessor.kt`; **input preprocessing, runtime compatibility and box-level phone parity have not yet been established**.

## Next: on-device verification

1. Verify SHA-256 and draw bounding boxes on a sample of held-out test images:

   ```powershell
   (Get-FileHash ".\ml\blocks_v18\output\maya_blocks_v18.onnx" -Algorithm SHA256).Hash
   python .\ml\blocks_v18\preview_detector.py --dataset "$env:USERPROFILE\mb18_boxes" --model ".\ml\blocks_v18\output\maya_blocks_v18.onnx" --count 8
   explorer ".\ml\blocks_v18\output\previews"
   ```

2. Check for false positives, missed blocks, and whether full glyph **blocks** are covered. A block can contain multiple signs.
3. For private local Android testing only, copy the verified ONNX to `app/src/main/assets/maya_blocks_v18.onnx`, sync/build, and use **Image → Maya glyph recognition → Panel scanning tools → Scan all glyphs automatically**. Check that result panel actually reports `Trained offline YOLO11n v18`, rather than the grid/seam fallback.
4. Measure latency, memory, boxes and missing/extra blocks on at least a few different test images on a physical Android device; compare with Python preview. If the ONNX fails to load, the app silently falls back and surfaces a fallback note — do not count fallback results as trained detector success.
5. Keep Research v4 single-sign predictions **unverified**; no new sign/English translation capability has been established.

## Credit and distribution

Images/boxes: Maya Epigraphy, Roboflow v18 (advertised CC BY 4.0; check source rights). Model training/export uses Ultralytics, whose AGPL/Enterprise conditions need review before distributing the Android app.

## Full 67-image validation: corrected fixed-threshold 300-box comparison

A second **user-run** evaluation uses original YOLO11n ONNX, fixed confidence
`0.35`, box matching IoU `0.50`, NMS IoU `0.45`, `--split valid --count 0`,
with `--max-blocks 300`. This removes the earlier script's artificial
**48-box** truncation; the full validation split has 1,888 labelled blocks.
The fixed operating-point counts are not mAP and should not be confused with
Ultralytics's confidence-swept precision/recall reported above.

| Fixed-threshold metric | Full image baseline | Baseline plus overlapping tiles |
|---|---:|---:|
| Correct detections (TP) | 1,623 | 1,696 |
| False detections (FP) | **170** | 430 |
| Missed ground-truth blocks (FN) | 265 | 192 |
| Precision | **0.9052** | 0.7977 |
| Recall | 0.8596 | **0.8983** |
| F1 @ IoU 0.50 | **0.8818** | 0.8450 |

Tiling found **73** extra true positive boxes and missed **73** fewer boxes,
but it added **260** false positives. It lowered fixed-threshold precision by
**10.75 percentage points** and F1 by **3.68 points**.
**Disposition: reject tiling as the default detector.** A higher
recall alone does not outweigh figure-art false positives and duplicate boxes.
Tiling is a Python experiment only; no Android code or model change was made.

The corrected evaluation reports `images_with_more_gt_blocks_than_cap=0`
and `minimum_forced_misses_due_to_cap=0` at cap 300.
The older **48-box** run (baseline TP 1,067, FP 83, FN 821,
F1 0.7024; tiled TP 1,102, FP 290, FN 786, F1 0.6720)
was artificially truncated and must not be quoted as full model recall.

### Important Android limitation, separate from Python results

`GlyphV18Postprocessor.kt` currently keeps **at most 48 blocks** per image
to limit phone inference costs and UI density, regardless of how many
instances the detector outputs. In the validation set alone,
`PalaceTablet` had **249** labelled blocks and `TC` had **230**.
Therefore this cap cannot represent all blocks of a dense inscription.
It is an application product/latency limitation rather than a detector
learning limitation. For dense panels, user-selected subregion or
grid-assisted inspection is needed until on-phone memory/latency and UI
handling have been evaluated for higher limits.

### Final decision for current FYP build

**Baseline detector retained; tiling not deployed; rotation modes removed
on user request.** The remaining release tasks are ONNX asset installation
and real Android phone tests for box location, speed and memory. The existing
48-region cap must be disclosed for dense panels. Do not tune model or
thresholds using the already visually exposed test images.

**Ready for local FYP prototype integration:** yes, with measured limitations.
**Validated for arbitrary inscriptions or reliable Maya translation:** no.
