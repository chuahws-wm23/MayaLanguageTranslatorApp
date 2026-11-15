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
import math
import os
from pathlib import Path
import shutil
import sys


def source_id(stem: str) -> str:
    """Recognise Roboflow's '.rf.HASH' *file rendition* (not independent drawing)."""
    return stem.split(".rf.")[0].lower()


def parse_annotation(raw: str) -> tuple[str, str | None]:
    """Return (reason, repaired normalized YOLO detection row or None).

    A polygon is convertible to its enclosing box, but must be *explicitly*
    converted to a separate dataset folder with --prepare-boxes.
    Slightly out-of-frame coordinates may be clipped ONLY if >=75% of the
    original box remains visible. Suspicious coordinates are never discarded.
    """
    parts = raw.strip().split()
    if not parts:
        return "empty_row", None
    try:
        cls = int(parts[0])
        coords = [float(v) for v in parts[1:]]
    except ValueError:
        return "non_numeric", None
    if cls != 0:
        return "unexpected_class", None
    if not all(math.isfinite(v) for v in coords):
        return "non_finite", None
    if len(parts) == 5:
        x, y, w, h = coords
        if w <= 0 or h <= 0:
            return "nonpositive_box_size", None
        if all(0 <= v <= 1 for v in coords):
            return "valid_box", " ".join(parts)
        # Only allow a *small* normalized-border error, not arbitrary
        # pixel coordinates or large out-of-frame labelled objects.
        if any(v < -.03 or v > 1.03 for v in coords):
            return "out_of_normalized_range", None
        left, right = x - w / 2, x + w / 2
        top, bottom = y - h / 2, y + h / 2
        l, r = max(0.0, left), min(1.0, right)
        t, b = max(0.0, top), min(1.0, bottom)
        retained = (max(0.0, r-l) * max(0.0, b-t)) / (w*h)
        if retained < .75:
            return "outside_frame_too_much", None
        return "clip_box_border", _format_box(l, t, r, b)
    if len(parts) >= 7 and len(parts) % 2 == 1:
        # YOLO instance-segmentation polygons: class x1 y1 x2 y2 ...
        if any(v < -.03 or v > 1.03 for v in coords):
            return "polygon_out_of_range", None
        xs = coords[0::2]
        ys = coords[1::2]
        if len(xs) < 3 or max(xs) - min(xs) <= 1e-6 or max(ys) - min(ys) <= 1e-6:
            return "degenerate_polygon", None
        l, r = max(0.0, min(xs)), min(1.0, max(xs))
        top, bottom = max(0.0, min(ys)), min(1.0, max(ys))
        retained = ((r-l)*(bottom-top)) / ((max(xs)-min(xs))*(max(ys)-min(ys)))
        if retained < .75 or r <= l or bottom <= top:
            return "polygon_outside_frame_too_much", None
        key = "polygon_to_box" if all(0 <= v <= 1 for v in coords) else "clip_polygon_to_box"
        return key, _format_box(l, top, r, bottom)
    return "unexpected_column_count", None


def _format_box(left: float, top: float, right: float, bottom: float) -> str:
    if not (right > left and bottom > top):
        raise ValueError("Empty clipping rectangle")
    cx, cy = (right + left) / 2, (bottom + top) / 2
    width, height = right - left, bottom - top
    return f"0 {cx:.8f} {cy:.8f} {width:.8f} {height:.8f}"


REPAIRABLE = {"valid_box", "clip_box_border", "polygon_to_box", "clip_polygon_to_box"}


def preflight(root: Path, diagnose: bool = False) -> dict:
    """Audit actual exported YOLO labels, including a useful breakdown.

    Normal audit requires plain detection boxes, because Android consumes
    bounding boxes and the YOLO trainer is task='detect'. Diagnose mode
    prints representative failures without starting training.
    """
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
        reasons = Counter()
        samples = []
        sha, sources = set(), set()
        for img in images:
            sha.add(hashlib.sha256(img.read_bytes()).hexdigest())
            sources.add(source_id(img.stem))
            label = labels_dir / (img.stem + ".txt")
            if not label.is_file():
                missing_labels += 1
                continue
            content = label.read_text(encoding="utf-8-sig").strip()
            if not content:
                empty += 1
                continue
            for line_number, line in enumerate(content.splitlines(), 1):
                kind, box = parse_annotation(line)
                reasons[kind] += 1
                if kind == "valid_box":
                    total_boxes += 1
                else:
                    invalid += 1
                    if len(samples) < 10:
                        samples.append({
                            "filename": label.name, "line": line_number,
                            "reason": kind, "value_count": len(line.split()),
                            "raw_preview": line[:140],
                            "safe_conversion_possible": box is not None,
                        })
        hashes_by_split[split] = sha
        sources_by_split[split] = sources
        report["split"][split] = {
            "images": len(images), "valid_detection_boxes": total_boxes,
            "invalid_or_non_box_rows": invalid,
            "label_formats_and_reasons": dict(reasons),
            "first_issues": samples,
            "missing_label_files": missing_labels,
            "empty_label_images": empty,
            "unique_roboflow_stems": len(sources),
            "image_directory": str(images_dir)
        }
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
    if not diagnose:
        issues = [
            f"{part}: {stats['invalid_or_non_box_rows']} affected rows "
            f"({stats['label_formats_and_reasons']}) and {stats['missing_label_files']} missing label files"
            for part, stats in report["split"].items()
            if stats["invalid_or_non_box_rows"] or stats["missing_label_files"]
        ]
        if issues:
            raise ValueError("YOLO annotation problems:\n  " + "\n  ".join(issues)
                             + "\nRun --diagnose-only to see actual examples. "
                               "If every row is repairable, use --prepare-boxes "
                               "to create a SEPARATE safe detection dataset.")
        if any(report["cross_split_exact_hash_collisions"].values()) or any(
            report["cross_split_source_name_collisions"].values()
        ):
            raise ValueError("Data leakage between splits. Inspect audit before training.")
    return report


