#!/usr/bin/env python3
"""Research-only TWKM template gallery with honest 'no reliable match' screening.

Reference bank = original 45 TWKM training drawings, NEVER held-out originals.
Choice of matching method & reject cutoffs uses only train leave-one-original-out
and disjoint unknown-sign calibration. The historical ten-sign val/test and
80 further unseen-sign originals are evaluated after selection. This is
NOT camera-photo accuracy and never updates the Android model.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import sys

import cv2
import numpy as np
from skimage.feature import hog

sys.path.insert(0, str(Path("ml/research_v4_single").resolve()))
import shape_svm_experiment as source
import opencv_single_glyph as image_utils

OUT = Path("ml/research_v4_single/template_match_results")
METHODS = ("pixel", "hog", "shifted", "hybrid")
SEED = 20261115
SIGNS = source.SIGNS


def normalized(x: np.ndarray) -> np.ndarray:
    v = x.astype(np.float32).reshape(-1)
    length = max(float(np.linalg.norm(v)), 1e-7)
    return v / length


def features(img: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    pixels = normalized(img)
    gradients = hog(img.astype(np.float32) / 255.0,
                    orientations=9, pixels_per_cell=(8, 8),
                    cells_per_block=(2, 2), block_norm="L2-Hys",
                    feature_vector=True)
    return pixels, normalized(gradients)


class ReferenceBank:
    def __init__(self, signs: np.ndarray, canvases: np.ndarray):
        assert canvases.ndim == 3 and canvases.shape[1:] == (64, 64)
        assert len(signs) == len(canvases)
        self.signs = np.asarray(signs, dtype=np.int32)
        self.canvases = np.asarray(canvases, dtype=np.uint8)
        assert self.signs.size > 0
        f = [features(im) for im in self.canvases]
        self.pixels = np.stack([x[0] for x in f])
        self.hogs = np.stack([x[1] for x in f])
        self.padded = [cv2.copyMakeBorder(c, 3, 3, 3, 3,
                                         cv2.BORDER_CONSTANT, value=0)
                       for c in self.canvases]

    def guess(self, image: np.ndarray, method: str) -> dict:
        assert method in METHODS and image.shape == (64, 64)
        pixels, gradients = features(image)
        pixel_sim = np.clip(self.pixels @ pixels, -1, 1)
        if method == "pixel":
            sim = pixel_sim
        elif method == "hog":
            sim = np.clip(self.hogs @ gradients, -1, 1)
        elif method == "hybrid":
            sim = .5 * pixel_sim + .5 * np.clip(self.hogs @ gradients, -1, 1)
        else:
            sim = np.asarray([
                min(1.0, float(cv2.matchTemplate(ref, image, cv2.TM_CCORR_NORMED).max()))
                for ref in self.padded
            ], dtype=np.float32)
        # The second-best score is taken from a DIFFERENT sign identity.
        # Comparing the nearest two templates from the same sign would
        # incorrectly make a well-covered class seem ambiguous.
        sign_scores = sorted(
            [(int(sign), float(np.max(sim[self.signs == sign])))
             for sign in np.unique(self.signs)],
            key=lambda x: (-x[1], x[0]),
        )
        best_sign, best_score = sign_scores[0]
        second_score = sign_scores[1][1] if len(sign_scores) > 1 else -1.
        return {"sign": best_sign, "score": round(best_score, 7),
                "margin": round(best_score - second_score, 7),
                "top3": [{"twkm_sign": sign, "similarity": round(score, 5)}
                         for sign, score in sign_scores[:3]]}


def decide(pred: dict, rule: dict | None) -> bool:
    return bool(rule is not None and
                pred["score"] >= rule["minimum_similarity"] and
                pred["margin"] >= rule["minimum_margin"])


def tune(train_matches: list[dict], train_true: list[int],
         negative_matches: list[dict]) -> dict | None:
    """Require some correct known acceptances and controlled unknown acceptances.

    Selection uses no historical validation/test or unknown-test drawings.
    A zero-accept policy is not counted as successful.
    """
    choices = []
    for score in np.arange(.15, .996, .02):
        for margin in (0., .01, .02, .04, .06, .09, .12, .16, .22):
            rule = {"minimum_similarity": float(round(score, 5)),
                    "minimum_margin": float(margin)}
            known_good = sum(decide(p, rule) and p["sign"] == t
                             for p, t in zip(train_matches, train_true))
            wrong_unknown = sum(decide(p, rule) for p in negative_matches)
            known_ratio = known_good / len(train_matches)
            wrong_unknown_ratio = wrong_unknown / len(negative_matches)
            if known_ratio >= .20 and wrong_unknown_ratio <= .10:
                choices.append((known_ratio, -wrong_unknown_ratio, -float(score),
                                -float(margin), rule, known_good, wrong_unknown))
    if not choices:
        return None
    chosen = max(choices)
    return {**chosen[4], "calibration_loo_correct_accepted": int(chosen[5]),
            "calibration_loo_total": len(train_matches),
            "calibration_unknown_wrong_accepted": int(chosen[6]),
            "calibration_unknown_total": len(negative_matches)}


def stats(predictions: list[dict], truths: list[int] | None,
          rule: dict | None) -> dict:
    if truths is None:
        accepted = sum(decide(p, rule) for p in predictions)
        return {"total": len(predictions), "unknown_wrong_accepted": accepted,
                "unknown_rejected": len(predictions) - accepted}
    correct = sum(p["sign"] == t for p, t in zip(predictions, truths))
    accepted = sum(decide(p, rule) for p in predictions)
    correct_accepted = sum(p["sign"] == t and decide(p, rule)
                           for p, t in zip(predictions, truths))
    return {"total": len(predictions), "top1_correct_before_rejection": correct,
            "accepted": accepted, "correct_and_accepted": correct_accepted,
            "rejected_valid": len(predictions) - accepted,
            "accepted_incorrectly": accepted - correct_accepted}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image", type=Path,
                        help="Predict one isolated glyph using saved research gallery")
    parser.add_argument("--gallery", type=Path, default=OUT / "gallery_train_only.npz")
    parser.add_argument("--report", type=Path, default=OUT / "results.json")
    args = parser.parse_args()
    if args.image:
        if not args.report.exists() or not args.gallery.exists():
            raise SystemExit("Run the benchmark first to build the calibrated reference bank.")
        report = json.loads(args.report.read_text(encoding="utf8"))
        selected = report.get("selected")
        if selected is None or selected.get("rule") is None:
            raise SystemExit("No tested rejection rule: cannot claim a reliable match.")
        data = np.load(args.gallery, allow_pickle=False)
        bank = ReferenceBank(data["signs"], data["canvases"])
        try:
            gray = image_utils.decode_image(args.image)
            canonical = image_utils.canonical_glyph(gray)
        except image_utils.UnreadableGlyph as ex:
            print(json.dumps({"status": "no_reliable_match", "reason": str(ex)}))
            return
        p = bank.guess(canonical, selected["method"])
        ok = decide(p, selected["rule"])
        print(json.dumps({
            "status": "possible_match_unverified" if ok else "no_reliable_match",
            "twkm_sign": p["sign"] if ok else None,
            "similarity_not_accuracy": p["score"],
            "top3_unverified": p["top3"] if ok else [],
            "warning": "Catalogue-only experimental threshold; unsupported signs may still be accepted. "
                       "Do NOT use as verified translation.",
        }, indent=2))
        return

    bysign, _ = source.v4.data()
    train, val, exposed_test, unknown_cal, unknown_test = source.v4.partitions(bysign)
    groups = [train, val, exposed_test, unknown_cal, unknown_test]
    raw = source.source_grays([r for group in groups for r in group])
    canonical = {sha: source.canonical(gray, 64)
                 for sha, gray in raw.items()}
    train_true = [int(r["sign"]) for r in train]
    gallery = ReferenceBank(np.asarray(train_true),
                            np.stack([canonical[r["sha"]] for r in train]))
    calibration = {}
    for method in METHODS:
        # Leave-one-ORIGINAL-out: never match a drawing to itself.
        oof = []
        for ix, row in enumerate(train):
            mask = np.arange(len(train)) != ix
            without_self = ReferenceBank(gallery.signs[mask], gallery.canvases[mask])
            oof.append(without_self.guess(canonical[row["sha"]], method))
        negcal = [gallery.guess(canonical[r["sha"]], method) for r in unknown_cal]
        rule = tune(oof, train_true, negcal)
        calibration[method] = {
            "train_leave_original_out": stats(oof, train_true, rule),
            "unknown_calibration": stats(negcal, None, rule),
            "rule": rule,
        }
        print("CALIBRATION", method, calibration[method], flush=True)
    eligible = [(name, info) for name, info in calibration.items()
                if info["rule"] is not None]
    # Only training-original LOO and independent unknown calibration select the method.
    selected_method = max(eligible, key=lambda entry: (
        entry[1]["train_leave_original_out"]["correct_and_accepted"],
        -entry[1]["unknown_calibration"]["unknown_wrong_accepted"],
    ))[0] if eligible else None
    selected = None if selected_method is None else {
        "method": selected_method, "rule": calibration[selected_method]["rule"]}

    final = {}
    if selected:
        for name, rows in (("known_validation", val),
                           ("previously_exposed_known_test", exposed_test),
                           ("disjoint_unknown_test", unknown_test)):
            pred = [gallery.guess(canonical[r["sha"]], selected_method) for r in rows]
            final[name] = {
                **stats(pred, None if name == "disjoint_unknown_test"
                        else [int(r["sign"]) for r in rows], selected["rule"]),
                "details": [
                    {"source_sha": row["sha"][:12], "graph": row["graph"],
                     "true_twkm_sign": int(row["sign"]), **p,
                     "accepted": decide(p, selected["rule"])}
                    for row, p in zip(rows, pred)
                ],
            }
            print("FINAL", name, {k: v for k, v in final[name].items()
                                if k != "details"}, flush=True)
    else:
        print("NO METHOD satisfied train-only acceptable-coverage vs unknown-rejection gate.",
              flush=True)
    OUT.mkdir(parents=True, exist_ok=True)
    np.savez_compressed(OUT / "gallery_train_only.npz", signs=gallery.signs,
                        canvases=gallery.canvases)
    report = {
        "experiment": "template image gallery, no CNN, no Roboflow label guessing",
        "catalogue": "TWKM, pinned source in ml/glyph_v4/train_v4.py",
        "supported_twkm_signs": SIGNS, "training_originals": len(train),
        "never_train_on_val_or_test": True,
        "unknown_cal_sign_identities_disjoint": True,
        "unknown_test_sign_identities_disjoint": True,
        "methods": list(METHODS),
        "selection": "Train-only leave-one-original-out and 80 disjoint unknown-calibration drawings",
        "calibration": calibration,
        "selected": selected, "results": final,
        "independent_photo_accuracy": "NOT MEASURED",
        "test_caveat": "10 known drawings previously exposed during FYP development; "
                       "not a blind external accuracy estimate",
        "one_reference_gallery_can_be_out_of_class": True,
        "release_recommended": bool(
            selected and
            final["known_validation"]["correct_and_accepted"] >= 7 and
            final["previously_exposed_known_test"]["correct_and_accepted"] >= 7 and
            final["disjoint_unknown_test"]["unknown_wrong_accepted"] <= 4
        ) if selected else False,
    }
    (OUT / "results.json").write_text(json.dumps(report, indent=2), encoding="utf8")
    summary = [
        "Template-gallery research, TEN verified TWKM signs (45 training originals)",
        "Calibration: 45 train leave-one-original-out; 80 unsupported calibration.",
        "Selected: " + str(selected),
    ]
    if selected:
        for name in ("known_validation", "previously_exposed_known_test", "disjoint_unknown_test"):
            values = final[name]
            summary.append(name + ": " + str({k: v for k, v in values.items() if k != "details"}))
    summary += ["Release gate met? " + str(report["release_recommended"]),
                "No phone-photo accuracy measured; no Android model modified."]
    (OUT / "summary.txt").write_text("\n".join(summary) + "\n", encoding="utf8")
    print("\n".join(summary), flush=True)


if __name__ == "__main__":
    main()
