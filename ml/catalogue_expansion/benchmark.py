#!/usr/bin/env python3
"""Audited TWKM catalogue expansion pilot. NOT a phone-photo sign reader.

Uses the pinned public TWKM standardized graph drawings (CC BY 4.0 only).
Counts independently labeled signs, then compares source-hash-disjoint
reference matching and a HOG/SVM candidate across more than 10 sign IDs.

The split is by image hash, NOT proof that two graphs depict distinct
underlying carvings. No English translation is inferred or released.
"""
from __future__ import annotations

import argparse
import collections
import hashlib
import io
import json
import random
from pathlib import Path

import cv2
import numpy as np
from datasets import Image as HFImage, load_dataset
from PIL import Image as PILImage
from sklearn.metrics import accuracy_score, f1_score
from sklearn.svm import SVC
from skimage.feature import hog

REPO = "tadad/twkm-maya-glyphs"
REV = "73fe9da1d975a5f1e588c7d42c952dfda53cefd0"
OLD_V4_SIGNS = (32, 184, 291, 4, 44, 507, 548, 122, 187, 1030)
SEED = 20261115
SIDE = 64
METHODS = ("pixels", "hog", "hybrid", "svm_hog")


def canonical(raw: bytes) -> np.ndarray:
    """64px white-ink-on-black glyph, retaining original aspect ratio."""
    with PILImage.open(io.BytesIO(raw)) as im:
        gray = np.array(im.convert("L"))
    if gray.ndim != 2 or min(gray.shape) < 20:
        raise ValueError("undersized or unreadable image")
    border = np.concatenate((gray[0], gray[-1], gray[:, 0], gray[:, -1]))
    if np.median(border) < 110:
        gray = 255 - gray
    threshold, _ = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    mask = gray < threshold
    if np.count_nonzero(mask) < 30:
        mask = gray <= threshold
    ink_fraction = float(np.mean(mask))
    if not .003 <= ink_fraction <= .85:
        raise ValueError("blank or mostly filled image")
    yy, xx = np.where(mask)
    x0, x1 = int(xx.min()), int(xx.max()) + 1
    y0, y1 = int(yy.min()), int(yy.max()) + 1
    pad = max(2, round(max(x1 - x0, y1 - y0) * .035))
    x0, x1 = max(0, x0 - pad), min(gray.shape[1], x1 + pad)
    y0, y1 = max(0, y0 - pad), min(gray.shape[0], y1 + pad)
    crop = mask[y0:y1, x0:x1].astype(np.uint8) * 255
    scale = (SIDE - 8) / max(crop.shape)
    nw, nh = max(1, round(crop.shape[1] * scale)), max(1, round(crop.shape[0] * scale))
    small = cv2.resize(crop, (nw, nh), interpolation=cv2.INTER_AREA if scale < 1 else cv2.INTER_LINEAR)
    out = np.zeros((SIDE, SIDE), np.uint8)
    ox, oy = (SIDE - nw) // 2, (SIDE - nh) // 2
    out[oy:oy + nh, ox:ox + nw] = small
    return out


def load_graphs():
    dataset = load_dataset(REPO, "graphs", split="train", revision=REV).cast_column(
        "image", HFImage(decode=False))
    source_hashes = collections.defaultdict(list)
    rejected = collections.Counter()
    for row in dataset:
        if not row.get("image") or str(row.get("drawing_license", "")).strip() != "CC BY 4.0":
            rejected["missing_or_non_ccby_drawing"] += 1
            continue
        try:
            sign = int(row["sign_no"])
            item = row["image"]
            raw = item.get("bytes")
            if raw is None and item.get("path"):
                raw = Path(item["path"]).read_bytes()
            if not raw:
                raise ValueError("no image bytes")
            picture = canonical(raw)
        except (TypeError, KeyError, ValueError, OSError) as exc:
            rejected["unusable_image"] += 1
            continue
        sha = hashlib.sha256(raw).hexdigest()
        source_hashes[sha].append({
            "sign": sign, "graph": str(row.get("graph_no", "")),
            "sha": sha, "image": picture,
            "translation": str(row.get("translation") or "").strip(),
        })
    bysign = collections.defaultdict(list)
    for sha, records in sorted(source_hashes.items()):
        if len({r["sign"] for r in records}) != 1:
            rejected["cross_sign_sha_collision"] += 1
            continue
        bysign[records[0]["sign"]].append(records[0])
        if len(records) > 1:
            rejected["same_sign_duplicate_sha"] += len(records) - 1
    return bysign, dict(rejected)


