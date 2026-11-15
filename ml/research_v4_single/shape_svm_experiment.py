#!/usr/bin/env python3
"""Research-only HOG/shape SVM complementary classifier for Maya TWKM glyphs.

Uses the predeclared source-hash separated TWKM training originals (n=45),
validation originals (n=10) and already EXPOSED test originals (n=10).
Source drawings are taken directly from the pinned TWKM snapshot. Candidate
feature sizes / regularisation are selected on TRAIN-only 3-fold original-
grouped CV; held-out validation/test images are never used in fitting.

Not a verified unknown-sign classifier or real-world camera photo model.
No Android model assets are overwritten.
"""
from __future__ import annotations
import csv,hashlib,io,json,os,random,sys
from pathlib import Path
import cv2,numpy as np
from PIL import Image as PILImage
from datasets import Image as HFImage,load_dataset
from sklearn.model_selection import StratifiedKFold
from sklearn.metrics import accuracy_score, f1_score,confusion_matrix,precision_recall_fscore_support
from sklearn.svm import SVC
from sklearn.preprocessing import Normalizer
from sklearn.pipeline import make_pipeline
from skimage.feature import hog
import joblib

sys.path.insert(0,str(Path("ml/glyph_v4").resolve()))
import train_v4 as v4
SIGNS=v4.SIGNS
SEED=20261115
OUT=Path("ml/research_v4_single/shape_svm_results")
random.seed(SEED)
np.random.seed(SEED)

def source_grays(all_rows):
    hashes={r["sha"] for r in all_rows}
    result={}
    ds=load_dataset(v4.SOURCE,"graphs",split="train",revision=v4.REV,
        cache_dir=os.environ.get("HF_DATASETS_CACHE","/tmp/maya-v4-hf")).cast_column(
        "image",HFImage(decode=False))
    for item in ds:
        im=item.get("image")
        if not im:continue
        raw=im.get("bytes")
        if raw is None and im.get("path"):raw=Path(im["path"]).read_bytes()
        if not raw:continue
        digest=hashlib.sha256(raw).hexdigest()
        if digest not in hashes:continue
        with PILImage.open(io.BytesIO(raw)) as pil:
            rgb=np.asarray(pil.convert("RGB"))
            result[digest]=cv2.cvtColor(rgb,cv2.COLOR_RGB2GRAY)
    missing=hashes-set(result)
    if missing:raise RuntimeError("Missing "+str(len(missing))+" source SHA images")
    return result

