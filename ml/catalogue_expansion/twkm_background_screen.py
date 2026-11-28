#!/usr/bin/env python3
"""Use the wider TWKM graph catalogue to research unknown-sign screening.

Core 25-sign MobileNetV3 + SVM is unchanged. A separate binary SVM is trained
on 25 known training-sign groups and held-IN other TWKM sign identities.
Forty calibration and forty regression-test unknown sign identities are
excluded from the binary fit entirely. Test images and the exposed historical
known cohort never select model hyperparameters or an acceptance threshold.

Research ONLY. No automatic Android model replacement or external-photo
accuracy claim. Allographs and physical source artifacts can still be related
even with different drawing hashes.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
from sklearn.svm import SVC

import benchmark as base
import pretrained_pilot as pilot

OUT = Path("ml/catalogue_expansion/twkm_background_output")
SVM_C = (0.1, 1.0, 10.0)


def background_sign_drawings(bysign, chosen, unknown_cal, unknown_test, max_signs=0):
    """One catalogue drawing per OTHER sign ID; never any held-out unknown ID."""
    excluded = set(chosen) | {r["sign"] for r in unknown_cal + unknown_test}
    background = []
    for sign in sorted(bysign):
        if sign in excluded:
            continue
        originals = sorted(bysign[sign], key=lambda r: r["sha"])
        if originals:
            background.append(originals[0])
        if max_signs and len(background) >= max_signs:
            break
    assert not ({r["sign"] for r in background} & excluded)
    return background


def accepted_by_reference(preds: list[dict], rule: dict) -> np.ndarray:
    return np.asarray([
        p["similarity"] >= rule["similarity_min"] and
        p["margin"] >= rule["margin_min"]
        for p in preds], dtype=bool)


def score(preds: list[dict], truths, reference_rule, binary_scores=None, cutoff=None):
    accepted = accepted_by_reference(preds, reference_rule)
    if binary_scores is not None and cutoff is not None:
        binary_scores = np.asarray(binary_scores, dtype=float)
        assert len(binary_scores) == len(preds)
        accepted &= binary_scores >= cutoff
    if truths is None:
        return {"n": len(preds), "unknown_wrongly_accepted": int(accepted.sum()),
                "unknown_rejected": int(len(preds) - accepted.sum())}
    match = np.asarray([p["sign"] == y for p, y in zip(preds, truths)], dtype=bool)
    return {"n": len(preds), "top1_correct": int(match.sum()),
            "correct_and_accepted": int((match & accepted).sum()),
            "wrong_known_accepted": int((~match & accepted).sum()),
            "known_abstained": int((~accepted).sum())}


def choose_cutoff(preds_valid, yvalid, preds_cal, ref_rule,
                  val_decision, cal_decision):
    """Validation/calibration only. Reject gains that discard too many knowns."""
    existing_known = score(preds_valid, yvalid, ref_rule)
    existing_unknown = score(preds_cal, None, ref_rule)
    require_known = int(np.ceil(existing_known["correct_and_accepted"] * .85))
    # Include 0 (default SVM margin) and score boundaries to explore trade-offs.
    values = np.r_[val_decision, cal_decision]
    thresholds = sorted(set(
        [float("-inf"), 0.0] +
        [float(x) for x in np.quantile(values, np.linspace(0, 1, 81))]
    ))
    viable = []
    for cutoff in thresholds:
        known = score(preds_valid, yvalid, ref_rule, val_decision, cutoff)
        unknown = score(preds_cal, None, ref_rule, cal_decision, cutoff)
        if known["correct_and_accepted"] < require_known:
            continue
        if known["wrong_known_accepted"] > existing_known["wrong_known_accepted"]:
            continue
        if unknown["unknown_wrongly_accepted"] >= existing_unknown["unknown_wrongly_accepted"]:
            continue
        viable.append((unknown["unknown_wrongly_accepted"],
                       -known["correct_and_accepted"],
                       known["wrong_known_accepted"],
                       cutoff, known, unknown))
    if not viable:
        return None
    chosen = min(viable)
    return {"cutoff": chosen[3],
            "known_validation": chosen[4],
            "unknown_calibration": chosen[5],
            "acceptance_guardrail": {
                "min_known_correct_and_accepted": require_known,
                "must_reduce_wrong_unknown_acceptance": True
            }}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=OUT)
    parser.add_argument("--max-background-signs", type=int, default=0,
                        help="0 means use all extra TWKM sign identities")
    args = parser.parse_args()
    if args.max_background_signs < 0:
        parser.error("Negative background limit is invalid")
    args.output.mkdir(parents=True, exist_ok=True)

    bysign, audit = base.load_graphs()
    chosen, train, valid, historical, cal, unknown_test = base.split(bysign, 5, 25)
    excluded = {r["sign"] for r in cal + unknown_test}
    background = background_sign_drawings(
        bysign, chosen, cal, unknown_test, args.max_background_signs)
    if len(background) < 50:
        raise RuntimeError("Insufficient disjoint additional TWKM signs to train a rejection screen")
    assert not ({r["sign"] for r in background} & excluded)
    assert not ({r["sha"] for r in background} &
                {r["sha"] for r in train + valid + historical + cal + unknown_test})

    net = pilot.load_backbone("mobilenet_v3_small")
    training_features = pilot.extract(net, train, pilot.VIEWS)
    gallery = training_features[::pilot.VIEWS]
    labels = np.asarray([r["sign"] for r in train], dtype=np.int64)
    v_features = pilot.extract(net, valid)
    h_features = pilot.extract(net, historical)
    cal_features = pilot.extract(net, cal)
    test_features = pilot.extract(net, unknown_test)
    negative_features = pilot.extract(net, background)

    primary = SVC(C=10., kernel="rbf", gamma="scale")
    primary.fit(training_features, np.repeat(labels, pilot.VIEWS))

    def guesses(features):
        return base.nearest(labels, gallery, gallery, features, features, "svm_hog", primary)
    v_preds, h_preds = guesses(v_features), guesses(h_features)
    cal_preds, test_preds = guesses(cal_features), guesses(test_features)
    valid_true = [r["sign"] for r in valid]
    historical_true = [r["sign"] for r in historical]
    rule = base.choose_rule(v_preds, valid_true, cal_preds)
    if rule is None:
        raise RuntimeError("Original 25-sign reference screen failed calibration")

    baseline = {
        "validation": score(v_preds, valid_true, rule),
        "unknown_calibration": score(cal_preds, None, rule),
        "previously_exposed_known": score(h_preds, historical_true, rule),
        "unknown_disjoint_test": score(test_preds, None, rule),
    }

    binary_x = np.concatenate([training_features, negative_features], axis=0)
    binary_y = np.r_[np.ones(len(training_features), dtype=np.int32),
                     np.zeros(len(negative_features), dtype=np.int32)]
    candidates = []
    for c in SVM_C:
        binary = SVC(C=c, kernel="rbf", gamma="scale", class_weight="balanced")
        binary.fit(binary_x, binary_y)
        val_decision = binary.decision_function(v_features)
        cal_decision = binary.decision_function(cal_features)
        proposal = choose_cutoff(v_preds, valid_true, cal_preds, rule,
                                 val_decision, cal_decision)
        candidates.append({"svm_C": c, "candidate_calibration": proposal})
        print("TWKM background C", c, "rule", proposal, flush=True)

    # Select only with validation and disjoint unknown calibration, not test.
    feasible = [x for x in candidates if x["candidate_calibration"]]
    chosen_candidate = (min(feasible, key=lambda x: (
        x["candidate_calibration"]["unknown_calibration"]["unknown_wrongly_accepted"],
        -x["candidate_calibration"]["known_validation"]["correct_and_accepted"],
        x["svm_C"])) if feasible else None)

    if chosen_candidate is not None:
        binary = SVC(C=chosen_candidate["svm_C"], kernel="rbf",
                     gamma="scale", class_weight="balanced")
        binary.fit(binary_x, binary_y)
        cutoff = chosen_candidate["candidate_calibration"]["cutoff"]
        candidate_historical = score(
            h_preds, historical_true, rule, binary.decision_function(h_features), cutoff)
        candidate_unknown = score(
            test_preds, None, rule, binary.decision_function(test_features), cutoff)
    else:
        candidate_historical = None
        candidate_unknown = None

    report = {
        "source": base.REPO, "pinned_revision": base.REV,
        "model_type": "25-class MobileNetV3+SVM with optional second binary known-vs-other SVM",
        "catalogue_distinct_sign_id_count": len(bysign),
        "known_sign_classes": len(chosen),
        "known_training_originals": len(train),
        "other_sign_id_count_trained": len(background),
        "unknown_calibration_identity_count_excluded": len(excluded & {r["sign"] for r in cal}),
        "unknown_test_identity_count_excluded": len(excluded & {r["sign"] for r in unknown_test}),
        "unknown_sign_identities_never_trained": True,
        "known_test_originals_never_trained": True,
        "baseline": baseline,
        "candidates_val_cal_only": candidates,
        "selected_val_cal_only": chosen_candidate,
        "previously_exposed_historical_known": candidate_historical,
        "different_sign_unknown_test": candidate_unknown,
        "false_acceptance_change_test": (
            None if candidate_unknown is None else
            candidate_unknown["unknown_wrongly_accepted"] -
            baseline["unknown_disjoint_test"]["unknown_wrongly_accepted"]),
        "known_correct_acceptance_change_test": (
            None if candidate_historical is None else
            candidate_historical["correct_and_accepted"] -
            baseline["previously_exposed_known"]["correct_and_accepted"]),
        "android_assets_changed": False,
        "eligible_for_release": False,
        "main_limit": "TWKM graph drawings not independent photos of artifacts; a block may contain several signs.",
        "experiment_note": "Do not tune on the exposed historical or disjoint unknown regression cohorts."
    }
    target = args.output / "wider_twkm_background_screen.json"
    target.write_text(json.dumps(report, indent=2), encoding="utf8")
    print("SUMMARY", json.dumps({k: report[k] for k in (
        "catalogue_distinct_sign_id_count", "known_sign_classes",
        "other_sign_id_count_trained", "baseline",
        "selected_val_cal_only", "previously_exposed_historical_known",
        "different_sign_unknown_test", "false_acceptance_change_test",
        "known_correct_acceptance_change_test", "android_assets_changed"
    )}, indent=2), flush=True)
    print("Saved", target)


if __name__ == "__main__":
    main()