def split(bysign, min_originals, max_signs):
    eligible = [s for s, rows in bysign.items() if len(rows) >= min_originals]
    # Keep all 10 original v4 labels when eligible, then expand by independent
    # catalogue image count. No sign names/glosses are guessed here.
    prioritized = [s for s in OLD_V4_SIGNS if s in eligible]
    others = sorted((s for s in eligible if s not in prioritized),
                    key=lambda s: (-len(bysign[s]), s))
    chosen = (prioritized + others)[:max_signs]
    if len(chosen) <= 10:
        raise ValueError("Not enough distinct TWKM graph originals to pilot >10 signs; see audit.")
    rng = random.Random(SEED)
    train, valid, test = [], [], []
    for sign in chosen:
        rows = sorted(bysign[sign], key=lambda r: r["sha"])
        rng.shuffle(rows)
        valid.append(rows[0])
        test.append(rows[1])
        train.extend(rows[2:])
    unknown_ids = [s for s in bysign if s not in chosen]
    unknown_ids.sort()
    rng.shuffle(unknown_ids)
    cal_ids, test_ids = unknown_ids[:40], unknown_ids[40:80]
    if len(cal_ids) < 30 or len(test_ids) < 30:
        raise ValueError("Too few sign-ID-disjoint unknown examples")
    unknown_cal = [sorted(bysign[s], key=lambda r: r["sha"])[0] for s in cal_ids]
    unknown_test = [sorted(bysign[s], key=lambda r: r["sha"])[0] for s in test_ids]
    all_rows = train + valid + test + unknown_cal + unknown_test
    if len({r["sha"] for r in all_rows}) != len(all_rows):
        raise AssertionError("Image hash leakage across splits")
    return chosen, train, valid, test, unknown_cal, unknown_test


def features(rows):
    pixels, shapes = [], []
    for row in rows:
        im = row["image"].astype(np.float32) / 255.0
        pixels.append(im.flatten())
        shapes.append(hog(im, orientations=9, pixels_per_cell=(8, 8),
                          cells_per_block=(2, 2), block_norm="L2-Hys",
                          feature_vector=True))
    p = np.asarray(pixels, dtype=np.float32)
    h = np.asarray(shapes, dtype=np.float32)
    def norm(x):
        return x / np.maximum(np.linalg.norm(x, axis=1, keepdims=True), 1e-6)
    return norm(p), norm(h)


def nearest(train_y, train_p, train_h, query_p, query_h, method, svm=None):
    if method == "svm_hog":
        if svm is None:
            raise ValueError("Missing fitted SVM")
        assigned = svm.predict(query_h)
        sims = query_h @ train_h.T
    elif method == "pixels":
        sims = query_p @ train_p.T
        assigned = None
    elif method == "hog":
        sims = query_h @ train_h.T
        assigned = None
    elif method == "hybrid":
        sims = .5 * (query_p @ train_p.T) + .5 * (query_h @ train_h.T)
        assigned = None
    else:
        raise ValueError("Unknown matcher")
    labels = sorted(set(train_y.tolist()))
    class_scores = np.asarray([sims[:, train_y == s].max(axis=1) for s in labels]).T
    top = np.argsort(-class_scores, axis=1)
    primary = np.asarray(labels)[top[:, 0]] if assigned is None else assigned
    own = np.asarray([class_scores[i, labels.index(int(s))] for i, s in enumerate(primary)])
    rival = np.asarray([max(class_scores[i, j] for j, label in enumerate(labels)
                            if label != int(s)) for i, s in enumerate(primary)])
    return [{"sign": int(sign), "similarity": float(score),
             "margin": float(score - other)} for sign, score, other in zip(primary, own, rival)]


def choose_rule(pos, truth, neg):
    # Reference similarity is NOT calibrated probability of correctness.
    eligible = []
    for sim in np.arange(.50, .991, .03):
        for margin in np.arange(-.05, .251, .02):
            accept = lambda p: p["similarity"] >= sim and p["margin"] >= margin
            good = sum(accept(p) and p["sign"] == target for p, target in zip(pos, truth))
            false = sum(accept(p) for p in neg)
            if good >= max(2, round(.20 * len(pos))) and false <= .10 * len(neg):
                eligible.append((good, -false, sim, margin))
    if not eligible:
        return None
    best = max(eligible)
    return {"similarity_min": round(float(best[2]), 5),
            "margin_min": round(float(best[3]), 5),
            "validation_correct_accepted": int(best[0]),
            "calibration_unknown_wrong_accepted": int(-best[1])}


def evaluate(predictions, truth, rule):
    labels = [r["sign"] for r in predictions]
    accepted = [bool(rule and r["similarity"] >= rule["similarity_min"] and
                     r["margin"] >= rule["margin_min"]) for r in predictions]
    if truth is None:
        return {"n": len(labels), "wrongly_accepted_unknown": sum(accepted),
                "unknown_rejected": len(labels) - sum(accepted)}
    return {"n": len(labels),
            "top1_correct": sum(p == t for p, t in zip(labels, truth)),
            "top1_accuracy": round(accuracy_score(truth, labels), 5),
            "macro_f1": round(f1_score(truth, labels, labels=sorted(set(truth)),
                                        average="macro", zero_division=0), 5),
            "correct_and_accepted": sum(p == t and a for p, t, a in zip(labels, truth, accepted)),
            "wrongly_accepted_known": sum(p != t and a for p, t, a in zip(labels, truth, accepted))}


