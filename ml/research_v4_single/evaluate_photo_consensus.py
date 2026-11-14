#!/usr/bin/env python3
"""Reproduce the v4 Android two/three-photo-view votes on TWKM catalog drawings.

This test uses a close *Python approximation* of Android preprocessing, not
a device execution. No user-image ground truth or independent real photographs
are included. The old v4 labelled split is already known to researchers.
"""
import json
import os
import sys
from pathlib import Path

import cv2
import numpy as np
import onnxruntime as ort
import torch
sys.path.insert(0,str(Path("ml/glyph_v4").resolve()))
import train_v4 as v4

SIGN_NUMBERS=v4.SIGNS
ASSET=Path("app/src/main/assets/maya_glyph_v4.onnx")
OUT=Path("ml/research_v4_single/output/photo_consensus_evaluation.json")

def normalized_gray_photo(img, rng, stressed):
    gray=cv2.resize(255-img,(96,96),interpolation=cv2.INTER_LINEAR)
    if stressed:
        y,x=np.mgrid[0:96,0:96]
        grad=rng.uniform(-40,40)*(x/95.0-.5)+rng.uniform(-35,35)*(y/95.0-.5)
        gray=np.clip(gray.astype(np.float32)+grad+rng.normal(0,4,gray.shape),0,255).astype(np.uint8)
        if rng.random()<.6:gray=cv2.GaussianBlur(gray,(3,3),rng.uniform(.4,.9))
    return gray

def android_like_standardize(gray):
    h,w=gray.shape
    border=np.r_[gray[0],gray[-1],gray[:,0],gray[:,-1]]
    if np.median(border)<110:gray=255-gray
    otsu,_=cv2.threshold(gray,0,255,cv2.THRESH_BINARY+cv2.THRESH_OTSU)
    mask=gray<max(1,int(otsu))
    pixels=int(mask.sum())
    if not(.003<=pixels/(w*h)<=.85):return None
    yy,xx=np.nonzero(mask)
    if len(xx)==0:return None
    x0,x1=xx.min(),xx.max()+1
    y0,y1=yy.min(),yy.max()+1
    pad=max(2,int(max(x1-x0,y1-y0)*.035))
    x0=max(0,x0-pad);x1=min(w,x1+pad)
    y0=max(0,y0-pad);y1=min(h,y1+pad)
    binary=np.where(mask[y0:y1,x0:x1],255,0).astype(np.uint8)
    s=56/max(binary.shape)
    nw=max(1,round(binary.shape[1]*s));nh=max(1,round(binary.shape[0]*s))
    inner=cv2.resize(binary,(nw,nh),interpolation=cv2.INTER_AREA)
    out=np.zeros((64,64),dtype=np.uint8)
    out[(64-nh)//2:(64-nh)//2+nh,(64-nw)//2:(64-nw)//2+nw]=inner
    return out

def fix_shadows(gray):
    h,w=gray.shape
    radius=max(12,min(96,min(w,h)//6))
    border=np.r_[gray[0],gray[-1],gray[:,0],gray[:,-1]]
    source=255-gray if np.median(border)<110 else gray
    size=2*radius+1
    # boxFilter border behavior differs very slightly from clipped integral
    # normalization in Kotlin; accepted as an approximate regression test.
    mean=cv2.boxFilter(source.astype(np.float32),-1,(size,size),normalize=True,
                       borderType=cv2.BORDER_REPLICATE)
    return np.clip(source.astype(np.float32)-mean+212,0,255).astype(np.uint8)

def classify(session, photo):
    variants=[photo,fix_shadows(photo),cv2.medianBlur(photo,3)]
    arrays=[android_like_standardize(z) for z in variants]
    pred=[]
    for im in arrays:
        if im is None:
            pred.append(None)
        else:
            x=v4.tensorize([im],160).numpy()
            out=session.run(["logits"],{"image":x})[0][0]
            pred.append(int(np.argmax(out)))
    return pred

def evaluate(session, rows, seed, stressed=False, unknown=False):
    rng=np.random.default_rng(seed)
    results={"total":0,"two_correct_accepted":0,"three_correct_accepted":0,
             "two_wrong_accepted":0,"three_wrong_accepted":0,
             "two_rejected":0,"three_rejected":0}
    for row in rows:
        repeats=3 if stressed else 1
        for _ in range(repeats):
            photo=normalized_gray_photo(row["image"],rng,stressed)
            raw,corr,med=classify(session,photo)
            two=raw is not None and corr is not None and raw==corr
            three=raw is not None and ((corr is not None and raw==corr) or
                                        (med is not None and raw==med))
            correct=not unknown and raw is not None and v4.SIGNS[raw]==row["sign"]
            results["total"]+=1
            for label,accepted in (("two",two),("three",three)):
                if not accepted:results[label+"_rejected"]+=1
                elif correct:results[label+"_correct_accepted"]+=1
                else:results[label+"_wrong_accepted"]+=1
    return results

def main():
    import hashlib
    digest=hashlib.sha256(ASSET.read_bytes()).hexdigest()
    assert digest=="b9bb6bcc816f9c7957792302d7a1e50aaee2a1ecf3d44b8ae9b9b493cf4efba9"
    session=ort.InferenceSession(str(ASSET),providers=["CPUExecutionProvider"])
    bysign,_=v4.data()
    train,val,test,cal_unknown,unknown=v4.partitions(bysign)
    report={
        "test_type":"Python approximation of Android preprocessing + real v4 ONNX weights",
        "model_sha256":digest,
        "sign_classes":SIGN_NUMBERS,
        "validation_clean":evaluate(session,val,7788),
        "validation_synthetic":evaluate(session,val,7788,stressed=True),
        "known_exposed_test_clean":evaluate(session,test,9922),
        "unknown_twkm_sign_test":evaluate(session,unknown,9922,unknown=True),
        "unknown_note":"Unknowns are drawn from the SAME catalogue, but have DIFFERENT sign identities",
        "independent_phone_photo_accuracy":"NOT TESTED",
        "caveat":"Synthetic perturbation and Python preprocessor are not real Android device results.",
    }
    OUT.parent.mkdir(parents=True,exist_ok=True)
    OUT.write_text(json.dumps(report,indent=2))
    for section,data in report.items():
        if isinstance(data,dict) and "total" in data:print(section,data,flush=True)
if __name__=="__main__":main()
