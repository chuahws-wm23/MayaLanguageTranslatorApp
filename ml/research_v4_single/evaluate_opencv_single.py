#!/usr/bin/env python3
"""Controlled comparison: original vs CLAHE, bilateral, adaptive and shadow.

The same pinned TWKM original-source images are used for every treatment.
Selection uses known validation (one source drawing per supported class),
fixed simulated-photo perturbations of those drawings, and 80 disjoint
unsupported-sign *calibration* examples. The already exposed known holdout
and 80 separate unsupported sign IDs are used descriptively afterward.

This is NOT an independent photo accuracy test; NO Android model is changed.
"""
import csv
import hashlib
import io
import json
import os
import sys
from pathlib import Path

import cv2
import numpy as np
import onnxruntime as ort
from datasets import Image as HFImage, load_dataset
from PIL import Image as PILImage
from sklearn.metrics import confusion_matrix, precision_recall_fscore_support

sys.path.insert(0,str(Path("ml/glyph_v4").resolve()))
sys.path.insert(0,str(Path("ml/research_v4_single").resolve()))
import train_v4 as v4
from opencv_single_glyph import (
    SIGNS, VARIANTS, MODEL_SHA256, decode_image, logits_for, top_candidates
)

ASSET=Path("app/src/main/assets/maya_glyph_v4.onnx")
OUT=Path("ml/research_v4_single/opencv_results")
VARIANTS_TO_TEST=("original","clahe","clahe_bilateral","adaptive","shadow")
SEED=20261115

def source_pixels(groups):
    selected={r["sha"] for group in groups for r in group}
    found={}
    images=load_dataset(v4.SOURCE,"graphs",split="train",revision=v4.REV,
        cache_dir=os.environ.get("HF_DATASETS_CACHE","/tmp/maya-v4-hf")).cast_column(
        "image",HFImage(decode=False))
    for row in images:
        item=row.get("image")
        if not item:continue
        data=item.get("bytes")
        if data is None and item.get("path"):data=Path(item["path"]).read_bytes()
        if not data:continue
        digest=hashlib.sha256(data).hexdigest()
        if digest not in selected:continue
        found[digest]=decode_image(data)
    if set(found)!=selected:
        raise RuntimeError(f"Missing {len(selected-set(found))} original glyph source images")
    return found

def simulate_camera(gray, seed, copies=3):
    """Non-mirrored light/shadow/blur perturbations; no identity-changing flips."""
    rng=np.random.default_rng(seed)
    h,w=gray.shape
    outputs=[]
    border=np.r_[gray[0],gray[-1],gray[:,0],gray[:,-1]]
    bg=int(np.median(border))
    for i in range(copies):
        angle=float(rng.uniform(-7,7))
        matrix=cv2.getRotationMatrix2D((w/2,h/2),angle,1.0)
        shifted=cv2.warpAffine(gray,matrix,(w,h),flags=cv2.INTER_LINEAR,
                               borderMode=cv2.BORDER_CONSTANT,borderValue=bg)
        y,x=np.mgrid[0:h,0:w]
        illumination=(rng.uniform(-60,60)*(x/max(w-1,1)-.5)+
                      rng.uniform(-50,50)*(y/max(h-1,1)-.5))
        noisy=np.clip(shifted.astype(np.float32)+illumination+
                      rng.normal(0,5.0,(h,w)),0,255).astype(np.uint8)
        if i!=0:
            noisy=cv2.GaussianBlur(noisy,(3,3),float(rng.uniform(.4,1.0)))
        outputs.append(noisy)
    return outputs

def run_all(session,gray):
    return {variant:logits_for(session,gray,variant) for variant in VARIANTS_TO_TEST}

def paired(logits,labels,variant,baseline="original"):
    original=logits[baseline]
    comparison=logits[variant]
    good=np.isfinite(original).all(axis=1)&np.isfinite(comparison).all(axis=1)
    prediction=original.argmax(axis=1)
    candidate=comparison.argmax(axis=1)
    accepted=good&(prediction==candidate)
    if variant=="original":
        accepted=np.isfinite(original).all(axis=1)
    result={
        "n":len(labels),"correct_accepted":int((accepted&(prediction==labels)).sum()) if labels is not None else None,
        "wrong_accepted":int((accepted&(prediction!=labels)).sum()) if labels is not None else int(accepted.sum()),
        "rejected":int((~accepted).sum()),
        "accepted_total":int(accepted.sum()),
    }
    return result,accepted,prediction

