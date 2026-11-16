#!/usr/bin/env python3
"""Frozen MobileNetV3-Small research pilot for ENGLISH-glossed TWKM classes.

Two fixed class sizes, 20 and 30, selected by number of independently
hashed graph drawing records in the source licensed gallery (not by test
results). One graph per class is held out before fitting. Source variants
can share artifacts. DO NOT treat this as independent camera accuracy.
"""
from __future__ import annotations
from collections import Counter,defaultdict
import json
import os
from pathlib import Path

import numpy as np
from PIL import Image
import torch
from torch import nn
from torchvision.models import mobilenet_v3_small, MobileNet_V3_Small_Weights
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import f1_score

DATA=Path("app/src/main/assets")
OUT=Path("ml/logogram_expansion/output")
CLASS_SIZES=(20,30)
torch.set_num_threads(min(4,os.cpu_count() or 2))
MEAN=np.array([.485,.456,.406],dtype=np.float32)[:,None,None]
STD=np.array([.229,.224,.225],dtype=np.float32)[:,None,None]

def inputs(bits):
    sample=np.unpackbits(np.frombuffer(bits,dtype=np.uint8),bitorder="big").reshape(64,64)
    # Training bitmap is bright foreground; create black ink on white.
    gray=255-255*sample
    resized=np.asarray(Image.fromarray(gray.astype(np.uint8)).resize(
        (224,224),Image.Resampling.BILINEAR),dtype=np.float32)/255.
    return torch.from_numpy((np.repeat(resized[None,:,:],3,axis=0)-MEAN)/STD)

def main():
    dataset=json.loads((DATA/"maya_twkm_logogram_index.json").read_text())
    blob=(DATA/"maya_twkm_logogram_bitmap.bin").read_bytes()
    graphrows=dataset["entries"]
    assert len(blob)==len(graphrows)*512
    bysign=defaultdict(list)
    for idx,row in enumerate(graphrows):
        bysign[int(row["sign"])].append(idx)
    counts=Counter({sign:len(records) for sign,records in bysign.items()})
    eligible=[sign for sign,cnt in counts.most_common()
              if cnt>=3]
    # No outcome-based class selection. Tie-break deterministically by sign.
    eligible=sorted(eligible,key=lambda sign:(-counts[sign],sign))
    print("ELIGIBLE_SIGN_COUNTS",[(s,counts[s]) for s in eligible[:45]],flush=True)
    assert len(eligible)>=max(CLASS_SIZES)

    model=mobilenet_v3_small(weights=MobileNet_V3_Small_Weights.DEFAULT)
    model.classifier=nn.Identity()
    model.eval()
    for p in model.parameters():p.requires_grad_(False)
    chosen=set(eligible[:max(CLASS_SIZES)])
    interested=[idx for idx,row in enumerate(graphrows) if int(row["sign"]) in chosen]
    emb={}
    with torch.no_grad():
        for j in range(0,len(interested),12):
            batch_ids=interested[j:j+12]
            ts=[inputs(blob[i*512:(i+1)*512]) for i in batch_ids]
            vec=model(torch.stack(ts)).flatten(1).cpu().numpy()
            vec=vec/np.maximum(np.linalg.norm(vec,axis=1,keepdims=True),1e-9)
            for i,x in zip(batch_ids,vec):emb[i]=x
    results={}
    for class_count in CLASS_SIZES:
        selected=set(eligible[:class_count])
        train=[];test=[]
        for sign in sorted(selected):
            indices=sorted(bysign[sign],key=lambda i:graphrows[i]["graph"])
            assert len(indices)>=3
            test.append(indices[-1])
            train.extend(indices[:-1])
        xtrain=np.stack([emb[i] for i in train])
        ytrain=np.array([int(graphrows[i]["sign"]) for i in train],dtype=int)
        xtest=np.stack([emb[i] for i in test])
        ytest=np.array([int(graphrows[i]["sign"]) for i in test],dtype=int)
        clf=LogisticRegression(C=10,max_iter=1200,solver="lbfgs")
        clf.fit(xtrain,ytrain)
        scores=clf.predict_proba(xtest)
        ranked=np.argsort(-scores,axis=1)
        top1=clf.classes_[ranked[:,0]]
        top3=clf.classes_[ranked[:,:3]]
        result={
            "english_sign_classes":class_count,
            "train_graph_variants":len(train),"heldout_source_graph_variants":len(test),
            "correct_top1":int((top1==ytest).sum()),
            "correct_top3":int(np.any(top3==ytest[:,None],axis=1).sum()),
            "macro_f1":float(f1_score(ytest,top1,labels=sorted(selected),
                                      average="macro",zero_division=0)),
            "selected_signs":[
                {"sign":s,"graph_count":counts[s],
                 "english":next(row["english"] for row in graphrows if row["sign"]==s)}
                for s in sorted(selected)
            ],
            "test_predictions":[
                {"true":int(ytest[i]),"pred":int(top1[i]),
                 "top3":[int(x) for x in top3[i]]}
                for i in range(len(test))
            ],
        }
        results[str(class_count)]=result
        print("CLASSIFIER_RESULT",json.dumps({
            k:v for k,v in result.items() if k not in ("selected_signs","test_predictions")
        }),flush=True)
    report={
        "model":"Frozen pretrained MobileNetV3-Small image features + multinomial logistic regression",
        "evaluation":"one held-out TWKM drawing per sign, SOURCE-RELATED not independent artifact or photo",
        "provenance":"Pinned TWKM licensed graph bitplanes, strongly rated proposed logograms",
        "source_variants_not_independent_photos":True,
        "trained_classifier_not_deployed_to_android":True,
        "class_sizes_preselected":list(CLASS_SIZES),
        "results":results,
    }
    OUT.mkdir(parents=True,exist_ok=True)
    (OUT/"english_logogram_20_30_classifier_pilot.json").write_text(
        json.dumps(report,indent=2,ensure_ascii=False),encoding="utf8")

if __name__=="__main__":
    main()
