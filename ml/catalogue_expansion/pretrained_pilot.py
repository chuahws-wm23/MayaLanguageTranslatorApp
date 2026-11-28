#!/usr/bin/env python3
"""Frozen pretrained vision-feature EXPANSION study for >10 TWKM signs.

This is a research-only extension of benchmark.py. All selection happens on
training-source-grouped 3-fold CV; validation selects a rejection cutoff.
The historical test partition was already exposed in the first catalogue
pilot and is therefore a regression cohort, NOT an untouched blind test.
Never deploy this classifier or label a multi-sign BLOCK as a single sign.
"""
from __future__ import annotations
import argparse
import json
import os
import random
import sys
from pathlib import Path

import cv2
import numpy as np
import torch
from torch import nn
from torchvision import models
from sklearn.model_selection import StratifiedKFold
from sklearn.metrics import f1_score
from sklearn.svm import SVC

sys.path.insert(0, str(Path(__file__).resolve().parent))
import benchmark as baseline

SEED = baseline.SEED
torch.manual_seed(SEED)
np.random.seed(SEED)
random.seed(SEED)
torch.set_num_threads(min(4, os.cpu_count() or 2))
MEAN = np.array([.485, .456, .406], dtype=np.float32).reshape(3, 1, 1)
STD = np.array([.229, .224, .225], dtype=np.float32).reshape(3, 1, 1)
VIEWS = 3
C_VALUES = (.1, 1., 10.)
KERNELS = ("linear", "rbf")


def input_image(img: np.ndarray, seed: int | None = None) -> torch.Tensor:
    # Canonical catalogue glyph is bright strokes against dark background.
    # ImageNet backbones receive dark ink on white, centered and scaled.
    light = 255 - img
    resized = cv2.resize(light, (224, 224), interpolation=cv2.INTER_LINEAR)
    if seed is not None:
        rng = np.random.default_rng(seed)
        affine = cv2.getRotationMatrix2D((112, 112), float(rng.uniform(-7, 7)),
                                         float(rng.uniform(.95, 1.05)))
        affine[0, 2] += float(rng.uniform(-6, 6))
        affine[1, 2] += float(rng.uniform(-6, 6))
        resized = cv2.warpAffine(resized, affine, (224, 224),
                                 borderMode=cv2.BORDER_CONSTANT, borderValue=255)
        if rng.random() < .2:
            resized = cv2.GaussianBlur(resized, (3, 3), .5)
    pix = resized.astype(np.float32) / 255.
    return torch.from_numpy((np.repeat(pix[None], 3, axis=0) - MEAN) / STD)


def load_backbone(name: str) -> nn.Module:
    if name == "resnet18":
        net = models.resnet18(weights=models.ResNet18_Weights.DEFAULT)
        net.fc = nn.Identity()
    elif name == "mobilenet_v3_small":
        net = models.mobilenet_v3_small(weights=models.MobileNet_V3_Small_Weights.DEFAULT)
        net.classifier = nn.Identity()
    else:
        raise ValueError(name)
    for p in net.parameters():
        p.requires_grad_(False)
    return net.eval()


def extract(net: nn.Module, rows: list[dict], views: int = 1) -> np.ndarray:
    vectors = []
    batch = []
    net.eval()
    with torch.no_grad():
        for row in rows:
            for view in range(views):
                seed = None if view == 0 else SEED + int(row["sha"][:8], 16) + view * 97
                batch.append(input_image(row["image"], seed))
                if len(batch) >= 12:
                    feats = net(torch.stack(batch)).flatten(1).cpu().numpy()
                    vectors.append(feats)
                    batch = []
        if batch:
            vectors.append(net(torch.stack(batch)).flatten(1).cpu().numpy())
    output = np.concatenate(vectors, axis=0).astype(np.float32)
    return output / np.maximum(np.linalg.norm(output, axis=1, keepdims=True), 1e-9)


def source_grouped_cv(x: np.ndarray, y: np.ndarray, views: int, kernel: str, c: float) -> dict:
    assert x.shape[0] == len(y) * views
    assert min(np.bincount(np.unique(y, return_inverse=True)[1])) >= 3
    splits = StratifiedKFold(n_splits=3, shuffle=True, random_state=SEED)
    predictions = np.full(y.shape, -1, dtype=np.int64)
    for fit, hold in splits.split(np.zeros(len(y)), y):
        train_ix = np.concatenate([np.arange(i * views, (i + 1) * views) for i in fit])
        model = SVC(C=c, kernel=kernel, gamma="scale").fit(
            x[train_ix], np.repeat(y[fit], views))
        predictions[hold] = model.predict(x[hold * views])
    return {
        "top1_correct": int(np.count_nonzero(predictions == y)),
        "n": len(y),
        "macro_f1": float(f1_score(y, predictions, labels=sorted(set(y)),
                                    average="macro", zero_division=0))
    }


