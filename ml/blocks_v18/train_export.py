#!/usr/bin/env python3
"""Train and evaluate one-class Maya glyph-BLOCK detection from local Roboflow v18.

Only localisation: a box can contain several actual Maya signs.
Use original train/valid/test split. Never automatically install an unvalidated
model into Android. Trained weights remain on the developer's own computer.

Example:
  python ml/blocks_v18/train_export.py --dataset "%USERPROFILE%/mb18" --epochs 45
"""
from __future__ import annotations
import argparse
from collections import Counter, defaultdict
import hashlib
import json
import os
from pathlib import Path
import shutil
import sys


def source_id(stem: str) -> str:
    """Recognise Roboflow's '.rf.HASH' *file rendition* (not independent drawing)."""
    return stem.split(".rf.")[0].lower()


def preflight(root: Path) -> dict:
    import yaml
    root = root.expanduser().resolve()
    yaml_file = root / "data.yaml"
    if not yaml_file.is_file():
        raise ValueError(f"Missing YOLO data.yaml under {root}")
    source = yaml.safe_load(yaml_file.read_text(encoding="utf8"))
    names = source.get("names")
    if isinstance(names, dict):
        names = [str(names[k]) for k in sorted(names, key=lambda v: int(v))]
    if not isinstance(names, list) or len(names) != 1:
        raise ValueError(f"Expected one-class glyph-blocks detection, got {names!r}")
    if str(names[0]).strip().lower().replace("_", "-") not in ("glyph-blocks", "glyph-block"):
        raise ValueError(f"Unexpected dataset class: {names!r}")
    report = {"dataset": str(root), "class_names": names, "split": {}}
    hashes_by_split = {}
    sources_by_split = {}
    for split, dirname in (("train", "train"), ("val", "valid"), ("test", "test")):
        images_dir = root / dirname / "images"
        labels_dir = root / dirname / "labels"
        if not images_dir.is_dir() or not labels_dir.is_dir():
            raise ValueError(f"Missing expected Roboflow YOLO folders: {images_dir}, {labels_dir}")
        images = sorted(p for p in images_dir.iterdir()
                        if p.is_file() and p.suffix.lower() in {".jpg", ".jpeg", ".png", ".webp"})
        if not images:
            raise ValueError(f"No {split} images in {images_dir}")
        total_boxes, empty, missing_labels, invalid = 0, 0, 0, 0
        sha, sources = set(), set()
        for img in images:
            sha.add(hashlib.sha256(img.read_bytes()).hexdigest())
            sources.add(source_id(img.stem))
            label = labels_dir / (img.stem + ".txt")
            if not label.is_file():
                missing_labels += 1
                continue
            text = label.read_text(encoding="utf8").strip()
            if not text:
                empty += 1
                continue
            for line in text.splitlines():
                values = line.split()
                if len(values) != 5:
                    invalid += 1
                    continue
                try:
                    cls = int(values[0])
                    nums = [float(v) for v in values[1:]]
                    import math
                    ok = cls == 0 and all(math.isfinite(v) for v in nums)
                    ok = ok and all(0 <= v <= 1 for v in nums)
                    ok = ok and nums[2] > 0 and nums[3] > 0
                except ValueError:
                    ok = False
                if not ok:
                    invalid += 1
                else:
                    total_boxes += 1
        if missing_labels or invalid:
            raise ValueError(f"{split}: {missing_labels} missing label files, {invalid} invalid YOLO rows.")
        hashes_by_split[split] = sha
        sources_by_split[split] = sources
        report["split"][split] = {
            "images": len(images), "boxes": total_boxes, "empty_label_images": empty,
            "unique_roboflow_stems": len(sources), "image_directory": str(images_dir)
        }
    # Exact duplicates and same-source re-exports must not cross train and evaluation.
    report["cross_split_exact_hash_collisions"] = {}
    report["cross_split_source_name_collisions"] = {}
    for a, b in (("train", "val"), ("train", "test"), ("val", "test")):
        key = f"{a}-{b}"
        report["cross_split_exact_hash_collisions"][key] = len(hashes_by_split[a] & hashes_by_split[b])
        report["cross_split_source_name_collisions"][key] = len(sources_by_split[a] & sources_by_split[b])
    report["warning"] = (
        "Different filenames can depict the same source monument/drawing. "
        "A zero overlap here is necessary, NOT sufficient for genuine source independence."
    )
    if any(report["cross_split_exact_hash_collisions"].values()) or any(
        report["cross_split_source_name_collisions"].values()
    ):
        raise ValueError("Data leakage between splits. Inspect report/filenames before training.")
    return report


