#!/usr/bin/env python3
"""Optional candidate retraining for the SAME 10-class Research v4.

Kaggle U_XXXX font-key glyphs are *NOT* assigned TWKM labels. Instead
the user's font dataset supplies only an ink/stroke-density augmentation
profile. Supervised training exclusively uses the pinned TWKM sign IDs.

A candidate is selected using only known validation originals and fixed
synthetic changes. Real phone accuracy is NOT measured by this experiment.
The currently bundled Android ONNX model is NEVER overwritten by this script.
"""
import argparse
import copy
import hashlib
import json
import os
import random
import time
from pathlib import Path

import cv2
import numpy as np
import torch
import torch.nn.functional as F
from PIL import Image
from torch import nn
from torchvision import models

import sys
sys.path.insert(0, str(Path("ml/glyph_v4").resolve()))
import train_v4 as v4

SEED=20261115
torch.manual_seed(SEED)
np.random.seed(SEED)
random.seed(SEED)
torch.set_num_threads(min(4, os.cpu_count() or 2))

def font_style_statistics(root: Path, max_images=20000):
    """Inspect all available user/public font files; NEVER treat keys as Maya readings."""
    fractions=[]
    identifiers=set()
    styles=set()
    visited=0
    duplicates=0
    blank=0
    for path in sorted(root.rglob("*.png")):
        if visited>=max_images:
            break
        visited+=1
        try:
            with Image.open(path) as img:
                gray=np.asarray(img.convert("L"))
            if gray.shape!=(64,64):
                continue
            sha=hashlib.sha256(gray.tobytes()).hexdigest()
            if sha in identifiers:
                duplicates+=1
                continue
            identifiers.add(sha)
            if int(gray.max())==int(gray.min()):
                blank+=1
                continue
            border=np.r_[gray[0],gray[-1],gray[:,0],gray[:,-1]]
            if np.median(border)<110:
                gray=255-gray
            _, ink=cv2.threshold(gray,0,255,cv2.THRESH_BINARY_INV+cv2.THRESH_OTSU)
            ratio=float(np.mean(ink>127))
            if .005<ratio<.90:
                fractions.append(ratio)
                styles.add("outline" if "outline" in str(path).lower() else "fill")
        except (OSError,ValueError):
            continue
    if len(fractions)<200 or len(styles)<2:
        raise RuntimeError("Insufficient usable Kaggle images from BOTH font styles.")
    quantiles=np.quantile(fractions,[.10,.25,.50,.75,.90]).tolist()
    return {
        "read_png_count":visited,
        "unique_pixel_hash_count":len(identifiers),
        "duplicate_pixels":duplicates,
        "blank_unique":blank,
        "usable_for_style_stats":len(fractions),
        "styles":sorted(styles),
        "ink_fraction_quantiles":[float(x) for x in quantiles],
    }

def augment_training(img, rng, stats):
    # The target class remains the VERIFIED TWKM sign. Kaggle images never
    # enter the sign classifier with a synthetic or guessed Maya label.
    source=cv2.warpAffine(img,
        _matrix(rng),(64,64),flags=cv2.INTER_LINEAR,
        borderMode=cv2.BORDER_CONSTANT,borderValue=0)
    low,_,median,_,high=stats["ink_fraction_quantiles"]
    target=float(rng.uniform(low,high))
    occupancy=float(np.mean(source>127))
    if occupancy>0.001 and target>occupancy*1.25:
        source=cv2.dilate(source,np.ones((2,2),np.uint8),iterations=1)
    elif occupancy>0.001 and target<occupancy*.77:
        source=cv2.erode(source,np.ones((2,2),np.uint8),iterations=1)
    elif rng.random()<.20:
        source=cv2.GaussianBlur(source,(3,3),float(rng.uniform(.3,.8)))
    if rng.random()<.18:
        noise=rng.normal(0,5,source.shape)
        source=np.clip(source.astype(np.float32)+noise,0,255).astype(np.uint8)
    return source

