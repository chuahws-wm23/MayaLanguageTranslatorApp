#!/usr/bin/env python3
"""Few-shot ImageNet frozen-feature SVM experiment for ten TWKM Maya signs.

Compares ResNet18 and EfficientNet-B0 frozen pretrained vision embeddings.
Tuning uses source-original-grouped 3-fold CV on the 45 predeclared TWKM
training originals only. Validation=10 originals, prior exposed test=10.
Neither catalogue accuracies nor camera-photo accuracy are guaranteed.
No Android assets are modified.
"""
from __future__ import annotations
import csv, hashlib, io, json, os, random, sys
from pathlib import Path
import cv2, joblib, numpy as np, torch
from torch import nn
from torchvision import models
from PIL import Image as PILImage
from datasets import load_dataset, Image as HFImage
from sklearn.svm import SVC
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import Normalizer
from sklearn.model_selection import StratifiedKFold
from sklearn.metrics import f1_score, confusion_matrix,precision_recall_fscore_support

sys.path.insert(0,str(Path("ml/glyph_v4").resolve()))
import train_v4 as v4
SEED=20261115
SIGNS=v4.SIGNS
OUTPUT=Path("ml/research_v4_single/frozen_features_results")
torch.manual_seed(SEED);random.seed(SEED);np.random.seed(SEED)
torch.set_num_threads(min(4,os.cpu_count() or 2))
MEAN=torch.tensor([.485,.456,.406])[:,None,None]
STD=torch.tensor([.229,.224,.225])[:,None,None]

def source_images(rows):
    wanted={r["sha"] for r in rows}
    data={}
    ds=load_dataset(v4.SOURCE,"graphs",split="train",revision=v4.REV,
        cache_dir=os.environ.get("HF_DATASETS_CACHE","/tmp/maya-v4-hf")).cast_column(
        "image",HFImage(decode=False))
    for row in ds:
        image=row.get("image")
        if not image:continue
        raw=image.get("bytes")
        if raw is None and image.get("path"):raw=Path(image["path"]).read_bytes()
        if not raw:continue
        key=hashlib.sha256(raw).hexdigest()
        if key not in wanted:continue
        with PILImage.open(io.BytesIO(raw)) as pil:
            data[key]=cv2.cvtColor(np.asarray(pil.convert("RGB")),cv2.COLOR_RGB2GRAY)
    if set(data)!=wanted:
        raise ValueError("Missing TWKM source images: "+str(len(wanted-set(data))))
    return data

