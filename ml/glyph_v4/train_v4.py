"""Train expanded TEN-sign glyph classifier using TWKM variants.

- Distinct SHA256 image originals only; cross-sign duplicate hashes excluded.
- Fixed supported signs, 1 unseen val and 1 unseen final test original per class.
- Unknown sign IDs are disjoint between threshold calibration and test.
- Both a frozen-pretrained MobileNetV3+SVM and fine-tuned MobileNetV3 are tried;
  CHOICE via the validation set ONLY. Test set evaluated once afterward.
- The metrics are on TWKM catalogue drawings, NOT independently sourced phone
  photographs, handwritten glyphs, or sentence translations.

Run: python ml/glyph_v4/train_v4.py
"""
import collections, copy, hashlib, io, json, os, random, struct, sys, time
from pathlib import Path

import cv2
import joblib
import numpy as np
import torch
from torch import nn
from torch.nn import functional as F
from torch.utils.data import DataLoader, Dataset
from torchvision import models
from datasets import Image, load_dataset
from sklearn.svm import SVC
from sklearn.metrics import accuracy_score, confusion_matrix, f1_score
from PIL import Image as PILImage

sys.path.insert(0, str(Path("ml/glyph_v2").resolve()))
import train as v2
import train_transfer as tr

ROOT=Path("ml/glyph_v4")
OUT=ROOT/"outputs"
OUT.mkdir(parents=True,exist_ok=True)
SEED=20261114
SOURCE=v2.REPO
REV=v2.REV
# Predeclared before drawing any held-out test original:
SIGNS=[32,184,291,4,44,507,548,122,187,1030]
LABELS=["K'UH | god/deity","K'INICH | resplendent/sunny","SIIP | deity name",
        "NAAH | house/first","TOK | cloud/mist","TZIMAH | gourd",
        "HAAB | year","K'AK' | fire","K'ABA' | name","K'AWIIL | deity name"]
assert len(set(SIGNS))==10
SIDE=160
VIEWS=4
EPOCHS=14
PAT=5
BATCH=16
torch.manual_seed(SEED)
np.random.seed(SEED)
random.seed(SEED)
torch.set_num_threads(min(4,os.cpu_count() or 2))

def data():
    ds=load_dataset(SOURCE,"graphs",split="train",revision=REV,
        cache_dir=os.environ.get("HF_DATASETS_CACHE","/tmp/maya-v4-hf")).cast_column("image",Image(decode=False))
    bysha=collections.defaultdict(list)
    for row in ds:
        image=row.get("image")
        if not image:continue
        raw=image.get("bytes")
        if raw is None and image.get("path"):raw=Path(image["path"]).read_bytes()
        if raw: bysha[hashlib.sha256(raw).hexdigest()].append(row)
    bysign=collections.defaultdict(list)
    collisions=0
    for sha,rows in bysha.items():
        codes={str(x.get("sign_no")) for x in rows}
        if len(codes)!=1:
            collisions+=1
            continue
        r=rows[0]
        try:s=int(r["sign_no"])
        except (TypeError,ValueError):continue
        try:im=v2.process(r["image"])
        except Exception:continue
        bysign[s].append({"sign":s,"graph":str(r["graph_no"]),"sha":sha,"image":im})
    print("Total distinct parsed signs",len(bysign),"cross-sign sha collisions",collisions,flush=True)
    return bysign,collisions

