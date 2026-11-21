#!/usr/bin/env python3
"""Experimental v4 known/unknown margin calibration.

TWKM validation originals and disjoint unknown-sign calibration examples
select ONE scalar margin threshold. The already-exposed known holdout and
separate unknown-sign IDs are evaluated only after threshold selection.

No threshold is shipped without a useful known-correct acceptance rate,
unknown rejection rate, and eventually independent phone photo validation.
This experiment does not change the bundled Android model.
"""
import hashlib
import io
import json
import os
import sys
from pathlib import Path

import cv2
import numpy as np
import onnxruntime as ort
from PIL import Image as PILImage
from datasets import Image as HFImage, load_dataset

sys.path.insert(0,str(Path("ml/glyph_v4").resolve()))
import train_v4 as v4
from evaluate_photo_consensus import android_like_standardize, fix_shadows

ASSET=Path("app/src/main/assets/maya_glyph_v4.onnx")
OUT=Path("ml/research_v4_single/output/unknown_margin_calibration.json")

def image_lookup(rows):
    wanted={r["sha"] for r in rows}
    raw_by_sha={}
    ds=load_dataset(v4.SOURCE,"graphs",split="train",revision=v4.REV,
        cache_dir=os.environ.get("HF_DATASETS_CACHE","/tmp/maya-v4-hf")).cast_column(
            "image",HFImage(decode=False))
    for row in ds:
        image=row.get("image")
        if not image: continue
        data=image.get("bytes")
        if data is None and image.get("path"):data=Path(image["path"]).read_bytes()
        if not data:continue
        h=hashlib.sha256(data).hexdigest()
        if h not in wanted:continue
        with PILImage.open(io.BytesIO(data)) as img:
            raw_by_sha[h]=cv2.cvtColor(
                np.asarray(img.convert("RGB")),cv2.COLOR_RGB2GRAY)
    if not all(r["sha"] in raw_by_sha for r in rows):
        raise RuntimeError("Missing labelled reference image")
    return raw_by_sha

def score(session, gray):
    candidates=[]
    for arr in [gray, fix_shadows(gray)]:
        canon=android_like_standardize(arr)
        if canon is None:return None
        x=v4.tensorize([canon],160).numpy()
        logits=session.run(["logits"],{"image":x})[0][0]
        best=np.argsort(logits)
        candidates.append((int(best[-1]),float(logits[best[-1]]-logits[best[-2]])))
    if candidates[0][0]!=candidates[1][0]:return None
    return (v4.SIGNS[candidates[0][0]],min(candidates[0][1],candidates[1][1]))

def prepare(session,rows,imgs):
    records=[]
    for r in rows:
        prediction=score(session,imgs[r["sha"]])
        records.append({
            "true_sign":r["sign"],"predicted_sign":prediction[0] if prediction else None,
            "margin":prediction[1] if prediction else float("-inf"),
            "correct":bool(prediction and prediction[0]==r["sign"]),
            "graph":r["graph"],
        })
    return records

def measure(rows, threshold, known):
    accepted=[r for r in rows if r["margin"]>=threshold]
    stats={"total":len(rows),"accepted":len(accepted),
        "rejected":len(rows)-len(accepted)}
    if known:
        stats["correct_accepted"]=sum(r["correct"] for r in accepted)
        stats["incorrect_accepted"]=sum(not r["correct"] for r in accepted)
    return stats

def main():
    sha=hashlib.sha256(ASSET.read_bytes()).hexdigest()
    assert sha=="b9bb6bcc816f9c7957792302d7a1e50aaee2a1ecf3d44b8ae9b9b493cf4efba9"
    session=ort.InferenceSession(str(ASSET),providers=["CPUExecutionProvider"])
    bysign,_=v4.data()
    train,val,test,unknown_cal,unknown_test=v4.partitions(bysign)
    raw=image_lookup(val+test+unknown_cal+unknown_test)
    known=prepare(session,val,raw)
    unknown=prepare(session,unknown_cal,raw)
    margins=sorted(set([0.0]+[r["margin"] for r in known+unknown if np.isfinite(r["margin"])]))
    candidates=[]
    for threshold in margins:
        k=measure(known,threshold,True)
        u=measure(unknown,threshold,False)
        if k["correct_accepted"]>=5 and u["rejected"]>=60:
            score=.5*(k["correct_accepted"]/len(known)+u["rejected"]/len(unknown))
            candidates.append((score,k["correct_accepted"],u["rejected"],float(threshold)))
    winner=max(candidates,default=None)
    report={
        "experiment":"Research v4 two-view top-logit margin screening; no new model version",
        "baseline_known_validation":measure(known,0,True),
        "baseline_unknown_calibration":measure(unknown,0,False),
        "threshold_selected":bool(winner),
        "calibration_rule":"At least 5/10 known validation correctly accepted; reject >=60/80 known-unsupported sign identities; maximize balanced criterion.",
        "chosen_margin_threshold":winner[3] if winner else None,
        "research_only_not_deployed":True,
        "known_validation_rows":10,
        "unknown_calibration_rows":80,
        "source":"pinned TWKM drawings, not independent photographs",
        "limitations":"Known final test was exposed by previous v4 research; no independent camera accuracy",
    }
    if winner:
        threshold=winner[3]
        report["calibration_known_at_threshold"]=measure(known,threshold,True)
        report["calibration_unknown_at_threshold"]=measure(unknown,threshold,False)
        report["known_exposed_test"]=measure(prepare(session,test,raw),threshold,True)
        report["unknown_sign_test"]=measure(prepare(session,unknown_test,raw),threshold,False)
        report["candidate_passes_catalogue_test_gate"]=(
            report["known_exposed_test"]["correct_accepted"]>=5 and
            report["unknown_sign_test"]["rejected"]>=60
        )
    OUT.parent.mkdir(parents=True,exist_ok=True)
    OUT.write_text(json.dumps(report,indent=2),encoding="utf8")
    print(json.dumps(report,indent=2),flush=True)
if __name__=="__main__":main()
