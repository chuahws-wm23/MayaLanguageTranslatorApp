"""Convert trained MobileNetV3 v4 to an offline Android ONNX graph.

The original PyTorch model is evaluated in 160x160 input mode and returns
10-class logits and 576-dimensional L2-normalized features.
This script verifies numerical parity and class agreement before committing.
"""
from pathlib import Path
import hashlib,json
import numpy as np,torch
from torch import nn
from torch.nn import functional as F
from torchvision import models
import onnx,onnxruntime as ort

ROOT=Path("ml/glyph_v4/outputs")
DEST=ROOT/"android"
DEST.mkdir(exist_ok=True,parents=True)
info=json.loads((ROOT/"metadata.json").read_text())
assert info["chosen_model"]=="fine_tuned", "Use SVM converter instead for frozen model"
SIGNS=info["signs"]
assert len(SIGNS)==10 and info["input_side"]==160
model=models.mobilenet_v3_small(weights=None)
model.classifier=nn.Linear(576,len(SIGNS))
model.load_state_dict(torch.load(ROOT/"mobilenet_v4_finetuned.pt",map_location="cpu",weights_only=True))
model.eval()

class DualOutput(nn.Module):
    def __init__(self,net):
        super().__init__()
        self.net=net
    def forward(self,x):
        feature=self.net.features(x)
        feature=self.net.avgpool(feature).flatten(1)
        logits=self.net.classifier(feature)
        normalized=F.normalize(feature,dim=1)
        return logits,normalized

wrapped=DualOutput(model).eval()
torch.manual_seed(20261114)
image=torch.randn(1,3,160,160)
with torch.inference_mode():
    expected_logits,expected_features=wrapped(image)
path=DEST/"maya_glyph_v4.onnx"
torch.onnx.export(wrapped,image,str(path),
    input_names=["image"],output_names=["logits","features"],
    dynamic_axes={"image":{0:"batch"},"logits":{0:"batch"},"features":{0:"batch"}},
    opset_version=17,export_params=True,do_constant_folding=True,dynamo=False)
onnx.checker.check_model(onnx.load(str(path)))
session=ort.InferenceSession(str(path),providers=["CPUExecutionProvider"])
got_logits,got_features=session.run(["logits","features"],{"image":image.numpy()})
logit_error=float(np.max(np.abs(got_logits-expected_logits.numpy())))
feature_error=float(np.max(np.abs(got_features-expected_features.numpy())))
assert logit_error<2e-4 and feature_error<2e-4,(logit_error,feature_error)
assert np.argmax(got_logits)==int(expected_logits.argmax())
assert got_logits.shape==(1,10) and got_features.shape==(1,576)
print("Numerical parity",logit_error,feature_error,flush=True)
meta={"model_type":"fine_tuned_mobilenet_v3_small_v4",
      "source":"ml/glyph_v4/outputs/mobilenet_v4_finetuned.pt",
      "input":"float32 NCHW [1,3,160,160] ImageNet-normalized RGB after 64px glyph standardization",
      "outputs":["logits float32[1,10]","features float32[1,576]"],
      "signs":SIGNS,"label_strings":info["labels"],
      "cross_format_max_abs_logit_error":logit_error,
      "cross_format_max_abs_features_error":feature_error,
      "small_catalogue_heldout_top1":json.loads((ROOT/"evaluation.json").read_text())["heldout_test_accuracy"],
      "unknown_rejection_tested":True,
      "known_screened_test_accepted_correct":1,
      "research_only_not_deployed":True,
      "source_caveat":"One-source TWKM test n=10; no independently sourced phone-photo accuracy",
      "assets":{p.name:{"size_bytes":p.stat().st_size,"sha256":hashlib.sha256(p.read_bytes()).hexdigest()} for p in [path,ROOT/"gallery_v4.bin"]}}
(DEST/"metadata.json").write_text(json.dumps(meta,indent=2),encoding="utf-8")
print("EXPORTED",str(path),path.stat().st_size,flush=True)