def pure_model(logits,known):
    preds=np.argmax(logits,axis=1)
    valid=np.isfinite(logits).all(axis=1)
    if known is None:return {"valid":int(valid.sum()),"n":len(preds)}
    return {"correct":int((valid&(preds==known)).sum()),
            "n":len(preds),"invalid":int((~valid).sum())}

def evaluate_groups(session,rows,images,stress=False):
    grays=[]
    truth=[]
    for row in rows:
        original=images[row["sha"]]
        selected=(simulate_camera(original,SEED+int(row["sha"][:6],16),3)
                  if stress else [original])
        for item in selected:
            grays.append(item)
            truth.append(SIGNS.index(row["sign"]) if row["sign"] in SIGNS else -1)
    results=run_all(session,grays)
    true=np.asarray(truth,dtype=int)
    known=None if np.any(true<0) else true
    data={}
    for variant in VARIANTS_TO_TEST:
        r,_,_=paired(results,known,variant)
        data[variant]={
            "pair_consensus_with_original":r,
            "variant_pure_top1":pure_model(results[variant],known),
        }
    return data, results, true

def per_class_validation(rows,score_array):
    labels=np.array([SIGNS.index(r["sign"]) for r in rows])
    valid=np.isfinite(score_array).all(axis=1)
    predicted=np.argmax(score_array,axis=1)
    # Invalid -> extra class; count as misses, don't silently discard.
    pred=np.where(valid,predicted,-1)
    cm=confusion_matrix(labels,pred,labels=np.arange(10))
    p,r,f,_=precision_recall_fscore_support(labels,pred,labels=np.arange(10),zero_division=0)
    return {
        "accuracy":float(np.mean(pred==labels)),
        "macro_precision":float(np.mean(p)),
        "macro_recall":float(np.mean(r)),
        "macro_f1":float(np.mean(f)),
        "confusion_matrix":cm.tolist(),
        "per_sign":[{"twkm_sign":SIGNS[i],"precision":float(p[i]),
                     "recall":float(r[i]),"f1":float(f[i]),
                     "support":int(np.sum(labels==i))} for i in range(10)],
    }

def write_csv(path,headers,records):
    with path.open("w",newline="",encoding="utf8") as f:
        writer=csv.writer(f)
        writer.writerow(headers)
        writer.writerows(records)