def train(args, audit: dict):
    from ultralytics import YOLO
    import yaml
    import torch
    # YOLO's YAML path resolution differs from Roboflow's relative '../train/images'.
    # Use one generated YAML with absolute paths to avoid Windows parent-directory bugs.
    root = Path(args.dataset).expanduser().resolve()
    out = Path(args.output).expanduser().resolve()
    out.mkdir(parents=True, exist_ok=True)
    config = {
        "path": str(root),
        "train": str(root / "train" / "images"),
        "val": str(root / "valid" / "images"),
        "test": str(root / "test" / "images"),
        "names": {0: "glyph-blocks"},
        "nc": 1,
    }
    generated_yaml = out / "blocks_v18_absolute.yaml"
    generated_yaml.write_text(yaml.safe_dump(config, sort_keys=False), encoding="utf8")
    requested = args.device
    if requested == "auto":
        requested = "0" if torch.cuda.is_available() else "cpu"
    print("Device:", requested, "CUDA available:", torch.cuda.is_available(), flush=True)
    if torch.cuda.is_available():
        print("CUDA GPU:", torch.cuda.get_device_name(0), flush=True)
    print("Training only on train split; selecting weights by VALIDATION. "
          "Final test is evaluated once after training.", flush=True)
    model = YOLO("yolo11n.pt")
    model.train(
        task="detect", data=str(generated_yaml), epochs=args.epochs,
        imgsz=512, batch=args.batch, patience=12,
        seed=20261115, deterministic=True, device=requested,
        workers=0 if os.name == "nt" else 2,
        project=str(out), name="run", exist_ok=True,
        amp=True, plots=True, cache=False,
        degrees=4., shear=2., scale=.15, translate=.08,
        fliplr=0., flipud=0., # flips can change Maya block structures
    )
    best = out / "run" / "weights" / "best.pt"
    if not best.is_file():
        raise RuntimeError(f"Training did not produce {best}")
    trained = YOLO(str(best))
    validation = trained.val(data=str(generated_yaml), split="val", imgsz=512,
                             batch=args.batch, device=requested, workers=0, plots=False)
    test = trained.val(data=str(generated_yaml), split="test", imgsz=512,
                       batch=args.batch, device=requested, workers=0, plots=False)
    def measures(metrics):
        box = metrics.box
        return {"mAP50": round(float(box.map50), 5),
                "mAP50_95": round(float(box.map), 5),
                "precision": round(float(box.mp), 5),
                "recall": round(float(box.mr), 5)}
    valid_stats, test_stats = measures(validation), measures(test)
    # Provisional minimums, not a performance guarantee or comparison to grid fallback.
    gate = (valid_stats["mAP50"] >= .60 and test_stats["mAP50"] >= .60
            and test_stats["recall"] >= .60)
    print("VALIDATION:", valid_stats, flush=True)
    print("FINAL TEST:", test_stats, flush=True)
    print("PROVISIONAL DETECTOR GATE:", "PASS" if gate else "FAIL", flush=True)
    # Export explicitly the *raw* YOLO11 one-class head (1,5,N).
    export = Path(trained.export(format="onnx", imgsz=512, opset=17,
                                dynamic=False, simplify=False, nms=False,
                                device="cpu")).resolve()
    import onnxruntime as ort
    session = ort.InferenceSession(str(export), providers=["CPUExecutionProvider"])
    inputs, outputs = session.get_inputs(), session.get_outputs()
    if len(inputs) != 1 or inputs[0].type != "tensor(float)" or inputs[0].shape != [1, 3, 512, 512]:
        raise RuntimeError(f"Unexpected exported model input: {[(i.name, i.shape) for i in inputs]}")
    if len(outputs) != 1 or len(outputs[0].shape) != 3 or outputs[0].shape[:2] != [1, 5]:
        raise RuntimeError(f"Unexpected YOLO11 one-class raw output: {[(o.name,o.shape) for o in outputs]}")
    target = out / "maya_blocks_v18.onnx"
    shutil.copy2(export, target)
    report = {
        "dataset_audit": audit, "backbone": "YOLO11n",
        "model_source": "locally trained on Roboflow Maya Epigraphy v18 YOLO labels",
        "validation": valid_stats, "test_once": test_stats,
        "gate_passed": gate, "model_path": str(target),
        "model_sha256": hashlib.sha256(target.read_bytes()).hexdigest(),
        "bytes": target.stat().st_size,
        "onnx_input": inputs[0].name, "onnx_output": outputs[0].name,
        "onnx_shape": outputs[0].shape,
        "not_a_reading_classifier": True,
        "android_installed_automatically": False,
        "required_manual_phone_box_test": True,
        "license_notice": "Roboflow Maya Epigraphy CC BY 4.0; check Ultralytics AGPL/enterprise obligations.",
    }
    (out / "evaluation.json").write_text(json.dumps(report, indent=2), encoding="utf8")
    print("ONNX ready:", target)
    print("Audit/evaluation report:", out / "evaluation.json")
    if not gate:
        print("Do NOT install as trusted detector: provisional accuracy gate failed.")
    else:
        print("Candidate only: compare detected boxes on your phone before installing into the project.")
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", required=True, type=Path,
                        help="EXTRACTED v18 YOLO folder, e.g. C:\\Users\\cws\\mb18")
    parser.add_argument("--epochs", type=int, default=45)
    parser.add_argument("--batch", type=int, default=8)
    parser.add_argument("--device", default="auto", help="auto, cpu or 0")
    parser.add_argument("--output", type=Path, default=Path("ml/blocks_v18/output"))
    parser.add_argument("--audit-only", action="store_true")
    args = parser.parse_args()
    if not 1 <= args.epochs <= 300 or not 1 <= args.batch <= 64:
        parser.error("epochs must be 1..300 and batch 1..64")
    report = preflight(args.dataset)
    print("Dataset preflight:", json.dumps(report, indent=2), flush=True)
    if args.audit_only:
        return 0
    return train(args, report)


if __name__ == "__main__":
    sys.exit(main())
