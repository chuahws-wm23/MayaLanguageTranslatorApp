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

## Troubleshooting: "273 invalid YOLO rows"

The earlier strict validator assumed every row had exactly five values:
\`class cx cy width height\`. It could not tell whether a label was a valid
*polygon* (YOLO segmentation), a coordinate slightly past the image border,
or truly broken data.

Update to the newest \`fyp-development\`, then **diagnose first**:

\`\`\`powershell
python .\ml\blocks_v18\train_export.py --dataset "$env:USERPROFILE\mb18" --diagnose-only
\`\`\`

Look at \`label_formats_and_reasons\` and \`first_issues\`. Examples:

- \`polygon_to_box\`: valid YOLO segmentation polygon, but detector needs a box
- \`clip_box_border\`: box only slightly outside normalized frame; 75% or more remains
- \`out_of_normalized_range\`, \`unexpected_class\`, \`non_numeric\`: cannot safely auto-fix, investigate source labels

**Only if all rows are repairable**, make a separate dataset copy:

\`\`\`powershell
python .\ml\blocks_v18\train_export.py --dataset "$env:USERPROFILE\mb18" --prepare-boxes
python .\ml\blocks_v18\train_export.py --dataset "$env:USERPROFILE\mb18_boxes" --audit-only
\`\`\`

Images are hardlinked when possible to avoid duplicating 494 MB; labels are
converted to five-number detection boxes. Original \`mb18\` remains unchanged,
and an explicit \`conversion_report.json\` records what was transformed.
If the command refuses to repair a category, do **not** delete or relabel
the bad rows blindly; inspect the first issues and source annotations.

Then use \`--dataset "$env:USERPROFILE\mb18_boxes"\` for training. The
training script never silently alters untrusted labels.

## Troubleshooting: training crashes after epoch 1 with `torchvision::nms` CUDA

If the dataset passes its audit, the YOLO model begins GPU training, and
epoch validation fails with:

```text
NotImplementedError: Could not run 'torchvision::nms' with arguments from the 'CUDA' backend
```

the local PyTorch/TorchVision installation has CUDA training support but
**does not have a CUDA TorchVision NMS kernel**. This is an environment issue,
not invalid glyph-block labels.

The trainer now **probes TorchVision CUDA NMS before epoch 1**. If only the
CPU NMS kernel is available, it installs a **process-local** fallback: only
NMS boxes/scores transfer to CPU, and the selected indices return to GPU.
All YOLO convolutions, gradients and validation forward passes remain on GPU.
Your installed libraries, dataset and checkpoint files are not modified.

After updating from GitHub, just run the same command again:

```powershell
python .\ml\blocks_v18\train_export.py --dataset "$env:USERPROFILE\mb18_boxes" --epochs 45 --batch 8 --device auto
```

The console should say `CPU NMS compatibility fallback: enabled`. The
fallback may make validation somewhat slower and can differ in tie-breaking
when boxes have equal scores. To restore full-speed CUDA NMS later, install
a compatible CUDA-enabled Torch/TorchVision pair from
https://pytorch.org/get-started/locally/ in a dedicated Python environment.
Do not randomly uninstall or mix wheel indexes during a live FYP training run.

## Visual failure analysis: stop guessing which boxes are correct

The current 45-epoch v18 YOLO11n scored mAP50=0.78510, mAP50-95=0.57286,
precision=0.88148, recall=0.72546 on 44 test images (1,097 boxes).
Uploaded previews show genuine missed small/faint glyphs, non-glyph artwork
false positives, and boxes covering several different blocks.

**Do not claim this detector is finished from mAP alone.** Inspect the
labelled ground truth. Side-by-side previews show green=annotated box,
blue=single-image baseline at threshold .35, orange=candidate. Use short
number tags rather than overlarge `glyph-blocks 0.xx` text.

Use `valid` (67 images), **not the previously inspected 44 test images**,
to choose between alternate multi-scale strategies. These are diagnostic
Python candidates and are **NOT deployed to Android**:

```powershell
# Preview eight spaced-apart validation images, baseline vs tiled candidate
python .\ml\blocks_v18\preview_detector.py --dataset "$env:USERPROFILE\mb18_boxes" --model ".\ml\blocks_v18\output\maya_blocks_v18.onnx" --split valid --mode tiled --count 8 --device cpu

# Preview rotation-sensitive candidates (0°, 90°, 180°, 270°)
python .\ml\blocks_v18\preview_detector.py --dataset "$env:USERPROFILE\mb18_boxes" --model ".\ml\blocks_v18\output\maya_blocks_v18.onnx" --split valid --mode rotated --count 8 --device cpu

explorer ".\ml\blocks_v18\output\diagnostics"

# Only after previews look plausible, compare whole validation split
python .\ml\blocks_v18\preview_detector.py --dataset "$env:USERPROFILE\mb18_boxes" --model ".\ml\blocks_v18\output\maya_blocks_v18.onnx" --split valid --mode tiled --count 0 --device cpu
```

This runs on **CPU** because the current TorchVision install has no CUDA
NMS kernel. CPU-only inference can be slower. `--count 0` processes all
images and emits numeric counts without writing dozens of giant images.
The script uses confidence .35, NMS IoU .45 (matching Android) and one-to-one
GT matching at IoU .50. It saves
`ml/blocks_v18/output/diagnostics/comparison_valid_tiled.json`
with **TP, FP, FN, precision, recall and F1** for baseline and candidate.

Don't pick the variant with the most rectangles. Prefer a candidate that
raises **correctly matched** blocks and validation recall *without* reducing
validation precision/F1 unacceptably. Multi-view and tiling can introduce
extra false positives. A box around two signs will still be wrong if the
annotations require two boxes. Review the source label style first.
For long-term generalization, add verified high-contrast/low-contrast,
rotation, weathered stone, circular inscriptions and **negative figure-art**
training photos with correctly drawn boxes; preserve monument/source grouping.
Keep these new photos out of the final independent test set.

The script does NOT retrain weights, change the ONNX, alter APK behavior,
or improve Maya sign **translation**. The user-uploaded seven examples
were already inspected and cannot fairly serve as fresh blind test data.