def _matrix(rng):
    m=cv2.getRotationMatrix2D((32,32),
        float(rng.uniform(-12,12)),float(rng.uniform(.90,1.10)))
    m[0,2]+=float(rng.uniform(-4,4))
    m[1,2]+=float(rng.uniform(-4,4))
    return m

def predictions(net, images):
    if not images:
        return np.zeros(0,dtype=np.int64)
    net.eval()
    out=[]
    with torch.inference_mode():
        for i in range(0,len(images),16):
            x=v4.tensorize(images[i:i+16],v4.SIDE)
            out.extend(net(x).argmax(dim=1).numpy().tolist())
    return np.asarray(out,dtype=np.int64)

def check(net, rows, stats, seed):
    real=[r["image"] for r in rows]
    labels=np.asarray([v4.SIGNS.index(r["sign"]) for r in rows],dtype=np.int64)
    clean=predictions(net,real)
    rng=np.random.default_rng(seed)
    stressed=[augment_training(r["image"],rng,stats) for r in rows for _ in range(3)]
    stressed_labels=np.repeat(labels,3)
    stressed_pred=predictions(net,stressed)
    return {
        "clean_correct":int(np.sum(clean==labels)),
        "clean_total":len(labels),
        "stress_correct":int(np.sum(stressed_pred==stressed_labels)),
        "stress_total":len(stressed_labels),
        "clean_accuracy":float(np.mean(clean==labels)),
        "stress_accuracy":float(np.mean(stressed_pred==stressed_labels)),
    }

def build():
    net=models.mobilenet_v3_small(weights=None)
    net.classifier=nn.Linear(576,len(v4.SIGNS))
    weights=Path("ml/glyph_v4/outputs/mobilenet_v4_finetuned.pt")
    net.load_state_dict(torch.load(weights,map_location="cpu",weights_only=True))
    net.eval()
    return net

def train_candidate(net, train, val, style, epochs, views):
    for p in net.parameters():p.requires_grad=False
    for p in net.features[-3:].parameters():p.requires_grad=True
    for p in net.classifier.parameters():p.requires_grad=True
    opt=torch.optim.AdamW((p for p in net.parameters() if p.requires_grad),
                         lr=3e-5,weight_decay=.002)
    rng=np.random.default_rng(SEED+44)
    best_state=None
    best_key=(-1,-1)
    patience=0
    history=[]
    targets=np.asarray([v4.SIGNS.index(r["sign"]) for r in train],dtype=np.int64)
    for epoch in range(1,epochs+1):
        images=[]
        y=[]
        for row,label in zip(train,targets):
            images.append(row["image"])
            y.append(int(label))
            for _ in range(views-1):
                images.append(augment_training(row["image"],rng,style))
                y.append(int(label))
        order=rng.permutation(len(images))
        losses=[]
        net.train()
        for layer in net.modules():
            if isinstance(layer,nn.BatchNorm2d): layer.eval()
        for start in range(0,len(order),16):
            ix=order[start:start+16]
            x=v4.tensorize([images[int(i)] for i in ix],v4.SIDE)
            target=torch.tensor([y[int(i)] for i in ix],dtype=torch.long)
            opt.zero_grad(set_to_none=True)
            logits=net(x)
            loss=F.cross_entropy(logits,target,label_smoothing=.06)
            loss.backward()
            torch.nn.utils.clip_grad_norm_(net.parameters(),1.5)
            opt.step()
            losses.append(float(loss.detach()))
        metrics=check(net,val,style,SEED+100)
        key=(metrics["clean_correct"],metrics["stress_correct"])
        history.append({"epoch":epoch,"train_loss":float(np.mean(losses)),**metrics})
        print("epoch",epoch,"train_loss",round(float(np.mean(losses)),4),
              "validation",metrics["clean_correct"],"/",len(val),
              "synthetic stress",metrics["stress_correct"],"/",len(val)*3,
              flush=True)
        if key>best_key:
            best_key=key
            best_state=copy.deepcopy(net.state_dict())
            patience=0
        else:
            patience+=1
        if patience>=3: break
    net.load_state_dict(best_state)
    net.eval()
    return net,history

