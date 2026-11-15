# Maya glyph BLOCK detection — Roboflow v18 → optional offline Android YOLO11n

This module trains a one-class **glyph-block bounding-box** detector using the
user-downloaded v18 YOLOv8-export ZIP. The deployed recognition model for
individual TWKM signs (Research v4) remains unchanged.

**The 494 MB dataset is not uploaded into Git.** Keep the extracted folder on
your own machine, e.g. `C:\Users\cws\mb18`. The project's trainer does not
need to read a remote Roboflow API key and makes no changes to that dataset.

## Step 0 — Update Android Studio Git checkout safely

From Android Studio PowerShell terminal:

```powershell
git status --short
# Only if clean:
git switch fyp-development
git pull --ff-only origin fyp-development
```

## Step 1 — Check local extracted dataset

```powershell
Test-Path "$env:USERPROFILE\mb18\data.yaml"
Get-ChildItem "$env:USERPROFILE\mb18"
```

Expected: data.yaml, train/images+labels, valid/images+labels, test/images+labels.
If your Roboflow extraction is elsewhere, replace the --dataset value below.

## Step 2 — Install Python training libraries

```powershell
python -m pip install -U ultralytics PyYAML onnx onnxruntime
```

Ultralytics installs PyTorch if required. NVIDIA GPU support depends on the
installed Torch/CUDA wheel; if GPU is unsupported, use `--device cpu`
(very slow) or install a compatible PyTorch wheel from official docs. No
GPU is required for the preflight checks.

## Step 3 — Audit the actual 431-image dataset before training

```powershell
python .\ml\blocks_v18\train_export.py --dataset "$env:USERPROFILE\mb18" --audit-only
```

This verifies one class `glyph-blocks`, valid YOLO normalized xywh labels,
required image/label pairs and obvious cross-split duplicate sources.
Training must not proceed if the audit reports a missing label, collision,
or an unexpected class. Distinct source names can still represent the same
monument; audit is necessary, not complete proof of independence.

## Step 4 — Train YOLO11n, validate, test and export ONNX

```powershell
python .\ml\blocks_v18\train_export.py --dataset "$env:USERPROFILE\mb18" --epochs 45 --batch 8 --device auto
```

If GPU runs out of memory, lower to `--batch 4`. Model uses 512×512 input,
one class, no vertical/horizontal flips. Original train/valid/test splits are
preserved; validation selects `best.pt`, and test is evaluated once.
**Roboflow's published RF-DETR mAP is NOT the accuracy of this YOLO model.**

Outputs under ignored `ml/blocks_v18/output`:

- `run/weights/best.pt` — trained PyTorch checkpoint.
- `maya_blocks_v18.onnx` — ONNX 1×3×512×512 RGB input, one raw
  [1,5,N] head; no embedded NMS.
- `evaluation.json` — split audit, precision, recall, mAP@50 and mAP@50:95,
  model SHA256, provisional pass/fail status.
- YOLO training curves and PR images.

**Do not copy any model that fails the provisional gate.** Even a pass
requires inspecting phone boxes and latency. Published v18 project numbers
describe a DIFFERENT hosted RF-DETR model and cannot be claimed for YOLO11n.

## Step 5 — Visualize boxes on unseen test pictures

```powershell
python .\ml\blocks_v18\preview_detector.py --dataset "$env:USERPROFILE\mb18" --model ".\ml\blocks_v18\output\maya_blocks_v18.onnx" --count 6
explorer ".\ml\blocks_v18\output\previews"
```

Inspect whether each entire glyph **block**, not a whole inscription or an
individual component, is inside its bounding rectangle. Save screenshots of
correct, missing and false boxes for the report. A detected block can
contain several phonetic/logographic components and should NOT be passed
off as one independently translated sign.

## Step 6 — Install into Android only after evidence checks

If the evaluation report passes the provisional gate AND test previews have
reasonable boxes, install this exact ONNX model locally:

```powershell
Copy-Item ".\ml\blocks_v18\output\maya_blocks_v18.onnx" ".\app\src\main\assets\maya_blocks_v18.onnx" -Force
```

Sync Android Studio and rebuild the app. On Image → Maya glyph recognition
→ Panel scanning tools → Scan all glyphs automatically, the app uses
trained ONNX block detection first, NMS to remove duplicate boxes, and
then the existing 10-sign Research v4 as an **unverified** follow-up.

If the asset is missing, broken or finds no usable boxes, the app falls
back to the existing offline grid/seam detector. The result panel says
which detector path was used. Grid-assisted scan remains available.

Before release, compare detection boxes, input letterbox parity, FPS/device
RAM and output shape on the emulator and a physical phone. Accuracy and
translation are separate problems.

## Source & licence

- Source images/boxes: <https://universe.roboflow.com/maya-epigraphy/maya-glyph-blocks-detection/dataset/18> by Maya Epigraphy (CC BY 4.0; verify individual source rights)
- Backbone: Ultralytics YOLO11n. Review Ultralytics AGPL/Enterprise
  licence obligations before distributing a closed-source build.
- This model detects **glyph blocks only**; it neither reads Maya syntax
  nor expands v4's 10 TWKM sign recognition categories.