def main():
    if hashlib.sha256(ASSET.read_bytes()).hexdigest()!=MODEL_SHA256:
        raise RuntimeError("Research v4 model integrity mismatch")
    OUT.mkdir(parents=True,exist_ok=True)
    session=ort.InferenceSession(str(ASSET),providers=["CPUExecutionProvider"])
    bysign,_=v4.data()
    train,val,test,unknown_cal,unknown_test=v4.partitions(bysign)
    pixels=source_pixels([val,test,unknown_cal,unknown_test])
    val_clean,val_scores,y_val=evaluate_groups(session,val,pixels)
    val_stress,stress_scores,y_stress=evaluate_groups(session,val,pixels,stress=True)
    cal_unknown,_,_=evaluate_groups(session,unknown_cal,pixels)
    baseline=val_clean["shadow"]["pair_consensus_with_original"]
    base_stress=val_stress["shadow"]["pair_consensus_with_original"]
    baseline_unknown=cal_unknown["shadow"]["pair_consensus_with_original"]
    candidates=[]
    for variant in ("clahe","clahe_bilateral","adaptive"):
        clean=val_clean[variant]["pair_consensus_with_original"]
        stress=val_stress[variant]["pair_consensus_with_original"]
        unknown=cal_unknown[variant]["pair_consensus_with_original"]
        # PREDECLARED: preserve supported clean correct and unknown safety;
        # require at least 3 additional correct accepted out of 30 stress photos.
        eligible=(
            clean["correct_accepted"]>=baseline["correct_accepted"] and
            stress["correct_accepted"]>=base_stress["correct_accepted"]+3 and
            unknown["wrong_accepted"]<=baseline_unknown["wrong_accepted"]
        )
        candidates.append({"variant":variant,"eligible":bool(eligible),
            "validation_clean":clean,"validation_stress":stress,"unknown_calibration":unknown})
    eligible=[x for x in candidates if x["eligible"]]
    selected=sorted(eligible,key=lambda x:(
        -x["validation_stress"]["correct_accepted"],
        x["unknown_calibration"]["wrong_accepted"],x["variant"]))[0]["variant"] if eligible else None

    # The earlier v4 known test set has ALREADY been exposed; descriptive only.
    test_clean,test_scores,y_test=evaluate_groups(session,test,pixels)
    test_stress,_,_=evaluate_groups(session,test,pixels,stress=True)
    test_unknown,_,_=evaluate_groups(session,unknown_test,pixels)
    for name,rows,preds in [("validation",val,val_scores),
                            ("exposed_test",test,test_scores)]:
        for variant in ("original","shadow") + ((selected,) if selected else ()):
            score=per_class_validation(rows,preds[variant])
            write_csv(OUT/f"{name}_{variant}_confusion.csv",
                ["Actual \\ Predicted"]+[str(x) for x in SIGNS],
                [[SIGNS[i]]+score["confusion_matrix"][i] for i in range(10)])
            write_csv(OUT/f"{name}_{variant}_per_glyph.csv",
                ["TWKM sign","support","precision","recall","f1"],
                [[x["twkm_sign"],x["support"],x["precision"],x["recall"],x["f1"]]
                for x in score["per_sign"]])
    report={
        "study":"Research v4 same CNN/weights: Python OpenCV image treatments",
        "model_sha256":MODEL_SHA256,
        "selection_data":"TWKM known validation originals and separate unknown-sign calibration IDs",
        "source":"TWKM pinned catalog, not independent real-world camera photos",
        "training_split_not_used_for_selection":True,
        "original_val_known":len(val),"exposed_test_known":len(test),
        "unknown_sign_ids_calibration":len(unknown_cal),
        "unknown_sign_ids_exposed_test":len(unknown_test),
        "validation_clean":val_clean,
        "validation_simulated_camera":val_stress,
        "unknown_calibration":cal_unknown,
        "predeclared_baseline":"Agreement between original and large-scale shadow correction",
        "candidate_selection_rule":"No loss of clean correct-accepted, gain >=3/30 simulated known correct-accepted, no rise in wrong-accepted unknowns",
        "candidates":candidates,
        "selected_candidate":selected,
        "exposed_known_test_clean":{k:test_clean[k] for k in ("original","shadow") + ((selected,) if selected else ())},
        "exposed_known_test_simulated_camera":{k:test_stress[k] for k in ("original","shadow") + ((selected,) if selected else ())},
        "exposed_unknown_sign_test":{k:test_unknown[k] for k in ("original","shadow") + ((selected,) if selected else ())},
        "deploy_to_android":False,
        "not_deploy_reason":"Python-vs-Kotlin parity and independent labelled real-camera tests still needed even for any selected research candidate",
        "independent_camera_accuracy":"NOT MEASURED",
        "limitations":"One validated source original per class and previously exposed final test; camera perturbations are synthetic and may not preserve all morphology.",
    }
    (OUT/"results.json").write_text(json.dumps(report,indent=2),encoding="utf-8")
    lines=[
        "# Research v4 OpenCV single-glyph preprocessing comparison",
        "",
        "**Only the existing ten-sign CNN was evaluated; no new model weights.**",
        "These are TWKM graph drawings and synthetic camera perturbations, not independently labelled field photos.",
        "",
        "| Preprocessing treatment paired with original | Known val correctly accepted / 10 | Synthetic photos correctly accepted / 30 | Unsupported signs wrongly accepted / 80 |",
        "|---|---:|---:|---:|",
    ]
    for variant in ("shadow","clahe","clahe_bilateral","adaptive"):
        k=val_clean[variant]["pair_consensus_with_original"]
        st=val_stress[variant]["pair_consensus_with_original"]
        uk=cal_unknown[variant]["pair_consensus_with_original"]
        lines.append(f"| {variant} | {k['correct_accepted']}/10 | {st['correct_accepted']}/30 | {uk['wrong_accepted']}/80 |")
    lines += ["",f"**Selected candidate:** {selected or 'None (did not pass preregistered gate)'}",
      "",
      "Unknown-sign rejection is not validated for use in the app. Never treat a high softmax relative score as verified confidence.",
      "The original Android ONNX weights are unchanged. If a variant wins validation, it still needs independent camera testing and preprocessing parity.",
      "",
      "Results JSON contains the exact numerical metrics; CSV files include per-sign confusion and precision/recall/F1.",
    ]
    (OUT/"RESULTS.md").write_text("\n".join(lines)+"\n",encoding="utf-8")
    print("\n".join(lines),flush=True)
if __name__=="__main__":
    main()