def partitions(bysign):
    rnd=random.Random(SEED)
    train=[];val=[];test=[]
    for sign in SIGNS:
        group=sorted(bysign[sign],key=lambda x:x["sha"])
        assert len(group)>=5, f"Need >=5 originals for sign {sign}; got {len(group)}"
        rnd.shuffle(group)
        test.append(group[0])
        val.append(group[1])
        train.extend(group[2:])
    # choose disjoint sign identities for reject calibration/test.
    eligible=sorted(s for s,g in bysign.items() if s not in SIGNS and len(g)>=2)
    rnd.shuffle(eligible)
    assert len(eligible)>=80,len(eligible)
    calIDs=eligible[:40];testIDs=eligible[40:80]
    negcal=[r for sign in calIDs for r in sorted(bysign[sign],key=lambda x:x["sha"])[:2]]
    negtest=[r for sign in testIDs for r in sorted(bysign[sign],key=lambda x:x["sha"])[:2]]
    assert len(negcal)==80 and len(negtest)==80
    keys=[r["sha"] for r in train+val+test+negcal+negtest]
    assert len(keys)==len(set(keys)), "Original-image leakage between splits"
    def summary(rows):return [{"graph":r["graph"],"sign":r["sign"],"sha256":r["sha"]} for r in rows]
    manifest={"source":SOURCE,"revision":REV,"seed":SEED,"supported_sign_numbers":SIGNS,
      "labels":LABELS,"training":summary(train),"validation":summary(val),"test":summary(test),
      "calibration_unknowns":summary(negcal),"test_unknowns":summary(negtest),
      "unknown_calibration_sign_ids":calIDs,"unknown_test_sign_ids":testIDs,
      "sources_do_not_overlap":True,
      "limitations":"All samples are within the same standardized TWKM catalogue; no independent phone photos"}
    (OUT/"split_manifest.json").write_text(json.dumps(manifest,indent=2),encoding="utf-8")
    return train,val,test,negcal,negtest

def tensorize(images,size):
    ims=[]
    for im in images:
        # preprocessor from v2: strokes=255, background=0, convert to ImageNet-like RGB.
        white=255-cv2.resize(im,(size,size),interpolation=cv2.INTER_LINEAR)
        grey=torch.from_numpy(white.copy()).float().div_(255)
        rgb=grey.repeat(3,1,1)
        rgb.sub_(torch.tensor([.485,.456,.406])[:,None,None])
        rgb.div_(torch.tensor([.229,.224,.225])[:,None,None])
        ims.append(rgb)
    return torch.stack(ims)

def pretrained():
    net=models.mobilenet_v3_small(weights=None)
    net.classifier=nn.Identity()
    net.load_state_dict(torch.load(
        "ml/glyph_v2/outputs/transfer/mobilenet_v3_small_backbone.pt",
        map_location="cpu",weights_only=True))
    net.eval()
    return net

def frozen_features(net, originals, do_aug=False):
    rng=np.random.default_rng(SEED)
    images=[]
    for r in originals:
        images.append(r["image"])
        if do_aug:images.extend(v2.augment(r["image"],rng) for _ in range(VIEWS-1))
    result=[]
    with torch.inference_mode():
        for pos in range(0,len(images),16):
            z=net(tensorize(images[pos:pos+16],224)).detach()
            result.extend(F.normalize(z,p=2,dim=1).cpu().numpy())
    return np.asarray(result,dtype=np.float32)

def unfreeze_last(net):
    for p in net.parameters():p.requires_grad=False
    for p in net.features[-3:].parameters():p.requires_grad=True
    net.classifier=nn.Linear(576,len(SIGNS))
    return net

def train_finetuned(net,trains,vals):
    optimizer=torch.optim.AdamW(
      list(p for p in net.parameters() if p.requires_grad),
      lr=2.5e-4,weight_decay=.001)
    rng=np.random.default_rng(SEED+10)
    val_t=tensorize([r["image"] for r in vals],SIDE)
    val_labels=torch.tensor([SIGNS.index(r["sign"]) for r in vals],dtype=torch.long)
    best=None
    bestval=(-1,-float("inf"))
    noimprove=0
    history=[]
    for epoch in range(1,EPOCHS+1):
        batchimgs=[];batchy=[]
        for r in trains:
            images=[r["image"]]
            images.extend(v2.augment(r["image"],rng) for _ in range(VIEWS-1))
            batchimgs.extend(images)
            batchy.extend([SIGNS.index(r["sign"])]*VIEWS)
        order=rng.permutation(len(batchimgs))
        net.train()
        # Freeze batch norm running statistics: small catalogue image batches are unstable.
        for layer in net.modules():
            if isinstance(layer,nn.BatchNorm2d):layer.eval()
        epochloss=[]
        for start in range(0,len(order),BATCH):
            ids=order[start:start+BATCH]
            X=tensorize([batchimgs[int(i)] for i in ids],SIDE)
            y=torch.tensor([batchy[int(i)] for i in ids],dtype=torch.long)
            optimizer.zero_grad(set_to_none=True)
            pred=net(X)
            loss=F.cross_entropy(pred,y,label_smoothing=.07)
            loss.backward()
            torch.nn.utils.clip_grad_norm_(net.parameters(),2.0)
            optimizer.step()
            epochloss.append(float(loss.detach()))
        net.eval()
        with torch.inference_mode():
            logits=net(val_t)
            valpred=logits.argmax(dim=1)
            va=float((valpred==val_labels).float().mean())
            vloss=float(F.cross_entropy(logits,val_labels))
        # Accuracy primary; validation loss secondary. No test images consulted.
        candidate=(va,-vloss)
        if candidate>bestval:
            bestval=candidate
            best=copy.deepcopy(net.state_dict())
            noimprove=0
        else:noimprove+=1
        history.append({"epoch":epoch,"train_loss":round(float(np.mean(epochloss)),5),
          "val_accuracy":round(va,5),"val_loss":round(vloss,5)})
        print("fine tune epoch",epoch,"train",round(np.mean(epochloss),3),
              "val accuracy",round(va,3),"val loss",round(vloss,3),flush=True)
        if noimprove>=PAT:break
    assert best is not None
    net.load_state_dict(best)
    net.eval()
    return net,history

