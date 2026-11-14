"""Export the *trained* MobileNetV3+RBF-SVM into two verified ONNX assets.
The model is closed-set: it does not learn an unknown-glyph category.
"""
from pathlib import Path
import hashlib
import json

import joblib
import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from torchvision.models import mobilenet_v3_small
import onnx
import onnxruntime as ort
from skl2onnx import convert_sklearn
from skl2onnx.common.data_types import FloatTensorType

HERE = Path("ml/glyph_v2/outputs")
ROOT = HERE / "transfer"
OUT = HERE / "android"
OUT.mkdir(parents=True, exist_ok=True)

class VisualFeatures(nn.Module):
    def __init__(self, net):
        super().__init__()
        self.net = net
    def forward(self, x):
        return F.normalize(self.net(x), p=2, dim=1)

backbone = mobilenet_v3_small(weights=None)
backbone.classifier = nn.Identity()
backbone.load_state_dict(torch.load(ROOT / "mobilenet_v3_small_backbone.pt", map_location="cpu", weights_only=True))
backbone = VisualFeatures(backbone.eval()).eval()

example = torch.randn(1, 3, 224, 224, generator=torch.Generator().manual_seed(701))
onnx_visual = OUT / "maya_mobilenet_v2.onnx"
torch.onnx.export(
    backbone, example, str(onnx_visual),
    input_names=["image"], output_names=["features"], opset_version=17,
    export_params=True, do_constant_folding=True, dynamo=False,
)
onnx.checker.check_model(onnx.load(str(onnx_visual)))
session = ort.InferenceSession(str(onnx_visual), providers=["CPUExecutionProvider"])
with torch.inference_mode():
    torch_output = backbone(example).numpy()
ort_output = session.run(["features"], {"image": example.numpy()})[0]
max_delta = float(np.max(np.abs(torch_output - ort_output)))
assert max_delta < 1e-4, f"Backbone export mismatch: {max_delta}"
assert ort_output.shape[1] == 576, ort_output.shape
print("MobileNet ONNX matches PyTorch:", max_delta, flush=True)

saved = joblib.load(ROOT / "mobilenet_v3_small_head.joblib")
classifier = saved["classifier"]
onnx_svm = OUT / "maya_svm_v2.onnx"
svm_model = convert_sklearn(
    classifier, "MayaGlyphSvmV2",
    initial_types=[("features", FloatTensorType([None, 576]))],
    options={id(classifier): {"zipmap": False}},
    target_opset={"": 17, "ai.onnx.ml": 3},
)
onnx.save_model(svm_model, str(onnx_svm))
onnx.checker.check_model(onnx.load(str(onnx_svm)))
svm_session = ort.InferenceSession(str(onnx_svm), providers=["CPUExecutionProvider"])
print("SVM inputs:", [(i.name, i.shape) for i in svm_session.get_inputs()], flush=True)
print("SVM outputs:", [(i.name, i.shape) for i in svm_session.get_outputs()], flush=True)
features = np.load(ROOT / "mobilenet_v3_small_features.npz")["features"].astype("float32")
expected = classifier.predict(features)
out = svm_session.run(None, {"features": features})
predicted = np.asarray(out[0]).reshape(-1)
agreement = float(np.mean(predicted == expected))
assert agreement == 1.0, f"SVM labels do not match sklearn: {agreement:.2%}"
assert set(np.unique(predicted).tolist()).issubset({0,1,2,3,4})
print("SVM ONNX matches sklearn on all 38 catalogue features:", agreement, flush=True)

metadata = {
    "model_name": "MayaGlyphResearchV2", "model_type": "ONNX MobileNetV3-Small + ONNX RBF SVM",
    "input": "one 224x224 RGB ImageNet-normalized image obtained by converting a 64x64 normalized glyph crop to 224x224",
    "v2_model_is_closed_set": True, "unknown_rejection_validated": False,
    "original_model_file": "mobilenet_v3_small_backbone.pt",
    "svm_model_file": "mobilenet_v3_small_head.joblib",
    "signs": [
        {"index": 0, "twkm_sign": 32, "reading": "K'UH", "gloss": "God / deity"},
        {"index": 1, "twkm_sign": 184, "reading": "K'INICH", "gloss": "Resplendent / sunny"},
        {"index": 2, "twkm_sign": 291, "reading": "SIIP", "gloss": "Deity name"},
        {"index": 3, "twkm_sign": 4, "reading": "NAAH", "gloss": "First / house"},
        {"index": 4, "twkm_sign": 44, "reading": "TOK", "gloss": "Cloud / mist"},
    ],
    "evaluation": "Exploratory 5-fold cross-validation 35/38 on TWKM reference drawings; not phone-photo accuracy",
    "onnx_visual_parity_max_abs_delta": max_delta,
    "onnx_svm_parity": agreement,
    "assets": {
        p.name: {"size_bytes": p.stat().st_size, "sha256": hashlib.sha256(p.read_bytes()).hexdigest()}
        for p in [onnx_visual, onnx_svm]
    },
}
(OUT / "maya_glyph_v2_metadata.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
print("Exported mobile assets", metadata["assets"], flush=True)
