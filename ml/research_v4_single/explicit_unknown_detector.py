#!/usr/bin/env python3
"""Research-only supported-vs-unknown classifier using v4 ONNX visual features.

Training: 45 known-class TWKM original drawings and 50 other-sign drawings
from 25 unknown sign IDs. Validation: 10 known held-out originals plus 30
other-sign drawings from 15 distinct negative sign IDs. Final unknown test:
80 drawings from 40 DIFFERENT sign IDs. Original 10 known test drawings
are historical/exposed, not a new independent test.

The recognizer's own ten-class prediction correctness is checked separately
from the binary detector acceptance. No Android replacement/claim of phone
accuracy can follow without independently verified photos.
"""
import hashlib, json, os, sys
from pathlib import Path
import numpy as np
import onnxruntime as ort
from sklearn.svm import SVC
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import accuracy_score

sys.path.insert(0,str(Path("ml/glyph_v4").resolve()))
import train_v4 as v4

SEED=20261115
SIGNS=v4.SIGNS
MODEL=Path("app/src/main/assets/maya_glyph_v4.onnx")
EXPECTED_SHA="b9bb6bcc816f9c7957792302d7a1e50aaee2a1ecf3d44b8ae9b9b493cf4efba9"
OUT=Path("ml/research_v4_single/unknown_detector_results")

def logits_features(session,rows):
    all_logits=[];all_features=[]
    for i in range(0,len(rows),24):
        X=v4.tensorize([r["image"] for r in rows[i:i+24]],160).numpy()
        scores,features=session.run(["logits","features"],{"image":X})
        if scores.shape!=(len(X),10) or features.shape!=(len(X),576):
            raise ValueError("Unexpected v4 graph outputs")
        all_logits.append(scores);all_features.append(features)
    scores=np.concatenate(all_logits);features=np.concatenate(all_features)
    probs=np.exp(scores-scores.max(axis=1,keepdims=True))
    probs=probs/probs.sum(axis=1,keepdims=True)
    top=np.sort(probs,axis=1)[:,-2:]
    margin=(top[:,1]-top[:,0])[:,None]
    pmax=top[:,1,None]
    entropy=(-probs*np.log(probs+1e-9)).sum(axis=1)[:,None]
    extra=np.concatenate([margin,pmax,entropy],axis=1)
    vectors=np.concatenate([features,extra],axis=1).astype(np.float32)
    labels=np.asarray([SIGNS.index(r["sign"]) if r["sign"] in SIGNS else -1 for r in rows])
    pred=np.argmax(scores,axis=1)
    return vectors,pred,labels

def predict_metrics(scores,pred,correct,true_known,threshold):
    accepted=scores>=threshold
    if true_known is not None:
        return {"total":len(scores),"accepted":int(accepted.sum()),
            "rejected":int((~accepted).sum()),
            "correct_and_accepted":int(np.sum(accepted&(pred==true_known))),
            "incorrect_but_accepted":int(np.sum(accepted&(pred!=true_known)))}
    return {"total":len(scores),"accepted_unknown_incorrectly":int(accepted.sum()),
        "rejected_unknown":int((~accepted).sum())}

def fit(method,C,X,y):
    if method=="logistic":
        clf=LogisticRegression(C=C,class_weight="balanced",
            max_iter=2000,random_state=SEED)
    elif method in ("linear","rbf"):
        clf=SVC(C=C,kernel=method,class_weight="balanced",gamma="scale",
                random_state=SEED)
    else:raise ValueError(method)
    return make_pipeline(StandardScaler(),clf).fit(X,y)

def model_score(model,X):
    return model.decision_function(X).astype(np.float64)

def threshold_search(vknown,yknown,predknown,vunknown):
    chosen=None
    scores=np.sort(np.unique(np.concatenate([vknown,vunknown])))
    grid=np.concatenate([[scores[0]-1e-4],scores,
                         (scores[:-1]+scores[1:])/2,[scores[-1]+1e-4]])
    for th in np.unique(grid):
        known=predict_metrics(vknown,predknown,None,yknown,th)
        unknown=predict_metrics(vunknown,None,None,None,th)
        if known["correct_and_accepted"]<6 or unknown["rejected_unknown"]<20:
            continue
        quality=.5*(known["correct_and_accepted"]/len(vknown)+
                     unknown["rejected_unknown"]/len(vunknown))
        key=(quality,known["correct_and_accepted"],
             unknown["rejected_unknown"],float(th))
        if chosen is None or key>chosen["key"]:
            chosen={"threshold":float(th),"key":key,
                    "known":known,"unknown":unknown}
    return chosen

