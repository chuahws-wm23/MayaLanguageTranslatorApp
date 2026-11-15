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
