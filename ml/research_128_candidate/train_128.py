#!/usr/bin/env python3
"""Experimental NEW 128px TWKM classifier, with or without Kaggle pretraining.

Uses *only* verified TWKM sign IDs for the final ten-class supervision.
Kaggle's 80 U_XXXX keys are auxiliary FONT labels and never Maya readings.
Train/validation/test split by the pinned TWKM source-original SHA256; the
original 10-sign holdout has been exposed in previous v4 research.

The model is NOT deployed automatically. Android v4 is kept unchanged.
"""
import argparse, collections, copy, csv, hashlib, io, json, os, random, sys
from pathlib import Path

import cv2
import numpy as np
import torch
from torch import nn
from torch.nn import functional as F
from PIL import Image as PILImage
from datasets import Image as HFImage, load_dataset
from sklearn.metrics import confusion_matrix, precision_recall_fscore_support

sys.path.insert(0,str(Path("ml/glyph_v4").resolve()))
sys.path.insert(0,str(Path("ml/research_v4_single").resolve()))
import train_v4 as v4
import retrain_v4 as r4
import train_font_transfer_v4 as fonts

SEED=20261115
SIGNS=v4.SIGNS
MEAN=torch.tensor([.485,.456,.406],dtype=torch.float32)[:,None,None]
STD=torch.tensor([.229,.224,.225],dtype=torch.float32)[:,None,None]
torch.manual_seed(SEED)
np.random.seed(SEED)
random.seed(SEED)
torch.set_num_threads(min(4,os.cpu_count() or 2))