def image_tensor(gray,representation,seed=None):
    h,w=gray.shape
    border=np.r_[gray[0],gray[-1],gray[:,0],gray[:,-1]]
    light=255-gray if float(np.median(border))<110 else gray.copy()
    thresh,_=cv2.threshold(light,0,255,cv2.THRESH_BINARY+cv2.THRESH_OTSU)
    ink=light<max(1,int(thresh))
    if ink.mean()<.003:ink=light<=int(thresh)
    if not(.003<=ink.mean()<=.85):
        raise ValueError("No clear sign in source image")
    ys,xs=np.nonzero(ink)
    left,right,top,bottom=int(xs.min()),int(xs.max())+1,int(ys.min()),int(ys.max())+1
    padding=max(2,int(max(right-left,bottom-top)*.035))
    left,top=max(0,left-padding),max(0,top-padding)
    right,bottom=min(w,right+padding),min(h,bottom+padding)
    crop=(np.where(ink,0,255).astype(np.uint8) if representation=="binary" else light)[top:bottom,left:right]
    factor=198/max(crop.shape)
    nw,nh=max(1,round(crop.shape[1]*factor)),max(1,round(crop.shape[0]*factor))
    patch=cv2.resize(crop,(nw,nh),interpolation=cv2.INTER_AREA if factor<1 else cv2.INTER_LINEAR)
    square=np.full((224,224),255,dtype=np.uint8)
    square[(224-nh)//2:(224-nh)//2+nh,(224-nw)//2:(224-nw)//2+nw]=patch
    if seed is not None:
        rng=np.random.default_rng(seed)
        m=cv2.getRotationMatrix2D((112,112),float(rng.uniform(-7,7)),float(rng.uniform(.94,1.06)))
        m[0,2]+=float(rng.uniform(-5,5));m[1,2]+=float(rng.uniform(-5,5))
        square=cv2.warpAffine(square,m,(224,224),borderMode=cv2.BORDER_CONSTANT,borderValue=255)
        if rng.random()<.25:square=cv2.GaussianBlur(square,(3,3),.5)
    x=torch.from_numpy(square.copy()).float().div_(255).repeat(3,1,1)
    return (x-MEAN)/STD

def images_for(rows,source,representation,augments):
    examples=[]
    for row in rows:
        examples.append(image_tensor(source[row["sha"]],representation))
        for i in range(1,augments):
            examples.append(image_tensor(source[row["sha"]],representation,
                seed=SEED+int(row["sha"][:10],16)+i*73))
    return torch.stack(examples)

def extract(net,imgs):
    result=[]
    net.eval()
    with torch.no_grad():
        for i in range(0,len(imgs),12):
            embedding=net(imgs[i:i+12]).flatten(start_dim=1)
            result.append(embedding.numpy())
    return np.concatenate(result,axis=0).astype(np.float32)

def load_extractor(name):
    if name=="resnet18":
        net=models.resnet18(weights=models.ResNet18_Weights.DEFAULT)
        net.fc=nn.Identity()
    elif name=="efficientnet_b0":
        net=models.efficientnet_b0(weights=models.EfficientNet_B0_Weights.DEFAULT)
        net.classifier=nn.Identity()
    else:raise ValueError(name)
    net.eval()
    for p in net.parameters():p.requires_grad_(False)
    return net

def fit(x,y,kernel,C):
    return make_pipeline(Normalizer(),SVC(kernel=kernel,C=C,gamma="scale",
              decision_function_shape="ovr")).fit(x,y)

def grouped_cv(X,train,kernel,C):
    y=np.asarray([SIGNS.index(r["sign"]) for r in train])
    folds=StratifiedKFold(n_splits=3,shuffle=True,random_state=SEED)
    oof=np.full(len(train),-1,dtype=int)
    for fit_source,holdout in folds.split(np.zeros(len(train)),y):
        train_ix=np.concatenate([np.arange(i*3,(i+1)*3) for i in fit_source])
        model=fit(X[train_ix],np.repeat(y[fit_source],3),kernel,C)
        oof[holdout]=model.predict(X[holdout*3])
    assert (oof>=0).all()
    return {"accuracy":float(np.mean(oof==y)),
        "correct":int(np.sum(oof==y)),"total":len(y),
        "macro_f1":float(f1_score(y,oof,labels=list(range(10)),average="macro",zero_division=0)),
        "tok_correct":int(sum(oof[i]==SIGNS.index(44) for i,r in enumerate(train) if r["sign"]==44)),
        "tok_total":sum(r["sign"]==44 for r in train)}

def measure(model,X,rows):
    predicted=model.predict(X).astype(int)
    y=np.asarray([SIGNS.index(r["sign"]) for r in rows])
    p,r,f1,ns=precision_recall_fscore_support(y,predicted,labels=list(range(10)),zero_division=0)
    cm=confusion_matrix(y,predicted,labels=list(range(10)))
    return {
      "correct":int(np.sum(predicted==y)),"total":len(y),
      "accuracy":float(np.mean(predicted==y)),
      "macro_precision":float(np.mean(p)),"macro_recall":float(np.mean(r)),"macro_f1":float(np.mean(f1)),
      "confusion":cm.tolist(),
      "per_sign":[{"sign":SIGNS[i],"support":int(ns[i]),"precision":float(p[i]),
          "recall":float(r[i]),"f1":float(f1[i])} for i in range(10)],
      "errors":[{"graph":row["graph"],"actual":row["sign"],"predicted":SIGNS[int(pred)]}
          for row,pred,true in zip(rows,predicted,y) if pred!=true]}

def save_confusion(path,result):
    with path.open("w",newline="",encoding="utf-8") as f:
        writer=csv.writer(f)
        writer.writerow(["Actual / Predicted"]+SIGNS)
        for label,row in zip(SIGNS,result["confusion"]):
            writer.writerow([label]+row)

def main():
    bysign,_=v4.data()
    train,val,test,uncal,untest=v4.partitions(bysign)
    source=source_images(train+val+test)
    grid=[]
    cached={}
    for backbone in ("resnet18","efficientnet_b0"):
        net=load_extractor(backbone)
        for representation in ("binary","gray"):
            X=extract(net,images_for(train,source,representation,3))
            V=extract(net,images_for(val,source,representation,1))
            T=extract(net,images_for(test,source,representation,1))
            cached[(backbone,representation)]=(X,V,T)
            for kernel in ("linear","rbf"):
                for C in (0.1,1,10):
                    cv=grouped_cv(X,train,kernel,C)
                    grid.append({"backbone":backbone,"representation":representation,
                                 "kernel":kernel,"C":C,"cv":cv})
                    print("source-only CV",backbone,representation,kernel,C,
                          cv["correct"],"/",cv["total"],"TOK",cv["tok_correct"],
                          "/"+str(cv["tok_total"]),flush=True)
    chosen=max(grid,key=lambda c:(c["cv"]["macro_f1"],c["cv"]["correct"],
                                   -c["C"],int(c["backbone"]=="resnet18")))
    backbone,repr_,kernel,C=chosen["backbone"],chosen["representation"],chosen["kernel"],chosen["C"]
    X,V,T=cached[(backbone,repr_)]
    y=np.asarray([SIGNS.index(r["sign"]) for r in train])
    model=fit(X,np.repeat(y,3),kernel,C)
    validation=measure(model,V,val)
    historical=measure(model,T,test)
    OUTPUT.mkdir(parents=True,exist_ok=True)
    save_confusion(OUTPUT/"validation_confusion.csv",validation)
    save_confusion(OUTPUT/"exposed_test_confusion.csv",historical)
    joblib.dump({"classifier":model,"backbone":backbone,"representation":repr_,
                 "signs":SIGNS},OUTPUT/"frozen_image_features_research.joblib",compress=3)
    results={
       "source":"TWKM pinned labelled graph originals",
       "training_originals":len(train),"validation_originals":len(val),
       "historical_exposed_test_originals":len(test),
       "image_grouped_cv_on_training_only":True,
       "feature_extractors":"ImageNet supervised pretrained ResNet18 and EfficientNet-B0",
       "research_selection":chosen,"all_cv":grid,
       "validation":validation,"historical_exposed_test":historical,
       "baseline_v4_validation_correct":8,"baseline_v4_exposed_test_correct":7,
       "real_camera_accuracy":"NOT MEASURED",
       "no_unfamiliar_sign_rejection_established":True,
       "android_deployed":False,
       "needs_independent_source_grouped_photo_test":True
    }
    (OUTPUT/"results.json").write_text(json.dumps(results,indent=2),encoding="utf-8")
    print("BEST",chosen,flush=True)
    print("VALIDATION",validation["correct"],"/10",flush=True)
    print("EXPOSED TEST",historical["correct"],"/10",flush=True)
    print("TOK",next((x for x in historical["errors"] if x["actual"]==44),"correct"),flush=True)

if __name__=="__main__":main()