def main():
    sha=hashlib.sha256(MODEL.read_bytes()).hexdigest()
    assert sha==EXPECTED_SHA,"Incorrect v4 weights"
    session=ort.InferenceSession(str(MODEL),providers=["CPUExecutionProvider"])
    bysign,_=v4.data()
    train,val,oldtest,unknowncal,unknown_test=v4.partitions(bysign)
    neg_ids=sorted({r["sign"] for r in unknowncal})
    # Deterministic 25/15 sign-identity grouping, separate from old 40
    # unknown TEST identities. Never random-row split negative drawings.
    rng=np.random.default_rng(SEED)
    shuffled=list(rng.permutation(neg_ids))
    negative_train=set(shuffled[:25])
    negative_val=set(shuffled[25:])
    assert len(negative_val)==15
    negtr=[r for r in unknowncal if r["sign"] in negative_train]
    negval=[r for r in unknowncal if r["sign"] in negative_val]
    assert len(negtr)==50 and len(negval)==30
    Xk,pk,yk=logits_features(session,train)
    Xnk,_,_=logits_features(session,negtr)
    Xv,pv,yv=logits_features(session,val)
    Xuv,_,_=logits_features(session,negval)
    Xtest,ptest,ytest=logits_features(session,oldtest)
    Xunk,_,_=logits_features(session,unknown_test)
    X=np.concatenate([Xk,Xnk],axis=0)
    y=np.asarray([1]*len(Xk)+[0]*len(Xnk))
    grid=[]
    best=None
    for method in ("logistic","linear","rbf"):
        for C in (.01,.1,1,10):
            model=fit(method,C,X,y)
            known_scores=model_score(model,Xv)
            unknown_scores=model_score(model,Xuv)
            tuned=threshold_search(known_scores,yv,pv,unknown_scores)
            row={"model":method,"C":C,"candidate_exists":bool(tuned)}
            if tuned:
                row.update({"validation_known":tuned["known"],
                            "validation_unknown":tuned["unknown"],
                            "threshold":tuned["threshold"],
                            "balanced":tuned["key"][0]})
                if best is None or tuned["key"]>best["tuned"]["key"]:
                    best={"model":model,"method":method,"C":C,"tuned":tuned}
            grid.append(row)
    report={
        "purpose":"Research v4 explicit closed-vocabulary vs unseen sign detector",
        "source":"Pinned TWKM standardised drawings",
        "ten_known_sign_numbers":SIGNS,
        "known_training_originals":len(train),
        "unknown_training_sign_identities":len(negative_train),
        "unknown_training_drawings":len(negtr),
        "known_validation_originals":len(val),
        "unknown_validation_sign_identities":len(negative_val),
        "unknown_validation_drawings":len(negval),
        "unknown_test_sign_identities":len({r["sign"] for r in unknown_test}),
        "unknown_test_drawings":len(unknown_test),
        "candidate_grid":grid,
        "candidate_available":bool(best),
        "model_deployed":False,
        "independent_camera_accuracy":"NOT MEASURED",
        "source_groups_disjoint":True,
        "test_previously_exposed":True,
    }
    if best:
        method,C=best["method"],best["C"]
        threshold=best["tuned"]["threshold"]
        ktest=predict_metrics(model_score(best["model"],Xtest),ptest,None,ytest,threshold)
        utest=predict_metrics(model_score(best["model"],Xunk),None,None,None,threshold)
        report.update({
           "selected_model":{"type":method,"C":C,"threshold":threshold},
           "val_known":best["tuned"]["known"],
           "val_unknown":best["tuned"]["unknown"],
           "exposed_known_test":ktest,
           "unseen_sign_identity_test":utest,
           "passes_provisional_test_gate":(
                ktest["correct_and_accepted"]>=6 and
                utest["rejected_unknown"]>=60),
           "must_not_deploy_without_real_photo_test":True,
        })
    OUT.mkdir(parents=True,exist_ok=True)
    (OUT/"results.json").write_text(json.dumps(report,indent=2),encoding="utf-8")
    print("SUMMARY",json.dumps({k:report.get(k) for k in
       ("candidate_available","selected_model","val_known","val_unknown",
        "exposed_known_test","unseen_sign_identity_test","passes_provisional_test_gate")},indent=2),
       flush=True)

if __name__=="__main__":main()
