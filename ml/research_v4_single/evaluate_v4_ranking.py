#!/usr/bin/env python3
"""Reproducible research-only evaluation of deployed Research v4 logits.

Outputs confusion matrices, per-glyph precision/recall/F1, top-3 accuracy,
second-choice confusions and unknown-sign score distributions. All source
drawings are TWKM catalogue examples; none is a new real-phone photo test.
The 10 known test originals were exposed by previous v4 experiments.
"""
import csv
import hashlib
import json
import os
import sys
from pathlib import Path

import cv2
import numpy as np
import onnxruntime as ort
from sklearn.metrics import confusion_matrix, precision_recall_fscore_support, accuracy_score

sys.path.insert(0, str(Path("ml/glyph_v4").resolve()))
import train_v4 as v4

MODEL=Path("app/src/main/assets/maya_glyph_v4.onnx")
OUT=Path("ml/research_v4_single/metrics")
SIGNS=v4.SIGNS
LABELS=v4.LABELS
HASH="b9bb6bcc816f9c7957792302d7a1e50aaee2a1ecf3d44b8ae9b9b493cf4efba9"

def softmax(logits):
    ex=np.exp(logits-logits.max(axis=1,keepdims=True))
    return ex / ex.sum(axis=1,keepdims=True)

def infer(session,rows):
    logits=[]
    for i in range(0,len(rows),20):
        x=v4.tensorize([r["image"] for r in rows[i:i+20]],160).numpy()
        logits.append(session.run(["logits"],{"image":x})[0])
    return np.concatenate(logits,axis=0)

def write_csv(name,headers,records):
    with (OUT/name).open("w",newline="",encoding="utf-8") as f:
        writer=csv.writer(f)
        writer.writerow(headers)
        writer.writerows(records)

def stats(logits,rows,tag):
    probs=softmax(logits)
    y=np.array([SIGNS.index(r["sign"]) for r in rows])
    best=np.argsort(-probs,axis=1)
    pred=best[:,0]
    p,r,f1,_=precision_recall_fscore_support(
        y,pred,labels=np.arange(len(SIGNS)),zero_division=0
    )
    cm=confusion_matrix(y,pred,labels=np.arange(len(SIGNS)))
    write_csv(tag+"_confusion_matrix.csv",["True \\ Pred"]+[str(i) for i in SIGNS],
              [[SIGNS[i]]+list(map(int,cm[i])) for i in range(len(SIGNS))])
    write_csv(tag+"_per_glyph.csv",
              ["TWKM sign","reading","support","precision","recall","f1","correct","predicted_as_this_sign"],
              [[SIGNS[i],LABELS[i],int(np.sum(y==i)),float(p[i]),float(r[i]),float(f1[i]),
                int(cm[i,i]),int(cm[:,i].sum())] for i in range(len(SIGNS))])
    predictions=[]
    for j,row in enumerate(rows):
        rank=best[j]
        predictions.append([
            row["graph"],row["sign"],SIGNS[rank[0]],bool(pred[j]==y[j]),
            SIGNS[rank[1]], SIGNS[rank[2]],
            round(float(probs[j,rank[0]]*100),2),
            round(float(probs[j,rank[1]]*100),2),
            round(float(probs[j,rank[2]]*100),2),
            round(float((probs[j,rank[0]]-probs[j,rank[1]])*100),2),
            row["sha"],
        ])
    write_csv(tag+"_ranked_predictions.csv",
              ["source_graph","true_twkm_sign","top1_twkm_sign","top1_correct",
               "top2_twkm_sign","top3_twkm_sign","top1_softmax_percent",
               "top2_softmax_percent","top3_softmax_percent","top1_top2_margin_points",
               "source_sha256"],predictions)
    return {
        "samples":len(rows),
        "unique_signs":len(set(y)),
        "correct_top1":int((pred==y).sum()),
        "top1_accuracy":float(np.mean(pred==y)),
        "top3_correct":int(sum(y[j] in best[j,:3] for j in range(len(y)))),
        "top3_accuracy":float(np.mean([y[j] in best[j,:3] for j in range(len(y))])),
        "macro_precision":float(np.mean(p)),
        "macro_recall":float(np.mean(r)),
        "macro_f1":float(np.mean(f1)),
        "misclassified": [
            {"graph":row["graph"],"true_sign":row["sign"],"predicted_sign":int(SIGNS[pred[j]]),
             "runner_up":int(SIGNS[best[j,1]]),"top1_softmax_percent":float(probs[j,best[j,0]]*100)}
            for j,row in enumerate(rows) if pred[j]!=y[j]
        ],
    }

def main():
    assert hashlib.sha256(MODEL.read_bytes()).hexdigest()==HASH
    session=ort.InferenceSession(str(MODEL),providers=["CPUExecutionProvider"])
    bysign,_=v4.data()
    train,val,test,uncal,untest=v4.partitions(bysign)
    OUT.mkdir(parents=True,exist_ok=True)
    validation=stats(infer(session,val),val,"validation")
    known_test=stats(infer(session,test),test,"known_exposed_test")
    probs=softmax(infer(session,untest))
    maximum=np.max(probs,axis=1)
    report={
        "model":"Research v4 SAME 10-class Android ONNX, unchanged SHA256",
        "model_sha256":HASH,
        "label_source":"TWKM catalogued drawings, not expert verified real photos",
        "validation":validation,
        "known_exposed_test":known_test,
        "unknown_sign_id_test":{
            "number_of_different_unsupported_sign_drawings":len(untest),
            "top1_softmax_percentiles":[round(float(q)*100,2) for q in np.quantile(maximum,[0,.25,.50,.75,.90,1])],
            "incorrect_closed_set_predictions":len(untest),
            "no_validated_unknown_rejection":True,
        },
        "warning":"ONLY one held-out original per supported class. Exposed test is descriptive and must not be used for tuning. Softmax percentages are not calibrated confidence.",
        "real_phone_photo_accuracy":"NOT MEASURED",
    }
    (OUT/"results.json").write_text(json.dumps(report,indent=2),encoding="utf-8")
    lines=[
        "# Research v4: per-glyph and top-3 model evaluation",
        "",
        "Source: pinned TWKM catalogue, not independent photographed inscriptions.",
        "The ten held-out reference originals have been seen in earlier experiments.",
        "",
        "| Metric | Known validation (10) | Exposed holdout (10) |",
        "|---|---:|---:|",
    ]
    for key in ["correct_top1","top3_correct"]:
        lines.append(f"| {key} | {validation[key]}/10 | {known_test[key]}/10 |")
    for key in ["macro_precision","macro_recall","macro_f1"]:
        lines.append(f"| {key} | {validation[key]:.3f} | {known_test[key]:.3f} |")
    lines+=["",
      "## Confusable sign pairs",
      "Check the ranked-prediction CSV for the next most likely sign on each image; ",
      "this is a model confusion proxy, NOT an expert-certified visual-similarity ranking.",
      "",
      "## Unknown / out-of-scope examples",
      f"{len(untest)} different unsupported sign samples were nevertheless assigned ",
      "one of ten classes by the closed-set model. Softmax scores are not calibrated.",
      f"Unknown-sign top1 score quartiles: {report['unknown_sign_id_test']['top1_softmax_percentiles']}",
      "",
      "No live photo accuracy is claimed. Do not use these known reference results ",
      "to adjust thresholds and then describe them as an untouched test."
    ]
    (OUT/"results.md").write_text("\n".join(lines)+"\n",encoding="utf-8")
    print(json.dumps(report,indent=2),flush=True)

if __name__=="__main__":main()