def canonical(gray,side):
    """Normalise the RAW TWKM source image at 64 or 128, retaining fine strokes."""
    if gray.ndim!=2 or gray.dtype!=np.uint8 or min(gray.shape)<25:
        raise ValueError("Invalid glyph source image")
    border=np.r_[gray[0],gray[-1],gray[:,0],gray[:,-1]]
    oriented=255-gray if float(np.median(border))<110 else gray
    threshold,_=cv2.threshold(oriented,0,255,cv2.THRESH_BINARY+cv2.THRESH_OTSU)
    mask=oriented<max(1,int(threshold))
    coverage=float(np.mean(mask))
    if coverage<.003:  # correct Otsu equality for clean two-tone images
        mask=oriented<=int(threshold)
        coverage=float(np.mean(mask))
    if not (.003<=coverage<=.85) or np.count_nonzero(mask)<30:
        raise ValueError("No isolated ink; cannot assign a label")
    ys,xs=np.nonzero(mask)
    x0,x1,y0,y1=xs.min(),xs.max()+1,ys.min(),ys.max()+1
    pad=max(2,int(max(x1-x0,y1-y0)*.035))
    x0,x1=max(0,int(x0)-pad),min(gray.shape[1],int(x1)+pad)
    y0,y1=max(0,int(y0)-pad),min(gray.shape[0],int(y1)+pad)
    crop=(mask[y0:y1,x0:x1].astype(np.uint8)*255)
    inner=side-16 if side>=128 else side-8
    s=inner/max(crop.shape)
    nw,nh=max(1,round(crop.shape[1]*s)),max(1,round(crop.shape[0]*s))
    resized=cv2.resize(crop,(nw,nh),interpolation=cv2.INTER_AREA if s<1 else cv2.INTER_LINEAR)
    output=np.zeros((side,side),dtype=np.uint8)
    output[(side-nh)//2:(side-nh)//2+nh,(side-nw)//2:(side-nw)//2+nw]=resized
    return output

def raw_twkm(rows):
    needed={r["sha"] for r in rows}
    found={}
    ds=load_dataset(v4.SOURCE,"graphs",split="train",revision=v4.REV,
        cache_dir=os.environ.get("HF_DATASETS_CACHE","/tmp/maya-v4-hf")).cast_column(
        "image",HFImage(decode=False))
    for row in ds:
        item=row.get("image")
        if not item:continue
        blob=item.get("bytes")
        if blob is None and item.get("path"):blob=Path(item["path"]).read_bytes()
        if not blob:continue
        sha=hashlib.sha256(blob).hexdigest()
        if sha not in needed:continue
        with PILImage.open(io.BytesIO(blob)) as pil:
            found[sha]=cv2.cvtColor(np.array(pil.convert("RGB")),cv2.COLOR_RGB2GRAY)
    if set(found)!=needed:
        raise RuntimeError("Missing "+str(len(needed-set(found)))+" TWKM images")
    return found

def tensor(images,side):
    values=[]
    for mask in images:
        if mask.shape!=(side,side):
            raise ValueError("Canonical resolution mismatch")
        white=255-mask
        arr=torch.from_numpy(white.copy()).float().div_(255).repeat(3,1,1)
        values.append((arr-MEAN)/STD)
    return torch.stack(values)

def augment(mask,rng):
    side=mask.shape[0]
    transform=cv2.getRotationMatrix2D((side/2,side/2),
        float(rng.uniform(-9,9)),float(rng.uniform(.92,1.08)))
    transform[0,2]+=float(rng.uniform(-4,4))
    transform[1,2]+=float(rng.uniform(-4,4))
    result=cv2.warpAffine(mask,transform,(side,side),flags=cv2.INTER_LINEAR,
        borderMode=cv2.BORDER_CONSTANT,borderValue=0)
    if rng.random()<.30:
        result=cv2.GaussianBlur(result,(3,3),float(rng.uniform(.3,.7)))
    if rng.random()<.22:
        result=np.clip(result.astype(np.float32)+rng.normal(0,4,result.shape),0,255).astype(np.uint8)
    return result

def photo_variant(gray,rng):
    h,w=gray.shape
    y,x=np.mgrid[0:h,0:w]
    sh=rng.uniform(-45,45)*(x/max(1,w-1)-.5)
    sh+=rng.uniform(-38,38)*(y/max(1,h-1)-.5)
    pixel=gray.astype(np.float32)*rng.uniform(.8,1.2)+sh+rng.normal(0,5,gray.shape)
    pixel=np.clip(pixel,0,255).astype(np.uint8)
    if rng.random()<.5:
        pixel=cv2.GaussianBlur(pixel,(3,3),float(rng.uniform(.4,.9)))
    return pixel

def infer(model,canonical_imgs,side):
    predictions=[]
    model.eval()
    with torch.inference_mode():
        for i in range(0,len(canonical_imgs),16):
            a=tensor(canonical_imgs[i:i+16],side)
            predictions.append(model(a).numpy())
    return np.concatenate(predictions,axis=0)

def evaluate(model,rows,raw,canonical_side,input_side,stress=False,seed=1412):
    rng=np.random.default_rng(seed)
    images=[];truth=[];graph=[]
    for record in rows:
        for _ in range(3 if stress else 1):
            gray=photo_variant(raw[record["sha"]],rng) if stress else raw[record["sha"]]
            images.append(canonical(gray,canonical_side))
            truth.append(SIGNS.index(record["sign"]) if record["sign"] in SIGNS else -1)
            graph.append(record["graph"])
    # Resize canonical images ONLY for the existing baseline model input size.
    if canonical_side!=input_side:
        images=[cv2.resize(im,(input_side,input_side),interpolation=cv2.INTER_LINEAR) for im in images]
    scores=infer(model,images,input_side)
    rank=np.argsort(-scores,axis=1)
    labels=np.array(truth,dtype=np.int64)
    top=rank[:,0]
    probabilities=np.exp(scores-scores.max(axis=1,keepdims=True))
    probabilities=probabilities/probabilities.sum(axis=1,keepdims=True)
    info={"n":len(labels),"top1":int(np.sum(top==labels)),
        "top3":int(sum(labels[i] in rank[i,:3] for i in range(len(labels)))),
        "relative_top1_softmax_median":float(np.median(probabilities[np.arange(len(labels)),top]))}
    if np.all(labels>=0):
        p,r,f,_=precision_recall_fscore_support(labels,top,labels=np.arange(10),zero_division=0)
        info.update({"accuracy":float(np.mean(top==labels)),
            "macro_precision":float(np.mean(p)),
            "macro_recall":float(np.mean(r)),"macro_f1":float(np.mean(f)),
            "confusion":confusion_matrix(labels,top,labels=np.arange(10)).tolist(),
            "per_sign":[{"twkm":SIGNS[i],"precision":float(p[i]),
                "recall":float(r[i]),"f1":float(f[i]),
                "support":int(np.sum(labels==i))} for i in range(10)],
            "mistakes":[{"graph":graph[i],"actual":SIGNS[int(labels[i])],
                         "predicted":SIGNS[int(top[i])],
                         "runner_up":SIGNS[int(rank[i,1])]} for i in range(len(labels)) if top[i]!=labels[i]]})
    else:
        info.update({"all_predictions_forced_closed_set":True,
            "unknown_signs_rejected":0})
    return info

def font_pretrain(samples,nclasses,epochs,seed):
    model=r4.build()
    model.classifier=nn.Linear(576,nclasses)
    for p in model.parameters():p.requires_grad=False
    for p in model.features[-4:].parameters():p.requires_grad=True
    for p in model.classifier.parameters():p.requires_grad=True
    optimizer=torch.optim.AdamW((p for p in model.parameters() if p.requires_grad),lr=9e-5,weight_decay=.002)
    rng=np.random.default_rng(seed)
    history=[]
    for epoch in range(1,epochs+1):
        ids=rng.permutation(len(samples))
        model.train()
        for m in model.modules():
            if isinstance(m,nn.BatchNorm2d):m.eval()
        losses=[]
        for offset in range(0,len(ids),32):
            indices=ids[offset:offset+32]
            masks=[cv2.resize(samples[int(j)][0],(128,128),interpolation=cv2.INTER_LINEAR) for j in indices]
            labels=torch.tensor([samples[int(j)][1] for j in indices],dtype=torch.long)
            optimizer.zero_grad(set_to_none=True)
            logits=model(tensor(masks,128))
            loss=F.cross_entropy(logits,labels,label_smoothing=.04)
            loss.backward()
            nn.utils.clip_grad_norm_(model.parameters(),2.0)
            optimizer.step()
            losses.append(float(loss.detach()))
        hist={"epoch":epoch,"font_key_training_loss":float(np.mean(losses))}
        history.append(hist)
        print("font-key epoch",epoch,"loss",round(hist["font_key_training_loss"],4),flush=True)
    model.eval()
    return model,history

def finetune(model,train,val,raw,epochs,seed):
    for p in model.parameters():p.requires_grad=False
    for p in model.features[-4:].parameters():p.requires_grad=True
    for p in model.classifier.parameters():p.requires_grad=True
    opt=torch.optim.AdamW((p for p in model.parameters() if p.requires_grad),lr=3e-5,weight_decay=.003)
    rng=np.random.default_rng(seed)
    originals=[canonical(raw[row["sha"]],128) for row in train]
    indices=[SIGNS.index(row["sign"]) for row in train]
    best_state=None;best=(-1,-1,-1.0);history=[];bad=0
    for epoch in range(1,epochs+1):
        masks=[];ys=[]
        for m,label in zip(originals,indices):
            masks.append(m);ys.append(label)
            for _ in range(4):
                masks.append(augment(m,rng));ys.append(label)
        order=rng.permutation(len(masks))
        model.train()
        for layer in model.modules():
            if isinstance(layer,nn.BatchNorm2d):layer.eval()
        losses=[]
        for i in range(0,len(order),20):
            subset=order[i:i+20]
            images=tensor([masks[int(j)] for j in subset],128)
            y=torch.tensor([ys[int(j)] for j in subset],dtype=torch.long)
            opt.zero_grad(set_to_none=True)
            logits=model(images)
            loss=F.cross_entropy(logits,y,label_smoothing=.05)
            loss.backward()
            nn.utils.clip_grad_norm_(model.parameters(),1.5)
            opt.step()
            losses.append(float(loss.detach()))
        model.eval()
        clean=evaluate(model,val,raw,128,128,seed=20261116)
        stressed=evaluate(model,val,raw,128,128,stress=True,seed=20261116)
        key=(clean["top1"],stressed["top1"],clean["macro_f1"])
        history.append({"epoch":epoch,"training_loss":float(np.mean(losses)),
            "validation_clean":clean["top1"],"validation_stress":stressed["top1"]})
        print("TWKM epoch",epoch,"clean",clean["top1"],"/10",
              "synthetic",stressed["top1"],"/30",flush=True)
        if key>best:
            best=key;best_state=copy.deepcopy(model.state_dict());bad=0
        else:bad+=1
        if bad>=5:break
    model.load_state_dict(best_state)
    model.eval()
    return model,history

def export_onnx(model,target):
    import onnx
    import onnxruntime as ort
    class Graph(nn.Module):
        def __init__(self,net):
            super().__init__()
            self.net=net
        def forward(self,x):
            features=self.net.avgpool(self.net.features(x)).flatten(1)
            return self.net.classifier(features),F.normalize(features,p=2,dim=1)
    g=Graph(model).eval()
    inp=torch.randn(1,3,128,128)
    with torch.inference_mode():
        logits,features=g(inp)
    torch.onnx.export(g,inp,str(target),opset_version=17,
        input_names=["image"],output_names=["logits","features"],
        dynamic_axes={"image":{0:"batch"},"logits":{0:"batch"},"features":{0:"batch"}},
        dynamo=False)
    onnx.checker.check_model(onnx.load(str(target)))
    sess=ort.InferenceSession(str(target),providers=["CPUExecutionProvider"])
    a,b=sess.run(["logits","features"],{"image":inp.numpy()})
    error=max(float(np.abs(a-logits.numpy()).max()),
              float(np.abs(b-features.numpy()).max()))
    assert error<2e-4,("ONNX PyTorch parity failure",error)
    return {"sha256":hashlib.sha256(target.read_bytes()).hexdigest(),
            "bytes":target.stat().st_size,"max_abs_error":error}

def write_csv(path,stats):
    with path.open("w",encoding="utf-8",newline="") as f:
        writer=csv.writer(f)
        writer.writerow(["true / predicted"]+SIGNS)
        for i,row in enumerate(stats["confusion"]):
            writer.writerow([SIGNS[i]]+row)

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--kaggle-dir",required=True)
    ap.add_argument("--output",default="ml/research_128_candidate/out")
    ap.add_argument("--font-epochs",type=int,default=3)
    ap.add_argument("--twkm-epochs",type=int,default=9)
    ap.add_argument("--per-style",type=int,default=20)
    args=ap.parse_args()
    out=Path(args.output);out.mkdir(exist_ok=True,parents=True)

    samples,keys,kaggmeta=fonts.kaggle_font_train_samples(Path(args.kaggle_dir),per_style=args.per_style)
    assert len(keys)==80,len(keys)
    print("Kaggle deduplicated font-key pretraining",kaggmeta,flush=True)
    bysign,_=v4.data()
    train,val,exposed_test,uncal,untest=v4.partitions(bysign)
    raw=raw_twkm(train+val+exposed_test+untest)
    print("TWKM original drawing counts",len(train),len(val),len(exposed_test),flush=True)

    baseline=r4.build()
    baseline_clean=evaluate(baseline,val,raw,64,160,seed=20261116)
    baseline_stress=evaluate(baseline,val,raw,64,160,stress=True,seed=20261116)
    print("existing v4 baseline",baseline_clean["top1"],"/10 and",baseline_stress["top1"],"/30",flush=True)

    # Control: identical 128px supervised training, but WITHOUT Kaggle pretraining.
    control,control_history=finetune(r4.build(),train,val,raw,args.twkm_epochs,SEED+50)
    control_clean=evaluate(control,val,raw,128,128,seed=20261116)
    control_stress=evaluate(control,val,raw,128,128,stress=True,seed=20261116)
    torch.save({"state_dict":control.state_dict(),"signs":SIGNS,"input_side":128},
               out/"twkm_128_no_kaggle.pt")

    # Two-stage: all 80 font key classes are DIFFERENT from TWKM sign numbers.
    pretrained,font_history=font_pretrain(samples,len(keys),args.font_epochs,SEED+100)
    transfer=r4.build()
    # Transfer only the deepest two feature modules; keep the TWKM sign head.
    for i in (-2,-1):
        transfer.features[i].load_state_dict(pretrained.features[i].state_dict())
    transfer,transfer_history=finetune(transfer,train,val,raw,args.twkm_epochs,SEED+50)
    transfer_clean=evaluate(transfer,val,raw,128,128,seed=20261116)
    transfer_stress=evaluate(transfer,val,raw,128,128,stress=True,seed=20261116)
    torch.save({"state_dict":transfer.state_dict(),"signs":SIGNS,"input_side":128,
                "font_pretraining_keys":keys},out/"kaggle_twkm_128_candidate.pt")

    def short(a):
        return {k:a[k] for k in ["n","top1","top3","accuracy","macro_precision",
                "macro_recall","macro_f1","mistakes"]}
    baseline_exposed=evaluate(baseline,exposed_test,raw,64,160,seed=20261116)
    control_exposed=evaluate(control,exposed_test,raw,128,128,seed=20261116)
    transfer_exposed=evaluate(transfer,exposed_test,raw,128,128,seed=20261116)
    unknown_results={n:evaluate(net,untest,raw,cs,side,seed=20261116)
         for n,net,cs,side in [("existing_v4",baseline,64,160),
                              ("128_no_kaggle",control,128,128),
                              ("128_kaggle_transfer",transfer,128,128)]}
    for name,model,clean in [
        ("128_no_kaggle",control,control_clean),
        ("128_kaggle_transfer",transfer,transfer_clean)]:
        write_csv(out/(name+"_validation_confusion.csv"),clean)
        write_csv(out/(name+"_exposed_test_confusion.csv"),
                  control_exposed if name=="128_no_kaggle" else transfer_exposed)

    control_meta=export_onnx(control,out/"twkm_128_no_kaggle.onnx")
    transfer_meta=export_onnx(transfer,out/"kaggle_twkm_128_candidate.onnx")
    # Predeclared validation rule. EXPOSED historical test is descriptive only.
    scores={"existing_v4":(baseline_clean["top1"],baseline_stress["top1"]),
            "128_no_kaggle":(control_clean["top1"],control_stress["top1"]),
            "128_kaggle_transfer":(transfer_clean["top1"],transfer_stress["top1"])}
    winner=max(scores,key=lambda k:scores[k])
    eligible=(winner!="existing_v4" and scores[winner][0]>=scores["existing_v4"][0]
              and scores[winner][1]>=scores["existing_v4"][1]+2)
    report={
      "experiment":"128px Kaggle font-feature pretraining then TWKM ten-sign finetuning",
      "seed":SEED,
      "kaggle":kaggmeta,
      "kaggle_font_classes":keys,
      "note_on_kaggle_labels":"U_XXXX keys are font codes, NOT verified Maya/TWKM sign identity or English translations.",
      "pinned_twkm_revision":v4.REV,
      "twkm_sign_ids":SIGNS,
      "train_originals":len(train),"validation_originals":len(val),
      "exposed_known_test_originals":len(exposed_test),"unknown_test_originals":len(untest),
      "split_source_sha_disjoint":True,
      "model_architecture":"MobileNetV3-Small 128x128 ten-class CNN",
      "baseline_existing_v4":{"clean":short(baseline_clean),"synthetic":short(baseline_stress),
                              "exposed_test":short(baseline_exposed)},
      "control_128_without_kaggle":{"clean":short(control_clean),"synthetic":short(control_stress),
                             "exposed_test":short(control_exposed),"onnx":control_meta},
      "candidate_128_with_kaggle":{"clean":short(transfer_clean),"synthetic":short(transfer_stress),
                             "exposed_test":short(transfer_exposed),"onnx":transfer_meta},
      "unknown_signs_closed_set":{"note":"All unsupported TWKM signs still forced into one of ten labels; no validated unknown detector.",
           "median_top1_softmax":{k:float(v["relative_top1_softmax_median"]) for k,v in unknown_results.items()}},
      "winner_by_validation":winner,"passes_predeclared_improvement_gate":eligible,
      "candidate_deployed_to_android":False,
      "why_not_deployed":"No independent labelled real phone-photo test or Android 128px preprocessing parity test.",
      "real_phone_photo_accuracy":"NOT MEASURED",
      "historical_test_warning":"Ten test TWKM images have been used in previous v4 research and are NOT a new blind test.",
      "kaggle_distribution":"Research-only artifacts, verify licensing before distributing weights externally.",
      "font_training_history":font_history,
      "control_training_history":control_history,
      "transfer_training_history":transfer_history}
    (out/"evaluation.json").write_text(json.dumps(report,indent=2),encoding="utf-8")
    print("FINAL EVALUATION",json.dumps({
       "existing_v4":scores["existing_v4"],"128_no_kaggle":scores["128_no_kaggle"],
       "128_kaggle_transfer":scores["128_kaggle_transfer"],
       "winner":winner,"passes_gate":eligible,
       "exposed_test_128_transfer":transfer_exposed["top1"],
    },indent=2),flush=True)

if __name__=="__main__": main()