def evaluate_backbone(name: str, groups: tuple) -> dict:
    chosen, train, val, historical, unknown_cal, unknown_test = groups
    net = load_backbone(name)
    train_feats = extract(net, train, VIEWS)
    clean_train = train_feats[::VIEWS]
    val_feats = extract(net, val)
    historic_feats = extract(net, historical)
    cal_feats = extract(net, unknown_cal)
    unknown_feats = extract(net, unknown_test)
    ytrain = np.asarray([r["sign"] for r in train], dtype=np.int64)
    yval = [r["sign"] for r in val]
    yhistoric = [r["sign"] for r in historical]
    candidates = []
    for kernel in KERNELS:
        for C in C_VALUES:
            cv = source_grouped_cv(train_feats, ytrain, VIEWS, kernel, C)
            candidates.append({"kernel": kernel, "C": C, "cv": cv})
            print("TRAIN ORIGINAL CV", name, kernel, C, cv, flush=True)
    selected = max(candidates, key=lambda r: (r["cv"]["macro_f1"],
                                             r["cv"]["top1_correct"], -r["C"]))
    model = SVC(C=selected["C"], kernel=selected["kernel"], gamma="scale")
    model.fit(train_feats, np.repeat(ytrain, VIEWS))
    def guesses(feats):
        # Similarity to training ORIGINALS only, for measured abstention.
        # Underlying classifier uses all augmentations; no holdout in index.
        return baseline.nearest(ytrain, clean_train, clean_train,
                                feats, feats, "svm_hog", model)
    pos = guesses(val_feats)
    cal = guesses(cal_feats)
    rule = baseline.choose_rule(pos, yval, cal)
    reg = guesses(historic_feats)
    negatives = guesses(unknown_feats)
    return {
        "backbone": name,
        "train_cv_only_selection": selected,
        "all_train_cv": candidates,
        "validation": baseline.evaluate(pos, yval, rule),
        "calibration_unknown": baseline.evaluate(cal, None, rule),
        "rejection_rule": rule,
        "previously_exposed_historical_test": baseline.evaluate(reg, yhistoric, rule),
        "different_sign_unknown_test": baseline.evaluate(negatives, None, rule),
        "validation_predictions": [dict(graph=r["graph"], true_sign=r["sign"], **p)
                                    for r, p in zip(val, pos)],
        "historical_test_predictions": [dict(graph=r["graph"], true_sign=r["sign"], **p)
                                        for r, p in zip(historical, reg)],
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=Path("ml/catalogue_expansion/pretrained_output"))
    parser.add_argument("--max-signs", type=int, default=25)
    args = parser.parse_args()
    if args.max_signs < 11:
        parser.error("Need at least 11 classes")
    args.output.mkdir(exist_ok=True, parents=True)
    bysign, rejected = baseline.load_graphs()
    groups = baseline.split(bysign, 5, args.max_signs)
    chosen, train, val, historical, negcal, negtest = groups
    results = []
    for backbone in ("resnet18", "mobilenet_v3_small"):
        result = evaluate_backbone(backbone, groups)
        results.append(result)
    # Select between backbones using TRAIN-ONLY grouped CV. Report both.
    best = max(results, key=lambda x: (
        x["train_cv_only_selection"]["cv"]["macro_f1"],
        x["train_cv_only_selection"]["cv"]["top1_correct"]))
    report = {
        "status": "EXPERIMENTAL ONLY",
        "data": "Pinned TWKM catalogued graph drawings (not actual stone photos)",
        "source_hash_disjoint_only": True,
        "old_v4_signs_retained_when_eligible": True,
        "chosen_signs": chosen,
        "sign_count": len(chosen),
        "train_distinct_graphs": len(train),
        "validation_graphs": len(val),
        "previously_exposed_historical_graphs": len(historical),
        "unknown_calibration_signs": len(negcal),
        "unknown_test_signs": len(negtest),
        "candidate_backbones": results,
        "selected_backbone_by_training_cv": best["backbone"],
        "selected_historical_test_regression": best["previously_exposed_historical_test"],
        "selected_unknown_regression": best["different_sign_unknown_test"],
        "different_sign_unknown_rejection": "Only catalogue unknowns, not arbitrary camera signs",
        "original_25_sign_template_top1_reference": "9/25 at exposed historical cohort",
        "test_not_blind": True,
        "android_deployment": "NONE",
        "release_ready": False,
        "warning": "Reference image variants are not independent artifacts. No real-phone accuracy established."
    }
    (args.output / "results.json").write_text(json.dumps(report, indent=2), encoding="utf8")
    print("EXPANDED PRETRAINED STUDY", json.dumps({
        "signs": len(chosen),
        "selected_backbone": best["backbone"],
        "selected_train_cv": best["train_cv_only_selection"]["cv"],
        "historical_exposed_regression": best["previously_exposed_historical_test"],
        "unknown_test_regression": best["different_sign_unknown_test"],
        "release_ready": False
    }, indent=2), flush=True)


if __name__ == "__main__":
    main()