def benchmark(args):
    bysign, rejected = load_graphs()
    sizes = {str(n): sum(len(rows) >= n for rows in bysign.values()) for n in (2, 3, 4, 5, 6, 8, 10)}
    audit = {"source": REPO, "revision": REV, "total_usable_signs": len(bysign),
             "counts_by_min_distinct_drawing_hashes": sizes, "rejected": rejected,
             "most_represented_signs": [{"sign_no": s, "distinct_drawings": len(bysign[s])}
                                        for s in sorted(bysign, key=lambda s: -len(bysign[s]))[:25]],
             "caveat": "Distinct hashes and graph numbers do not prove independent stone carvings."}
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "catalogue_audit.json").write_text(json.dumps(audit, indent=2), encoding="utf8")
    chosen, tr, va, te, uncal, untest = split(bysign, args.min_originals, args.max_signs)
    tp, th = features(tr)
    vp, vh = features(va)
    ep, eh = features(te)
    cp, ch = features(uncal)
    up, uh = features(untest)
    ty = np.asarray([r["sign"] for r in tr], dtype=np.int64)
    vy = [r["sign"] for r in va]
    ey = [r["sign"] for r in te]
    svm = SVC(C=10, kernel="rbf", gamma="scale", decision_function_shape="ovr")
    svm.fit(th, ty)
    trials = {}
    for method in METHODS:
        val = nearest(ty, tp, th, vp, vh, method, svm)
        neg = nearest(ty, tp, th, cp, ch, method, svm)
        trials[method] = {"validation": evaluate(val, vy, None),
                          "unknown_calibration": evaluate(neg, None, None)}
        trials[method]["unknown_calibration"]["threshold_not_selected"] = True
    # Choose method by known validation only; thresholds by validation +
    # sign-ID-disjoint unknown calibration. The final test is read once.
    selected = max(METHODS, key=lambda m: (
        trials[m]["validation"]["top1_correct"], trials[m]["validation"]["macro_f1"],
        -METHODS.index(m)))
    vpred = nearest(ty, tp, th, vp, vh, selected, svm)
    cpred = nearest(ty, tp, th, cp, ch, selected, svm)
    rule = choose_rule(vpred, vy, cpred)
    tpred = nearest(ty, tp, th, ep, eh, selected, svm)
    upred = nearest(ty, tp, th, up, uh, selected, svm)
    known = evaluate(tpred, ey, rule)
    unknown = evaluate(upred, None, rule)
    report = {"scope": "EXPERIMENTAL CLASSIFICATION OF CATALOGUE DRAWINGS ONLY",
              "source": REPO, "revision": REV, "selected_sign_ids": chosen,
              "n_signs": len(chosen),
              "train_originals": len(tr), "validation_originals": len(va),
              "test_originals": len(te),
              "unknown_calibration_different_signs": len(uncal),
              "unknown_test_different_signs": len(untest),
              "selection_validation_only": selected,
              "trials": trials, "rejection_rule": rule,
              "test_known": known, "test_unknown": unknown,
              "trained_model": "HOG SVM candidate plus 3 independent nearest-reference baselines",
              "ready_for_android": False,
              "reason": "Requires external source-disjoint labelled camera-photo evaluation, calibration, and app parity tests",
              "caveats": ["No claim of independent real-world accuracy",
                          "Graph variants may derive from the same artifact even with different image hashes",
                          "A glyph block may contain multiple signs",
                          "English meanings cannot be inferred from visual nearest matches",
                          "Do not fit on validation, test or unknown-test images"]}
    (args.output / "benchmark.json").write_text(json.dumps(report, indent=2), encoding="utf8")
    labels = [{"twkm_sign_no": int(sign),
               "training_graph_count": int(sum(r["sign"] == sign for r in tr)),
               "graph_examples": [r["graph"] for r in tr if r["sign"] == sign][:6],
               "glosses_as_published_not_verified_readings": sorted({
                   r["translation"] for r in bysign[sign] if r["translation"]})[:5]}
              for sign in chosen]
    (args.output / "pilot_labels.json").write_text(json.dumps(labels, indent=2), encoding="utf8")
    np.savez_compressed(args.output / "research_train_gallery_only.npz",
                        images=np.stack([r["image"] for r in tr]), signs=ty,
                        graph_ids=np.asarray([r["graph"] for r in tr]))
    print(json.dumps({"eligible_signs": sizes, "pilot_signs": len(chosen),
                      "train": len(tr), "selected_method": selected,
                      "rule": rule, "test_known": known, "test_unknown": unknown,
                      "android_changed": False}, indent=2), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=Path("ml/catalogue_expansion/output"))
    parser.add_argument("--min-originals", type=int, default=5)
    parser.add_argument("--max-signs", type=int, default=50)
    opts = parser.parse_args()
    if opts.min_originals < 3 or opts.max_signs < 11:
        parser.error("Need at least 3 source originals per sign and >10 candidate signs.")
    benchmark(opts)
