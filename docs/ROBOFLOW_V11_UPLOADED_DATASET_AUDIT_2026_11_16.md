# Uploaded Roboflow v11 COCO segmentation — measured audit and next model

**Date:** 16 November 2026. **Branch:** `fyp-development`.

The user provided `Maya Glyphs segmentation model.v11i.coco-segmentation.zip`
(40.8 MB). I inspected the annotations, source image names, polygons,
class distribution and exported images directly. The complete user-provided
source ZIP has not been uploaded to the public repository.

## Inventory, not inferred estimates

| Split | Images | COCO polygons | Usable YOLO polygons | Unique original export names |
| --- | ---: | ---: | ---: | ---: |
| train | 2,307 | 4,046 | 4,046 | 756 |
| valid | 74 | 184 | 183 | 74 |
| test | 22 | 64 | 64 | 22 |
| **Total** | **2,403** | **4,294** | **4,293** | **852** |

- Every COCO object has one segmentation polygon; all images are 384x384.
- Class taxonomy: 378 category entries including one background
  `maya-glyphs` class, or **377 non-background Roboflow codes**.
  **362** codes occur in train; 155 of them have fewer than 5 annotations.
- 794 training images have more than one annotated sign component.
- No matching original *export filenames* occurred across training,
  validation and test. This does **not** prove archaeological source-artifact
  independence.
- One validation annotation ID 56 is a **0.19 by 0.27 pixel polygon**;
  it was conservatively excluded from the converted training targets.
- Validation has 13 Roboflow codes absent from the training annotations;
  the test set has 2. Training a hundreds-class recognizer without
  verifying codes/mappings would be unreliable.
- Most samples are **line drawings**, not camera photographs. The
  `README.dataset.txt` says CC BY 4.0 but independently derived artwork
  may have separate attribution and usage requirements.

## Model direction

**Use v11 polygons to train a separate ONE-class segmentation model
`glyph_component`, not a 377-class TWKM decoder.**

The user-facing two-stage workflow should be:

1. Existing YOLO11n v18 detects inscription glyph **blocks**;
2. The new one-class YOLO11n-seg identifies individual **graphic components**;
3. An independent verified TWKM or syllabary model retrieves top-three
   similar known reference drawings and can abstain;
4. Never present unverified matches as an authentic reading/translation.

The v11 instance polygons are not guaranteed to correspond to phonetic
syllables, so specialist checking remains necessary.

## Conversion and training status

A user-downloadable local-only training kit was generated in the
conversation, with the full converted YOLO split directories, 4,293
normalized polygons, a source provenance CSV, five passing converter
tests, an accepted dry run, and the `train_single_class.py` command.

**Conversion is complete. Model training and held-out mask mAP are NOT
complete.** The current container has CPU-only PyTorch but cannot install
Ultralytics because network resolution is unavailable. No new ONNX file
was produced, and the app's existing Android models remain unchanged.
Do not claim a new segmentation or 60–70% recognition result.

On a Windows NVIDIA GPU with a compatible recent CUDA PyTorch wheel
and `ultralytics` installed, extract the kit and run:

```powershell
python .\ml\research_roboflow_v11\train_single_class.py --data-dir .\yolo_v11_components --dry-run
python .\ml\research_roboflow_v11\train_single_class.py --data-dir .\yolo_v11_components --epochs 50 --batch 8 --imgsz 512 --device 0
```

The script evaluates the original 22-image test cohort **once** after
checkpoint selection on validation and attempts an ONNX segmentation
export. It is a task-appropriate **component localization** model, not a
reading-accuracy benchmark.

## Android integration block

YOLO11n-seg ONNX inference returns candidate box + mask coefficients and
prototype masks, unlike the existing YOLO11n v18 block detector's
`[1,5,N]` box-only output. A dedicated on-device decoder and
source-photo validation are required, and the new model must not replace
`maya_blocks_v18.onnx` or the 25-sign recognizer.
