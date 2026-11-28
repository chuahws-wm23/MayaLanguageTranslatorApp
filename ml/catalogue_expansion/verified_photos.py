#!/usr/bin/env python3
"""Load ONLY locally recorded, human-verified single-sign photos for TWKM research.

This module does not download images, infer sign labels, upload personal photos,
or count a transformed copy of a known source drawing as independent evidence.
"""
from __future__ import annotations

import csv
import hashlib
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "dataset" / "scripts"))
from validate_real_glyph_photos import audit

import benchmark

SPLITS = ("train", "validation", "test")


def load_verified_photos(manifest: Path, root: Path, supported_signs: list[int],
                         existing_sha: set[str]):
    """Return split-name -> source records and an audit report.

    Unsupported labelled photo signs may appear only as literal UNKNOWN; these
    can calibrate or evaluate abstention but cannot become claimed TWKM IDs.
    """
    report = audit(manifest, root, allowed_signs=set(supported_signs))
    if report["errors"]:
        raise ValueError("Local real-glyph manifest failed audit: " +
                         " | ".join(report["errors"][:8]))
    photo_splits = {name: [] for name in SPLITS}
    counts = Counter()
    dataset_sha = set()
    with manifest.open(newline="", encoding="utf-8-sig") as stream:
        rows = list(csv.DictReader(stream))
    for i, row in enumerate(rows, start=2):
        filename = row["image_path"].strip()
        split = row["split"].strip().lower()
        sign_string = row["twkm_sign"].strip()
        sign = -1 if sign_string == "UNKNOWN" else int(sign_string)
        blob = (root / filename).resolve().read_bytes()
        digest = hashlib.sha256(blob).hexdigest()
        if digest in existing_sha:
            raise ValueError("A new photo exactly duplicates a pinned TWKM source drawing: " +
                             filename)
        if digest in dataset_sha:
            raise ValueError("Duplicate photo bytes in verified manifest: " + filename)
        dataset_sha.add(digest)
        try:
            picture = benchmark.canonical(blob)
        except (OSError, ValueError) as exc:
            raise ValueError("Cannot isolate a SINGLE sign in "+filename+": "+str(exc)) from exc
        photo_splits[split].append({
            "graph": "local_verified_photo_row_" + str(i),
            "sha": digest,
            "image": picture,
            "sign": sign,
        })
        counts[split + ("_unknown" if sign < 0 else "_known")] += 1
    return photo_splits, {
        "num_photos": sum(map(len, photo_splits.values())),
        "sign_ids": list(supported_signs),
        "split_counts": dict(counts),
        "warnings_require_human_review": report["warnings"][:30],
        "no_exact_SHA_leakage": True,
        "source_groups": report["distinct_source_groups"],
        "note": "Labels and source groups are user-provided, not independently verified by software."
    }


def split_for_training(splits):
    training = [r for r in splits["train"] if r["sign"] >= 0]
    ignored_unknown_train = sum(r["sign"] < 0 for r in splits["train"])
    return training, ignored_unknown_train


def score_photos(splits, classifier, neural, source_rows, rule):
    """No tuning on local validation/test. Keep test results separate."""
    import numpy as np
    import pretrained_pilot as pilot
    train_feats = pilot.extract(neural, source_rows, 1)
    labels = np.asarray([r["sign"] for r in source_rows], dtype=np.int64)
    result = {}
    for name in ("validation", "test"):
        records = splits[name]
        if not records:
            result[name] = {"total": 0, "note": "No provided independently labelled photos"}
            continue
        pred_feats = pilot.extract(neural, records, 1)
        guesses = benchmark.nearest(labels, train_feats, train_feats,
                                    pred_feats, pred_feats, "svm_hog", classifier)
        known = [(p, r["sign"]) for p, r in zip(guesses, records) if r["sign"] >= 0]
        unknown = [p for p, r in zip(guesses, records) if r["sign"] < 0]
        result[name] = {
            "known": benchmark.evaluate([p for p, _ in known],
                                         [t for _, t in known], rule) if known else {"n": 0},
            "unknown": benchmark.evaluate(unknown, None, rule),
            "note": "Only user-verified source groups; independent photographic accuracy requires additional audit."
        }
    return result
