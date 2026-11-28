#!/usr/bin/env python3
"""Research-only comparison of isolated Maya syllabary reference descriptors.

Requires a locally extracted copy of the user-supplied syllabary study ZIP.
The glyph artwork is intentionally NOT committed or redistributed in GitHub.
Heldout images come from the same source charts: these are NOT real-photo
recognition or translation accuracy estimates.
"""
import argparse
import json
import sys
from collections import defaultdict
from pathlib import Path

import cv2
import numpy as np
from skimage.feature import hog


def unit(matrix):
    return matrix / np.maximum(np.linalg.norm(matrix, axis=1, keepdims=True), 1e-8)


def describe(images, method):
    result = []
    for original in images:
        image = original.astype(np.uint8)
        if method == "pixel":
            vector = cv2.resize(image, (48, 48), interpolation=cv2.INTER_AREA).ravel()
        elif method in ("hog", "hog_fine", "hog_coarse"):
            size = {"hog": 12, "hog_fine": 8, "hog_coarse": 16}[method]
            vector = hog(image.astype(np.float32) / 255, orientations=9,
                         pixels_per_cell=(size, size), cells_per_block=(2, 2),
                         feature_vector=True)
        elif method == "distance":
            binary = (image > 127).astype(np.uint8)
            vector = cv2.resize(cv2.distanceTransform(1 - binary, cv2.DIST_L2, 3),
                                (32, 32)).ravel()
        elif method == "blur":
            vector = cv2.resize(cv2.GaussianBlur(image, (7, 7), 1.7),
                                (32, 32)).ravel()
        elif method == "gradient":
            gx = cv2.Sobel(image, cv2.CV_32F, 1, 0, ksize=3)
            gy = cv2.Sobel(image, cv2.CV_32F, 0, 1, ksize=3)
            vector = np.concatenate((cv2.resize(gx, (32, 32)).ravel(),
                                     cv2.resize(gy, (32, 32)).ravel()))
        else:
            raise ValueError("Unknown method " + method)
        result.append(vector)
    return unit(np.asarray(result, dtype=np.float32))


def evaluate(index):
    with np.load(index, allow_pickle=False) as gallery:
        images = gallery["images"]
        labels = gallery["labels"].astype(str)
    grouped = defaultdict(list)
    original_count = 86
    if len(images) != 152 or len(set(labels)) != 86:
        raise ValueError("Expected original user-supplied 152-crop, 86-label research gallery")
    for i in range(original_count, len(labels)):
        grouped[labels[i]].append(i)
    holdouts = np.asarray([ids[0] for label, ids in sorted(grouped.items())])
    truth = labels[holdouts]
    methods = ("pixel", "hog", "hog_fine", "hog_coarse", "distance", "blur", "gradient")
    outcomes = []
    for method in methods:
        features = describe(images, method)
        sims = features[holdouts] @ features.T
        sims[np.arange(len(holdouts)), holdouts] = -np.inf
        correct = {1: 0, 3: 0}
        for target, order in zip(truth, np.argsort(-sims, axis=1)):
            ranked = []
            for index_id in order:
                label = labels[index_id]
                if label not in ranked:
                    ranked.append(label)
                if len(ranked) >= 3:
                    break
            correct[1] += int(target == ranked[0])
            correct[3] += int(target in ranked)
        outcomes.append({"method": method, "top1": correct[1], "top3": correct[3],
                         "test_chart_variants": len(holdouts)})
    return {
        "data": "User-supplied syllabary charts, 152 provisional crops, 86 labels",
        "evaluation": "45 first alternate chart variants withheld one at a time",
        "methods": outcomes,
        "not_blind_independent_test": True,
        "accuracy_on_real_photos": "NOT MEASURED",
        "image_redistribution_permission": "NOT CONFIRMED",
        "android_ready": False
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--index", type=Path, required=True)
    ap.add_argument("--output", type=Path, required=True)
    args = ap.parse_args()
    report = evaluate(args.index)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
