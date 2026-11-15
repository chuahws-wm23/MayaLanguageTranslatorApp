#!/usr/bin/env python3
"""Score genuine, source-independent Maya glyph photographs with Research v4.

Requires a local manifest with expert-verified TWKM labels and provenance.
Uses the SAME v4 ONNX weights and the Python original/shadow image pipeline.
WARNING: Python processing approximates Android and is not on-device testing.

Never tunes thresholds on the test set. Outputs Wilson 95% uncertainty bounds,
per-sign metrics, raw predictions and a conservative release-readiness gate.
"""
from __future__ import annotations
import argparse,csv,hashlib,json,math,sys
from pathlib import Path
from collections import Counter

import numpy as np
from PIL import Image,ImageOps
from sklearn.metrics import precision_recall_fscore_support,confusion_matrix

sys.path.insert(0,str(Path("dataset/scripts").resolve()))
from validate_real_glyph_photos import audit,SIGNS
sys.path.insert(0,str(Path("ml/research_v4_single").resolve()))
from opencv_single_glyph import decode_image,logits_for,MODEL_SHA256

ORDER=[32,184,291,4,44,507,548,122,187,1030]

def wilson(successes,n,z=1.96):
    if n<1:return None
    p=successes/n
    den=1+z*z/n
    center=(p+z*z/(2*n))/den
    spread=z/den*math.sqrt(p*(1-p)/n+z*z/(4*n*n))
    return [round(max(0.,center-spread),4),round(min(1.,center+spread),4)]

def measure(truth,predicted,accepted,top3=None):
    if not (len(truth)==len(predicted)==len(accepted)):
        raise ValueError("Prediction/label lengths differ")
    known=[i for i,y in enumerate(truth) if y in ORDER]
    unknown=[i for i,y in enumerate(truth) if y=="UNKNOWN"]
    if len(known)+len(unknown)!=len(truth):
        raise ValueError("Manifest labels outside supported set")
    known_good=sum(bool(accepted[i] and truth[i]==predicted[i]) for i in known)
    known_wrong=sum(bool(accepted[i] and truth[i]!=predicted[i]) for i in known)
    known_rejected=len(known)-known_good-known_wrong
    unknown_false=sum(bool(accepted[i]) for i in unknown)
    unknown_reject=len(unknown)-unknown_false
    true_y=[ORDER.index(truth[i]) for i in known]
    pred_y=[ORDER.index(predicted[i]) if accepted[i] and predicted[i] in ORDER else -1 for i in known]
    if known:
        prec,rec,f1,support=precision_recall_fscore_support(
            true_y,pred_y,labels=list(range(len(ORDER))),zero_division=0)
        conf=confusion_matrix(true_y,pred_y,labels=[-1]+list(range(len(ORDER))))
        per_class=[{"twkm_sign":ORDER[i],"support":int(support[i]),
            "precision":float(prec[i]),"recall":float(rec[i]),"f1":float(f1[i])}
            for i in range(len(ORDER))]
        macro_f1=float(np.mean(f1))
        allmin=all(c["support"]>=10 for c in per_class)
        per_sign_recall=all(c["recall"]>=.7 for c in per_class)
    else:
        per_class=[];conf=np.zeros((11,11),dtype=int)
        macro_f1=None
        allmin=False;per_sign_recall=False
    lower=wilson(known_good,len(known))
    upper=wilson(unknown_false,len(unknown))
    # Explicitly aspirational research gate, never reached merely by
    # catalogue or synthetic images: count at least 100 independent known
    # and 100 out-of-class real photographs, with coverage across all ten.
    passed=bool(len(known)>=100 and len(unknown)>=100 and
                allmin and per_sign_recall and macro_f1 is not None and macro_f1>=.8 and
                lower[0]>=.8 and upper[1]<=.05)
    return {
       "known_photos":len(known),
       "known_correct_and_accepted":known_good,
       "known_wrongly_accepted":known_wrong,
       "known_rejected":known_rejected,
       "known_correct_accepted_rate":known_good/len(known) if known else None,
       "known_correct_accepted_wilson95":lower,
       "unknown_photos":len(unknown),
       "unknown_rejected":unknown_reject,
       "unknown_wrongly_accepted":unknown_false,
       "unknown_wrong_acceptance_rate":unknown_false/len(unknown) if unknown else None,
       "unknown_wrong_acceptance_wilson95":upper,
       "top3_correct_known_accepted":sum(
            bool(accepted[i] and top3 is not None and truth[i] in top3[i])
            for i in known) if top3 is not None else None,
       "macro_f1_known_with_rejections_as_errors":macro_f1,
       "per_sign":per_class,
       "confusion_matrix_with_rejected_first":conf.tolist(),
       "provisional_reliability_release_gate":passed,
       "gate_definition":"100+ source-independent known photos (>=10 per sign), 100+ unknowns; Wilson95 known correct-and-accepted lower >=80%, unknown false-accept upper <=5%; macro F1 >=.80 and each sign recall >=.70. Device parity and expert review still required.",
       "real_photo_accuracy_cannot_be_inferred_from_synthetic":True,
    }