def prepare_boxes(root: Path, target: Path) -> dict:
    """Create plain YOLO detection boxes without ever modifying original ZIP/extract.

    Uses hardlinks for images on the same Windows volume (copy fallback).
    Fails before writing ANY files if an annotation is not safely convertible.
    """
    import yaml
    root, target = root.expanduser().resolve(), target.expanduser().resolve()
    if root == target or target.exists():
        raise ValueError("Choose a NEW, non-existing output directory outside the original dataset.")
    audit = preflight(root, diagnose=True)
    if any(audit["cross_split_exact_hash_collisions"].values()) or any(
        audit["cross_split_source_name_collisions"].values()
    ):
        raise ValueError("Cannot prepare boxes: original train/valid/test sources overlap.")
    for part, stats in audit["split"].items():
        if stats["missing_label_files"]:
            raise ValueError(f"{part}: missing label files; refusing to invent background labels.")
        bad = {key: count for key, count in stats["label_formats_and_reasons"].items()
               if count > 0 and key not in REPAIRABLE}
        if bad:
            raise ValueError(f"Cannot safely convert {part} annotations: {bad}. "
                             "Use --diagnose-only to review examples first.")
    target.mkdir(parents=True)
    converted = Counter()
    for dirname in ("train", "valid", "test"):
        img_dir, label_dir = root / dirname / "images", root / dirname / "labels"
        new_img_dir, new_label_dir = target / dirname / "images", target / dirname / "labels"
        new_img_dir.mkdir(parents=True)
        new_label_dir.mkdir(parents=True)
        for img in img_dir.iterdir():
            if not img.is_file() or img.suffix.lower() not in {".jpg", ".jpeg", ".png", ".webp"}:
                continue
            dest = new_img_dir / img.name
            try:
                os.link(img, dest)
            except OSError:
                shutil.copy2(img, dest)
            label = label_dir / f"{img.stem}.txt"
            normalized = []
            for line in label.read_text(encoding="utf-8-sig").splitlines():
                if not line.strip():
                    continue
                status, value = parse_annotation(line)
                if status not in REPAIRABLE or value is None:
                    raise RuntimeError(f"Unexpected unrepairable label in {label}: {status}")
                normalized.append(value)
                converted[status] += 1
            (new_label_dir / label.name).write_text("\n".join(normalized) +
                ("\n" if normalized else ""), encoding="utf8")
    (target / "data.yaml").write_text(yaml.safe_dump({
        "path": str(target), "train": "train/images", "val": "valid/images",
        "test": "test/images", "nc": 1, "names": ["glyph-blocks"]
    }, sort_keys=False), encoding="utf8")
    verification = preflight(target)
    report = {
        "source": str(root), "prepared": str(target),
        "row_categories_converted": dict(converted), "result": verification,
        "preserves_original_images": True,
        "does_not_relabel_semantic_sign_identities": True,
    }
    (target / "conversion_report.json").write_text(json.dumps(report, indent=2), encoding="utf8")
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
    parser.add_argument("--diagnose-only", action="store_true",
                        help="Print reasons and sample rows, do not train.")
    parser.add_argument("--prepare-boxes", action="store_true",
                        help="Make a new data folder with valid YOLO boxes; never edit originals.")
    parser.add_argument("--prepared-output", type=Path,
                        help="Destination for prepared boxes (default next to input: <dataset>_boxes).")
    args = parser.parse_args()
    if not 1 <= args.epochs <= 300 or not 1 <= args.batch <= 64:
        parser.error("epochs must be 1..300 and batch 1..64")
    if args.diagnose_only:
        print(json.dumps(preflight(args.dataset, diagnose=True), indent=2), flush=True)
        return 0
    if args.prepare_boxes:
        target = args.prepared_output or args.dataset.with_name(args.dataset.name + "_boxes")
        print(json.dumps(prepare_boxes(args.dataset, target), indent=2), flush=True)
        return 0
    report = preflight(args.dataset)
    print("Dataset preflight:", json.dumps(report, indent=2), flush=True)
    if args.audit_only:
        return 0
    return train(args, report)


if __name__ == "__main__":
    sys.exit(main())
