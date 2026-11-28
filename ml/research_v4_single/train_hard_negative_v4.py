#!/usr/bin/env python3
"""Research v4 discriminative fine-tuning with source-separated TWKM drawings.

Uses hard-positive and hardest-negative *feature* distances among two distinct
training originals from each of ten supported signs. No unverified Kaggle font
keys are treated as TWKM labels; Kaggle is used solely for stroke-density style
augmentation. Same v4 MobileNetV3 architecture, same classes. A candidate
model is only saved if validation clean accuracy is not worse and the stress
metric materially improves; no Android model update is automatic.
"""
import argparse
import copy
import json
import os
from pathlib import Path
import random
import sys

import numpy as np
import torch
import torch.nn.functional as F
from torch import nn
sys.path.insert(0, str(Path("ml/research_v4_single").resolve()))
import retrain_v4 as r4
import train_v4 as v4

SEED=20261116
torch.manual_seed(SEED)
np.random.seed(SEED)
random.seed(SEED)
torch.set_num_threads(min(4,os.cpu_count() or 2))

def class_pairs(trains, rng, styles):
    grouped={s:[r for r in trains if r["sign"]==s] for s in v4.SIGNS}
    images=[]
    y=[]
    for label,sign in enumerate(v4.SIGNS):
        assert len(grouped[sign])>=2, "Need two DIFFERENT source originals per class"
        idx=rng.choice(len(grouped[sign]),size=2,replace=False)
        rows=[grouped[sign][int(i)] for i in idx]
        for row in rows:
            img = row["image"] if rng.random()<.2 else r4.augment_training(
                row["image"],rng,styles)
            images.append(img)
            y.append(label)
    return images,torch.tensor(y,dtype=torch.long)

def batch_hard_loss(features, labels, margin=.16):
    normalized=F.normalize(features,p=2,dim=1)
    sim=normalized @ normalized.T
    same=labels[:,None]==labels[None,:]
    identity=torch.eye(len(labels),dtype=torch.bool)
    positives=sim.masked_fill(~same|identity, float("inf"))
    negatives=sim.masked_fill(same, float("-inf"))
    hardest_pos=positives.min(dim=1).values
    hardest_neg=negatives.max(dim=1).values
    return F.relu(hardest_neg-hardest_pos+margin).mean()

def train(net, rows, val, styles, epochs, batches):
    for p in net.parameters():p.requires_grad=False
    for p in net.features[-3:].parameters():p.requires_grad=True
    for p in net.classifier.parameters():p.requires_grad=True
    optimizer=torch.optim.AdamW(
        (p for p in net.parameters() if p.requires_grad),
        lr=2.5e-5,weight_decay=.003)
    rng=np.random.default_rng(SEED)
    best=None
    best_key=(-1,-1)
    no_improve=0
    history=[]
    for epoch in range(1,epochs+1):
        losses=[]
        rankloss=[]
        net.train()
        for layer in net.modules():
            if isinstance(layer,nn.BatchNorm2d):
                layer.eval()
        for _ in range(batches):
            imgs,labels=class_pairs(rows,rng,styles)
            x=v4.tensorize(imgs,160)
            optimizer.zero_grad(set_to_none=True)
            feat=net.avgpool(net.features(x)).flatten(1)
            logits=net.classifier(feat)
            ce=F.cross_entropy(logits,labels,label_smoothing=.05)
            rank=batch_hard_loss(feat,labels)
            loss=ce+.18*rank
            loss.backward()
            nn.utils.clip_grad_norm_(net.parameters(),1.5)
            optimizer.step()
            losses.append(float(loss.detach()))
            rankloss.append(float(rank.detach()))
        net.eval()
        metrics=r4.check(net,val,styles,SEED+100)
        key=(metrics["clean_correct"],metrics["stress_correct"])
        history.append({
            "epoch":epoch,
            "train_loss":float(np.mean(losses)),
            "metric_loss":float(np.mean(rankloss)),
            **metrics
        })
        print("epoch",epoch,"train_loss",round(np.mean(losses),3),
              "hard_negative_loss",round(np.mean(rankloss),3),
              "clean",metrics["clean_correct"],"/",metrics["clean_total"],
              "stress",metrics["stress_correct"],"/",metrics["stress_total"],
              flush=True)
        if key>best_key:
            best_key=key
            best=copy.deepcopy(net.state_dict())
            no_improve=0
        else:
            no_improve+=1
        if no_improve>=4:break
    net.load_state_dict(best)
    net.eval()
    return net,history

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--kaggle-dir",required=True)
    p.add_argument("--output",default="ml/research_v4_single/hard_negative_output")
    p.add_argument("--epochs",type=int,default=8)
    p.add_argument("--batches",type=int,default=16)
    args=p.parse_args()
    out=Path(args.output)
    out.mkdir(parents=True,exist_ok=True)
    stats=r4.font_style_statistics(Path(args.kaggle_dir))
    bysign,_=v4.data()
    trains,val,exposed_test,unknown_cal,unknown_test=v4.partitions(bysign)
    baseline=r4.build()
    baseline_val=r4.check(baseline,val,stats,SEED+100)
    baseline_untouched_stress=r4.check(baseline,exposed_test,stats,SEED+202)
    candidate,history=train(r4.build(),trains,val,stats,args.epochs,args.batches)
    candidate_val=r4.check(candidate,val,stats,SEED+100)
    # Selection is on validation ONLY. This is no substitute for external photos.
    improved=(candidate_val["clean_correct"]>=baseline_val["clean_correct"] and
              candidate_val["stress_correct"]>=baseline_val["stress_correct"]+2)
    report={
        "experiment":"Research v4 same-class hard-negative fine-tuning",
        "architecture":"MobileNetV3-Small with batch-hard supervised feature loss",
        "twkm_signs":v4.SIGNS,
        "training_distinct_originals":len(trains),
        "validation_distinct_originals":len(val),
        "prior_exposed_test_originals":len(exposed_test),
        "unmapped_kaggle_use":"Unlabelled style statistics only, not Maya sign identities",
        "unmapped_kaggle_style_stats":stats,
        "baseline_validation":baseline_val,
        "candidate_validation":candidate_val,
        "candidate_passed_validation_gate":improved,
        "history":history,
        "previous_original_holdout_is_exposed":True,
        "new_independent_phone_accuracy":"NOT MEASURED",
        "decision":"Candidate only; no automatic Android ONNX replacement",
    }
    if improved:
        # Report test only AFTER validation selection, descriptive because
        # earlier experiments already exposed these original image IDs.
        report["baseline_exposed_test"]=baseline_untouched_stress
        report["candidate_exposed_test"]=r4.check(candidate,exposed_test,stats,SEED+202)
        torch.save(candidate.state_dict(),out/"research_v4_hard_negative_candidate.pt")
        report["checkpoint_saved_for_review"]=True
    else:
        report["checkpoint_saved_for_review"]=False
    (out/"results.json").write_text(json.dumps(report,indent=2),encoding="utf-8")
    print("RESULT:",json.dumps({
        "baseline_validation":baseline_val,
        "candidate_validation":candidate_val,
        "selected":improved,
    },indent=2),flush=True)

if __name__=="__main__":main()