def ft_infer(net,rows):
    images=[r["image"] for r in rows]
    logits=[];features=[]
    with torch.inference_mode():
        for pos in range(0,len(images),16):
            x=tensorize(images[pos:pos+16],SIDE)
            z=net.features(x)
            z=net.avgpool(z).flatten(1)
            features.append(F.normalize(z,p=2,dim=1).numpy())
            logits.append(net.classifier(z).numpy())
    l=np.concatenate(logits,axis=0)
    return np.argmax(l,axis=1).astype(int), np.concatenate(features,axis=0).astype(np.float32)

def similarity(predicted, query, train_gallery, train_y):
    mat=query@train_gallery.T
    own=np.zeros(len(predicted),dtype=np.float32)
    dif=np.zeros(len(predicted),dtype=np.float32)
    for i,j in enumerate(predicted):
        mask=train_y==j
        own[i]=np.max(mat[i,mask])
        dif[i]=np.max(mat[i,~mask])
    return own,own-dif

def tune(pos_pred,pos_feats,pos_true,neg_pred,neg_feats,training_features,training_labels):
    posown,posmargin=similarity(pos_pred,pos_feats,training_features,training_labels)
    negown,negmargin=similarity(neg_pred,neg_feats,training_features,training_labels)
    # Fixed grid and minimum >=30% correct known accepted on VALIDATION.
    choices=[]
    for sim in np.arange(.60,.991,.01):
        for margin in np.arange(-.10,.251,.02):
            accepted=(posown>=sim)&(posmargin>=margin)
            good=np.mean(accepted&(pos_pred==pos_true))
            rejected=np.mean((negown<sim)|(negmargin<margin))
            if good>=.30:
                choices.append((.5*(good+rejected),good,rejected,float(sim),float(margin)))
    if not choices:
        # No threshold can preserve the minimum known validation acceptance.
        return {"selected":False,"reason":"No threshold meets >=30% correctly-accepted known validation examples"}
    chosen=max(choices,key=lambda c:(c[0],c[1],c[2],c[3]))
    return {"selected":True,"similarity_threshold":round(chosen[3],5),
      "margin_threshold":round(chosen[4],5),"known_val_correct_accepted":round(chosen[1],4),
      "unknown_cal_rejected":round(chosen[2],4),"val_balanced":round(chosen[0],4)}

def score(pred,features,train_features,trainlabels,sim,margin):
    own,gap=similarity(pred,features,train_features,trainlabels)
    accepted=(own>=sim)&(gap>=margin)
    return accepted,own,gap

