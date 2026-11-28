# v11 individual Maya glyph component segmenter — trained checkpoint to Android research mode

**Status:** Actual training finished on the user's RTX 4050 Laptop GPU.
Uploaded `best.pt` is a valid 6,019,556-byte PyTorch ZIP checkpoint containing
Ultralytics `SegmentationModel` and a segmentation head. SHA256:
`d879de0382d9c942a2c731ffe4442534f81db569094f7b000e3773c0e06df427`.

No third-party checkpoint bytes were committed to this repository.
This checkpoint format uses Python object serialization; only load it
in your trusted local Ultralytics installation.

## Reported held-out COCO v11 performance (22 test images)

| Metric | Value |
| --- | ---: |
| Box mAP50 | **0.85838** |
| Mask mAP50 | **0.85828** |
| Mask mAP50-95 | **0.64089** |

These are useful *instance segmentation* results on a tiny original dataset
test split of primarily glyph drawings. They do **NOT** establish
classification, phonetic recognition, epigraphic reading, real stone-photo
accuracy, or generalization to independent archaeological sources.
The mAP50 is not a literal percentage of photos perfectly segmented.

## Export on the Windows PC that trained the model

Open Android Studio's **PowerShell Terminal** in the project root and run:

```powershell
git switch fyp-development
git pull origin fyp-development

$best = ".\ml\runs\maya_v11_sign_segmentation\fit-2\weights\best.pt"
$onnx = ".\ml\runs\maya_v11_sign_segmentation\fit-2\weights\best.onnx"

Test-Path $best
Test-Path $onnx
```

If the first command prints `True` and the second `False`, export:

```powershell
python -c "from ultralytics import YOLO; YOLO(r'ml\runs\maya_v11_sign_segmentation\fit-2\weights\best.pt').export(format='onnx',imgsz=512,opset=13,dynamic=False,simplify=False,nms=False)"
```

If export is missing dependencies, install the recommended `onnx` package
into the same Python environment as `ultralytics`.
The export's tensors **must** be:

- Model input `[1,3,512,512]`, RGB float32 0 to 1;
- `output0` `[1,37,5376]` = four xywh box values, one class score, 32 mask coefficients;
- `output1` `[1,32,128,128]` = mask prototype tensor.

Then run the repository's exporter compatibility check:

```powershell
python -m pip install onnx
python .\ml\research_roboflow_v11\verify_android_onnx.py --onnx $onnx
```

**Only if verification succeeds**, copy your locally trained weight file:

```powershell
New-Item -ItemType Directory -Force ".\app\src\main\assets" | Out-Null
Copy-Item $onnx ".\app\src\main\assets\maya_signs_v11_seg.onnx" -Force
```

Rebuild and launch the Android app; open Photo/Glyph Recognition, choose a
photo, open **Panel scanning tools (optional)** and tap
**Find individual glyph components (research)**.

This is a **new opt-in path**, not the existing block scanning action.
Without the ONNX asset the button will not be available, and the old
block and v4 recognition methods remain unchanged. New predicted masks are
presented as approximate **bounding rectangles** for user inspection:
a separate implementation would be needed for colored, pixel-exact mask overlays.

## What changed in Android code

- `integration/glyph/GlyphV11SegDetector.kt` accepts only compatible raw
  one-class YOLO11 segmentation outputs, NOT the detector v18's layout.
- `integration/glyph/GlyphV11SegPostprocessor.kt` filters proposals, applies
  NMS, generates prototype masks from coefficients, and builds source-image
  rectangles around the positive mask pixels.
- `presentation/translation/GlyphRecognitionViewModel.kt` and the Compose
  screen expose an optional local scan that **never invents readings**.
- Segmentation and postprocessing unit tests run in Android CI.

Unlike a Python or native .pt checkpoint, an exported .onnx is loadable via
the ONNX Runtime dependency already in the Android application.
The trained ONNX is kept local (`.gitignore`) until independent photo
evaluation and redistribution/licensing review.

## Critical follow-up

1. Test on a photo **not** from the training, validation, or test data; check
   missed and duplicate glyph components.
2. Compare on-device output to Python Ultralytics output on the same image.
3. Profile latency/memory on actual Android hardware.
4. Keep a human validation step for any sign identity and a separate
   classifier for TWKM sign readings. YOLO v11 polygon classes are not
   automatically verified linguistic sign identities.
5. Follow the upstream Ultralytics model license conditions before distributing
   an APK with their pretrained-derived weights (AGPL-3.0 or a commercial
   agreement, depending on use).