def canonical(gray,side):
    if gray.ndim!=2 or gray.dtype!=np.uint8 or min(gray.shape)<25:
        raise ValueError("Invalid TWKM drawing")
    border=np.r_[gray[0],gray[-1],gray[:,0],gray[:,-1]]
    light=255-gray if float(np.median(border))<110 else gray
    threshold,_=cv2.threshold(light,0,255,cv2.THRESH_BINARY+cv2.THRESH_OTSU)
    mask=light<max(1,int(threshold))
    if mask.mean()<.003:mask=light<=int(threshold)
    coverage=float(mask.mean())
    if not (.003<=coverage<=.85):
        raise ValueError("No valid glyph ink")
    y,x=np.nonzero(mask)
    left,right,top,bottom=int(x.min()),int(x.max())+1,int(y.min()),int(y.max())+1
    margin=max(2,int(max(right-left,bottom-top)*.035))
    left,top=max(0,left-margin),max(0,top-margin)
    right,bottom=min(gray.shape[1],right+margin),min(gray.shape[0],bottom+margin)
    crop=(mask[top:bottom,left:right].astype(np.uint8)*255)
    target=side-8 if side==64 else side-16
    factor=target/max(crop.shape)
    w,h=max(1,round(crop.shape[1]*factor)),max(1,round(crop.shape[0]*factor))
    small=cv2.resize(crop,(w,h),interpolation=cv2.INTER_AREA if factor<1 else cv2.INTER_LINEAR)
    canvas=np.zeros((side,side),dtype=np.uint8)
    canvas[(side-h)//2:(side-h)//2+h,(side-w)//2:(side-w)//2+w]=small
    return canvas

def augment(canvas,seed):
    rng=np.random.default_rng(seed)
    side=len(canvas)
    M=cv2.getRotationMatrix2D((side/2,side/2),float(rng.uniform(-8,8)),float(rng.uniform(.94,1.06)))
    M[0,2]+=float(rng.uniform(-3,3))*side/64.0
    M[1,2]+=float(rng.uniform(-3,3))*side/64.0
    result=cv2.warpAffine(canvas,M,(side,side),borderMode=cv2.BORDER_CONSTANT,borderValue=0)
    if rng.random()<.2:result=cv2.GaussianBlur(result,(3,3),.5)
    return result

def vector(canvas,mode):
    side=canvas.shape[0]
    cell=8
    gradient=hog(canvas.astype(np.float32)/255,
        orientations=9,pixels_per_cell=(cell,cell),
        cells_per_block=(2,2),block_norm="L2-Hys",feature_vector=True)
    if mode.endswith("pixels"):
        low=cv2.resize(canvas,(32,32),interpolation=cv2.INTER_AREA).astype(np.float32)/255
        # Combine shape and coarse stroke layout; scale both groups so
        # a large pixel vector does not swamp normalized gradient features.
        gradient=gradient/max(1e-8,np.linalg.norm(gradient))
        pixel=low.reshape(-1);pixel=pixel/max(1e-8,np.linalg.norm(pixel))
        gradient=np.concatenate((gradient,pixel))
    return gradient.astype(np.float32)

def prepare(rows,raw,mode,views=1):
    side=128 if mode.startswith("128") else 64
    all_vectors=[];signs=[];hashes=[]
    for row in rows:
        orig=canonical(raw[row["sha"]],side)
        for k in range(views):
            img=orig if k==0 else augment(orig,int(row["sha"][:12],16)+SEED+31*k)
            all_vectors.append(vector(img,mode))
            signs.append(SIGNS.index(row["sign"]) if row["sign"] in SIGNS else -1)
            hashes.append(row["sha"])
    return np.stack(all_vectors),np.asarray(signs),hashes

def train_one(X,y,kernel,C):
    return make_pipeline(Normalizer(),SVC(kernel=kernel,C=C,gamma="scale",
        decision_function_shape="ovr",break_ties=False)).fit(X,y)

def predicted(model,X):
    return model.predict(X).astype(np.int64)

def cross_validate(feature,train,raw,kernel,C):
    X,y,_=prepare(train,raw,feature,views=4)
    groups=np.asarray([r["sign"] for r in train])
    cv=StratifiedKFold(n_splits=3,shuffle=True,random_state=SEED)
    oof=np.full(len(train),-1,dtype=int)
    for tri,vali in cv.split(np.zeros(len(train)),groups):
        ids=np.concatenate([np.arange(i*4,(i+1)*4) for i in tri])
        model=train_one(X[ids],y[ids],kernel,C)
        pred=predicted(model,X[vali*4])
        oof[vali]=pred
    assert np.all(oof>=0)
    truth=np.asarray([SIGNS.index(r["sign"]) for r in train])
    return {"accuracy":float((oof==truth).mean()),
       "macro_f1":float(f1_score(truth,oof,labels=list(range(10)),average="macro",zero_division=0)),
       "correct":int((oof==truth).sum()),"total":len(truth),
       "tok_correct":int(sum(oof[i]==SIGNS.index(44) for i,r in enumerate(train) if r["sign"]==44)),
       "tok_total":sum(r["sign"]==44 for r in train)}

def evaluation(model,rows,raw,feature):
    X,y,_=prepare(rows,raw,feature)
    pred=predicted(model,X)
    precision,recall,f1,support=precision_recall_fscore_support(
        y,pred,labels=list(range(10)),zero_division=0)
    cm=confusion_matrix(y,pred,labels=list(range(10)))
    return {
        "correct":int((pred==y).sum()),"total":len(y),
        "accuracy":float((pred==y).mean()),
        "macro_precision":float(np.mean(precision)),
        "macro_recall":float(np.mean(recall)),
        "macro_f1":float(np.mean(f1)),
        "confusion":cm.tolist(),
        "per_sign":[{"sign":SIGNS[i],"precision":float(precision[i]),
            "recall":float(recall[i]),"f1":float(f1[i]),
            "count":int(support[i])} for i in range(10)],
        "misclassifications":[{"graph":r["graph"],"actual":r["sign"],
            "predicted":SIGNS[int(p)]} for r,p,t in zip(rows,pred,y) if p!=t]}

def write_matrix(data,path):
    with path.open("w",newline="",encoding="utf-8") as f:
        w=csv.writer(f)
        w.writerow(["Actual / Predicted"]+SIGNS)
        for sign,row in zip(SIGNS,data["confusion"]):
            w.writerow([sign]+row)

def main():
    bysign,_=v4.data()
    train,val,test,uncal,untest=v4.partitions(bysign)
    raw=source_grays(train+val+test+uncal+untest)
    grid=[]
    for mode in ("64_hog","64_hog_pixels","128_hog","128_hog_pixels"):
        for kernel in ("linear","rbf"):
            for C in (0.1,1,10):
                result=cross_validate(mode,train,raw,kernel,C)
                print("TRAIN GROUPED CV",mode,kernel,C,
                    result["correct"],"/",result["total"],result["tok_correct"],
                    "/"+str(result["tok_total"]),flush=True)
                grid.append({"feature":mode,"kernel":kernel,"C":C,"cv":result})
    # Select hyperparameters from training original-grouped CV ONLY.
    chosen=max(grid,key=lambda x:(x["cv"]["macro_f1"],x["cv"]["accuracy"],
        -x["C"],-int(x["feature"].startswith("128"))))
    mode,kernel,C=chosen["feature"],chosen["kernel"],chosen["C"]
    X,y,_=prepare(train,raw,mode,views=4)
    clf=train_one(X,y,kernel,C)
    val_stats=evaluation(clf,val,raw,mode)
    old_test=evaluation(clf,test,raw,mode)
    # Unsupported sign IDs are entirely disjoint from training/validation
    # sign IDs. Closed-set SVM cannot reject these by itself.
    UX,_,_=prepare(untest,raw,mode)
    unknown_guesses=predicted(clf,UX)
    OUT.mkdir(parents=True,exist_ok=True)
    write_matrix(val_stats,OUT/"validation_confusion.csv")
    write_matrix(old_test,OUT/"exposed_test_confusion.csv")
    report={
       "experiment":"TWKM few-shot HOG and stroke-shape SVM trained on source originals",
       "twkm_source":v4.SOURCE,"revision":v4.REV,"seed":SEED,
       "known_sign_ids":SIGNS,"train_originals":len(train),
       "originals_per_sign":{str(s):sum(r["sign"]==s for r in train) for s in SIGNS},
       "selection":"TRAIN-only stratified 3-fold grouped originals, augment AFTER splitting",
       "best_train_cv":chosen,
       "all_train_cv":grid,
       "known_validation":val_stats,
       "exposed_historical_known_test":old_test,
       "unknown_signs_test":{"count":len(untest),
          "forced_guesses":len(unknown_guesses),
          "reliable_rejection_available":False},
       "baseline_v4_clean_val":8,
       "baseline_v4_exposed_known_test":7,
       "independent_real_phone_photo_accuracy":"NOT MEASURED",
       "test_not_blind":"Historical 10-sign test was previously exposed in original v4 development",
       "deployed_to_android":False
    }
    (OUT/"results.json").write_text(json.dumps(report,indent=2),encoding="utf-8")
    joblib.dump(clf,OUT/"best_shape_classifier_research_only.joblib",compress=3)
    summary=(f"Best HOG feature: {mode} / {kernel} / C={C}\n"
      f"Training-grouped CV: {chosen['cv']['correct']}/{chosen['cv']['total']}\n"
      f"TWKM validation: {val_stats['correct']}/{val_stats['total']}\n"
      f"Previously exposed TWKM holdout: {old_test['correct']}/{old_test['total']}\n"
      f"TOK on exposed test: "+
      str(next((r["predicted"]==44 for r in old_test["misclassifications"] if r["actual"]==44),True))+
      "\nNo independent photo accuracy or unknown-sign rejection.\n")
    (OUT/"summary.txt").write_text(summary)
    print(summary,flush=True)

if __name__=="__main__":main()