def run(manifest:Path,root:Path,model:Path,out:Path):
    report=audit(manifest,root)
    if report["errors"]:
        raise ValueError("Manifest errors: "+"; ".join(report["errors"][:5]))
    if report["warnings"]:
        raise ValueError("Review possible duplicate images before a blind test: "+
                         "; ".join(report["warnings"][:3]))
    if hashlib.sha256(model.read_bytes()).hexdigest()!=MODEL_SHA256:
        raise ValueError("Model checksum changed; cannot verify trained sign order")
    import onnxruntime as ort
    sess=ort.InferenceSession(str(model),providers=["CPUExecutionProvider"])
    rows=[]
    with manifest.open(newline="",encoding="utf-8-sig") as f:
        for r in csv.DictReader(f):
            if r["split"].strip().lower()=="test":rows.append(r)
    if not rows:raise ValueError("Manifest has no held-out test images")
    raw=[decode_image(root/r["image_path"].strip()) for r in rows]
    original=logits_for(sess,raw,"original")
    corrected=logits_for(sess,raw,"shadow")
    truth=[];predicted=[];accepted=[];topthree=[]
    records=[]
    for i,row in enumerate(rows):
        sign=row["twkm_sign"].strip()
        label="UNKNOWN" if sign=="UNKNOWN" else int(sign)
        ok=np.isfinite(original[i]).all() and np.isfinite(corrected[i]).all()
        a=int(np.argmax(original[i])) if ok else -1
        b=int(np.argmax(corrected[i])) if ok else -1
        supported=bool(ok and a==b)
        top=[ORDER[int(k)] for k in np.argsort(-original[i])[:3]] if ok else []
        predicted_sign=ORDER[a] if a>=0 else "UNKNOWN"
        truth.append(label);predicted.append(predicted_sign)
        accepted.append(supported);topthree.append(top)
        records.append({"image_path":row["image_path"],"source_group":row["source_group"],
             "true_sign":label,"predicted_sign":predicted_sign,
             "accepted":supported,"top3":";".join(map(str,top))})
    stats=measure(truth,predicted,accepted,topthree)
    stats["study"]="Real-world expert-verified single glyph photo evaluation (Python v4 two-view approximation)"
    stats["source_manifest"]=str(manifest)
    stats["method"]="ONNX v4 original/shadow consensus with unchanged weights"
    stats["android_inference_parity_not_tested"]=True
    stats["research_photo_audit"]=report
    out.mkdir(parents=True,exist_ok=True)
    (out/"report.json").write_text(json.dumps(stats,indent=2),encoding="utf-8")
    with (out/"predictions.csv").open("w",newline="",encoding="utf-8") as f:
        w=csv.DictWriter(f,fieldnames=list(records[0]))
        w.writeheader();w.writerows(records)
    (out/"README.txt").write_text(
        "These are predictions on labelled files provided in the local photo manifest.\n"+
        "No accuracy claims are valid if source_group information or expert labels are unverified.\n"+
        "This Python preprocessing approximates Kotlin and needs on-device parity testing.\n",encoding="utf-8")
    return stats

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--manifest",required=True,type=Path)
    p.add_argument("--root",required=True,type=Path)
    p.add_argument("--model",type=Path,default=Path("app/src/main/assets/maya_glyph_v4.onnx"))
    p.add_argument("--output",type=Path,default=Path("dataset/local_verified_photos/evaluation"))
    a=p.parse_args()
    data=run(a.manifest,a.root,a.model,a.output)
    print(json.dumps({k:data[k] for k in (
        "known_photos","known_correct_and_accepted","known_wrongly_accepted",
        "unknown_photos","unknown_wrongly_accepted",
        "known_correct_accepted_wilson95","unknown_wrong_acceptance_wilson95",
        "provisional_reliability_release_gate")},indent=2))

if __name__=="__main__":main()
