#!/usr/bin/env python3
"""DINOv2-Small few-shot TWKM research comparison, without any app changes.

Pretrained self-supervised ViT-S/14 image embeddings (Apache 2.0 weights) are
frozen; SVM trained on exact TWKM ten sign-ID labels only. Original-grouped
3-fold CV on 45 training originals picks representation/hyperparameters.
Validation and historical previously-exposed test are reported separately.
This does NOT claim reliable open-set recognition or real-phone accuracy.
"""
import json,sys,os
from pathlib import Path
import numpy as np,torch
from transformers import AutoModel

sys.path.insert(0,str(Path("ml/research_v4_single").resolve()))
import frozen_pretrained_features as f

OUT=Path("ml/research_v4_single/dinov2_results")
torch.set_num_threads(min(4,os.cpu_count() or 2))

def extract_dino(model,images):
    model.eval()
    embeddings=[]
    with torch.inference_mode():
        for i in range(0,len(images),12):
            output=model(pixel_values=images[i:i+12])
            emb=output.last_hidden_state[:,0].cpu().numpy()
            if emb.shape[1]!=384:raise ValueError("DINOv2 Small embedding dimension changed")
            embeddings.append(emb)
    return np.concatenate(embeddings).astype(np.float32)

def main():
    bysign,_=f.v4.data()
    train,val,test,uncal,untest=f.v4.partitions(bysign)
    source=f.source_images(train+val+test)
    # Public frozen self-supervised DINOv2 ViT-Small pretrained model.
    net=AutoModel.from_pretrained("facebook/dinov2-small")
    net.eval()
    for p in net.parameters():p.requires_grad_(False)
    cached={}
    grid=[]
    for representation in ("binary","gray"):
        train_x=extract_dino(net,f.images_for(train,source,representation,3))
        val_x=extract_dino(net,f.images_for(val,source,representation,1))
        test_x=extract_dino(net,f.images_for(test,source,representation,1))
        cached[representation]=(train_x,val_x,test_x)
        for kernel in ("linear","rbf"):
            for c in (0.1,1,10):
                metrics=f.grouped_cv(train_x,train,kernel,c)
                grid.append({"representation":representation,"kernel":kernel,"C":c,"grouped_cv":metrics})
                print("DINO grouped CV",representation,kernel,c,
                      metrics["correct"],"/45","TOK",metrics["tok_correct"],"/4",flush=True)
    chosen=max(grid,key=lambda k:(k["grouped_cv"]["macro_f1"],
                                  k["grouped_cv"]["correct"],-k["C"]))
    feature=chosen["representation"]
    X,V,T=cached[feature]
    y=np.asarray([f.SIGNS.index(row["sign"]) for row in train])
    clf=f.fit(X,np.repeat(y,3),chosen["kernel"],chosen["C"])
    val_metrics=f.measure(clf,V,val)
    exposed=f.measure(clf,T,test)
    OUT.mkdir(parents=True,exist_ok=True)
    f.save_confusion(OUT/"validation_confusion.csv",val_metrics)
    f.save_confusion(OUT/"exposed_test_confusion.csv",exposed)
    report={
       "method":"Frozen DINOv2 ViT-Small pretrained self-supervised features plus TWKM-labelled SVM",
       "pretrained_model":"facebook/dinov2-small",
       "license":"Apache 2.0 pretrained DINOv2 model; TWKM image source credited separately",
       "source":"TWKM pinned source-original 10-sign split",
       "selection_training_only_source_grouped_cv":True,
       "num_training_originals":len(train),
       "num_val_originals":len(val),
       "historical_test_exposed":True,
       "research_selection":chosen,
       "full_cv":grid,
       "known_validation":val_metrics,
       "previously_exposed_test":exposed,
       "baseline_existing_v4_known_val":8,
       "baseline_existing_v4_exposed_test":7,
       "independent_real_phone_accuracy":"NOT TESTED",
       "unknown_sign_rejection":"NOT TESTED (closed-set sign classifier)",
       "android_model_unchanged":True
    }
    (OUT/"results.json").write_text(json.dumps(report,indent=2),encoding="utf-8")
    print("DINO SELECTED",chosen,flush=True)
    print("DINO VALIDATION",val_metrics["correct"],"/10",flush=True)
    print("DINO EXPOSED KNOWN TEST",exposed["correct"],"/10",flush=True)
    print("DINO TOK",next((x for x in exposed["errors"] if x["actual"]==44),"correct"),flush=True)

if __name__=="__main__":main()
