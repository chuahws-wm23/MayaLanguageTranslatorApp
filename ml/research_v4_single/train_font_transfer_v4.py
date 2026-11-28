#!/usr/bin/env python3
"""Experimental two-stage visual transfer for the SAME Research v4 sign model.

Stage A: learn shared stroke/shape features from 80 U_XXXX Kaggle FONT-KEY
classes, with no Maya/English label assumptions. Only nonblank, unique pixels
from classes present in both filled and outlined styles are used.
Stage B: transfer last visual feature blocks to original v4 and fine-tune its
TEN verified TWKM sign categories on TRAIN ORIGINALS ONLY.

A candidate is selected solely using held-out TWKM validation originals
and fixed synthetic stress transforms. Prior test originals are exposed.
The Android ONNX asset is never modified and no redistributed Kaggle-trained
weight file is produced without licence review.
"""
import argparse
import copy
import hashlib
from collections import defaultdict
from pathlib import Path
import json
import random
import sys

import cv2
import numpy as np
from PIL import Image
import torch
from torch import nn
import torch.nn.functional as F
from torchvision import models

sys.path.insert(0,str(Path("ml/research_v4_single").resolve()))
import retrain_v4 as r4
import train_v4 as v4

SEED=20261116
np.random.seed(SEED)
torch.manual_seed(SEED)
random.seed(SEED)
torch.set_num_threads(4)

def kaggle_font_train_samples(root, per_style=8):
    candidates=defaultdict(lambda:defaultdict(list))
    hash_sets=set()
    for path in sorted(Path(root).rglob("*.png")):
        parts=path.parts
        label=next((p for p in parts if p.startswith("U_")),None)
        style=next((p for p in parts if p in ("mayanglyphsfill_regular",
                                            "mayanglyphsoutline_regular")),None)
        if not label or not style:continue
        try:
            with Image.open(path) as src:
                gray=np.asarray(src.convert("L"))
        except OSError:continue
        if gray.shape!=(64,64) or gray.min()==gray.max():continue
        identity=hashlib.sha256(gray.tobytes()).hexdigest()
        if identity in hash_sets:continue
        hash_sets.add(identity)
        candidates[label][style].append(gray)
    labels=sorted(k for k,v in candidates.items() if len(v)==2 and
                  min(len(x) for x in v.values())>=3)
    assert len(labels)>=75,("Not enough shared nonblank font glyph keys",len(labels))
    labels=labels[:80]
    rng=np.random.default_rng(SEED)
    samples=[]
    for i,label in enumerate(labels):
        for style in ("mayanglyphsfill_regular","mayanglyphsoutline_regular"):
            images=candidates[label][style]
            ids=rng.choice(len(images),size=min(per_style,len(images)),replace=False)
            for ix in ids:
                gray=images[int(ix)]
                border=np.r_[gray[0],gray[-1],gray[:,0],gray[:,-1]]
                if np.median(border)<110: gray=255-gray
                _,mask=cv2.threshold(gray,0,255,cv2.THRESH_BINARY_INV+cv2.THRESH_OTSU)
                samples.append((mask,i))
    return samples,labels,{"classes":len(labels),"unique_font_images_used":len(samples),
      "number_of_unique_pixel_hashes":len(hash_sets)}

def font_to_tensor(images):
    out=[]
    for im in images:
        # grayscale dark-ink mask -> bright background training pixel range.
        canvas=cv2.resize(255-im,(160,160),interpolation=cv2.INTER_LINEAR)
        tensor=torch.from_numpy(canvas.copy()).float().div_(255).repeat(3,1,1)
        tensor.sub_(torch.tensor([.485,.456,.406])[:,None,None])
        tensor.div_(torch.tensor([.229,.224,.225])[:,None,None])
        out.append(tensor)
    return torch.stack(out)

def pretrain_visual_features(source,labels,epochs=3):
    baseline=r4.build()
    baseline.classifier=nn.Linear(576,len(labels))
    for p in baseline.parameters():p.requires_grad=False
    for p in baseline.features[-3:].parameters():p.requires_grad=True
    for p in baseline.classifier.parameters():p.requires_grad=True
    opt=torch.optim.AdamW((p for p in baseline.parameters() if p.requires_grad),
                          lr=1e-4,weight_decay=.002)
    rng=np.random.default_rng(SEED)
    history=[]
    for epoch in range(1,epochs+1):
        order=rng.permutation(len(source))
        losses=[]
        baseline.train()
        for layer in baseline.modules():
            if isinstance(layer,nn.BatchNorm2d):layer.eval()
        for start in range(0,len(order),32):
            positions=order[start:start+32]
            x=font_to_tensor([source[int(i)][0] for i in positions])
            y=torch.tensor([source[int(i)][1] for i in positions],dtype=torch.long)
            opt.zero_grad(set_to_none=True)
            logits=baseline(x)
            loss=F.cross_entropy(logits,y,label_smoothing=.04)
            loss.backward()
            nn.utils.clip_grad_norm_(baseline.parameters(),2.0)
            opt.step()
            losses.append(float(loss.detach()))
        history.append({"epoch":epoch,"font_key_loss":float(np.mean(losses))})
        print("aux font-key epoch",epoch,"loss",round(np.mean(losses),4),flush=True)
    return baseline,history

