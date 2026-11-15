#!/usr/bin/env python3
"""Local *code-only* visual reference search for Roboflow v11 COCO glyph crops.

This is NOT trained neural recognition. It retrieves a similar *Roboflow code*,
NOT a TWKM sign number or a Maya reading. A strict threshold can abstain, but
does not prove that unsupported signs cannot be mistaken for familiar ones.

Build from TRAIN split for evaluation; --include-all is an optional final
catalogue gallery that includes validation/test and MUST NOT be evaluated as
though those images were unseen.
"""
from __future__ import annotations
import argparse
from collections import defaultdict
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import zipfile

import cv2
import numpy as np
from PIL import Image, ImageOps

SIDE = 64
DEFAULT_SIM = 0.97  # deliberately strict starting value, NOT calibrated
DEFAULT_MARGIN = 0.06


class GlyphError(ValueError):
    pass


def canonical(gray: np.ndarray) -> np.ndarray:
    """Normalise one dark-on-light glyph into centered 64x64 bright strokes."""
    if gray.ndim != 2 or min(gray.shape) < 20:
        raise GlyphError("Crop must contain one sufficiently large glyph.")
    gray = gray.astype(np.uint8)
    border = np.r_[gray[0, :], gray[-1, :], gray[:, 0], gray[:, -1]]
    if float(np.median(border)) < 110:
        gray = 255 - gray
    threshold, _ = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    ink = gray < max(1, threshold)
    if ink.mean() < .003:
        ink = gray <= threshold
    area = float(ink.mean())
    if area < .003 or area > .85:
        raise GlyphError("No isolated glyph strokes; crop more tightly.")
    yy, xx = np.nonzero(ink)
    l, r, t, b = int(xx.min()), int(xx.max()) + 1, int(yy.min()), int(yy.max()) + 1
    pad = max(2, int(max(r - l, b - t) * .035))
    l, r = max(0, l-pad), min(gray.shape[1], r+pad)
    t, b = max(0, t-pad), min(gray.shape[0], b+pad)
    crop = (ink[t:b, l:r].astype(np.uint8) * 255)
    h, w = crop.shape
    ratio = 56 / max(h, w)
    nw, nh = max(1, round(w*ratio)), max(1, round(h*ratio))
    small = cv2.resize(crop, (nw, nh),
                       interpolation=cv2.INTER_AREA if ratio < 1 else cv2.INTER_LINEAR)
    out = np.zeros((SIDE, SIDE), dtype=np.uint8)
    out[(SIDE-nh)//2:(SIDE-nh)//2+nh,
        (SIDE-nw)//2:(SIDE-nw)//2+nw] = small
    return out


def crop_annotation(rgb: np.ndarray, ann: dict) -> np.ndarray:
    """Use COCO bbox; polygon outside is white to avoid neighbouring signs."""
    h, w = rgb.shape[:2]
    x, y, bw, bh = map(float, ann["bbox"])
    left, top = max(0, int(np.floor(x))), max(0, int(np.floor(y)))
    right, bottom = min(w, int(np.ceil(x+bw))), min(h, int(np.ceil(y+bh)))
    if right-left < 20 or bottom-top < 20:
        raise GlyphError("Invalid/tiny annotation box.")
    gray = cv2.cvtColor(rgb, cv2.COLOR_RGB2GRAY)
    mask = np.zeros(gray.shape, dtype=np.uint8)
    polygons = ann.get("segmentation", [])
    if isinstance(polygons, list):
        pts = []
        for polygon in polygons:
            if isinstance(polygon, list) and len(polygon) >= 6:
                p = np.asarray(polygon, dtype=np.float32).reshape(-1, 2)
                pts.append(np.round(p).astype(np.int32))
        if pts:
            cv2.fillPoly(mask, pts, 255)
    if mask.any():
        gray = np.where(mask > 0, gray, 255).astype(np.uint8)
    return canonical(gray[top:bottom, left:right])


class Dataset:
    """Read Roboflow COCO export from directory OR user's ZIP without extracting."""
    def __init__(self, path: Path):
        self.path = path
        self.zip = zipfile.ZipFile(path, "r") if path.is_file() and zipfile.is_zipfile(path) else None
        if self.zip:
            self.names = set(self.zip.namelist())
            paths = [PurePosixPath(p) for p in self.names
                     if p.endswith("/_annotations.coco.json")]
        elif path.is_dir():
            paths = list(path.rglob("_annotations.coco.json"))
        else:
            raise GlyphError("Provide a COCO dataset ZIP or extracted folder.")
        self.ann_paths = {}
        for p in paths:
            split = p.parent.name.lower()
            if split in ("train", "valid", "test"):
                self.ann_paths[split] = p
        if "train" not in self.ann_paths:
            raise GlyphError("Expected train/_annotations.coco.json (COCO Segmentation export).")

    def close(self):
        if self.zip:
            self.zip.close()

    def read(self, path) -> bytes:
        return self.zip.read(str(path)) if self.zip else Path(path).read_bytes()

    def iter_crops(self, split: str):
        annotation_path = self.ann_paths.get(split)
        if not annotation_path:
            return
        data = json.loads(self.read(annotation_path))
        names = {int(cat["id"]): str(cat["name"]).strip()
                 for cat in data["categories"]}
        groups = defaultdict(list)
        for ann in data["annotations"]:
            groups[int(ann["image_id"])].append(ann)
        for entry in data["images"]:
            # Resolve only the basename *within the annotation's split*.
            # Prevent COCO file_name from escaping split folder.
            base = PurePosixPath(str(entry["file_name"]).replace("\\", "/")).name
            candidate = annotation_path.parent / base
            if self.zip and str(candidate) not in self.names:
                candidate = annotation_path.parent / "images" / base
            if not self.zip and not candidate.exists():
                candidate = annotation_path.parent / "images" / base
            try:
                with Image.open(io.BytesIO(self.read(candidate))) as img:
                    rgb = np.asarray(ImageOps.exif_transpose(img).convert("RGB"))
            except (OSError, KeyError, FileNotFoundError):
                continue
            for ann in groups.get(int(entry["id"]), []):
                label = names.get(int(ann["category_id"]), "")
                if not label or label.lower() == "maya-glyphs":
                    continue
                try:
                    glyph = crop_annotation(rgb, ann)
                except (GlyphError, TypeError, ValueError, cv2.error):
                    continue
                yield label, glyph, str(base)


def build(dataset: Dataset, include_all: bool) -> tuple[np.ndarray, np.ndarray, dict]:
    splits = ["train", "valid", "test"] if include_all else ["train"]
    labels = []
    glyphs = []
    seen: dict[str, str] = {}
    collisions = set()
    skipped_duplicates = 0
    for split in splits:
        for label, image, _source in dataset.iter_crops(split):
            digest = hashlib.sha256(image.tobytes()).hexdigest()
            if digest in seen:
                if seen[digest] != label:
                    collisions.add(digest)
                else:
                    skipped_duplicates += 1
                continue
            seen[digest] = label
            labels.append(label)
            glyphs.append(image)
    if not glyphs:
        raise GlyphError("No usable annotated glyph crops.")
    # Exact duplicate images with contradictory codes cannot be trusted.
    keep = [i for i, g in enumerate(glyphs)
            if hashlib.sha256(g.tobytes()).hexdigest() not in collisions]
    if not keep:
        raise GlyphError("All reference images are ambiguous.")
    images = np.stack([glyphs[i] for i in keep])
    result_labels = np.asarray([labels[i] for i in keep], dtype="<U64")
    return result_labels, images, {
        "source_splits": splits,
        "index_count": len(images),
        "class_count": len(set(result_labels.tolist())),
        "exact_duplicates_skipped": skipped_duplicates,
        "cross_class_pixel_collisions_dropped": len(collisions),
        "all_splits_in_gallery": include_all,
        "warning": "Roboflow codes not TWKM translations; 0.97 similarity is not calibrated confidence.",
    }


class Matcher:
    def __init__(self, labels: np.ndarray, images: np.ndarray):
        if not len(labels) or len(labels) != len(images) or images.shape[1:] != (64, 64):
            raise GlyphError("Invalid gallery images/labels.")
        self.labels = labels.astype("<U64")
        self.images = images.astype(np.uint8)
        features = self.images.astype(np.float32).reshape(len(images), -1)
        lengths = np.maximum(np.linalg.norm(features, axis=1, keepdims=True), 1e-6)
        self.features = features / lengths

    def rank(self, image: np.ndarray, top: int = 3):
        v = image.astype(np.float32).reshape(-1)
        v /= max(float(np.linalg.norm(v)), 1e-6)
        scores = np.clip(self.features @ v, -1, 1)
        # Max over the reference instances of each distinct code.
        best = {}
        for idx in np.argsort(-scores):
            code = str(self.labels[int(idx)])
            if code not in best:
                best[code] = float(scores[int(idx)])
            if len(best) >= max(top, 2):
                break
        ordered = sorted(best.items(), key=lambda x: (-x[1], x[0]))
        return ordered

    def query(self, image: np.ndarray, minimum_similarity=DEFAULT_SIM,
              minimum_margin=DEFAULT_MARGIN):
        options = self.rank(image, top=3)
        best = options[0]
        margin = float(best[1] - options[1][1]) if len(options) > 1 else 1.
        acceptable = bool(best[1] >= minimum_similarity and margin >= minimum_margin)
        return {
            "status": "possible_roboflow_code_match_UNVERIFIED" if acceptable else "no_reliable_match",
            "matched_glyph_code": best[0] if acceptable else None,
            "top_candidates_for_review": [
                {"glyph_code": code, "visual_similarity_not_accuracy": round(score, 4)}
                for code, score in options
            ],
            "nearest_similarity_not_accuracy": round(best[1], 4),
            "different_class_margin": round(margin, 4),
            "thresholds_are_not_calibrated": True,
            "warning": "This is visual pattern search only. No TWKM number or English meaning is verified."
        }


def evaluate(dataset: Dataset, matcher: Matcher, min_sim: float, min_margin: float):
    report = {}
    supported = set(matcher.labels.tolist())
    for split in ("valid", "test"):
        correct, accepted, wrong_accepted, out_of_class, total = 0, 0, 0, 0, 0
        for true, image, _source in dataset.iter_crops(split):
            total += 1
            verdict = matcher.query(image, min_sim, min_margin)
            yes = verdict["matched_glyph_code"] is not None
            if yes:
                accepted += 1
                if true == verdict["matched_glyph_code"]:
                    correct += 1
                else:
                    wrong_accepted += 1
            if true not in supported:
                out_of_class += 1
        report[split] = {
            "total": total,
            "correct_and_accepted": correct,
            "accepted_incorrectly": wrong_accepted,
            "rejected": total - accepted,
            "actual_codes_absent_from_gallery": out_of_class,
            "note": "Images may share original source drawings even if filenames differ; "
                    "these are Roboflow-code outcomes, not TWKM translations."
        }
    return report


def main():
    parser = argparse.ArgumentParser(description="Conservative visual search on actual Roboflow v11 annotated glyphs")
    parser.add_argument("--dataset", type=Path, help="Maya_Glyphs_v11.zip or extracted COCO folder")
    parser.add_argument("--build", action="store_true", help="Index reference crops from dataset")
    parser.add_argument("--include-all", action="store_true",
                        help="Use train+valid+test only for final visual lookup; disables accuracy evaluation")
    parser.add_argument("--index", type=Path, default=Path("maya_roboflow_v11_gallery.npz"))
    parser.add_argument("--image", type=Path, help="A local image containing ONE glyph")
    parser.add_argument("--evaluate", action="store_true", help="Evaluate only with train-only gallery")
    parser.add_argument("--minimum-similarity", type=float, default=DEFAULT_SIM)
    parser.add_argument("--minimum-margin", type=float, default=DEFAULT_MARGIN)
    args = parser.parse_args()

    if not 0 < args.minimum_similarity <= 1 or not 0 <= args.minimum_margin <= 1:
        raise SystemExit("Similarity must be (0,1]; margin must be [0,1].")
    dataset = Dataset(args.dataset) if args.dataset else None
    try:
        if args.build:
            if dataset is None:
                raise GlyphError("--build needs --dataset")
            labels, images, info = build(dataset, args.include_all)
            args.index.parent.mkdir(parents=True, exist_ok=True)
            np.savez_compressed(args.index, labels=labels, images=images,
                                all_splits=np.array(int(args.include_all), dtype=np.uint8))
            print(json.dumps({"gallery_created": str(args.index), **info}, indent=2))
        if args.image or args.evaluate:
            if not args.index.is_file():
                raise GlyphError("Build the gallery using --build first.")
            with np.load(args.index, allow_pickle=False) as data:
                labels, images = data["labels"], data["images"]
                all_splits = bool(data["all_splits"].item())
            matcher = Matcher(labels, images)
            if args.image:
                with Image.open(args.image) as src:
                    gray = np.asarray(ImageOps.exif_transpose(src).convert("L"))
                try:
                    v = matcher.query(canonical(gray), args.minimum_similarity, args.minimum_margin)
                except GlyphError as exc:
                    v = {"status": "no_reliable_match", "reason": str(exc)}
                print(json.dumps(v, indent=2))
            if args.evaluate:
                if dataset is None:
                    raise GlyphError("--evaluate needs --dataset.")
                if all_splits:
                    raise GlyphError("Cannot score held-out accuracy after indexing validation/test pictures.")
                print(json.dumps({"evaluation": evaluate(dataset, matcher,
                                       args.minimum_similarity, args.minimum_margin),
                                  "note": "Strict .97/.06 cutoffs are provisional, NOT calibrated on "
                                          "unseen real photos."}, indent=2))
        if not(args.build or args.image or args.evaluate):
            parser.print_help()
    except GlyphError as exc:
        raise SystemExit(str(exc))
    finally:
        if dataset:
            dataset.close()


if __name__ == "__main__":
    main()