def export_onnx(net,outdir):
    import onnx
    import onnxruntime as ort
    class Dual(nn.Module):
        def __init__(self,inner):
            super().__init__()
            self.net=inner
        def forward(self,x):
            z=self.net.avgpool(self.net.features(x)).flatten(1)
            return self.net.classifier(z), F.normalize(z,p=2,dim=1)
    wrapped=Dual(net).eval()
    path=outdir/"maya_glyph_v4_candidate.onnx"
    x=torch.randn(1,3,160,160)
    with torch.inference_mode():
        a,b=wrapped(x)
    torch.onnx.export(wrapped,x,str(path),opset_version=17,
        input_names=["image"],output_names=["logits","features"],
        dynamic_axes={"image":{0:"batch"},"logits":{0:"batch"},"features":{0:"batch"}},
        dynamo=False)
    onnx.checker.check_model(onnx.load(str(path)))
    session=ort.InferenceSession(str(path),providers=["CPUExecutionProvider"])
    actual=session.run(["logits","features"],{"image":x.numpy()})
    error=max(float(np.max(np.abs(actual[0]-a.numpy()))),
              float(np.max(np.abs(actual[1]-b.numpy()))))
    if error>=2e-4:
        path.unlink(missing_ok=True)
        raise ValueError("ONNX vs PyTorch disagreement: "+str(error))
    return {"path":str(path),"sha256":hashlib.sha256(path.read_bytes()).hexdigest(),
            "size_bytes":path.stat().st_size,"max_abs_parity_error":error}

def main():
    args=parse_args()
    out=Path(args.output)
    out.mkdir(parents=True,exist_ok=True)
    stats=font_style_statistics(Path(args.kaggle_dir))
    print("Kaggle input used for style stats",stats,flush=True)
    bysign,collisions=v4.data()
    train,val,test,unknown_cal,unknown_test=v4.partitions(bysign)
    # Fixed v4 splits from original research; never retrain on exposed
    # validation/test or on unknown sign samples.
    baseline=build()
    original_validation=check(baseline,val,stats,SEED+100)
    original_known_test=check(baseline,test,stats,SEED+101)
    print("Original v4",original_validation,flush=True)
    candidate,history=train_candidate(build(),train,val,stats,args.epochs,args.views)
    candidate_validation=check(candidate,val,stats,SEED+100)
    improved=(candidate_validation["clean_correct"]>=original_validation["clean_correct"] and
              candidate_validation["stress_correct"]>=original_validation["stress_correct"]+2)
    report={
        "experiment":"Research v4 same ten TWKM class identities (no v5)",
        "dataset_verified_labels":"TWKM pinned "+str(v4.REV),
        "other_dataset_usage":"Kaggle original user ZIP: ink/style statistics ONLY, no U_XXXX-to-Maya mapping",
        "kaggle":stats,
        "train_originals":len(train),"validation_originals":len(val),
        "known_test_originals":len(test),"unknown_calibration":len(unknown_cal),
        "unknown_test":len(unknown_test),
        "baseline_validation":original_validation,
        "baseline_known_test_regression":original_known_test,
        "candidate_validation":candidate_validation,
        "candidate_selected_by_validation_only":improved,
        "candidate_history":history,
        "known_test_note":"The original v4 known holdout has already been exposed in previous reports.",
        "independent_camera_accuracy":"NOT MEASURED",
        "disposition":"CANDIDATE ONLY, do not replace Android until independent phone-photo results and attribution review",
    }
    if improved:
        report["candidate_known_test_regression"]=check(candidate,test,stats,SEED+101)
        report["candidate_onnx"]=export_onnx(candidate,out)
    (out/"evaluation.json").write_text(json.dumps(report,indent=2),encoding="utf8")
    print("Validation selected:",improved,flush=True)
    print("Report:",out/"evaluation.json",flush=True)

def parse_args():
    p=argparse.ArgumentParser()
    p.add_argument("--kaggle-dir",required=True)
    p.add_argument("--output",default="ml/research_v4_single/output")
    p.add_argument("--epochs",type=int,default=5)
    p.add_argument("--views",type=int,default=5)
    return p.parse_args()

if __name__=="__main__":
    main()