def finetune_verified_twkm(aux,train,val,style_stats,epochs=8):
    net=r4.build()
    # The TWKM sign classifier head remains the verified ten-class head;
    # only visual convolutions are transferred from font-key pretraining.
    for i in (-3,-2,-1):
        net.features[i].load_state_dict(aux.features[i].state_dict())
    for p in net.parameters():p.requires_grad=False
    for p in net.features[-3:].parameters():p.requires_grad=True
    for p in net.classifier.parameters():p.requires_grad=True
    opt=torch.optim.AdamW((p for p in net.parameters() if p.requires_grad),
                          lr=2.5e-5,weight_decay=.002)
    rng=np.random.default_rng(SEED+200)
    history=[]
    best=None
    bestkey=(-1,-1)
    patience=0
    for epoch in range(1,epochs+1):
        images=[]
        y=[]
        for row in train:
            idx=v4.SIGNS.index(row["sign"])
            images.append(row["image"]);y.append(idx)
            for _ in range(4):
                images.append(r4.augment_training(row["image"],rng,style_stats))
                y.append(idx)
        order=rng.permutation(len(images))
        net.train()
        for layer in net.modules():
            if isinstance(layer,nn.BatchNorm2d):layer.eval()
        losses=[]
        for pos in range(0,len(order),20):
            ids=order[pos:pos+20]
            x=v4.tensorize([images[int(i)] for i in ids],160)
            labels=torch.tensor([y[int(i)] for i in ids],dtype=torch.long)
            opt.zero_grad(set_to_none=True)
            logits=net(x)
            loss=F.cross_entropy(logits,labels,label_smoothing=.06)
            loss.backward()
            nn.utils.clip_grad_norm_(net.parameters(),1.5)
            opt.step()
            losses.append(float(loss.detach()))
        net.eval()
        metrics=r4.check(net,val,style_stats,SEED+100)
        key=(metrics["clean_correct"],metrics["stress_correct"])
        history.append({"epoch":epoch,"train_loss":float(np.mean(losses)),**metrics})
        print("verified TWKM epoch",epoch,"validation clean",metrics["clean_correct"],
              "stress",metrics["stress_correct"],flush=True)
        if key>bestkey:
            bestkey=key;best=copy.deepcopy(net.state_dict());patience=0
        else:patience+=1
        if patience>=4:break
    net.load_state_dict(best)
    net.eval()
    return net,history

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--kaggle-dir",required=True)
    parser.add_argument("--output",default="ml/research_v4_single/transfer_output")
    parser.add_argument("--font-epochs",type=int,default=3)
    parser.add_argument("--twkm-epochs",type=int,default=8)
    args=parser.parse_args()
    out=Path(args.output)
    out.mkdir(parents=True,exist_ok=True)
    samples,keys,font= kaggle_font_train_samples(args.kaggle_dir)
    style=r4.font_style_statistics(Path(args.kaggle_dir))
    bysign,_=v4.data()
    train,val,exposed_test,unknown_cal,unknown_test=v4.partitions(bysign)
    original=r4.build()
    old=r4.check(original,val,style,SEED+100)
    aux,font_history=pretrain_visual_features(samples,keys,args.font_epochs)
    candidate,history=finetune_verified_twkm(aux,train,val,style,args.twkm_epochs)
    new=r4.check(candidate,val,style,SEED+100)
    accepted=(new["clean_correct"]>=old["clean_correct"] and
              new["stress_correct"]>=old["stress_correct"]+2)
    report={
        "experiment":"Research v4 font-key AUXILIARY pretraining then verified TWKM fine tuning",
        "same_model_version":"Research v4",
        "kaggle_font_key_label_meaning":"Only arbitrary font keys, NEVER Maya readings",
        "font":font,
        "twkm_verified_supported_signs":v4.SIGNS,
        "twkm_train_originals":len(train),
        "baseline_validation":old,
        "transfer_candidate_validation":new,
        "candidate_selected_by_validation":accepted,
        "candidate_not_deployed":True,
        "font_history":font_history,
        "twkm_history":history,
        "external_real_photo_accuracy":"NOT EVALUATED",
        "licence_redistribution_not_verified":True,
    }
    if accepted:
        report["baseline_exposed_test"]=r4.check(original,exposed_test,style,SEED+240)
        report["candidate_exposed_test"]=r4.check(candidate,exposed_test,style,SEED+240)
        # No weights distributed or placed in Android unless source rights
        # and independent photo performance are subsequently verified.
    (out/"results.json").write_text(json.dumps(report,indent=2),encoding="utf-8")
    print("RESULT",json.dumps({
        "baseline":old,"candidate":new,"selected":accepted,
    },indent=2),flush=True)

if __name__=="__main__":main()
