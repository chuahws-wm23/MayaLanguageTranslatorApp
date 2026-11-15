#!/usr/bin/env python3
"""Export the source-audited 25-TWKM-sign frozen-MobileNetV3 + SVM research candidate.

Produces paired ONNX assets, a training-only similarity gallery and a metadata
manifest. The SAME pinned source split and selected C=10/RBF SVM as
pretrained_pilot.py are used; all historical tests remain labelled exposed.

This tool is NOT permission to claim phone-photo accuracy or verified readings.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import struct
from pathlib import Path

import numpy as np
import torch
from torch import nn
from torch.nn import functional as F
from sklearn.svm import SVC

import benchmark as baseline
import pretrained_pilot as pilot
import verified_photos as local_photos

FILES = {
    "feature": "maya_twkm25_feature.onnx",
    "svm": "maya_twkm25_svm.onnx",
    "gallery": "maya_twkm25_gallery.bin",
    "metadata": "maya_twkm25_metadata.json",
}


class FeatureExtractor(nn.Module):
    def __init__(self, backbone):
        super().__init__()
        self.backbone = backbone

    def forward(self, image):
        output = self.backbone(image).flatten(start_dim=1)
        return F.normalize(output, p=2, dim=1, eps=1e-9)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def save_gallery(path: Path, y, features):
    """Little-endian MW25 + version,n,dimension + sign labels + row-major float32."""
    assert len(y) == len(features) and features.shape[1] > 0
    with path.open("wb") as file:
        file.write(struct.pack("<4sIII", b"MW25", 1, len(y), features.shape[1]))
        file.write(np.asarray(y, dtype="<i4").tobytes(order="C"))
        file.write(np.asarray(features, dtype="<f4").tobytes(order="C"))


def evaluate_parity(exported, classifier, network, source_rows, expected, feature_dim):
    """Test exported feature AND SVM outputs on every original sample."""
    import onnxruntime as ort
    feature_sess = ort.InferenceSession(str(exported / FILES["feature"]),
                                        providers=["CPUExecutionProvider"])
    svm_sess = ort.InferenceSession(str(exported / FILES["svm"]),
                                    providers=["CPUExecutionProvider"])
    max_error = 0.
    mismatches = []
    with torch.no_grad():
        for row, correct_pred in zip(source_rows, expected):
            x = pilot.input_image(row["image"]).unsqueeze(0).numpy().astype(np.float32)
            reference_features = F.normalize(network(torch.from_numpy(x)), p=2, dim=1).numpy()
            outputs = feature_sess.run(None, {"image": x})[0]
            if outputs.shape != (1, feature_dim):
                raise ValueError("ONNX feature extractor returned wrong dimensions")
            error = float(np.max(np.abs(reference_features - outputs)))
            max_error = max(max_error, error)
            onnx_output = svm_sess.run(None, {"features": outputs})[0].reshape(-1)[0]
            inferred = int(onnx_output)
            if inferred != int(correct_pred):
                mismatches.append({"graph": row["graph"], "expected": int(correct_pred),
                                   "got": inferred})
    if max_error > .0005 or mismatches:
        raise RuntimeError("Export parity failed: max_err=%f mismatches=%s" %
                           (max_error, mismatches[:10]))
    return {"checked_samples": len(source_rows),
            "max_feature_absolute_error": round(max_error, 7),
            "svm_label_mismatches": len(mismatches)}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--output", type=Path, default=Path("ml/catalogue_expansion/android_export"))
    ap.add_argument("--photo-manifest", type=Path,
                    help="CSV of source-verified single-sign photos, kept locally")
    ap.add_argument("--photo-root", type=Path,
                    help="Local image directory; never uploaded by this script")
    args = ap.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    if bool(args.photo_manifest) != bool(args.photo_root):
        ap.error("Supply BOTH --photo-manifest and --photo-root, or neither")

    bysign, rejected = baseline.load_graphs()
    chosen, train, val, historical, negcal, negtest = baseline.split(bysign, 5, 25)
    assert len(chosen) == 25
    catalogue_original_count = len(train)
    new_photo_splits = None
    new_photo_audit = None
    if args.photo_manifest:
        all_catalogue_shas = {row["sha"] for group in (train, val, historical,
                               negcal, negtest) for row in group}
        new_photo_splits, new_photo_audit = local_photos.load_verified_photos(
            args.photo_manifest, args.photo_root, chosen, all_catalogue_shas)
        additions, ignored = local_photos.split_for_training(new_photo_splits)
        if not additions:
            raise ValueError("Photo manifest contains no verified train-split images. " +
                             "No retraining was performed.")
        train = train + additions
        new_photo_audit["unknown_train_not_used_as_known"] = ignored
    net = pilot.load_backbone("mobilenet_v3_small")
    xtrain = pilot.extract(net, train, 3)
    ytrain = np.asarray([r["sign"] for r in train], dtype=np.int64)
    svm = SVC(C=10., kernel="rbf", gamma="scale", decision_function_shape="ovr")
    svm.fit(xtrain, np.repeat(ytrain, 3))

    def candidate(rows):
        embeddings = pilot.extract(net, rows)
        predictions = baseline.nearest(ytrain, xtrain[::3], xtrain[::3],
                                       embeddings, embeddings, "svm_hog", svm)
        return embeddings, predictions

    valx, valpred = candidate(val)
    calx, calpred = candidate(negcal)
    rule = baseline.choose_rule(valpred, [r["sign"] for r in val], calpred)
    if rule is None:
        raise RuntimeError("No fitted known/unknown screening rule.")
    histx, histpred = candidate(historical)
    unx, unpred = candidate(negtest)
    evaluated = {
        "validation": baseline.evaluate(valpred, [r["sign"] for r in val], rule),
        "previously_exposed_test": baseline.evaluate(histpred,
                                                     [r["sign"] for r in historical], rule),
        "unknown_calibration": baseline.evaluate(calpred, None, rule),
        "unknown_disjoint_test": baseline.evaluate(unpred, None, rule),
    }

    import onnx
    from skl2onnx import convert_sklearn
    from skl2onnx.common.data_types import FloatTensorType

    extractor = FeatureExtractor(net).eval()
    inp = pilot.input_image(train[0]["image"]).unsqueeze(0)
    torch.onnx.export(extractor, inp, str(args.output / FILES["feature"]),
                      input_names=["image"], output_names=["features"],
                      opset_version=17, dynamo=False,
                      dynamic_axes={"image": {0: "batch"}, "features": {0: "batch"}})
    onnx.checker.check_model(onnx.load(str(args.output / FILES["feature"])))

    export_svm = convert_sklearn(svm,
                initial_types=[("features", FloatTensorType([None, xtrain.shape[1]]))],
                options={id(svm): {"zipmap": False}},
                target_opset=17)
    onnx.save_model(export_svm, str(args.output / FILES["svm"]))
    onnx.checker.check_model(onnx.load(str(args.output / FILES["svm"])))
    save_gallery(args.output / FILES["gallery"], ytrain, xtrain[::3])

    all_checked = val + historical + negcal + negtest
    expected = [r["sign"] for r in valpred + histpred + calpred + unpred]
    parity = evaluate_parity(args.output, svm, net, all_checked, expected, xtrain.shape[1])
    photo_results = None
    if new_photo_splits is not None:
        photo_results = local_photos.score_photos(new_photo_splits, svm, net,
                                                 train, rule)

    metadata = {
        "format_version": 1,
        "source": baseline.REPO, "revision": baseline.REV,
        "model": "ImageNet pretrained MobileNetV3 Small frozen features, RBF SVM C=10",
        "supported_twkm_sign_numbers": [int(x) for x in chosen],
        "feature_dim": int(xtrain.shape[1]),
        "image_side": 224,
        "gallery_size": int(len(ytrain)),
        "original_train_source_hashes": catalogue_original_count,
        "additional_training_photo_count": len(train) - catalogue_original_count,
        "new_photo_audit": new_photo_audit,
        "new_photo_metrics": photo_results,
        "rejection": rule,
        "files": {key: {"name": FILES[key],
                         "sha256": sha256(args.output / FILES[key]),
                         "bytes": (args.output / FILES[key]).stat().st_size}
                  for key in ("feature", "svm", "gallery")},
        "evaluation": evaluated,
        "onnx_parity": parity,
        "catalogue_test_previously_exposed": True,
        "real_phone_photo_accuracy": "NOT MEASURED",
        "sign_detection_and_inscription_translation": "NOT SUPPORTED",
        "release_approved": False,
        "mobile_status": "OPTIONAL EXPERIMENT ONLY; explicit user install, no automatic replacement",
        "rights": "TWKM Christian Prager drawings CC BY 4.0; check rights and source groups"
    }
    (args.output / FILES["metadata"]).write_text(json.dumps(metadata, indent=2), encoding="utf8")
    print(json.dumps({
        "model_files": FILES,
        "sign_classes": len(chosen),
        "rule": rule,
        "metrics": evaluated,
        "onnx_parity": parity,
        "added_training_photos": len(train) - catalogue_original_count,
        "external_photo_metrics": photo_results,
        "release_approved": False
    }, indent=2), flush=True)


if __name__ == "__main__":
    main()