def main():
    started=time.time()
    print("Starting v4 TEN-sign experiment",flush=True)
    bysign,collisions=data()
    trains,vals,tests,unkcal,unktest=partitions(bysign)
    label=lambda r:SIGNS.index(r["sign"])
    ytrain=np.asarray([label(r) for r in trains],dtype=int)
    yval=np.asarray([label(r) for r in vals],dtype=int)
    ytest=np.asarray([label(r) for r in tests],dtype=int)
    print("Split:",len(trains),"training",len(vals),"validation",
          len(tests),"held-out originals",len(unkcal),"unknown calibration",
          len(unktest),"unknown test",flush=True)

    # Baseline 1: pretrained vision features + fit SVM using training originals ONLY.
    base=pretrained()
    t_train=frozen_features(base,trains,do_aug=True).reshape(len(trains),VIEWS,-1)
    t_val=frozen_features(base,vals)
    t_unknown_cal=frozen_features(base,unkcal)
    svm=SVC(kernel="rbf",C=10,gamma="scale")
    svm.fit(t_train.reshape(-1,t_train.shape[-1]),np.repeat(ytrain,VIEWS))
    svm_val=svm.predict(t_val).astype(int)
    svm_val_acc=float(accuracy_score(yval,svm_val))
    print("Frozen MobileNetV3+RBF SVM val accuracy:",svm_val_acc,flush=True)
    # Baseline 2: fine-tune last convolution blocks and linear head.
    model=unfreeze_last(pretrained())
    model,history=train_finetuned(model,trains,vals)
    ft_val,ft_val_feat=ft_infer(model,vals)
    ft_val_acc=float(accuracy_score(yval,ft_val))
    print("Fine-tuned MobileNetV3 val accuracy:",ft_val_acc,flush=True)

    # Only validation performance selects the classifier.
    selected="fine_tuned" if ft_val_acc>svm_val_acc else "frozen_rbf_svm"
    print("SELECTED from validation:",selected,flush=True)
    if selected=="frozen_rbf_svm":
        train_features=t_train[:,0,:]
        val_features=t_val
        cal_features=t_unknown_cal
        cal_pred=svm.predict(cal_features).astype(int)
    else:
        _,train_features=ft_infer(model,trains)
        val_features=ft_val_feat
        cal_pred,cal_features=ft_infer(model,unkcal)
    val_pred=svm_val if selected=="frozen_rbf_svm" else ft_val
    rules=tune(val_pred,val_features,yval,cal_pred,cal_features,train_features,ytrain)
    print("Rejection calibration:",rules,flush=True)

    # FINAL untouched ORIGINAL held-out test; no model/threshold changes afterward.
    if selected=="frozen_rbf_svm":
        test_feats=frozen_features(base,tests)
        unknown_feats=frozen_features(base,unktest)
        test_pred=svm.predict(test_feats).astype(int)
        unknown_pred=svm.predict(unknown_feats).astype(int)
    else:
        test_pred,test_feats=ft_infer(model,tests)
        unknown_pred,unknown_feats=ft_infer(model,unktest)
    closed_correct=(test_pred==ytest)
    if rules["selected"]:
        accepted,known_sim,known_margin=score(test_pred,test_feats,train_features,ytrain,
              rules["similarity_threshold"],rules["margin_threshold"])
        unknown_accepted,unknown_sim,unknown_margin=score(unknown_pred,unknown_feats,
              train_features,ytrain,rules["similarity_threshold"],rules["margin_threshold"])
    else:
        accepted=np.ones(len(tests),dtype=bool)
        unknown_accepted=np.ones(len(unktest),dtype=bool)
        known_sim=np.zeros(len(tests));known_margin=np.zeros(len(tests))
        unknown_sim=np.zeros(len(unktest));unknown_margin=np.zeros(len(unktest))
    true_known_good=int(np.sum(closed_correct&accepted))
    unknown_rejected=int(np.sum(~unknown_accepted))
    report={
     "experiment":"Maya glyph v4 10 supported TWKM categories; source-separated originals",
     "dataset":SOURCE,"dataset_revision":REV,"seed":SEED,
     "signs":[{"number":n,"label":l,"train_originals":sum(r["sign"]==n for r in trains),
         "val_originals":1,"test_originals":1} for n,l in zip(SIGNS,LABELS)],
     "classification_candidates":[
       {"method":"frozen_rbf_svm","validation_correct":int((svm_val==yval).sum()),
         "validation_total":len(vals),"validation_accuracy":svm_val_acc},
       {"method":"fine_tuned","validation_correct":int((ft_val==yval).sum()),
         "validation_total":len(vals),"validation_accuracy":ft_val_acc}],
     "selected_by_validation_only":selected,
     "rejection_threshold_selected_by_validation":rules,
     "heldout_test_accuracy":float(np.mean(closed_correct)),
     "heldout_test_correct":int(closed_correct.sum()),"heldout_test_originals":len(tests),
     "heldout_test_macro_f1":float(f1_score(ytest,test_pred,average="macro",zero_division=0)),
     "heldout_confusion_matrix":confusion_matrix(ytest,test_pred,
         labels=list(range(len(SIGNS)))).tolist(),
     "screened_test_correct_and_accepted":true_known_good,
     "screened_test_accuracy":true_known_good/len(tests),
     "screened_known_rejected":int(np.sum(~accepted)),
     "unknown_test_rejected":unknown_rejected,
     "unknown_test_total":len(unktest),
     "unknown_test_rejection_rate":unknown_rejected/len(unktest),
     "open_set_balanced_score":.5*(true_known_good/len(tests)+unknown_rejected/len(unktest)),
     "test_predictions":[{"graph":r["graph"],"true_sign":r["sign"],
       "predicted_sign":SIGNS[int(p)],"correct":bool(p==y),
       "accepted":bool(ok),"nearest_similarity":round(float(s),4),
       "margin":round(float(g),4)} for r,p,y,ok,s,g in zip(tests,test_pred,ytest,accepted,known_sim,known_margin)],
     "unknown_predictions":[{"graph":r["graph"],"true_sign":r["sign"],
       "predicted_sign":SIGNS[int(p)],"accepted_incorrectly":bool(ok),
       "nearest_similarity":round(float(s),4),"margin":round(float(g),4)}
       for r,p,ok,s,g in zip(unktest,unknown_pred,unknown_accepted,unknown_sim,unknown_margin)],
     "small_test_warning":"Only ONE held-out original per sign: n=10 accuracy has very wide uncertainty",
     "independent_phone_accuracy":"NOT TESTED — all samples come from the same TWKM catalogue",
     "unknown_source_warning":"Unknown signs disjoint by sign ID, but drawn from same TWKM source",
     "fine_tune_training_history":history,
     "elapsed_seconds":round(time.time()-started,1),
    }
    (OUT/"evaluation.json").write_text(json.dumps(report,indent=2),encoding="utf-8")
    # Model candidates (Research-only): export the chosen trained model; Android
    # conversion and controlled deployment are a separate stage after evaluation.
    if selected=="frozen_rbf_svm":
        joblib.dump({"svm":svm,"signs":SIGNS,"labels":LABELS},
             OUT/"classifier_v4.joblib",compress=3)
    else:
        torch.save(model.state_dict(),OUT/"mobilenet_v4_finetuned.pt")
    # Nearest-neighbour references are TRAIN ORIGINALS only, never held-out images.
    gallery=OUT/"gallery_v4.bin"
    with gallery.open("wb") as f:
        f.write(struct.pack("<II",len(trains),576))
        f.write(bytes(int(y) for y in ytrain))
        f.write(train_features.astype("<f4").tobytes())
    info={"chosen_model":selected,"signs":SIGNS,"labels":LABELS,
       "input_side":224 if selected=="frozen_rbf_svm" else SIDE,
       "gallery_sha256":hashlib.sha256(gallery.read_bytes()).hexdigest(),
       "gallery_count":len(trains),"feature_dimension":576,
       "rules":rules,"can_deploy_research_mode":bool(
           rules["selected"] and report["heldout_test_accuracy"]>=.5
           and report["unknown_test_rejection_rate"]>=.75),
       "criteria":">=50% CLOSED SET on unseen original signs; >=75% unknown sign rejection; thresholds picked without touching test",
       "not_a_phone_accuracy_estimate":True}
    (OUT/"metadata.json").write_text(json.dumps(info,indent=2),encoding="utf-8")
    print("FINAL HELD OUT",report["heldout_test_correct"],"/",len(tests),
        f"={report['heldout_test_accuracy']:.1%}",flush=True)
    print("SCREENED correct",true_known_good,"/",len(tests),
        "unknown reject",unknown_rejected,"/",len(unktest),flush=True)
    print("DEPLOY RESEARCH MODE?",info["can_deploy_research_mode"],flush=True)
if __name__=="__main__":main()
