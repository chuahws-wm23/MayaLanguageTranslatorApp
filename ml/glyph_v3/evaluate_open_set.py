"""Research-only open-set evaluation on TWKM originals.

Fixed five supported sign classes, sign-disjoint unsupported classes.
Training/inner-calibration/outer-test positives are original-image disjoint.
Unknown threshold calibration sign IDs NEVER overlap unknown test sign IDs.
The evaluation measures catalogue drawings, NOT different-source phone photos.
"""
import collections,hashlib,io,json,math,os,random,struct,sys,time
from pathlib import Path
import numpy as np,torch,joblib
from datasets import load_dataset,Image
from sklearn.model_selection import StratifiedKFold
from sklearn.svm import SVC
from torch import nn
from torchvision import models
sys.path.insert(0,str(Path("ml/glyph_v2").resolve()))
import train
import train_transfer

HERE=Path("ml/glyph_v3")
OUT=HERE/"outputs"
OUT.mkdir(exist_ok=True,parents=True)
SEED=20261114
SIGNS=train.SIGNS
BACKBONE=Path("ml/glyph_v2/outputs/transfer/mobilenet_v3_small_backbone.pt")
VIEWS=4
numthreads=min(4,os.cpu_count() or 2)
torch.set_num_threads(numthreads)

def get_unknown_examples():
    print("Reading pinned TWKM full catalogue for out-of-scope sign samples",flush=True)
    graphs=load_dataset(train.REPO,"graphs",split="train",revision=train.REV,
        cache_dir=os.environ.get("HF_DATASETS_CACHE","/tmp/glyph-v3-cache")).cast_column("image",Image(decode=False))
    by_hash=collections.defaultdict(list)
    for r in graphs:
        img=r.get("image")
        if not img:continue
        raw=img.get("bytes")
        if raw is None and img.get("path"):raw=Path(img["path"]).read_bytes()
        if raw:
            by_hash[hashlib.sha256(raw).hexdigest()].append(r)
    groups=collections.defaultdict(list)
    for sha,items in by_hash.items():
        labels={str(r.get("sign_no")) for r in items}
        if len(labels)!=1: continue
        r=items[0]
        try:s=int(r.get("sign_no"))
        except (ValueError,TypeError):continue
        if s in SIGNS:continue
        try:image=train.process(r["image"])
        except Exception:continue
        groups[s].append({"sign":s,"graph":str(r["graph_no"]),"sha256":sha,"im":image})
    eligible=[s for s,g in groups.items() if len(g)>=2]
    rng=random.Random(SEED+99)
    eligible.sort()
    rng.shuffle(eligible)
    assert len(eligible)>=100, f"Need 100+ out-of-scope signs, found {len(eligible)}"
    chosen=eligible[:100]
    calibrator_signs=set(chosen[:50])
    test_signs=set(chosen[50:])
    assert calibrator_signs.isdisjoint(test_signs)
    rows=[]
    for s in chosen:
        rows.extend(sorted(groups[s],key=lambda r:r["sha256"])[:2])
    cal=[r for r in rows if r["sign"] in calibrator_signs]
    test=[r for r in rows if r["sign"] in test_signs]
    assert len(cal)==100 and len(test)==100
    return cal,test,sorted(calibrator_signs),sorted(test_signs)

def embeddings(model,images):
    return train_transfer.embed(model,images)

def get_model():
    m=models.mobilenet_v3_small(weights=None)
    m.classifier=nn.Identity()
    m.load_state_dict(torch.load(BACKBONE,map_location="cpu",weights_only=True))
    m.eval()
    return m

def fit_svm(views,indices,y):
    x=views[indices].reshape(-1,views.shape[-1])
    labels=np.repeat(y[indices],VIEWS)
    clf=SVC(kernel="rbf",C=10,gamma="scale")
    clf.fit(x,labels)
    return clf

def accepted_values(clf,train_indices,known_features,unknown_features):
    """Score per image: nearest cosine to same-predicted-class reference and
    nearest same-class cosine minus most similar OTHER class reference."""
    nclasses=len(SIGNS)
    gallery=known_features[train_indices]
    gallery_labels=known_labels[train_indices]
    predicted=clf.predict(unknown_features).astype(int)
    similarities=unknown_features @ gallery.T
    scores=np.zeros((len(predicted),2),dtype=np.float32)
    for i,pred in enumerate(predicted):
        in_class=gallery_labels==pred
        near=float(np.max(similarities[i,in_class]))
        other=float(np.max(similarities[i,~in_class]))
        scores[i]=[near,near-other]
    return predicted,scores

def choose_threshold(pos_pred,pos_scores,pos_true,unknown_scores):
    # Only inner-validation ORIGINAL known drawings and calibration unknowns.
    # A rule must identify >=40% of calibration known samples correctly.
    candidates=[]
    for similarity in np.linspace(.5,.995,100):
        for margin in np.linspace(-.15,.20,36):
            accepted=(pos_scores[:,0]>=similarity)&(pos_scores[:,1]>=margin)
            known_good=np.mean(accepted&(pos_pred==pos_true))
            unknown_rejected=np.mean((unknown_scores[:,0]<similarity)|
                                     (unknown_scores[:,1]<margin))
            balance=.5*(known_good+unknown_rejected)
            if known_good>=.4:
                candidates.append((balance,known_good,unknown_rejected,float(similarity),float(margin)))
    if not candidates:
        return .90,0.
    return sorted(candidates,key=lambda z:(z[0],z[1],z[2],-z[3],-z[4]))[-1][3:]

def main():
    global known_labels
    start=time.time()
    torch.manual_seed(SEED);np.random.seed(SEED)
    known,_=train.get_rows()
    known_rows=[r for sign in SIGNS for r in sorted(known[sign],key=lambda z:z["sha256"])]
    known_labels=np.asarray([SIGNS.index(r["sign"]) for r in known_rows],dtype=np.int64)
    assert len(known_rows)==38
    rng=np.random.default_rng(SEED)
    known_views=[]
    for r in known_rows:
        known_views.append(r["im"])
        known_views.extend(train.augment(r["im"],rng) for _ in range(VIEWS-1))
    unknown_cal,unknown_test,cal_signs,test_signs=get_unknown_examples()
    model=get_model()
    known_features=embeddings(model,[r["im"] for r in known_rows])
    view_features=embeddings(model,known_views).reshape(len(known_rows),VIEWS,-1)
    cal_features=embeddings(model,[r["im"] for r in unknown_cal])
    test_features=embeddings(model,[r["im"] for r in unknown_test])
    assert known_features.shape==(38,576)

    folds=[]
    predicted_known=np.full(len(known_rows),-1,dtype=int)
    accepted_known=np.zeros(len(known_rows),dtype=bool)
    thresholds=[]
    test_unknown_by_fold=[]
    for fold,(training,held) in enumerate(StratifiedKFold(n_splits=5,shuffle=True,
                   random_state=SEED).split(np.zeros(len(known_rows)),known_labels),1):
        # Inner validation ORIGINALS are chosen only from outer TRAIN.
        shuffled=np.random.default_rng(SEED+fold)
        inner_val=[]
        for label in range(len(SIGNS)):
            label_ids=training[known_labels[training]==label].copy()
            shuffled.shuffle(label_ids)
            inner_val.append(int(label_ids[0]))
        inner_val=np.asarray(inner_val,dtype=int)
        inner_train=np.asarray([idx for idx in training if idx not in set(inner_val)],dtype=int)
        calibrator=fit_svm(view_features,inner_train,known_labels)
        cal_pred,positive_cal_scores=accepted_values(calibrator,inner_train,known_features,
                                                       known_features[inner_val])
        _,negative_cal_scores=accepted_values(calibrator,inner_train,known_features,cal_features)
        sim,margin=choose_threshold(cal_pred,positive_cal_scores,
                                    known_labels[inner_val],negative_cal_scores)
        thresholds.append((sim,margin))

        classifier=fit_svm(view_features,training,known_labels)
        pos_pred,pos_scores=accepted_values(classifier,training,known_features,
                                             known_features[held])
        neg_pred,neg_scores=accepted_values(classifier,training,known_features,
                                             test_features)
        positive_accept=(pos_scores[:,0]>=sim)&(pos_scores[:,1]>=margin)
        unknown_accept=(neg_scores[:,0]>=sim)&(neg_scores[:,1]>=margin)
        predicted_known[held]=pos_pred
        accepted_known[held]=positive_accept
        fold_result={
          "fold":fold, "outer_train_originals":len(training),
          "outer_test_originals":len(held),
          "inner_calibration_originals":len(inner_val),
          "calibration_unknown_signs":len(cal_signs),
          "test_unknown_signs":len(test_signs),
          "threshold_min_similarity":round(sim,4),
          "threshold_min_margin":round(margin,4),
          "correctly_classified_known":int(np.sum(pos_pred==known_labels[held])),
          "correct_and_accepted_known":int(np.sum(positive_accept&(pos_pred==known_labels[held]))),
          "rejected_known":int(np.sum(~positive_accept)),
          "unknown_test_accepted":int(np.sum(unknown_accept)),
          "unknown_test_total":len(unknown_test),
          "per_known_original":[{"graph":known_rows[k]["graph"],"sign":known_rows[k]["sign"],
             "predicted_sign":SIGNS[int(pred)],"accepted":bool(ok),
             "correct":bool(pred==known_labels[k]),"similarity":round(float(scores[0]),4),
             "nearest_class_margin":round(float(scores[1]),4)}
              for k,pred,ok,scores in zip(held,pos_pred,positive_accept,pos_scores)],
          "unknown_test_rejected":int(np.sum(~unknown_accept)),
        }
        folds.append(fold_result)
        test_unknown_by_fold.append(unknown_accept)
        print("FOLD",fold,
              "known correct",fold_result["correctly_classified_known"],"/",len(held),
              "known accepted correct",fold_result["correct_and_accepted_known"],"/",len(held),
              "unknown rejected",fold_result["unknown_test_rejected"],"/",len(unknown_test),
              "threshold",sim,margin,flush=True)

    assert np.all(predicted_known!=-1)
    correct_known=(predicted_known==known_labels)
    correct_and_accepted=correct_known&accepted_known
    known_recall=float(np.mean(correct_and_accepted))
    test_unknown=np.stack(test_unknown_by_fold)
    unknown_reject_mean=float(np.mean(~test_unknown))
    median_sim=float(np.median([t[0] for t in thresholds]))
    median_margin=float(np.median([t[1] for t in thresholds]))
    report={
     "experiment":"v3 open-set screening of v2 MobileNetV3+SVM on TWKM",
     "supported_signs":SIGNS, "source":train.REPO,"revision":train.REV,"seed":SEED,
     "positive_training_sources":"38 distinct catalog drawings, each train/test split by original SHA",
     "negative_definition":"TWKM signs OUTSIDE the five supported sign codes",
     "negative_signs_used_only_for_calibration":cal_signs,
     "negative_signs_used_only_for_testing":test_signs,
     "negative_calibration_images":len(unknown_cal),
     "negative_test_images_per_fold":len(unknown_test),
     "closed_set_known_correct":int(correct_known.sum()),
     "closed_set_known_total":len(correct_known),
     "screened_correct_and_accepted_known":int(correct_and_accepted.sum()),
     "screened_known_total":len(known_rows),
     "screened_known_accuracy":round(known_recall,4),
     "known_rejection_count":int(np.sum(~accepted_known)),
     "unknown_rejection_mean_over_folds":round(unknown_reject_mean,4),
     "unknown_false_accept_rate_mean_over_folds":round(1-unknown_reject_mean,4),
     "balanced_known_correct_plus_unknown_rejection":round(.5*(known_recall+unknown_reject_mean),4),
     "deployed_rejection_similarity_threshold":round(median_sim,4),
     "deployed_rejection_margin_threshold":round(median_margin,4),
     "folds":folds,
     "limitations":[
       "All positives and unknowns are standardized illustrations FROM THE SAME TWKM catalogue, not independent museum or phone-photo data.",
       "This protocol excludes each known test drawing and its augmentations from fit/calibration, and unknown test SIGN IDs are disjoint from calibration unknown sign IDs.",
       "The same 100 unknown test drawings are scored across five different fold models; do not treat 500 predictions as 500 independent images.",
       "Thresholds were chosen by inner validation on tiny positive sets; estimates are unstable and not calibrated to clinical or real-world probabilities.",
       "The shipped v3 model is retrained on all 38 known drawings, so its actual test-on-new-originals performance remains unknown.",
       "No guarantee on handwriting, cacao, deer, damaged codices, or unknown glyphs of different domains.",
       "The median deployment threshold is a provisional prototype choice and has no external calibration."
      ],
     "runtime_seconds":round(time.time()-start,1)}
    OUT.mkdir(parents=True,exist_ok=True)
    (OUT/"evaluation.json").write_text(json.dumps(report,indent=2))
    # 38 reference embeddings from trained full-model's known classes; no source drawings shipped.
    gallery_path=OUT/"maya_gallery_v3.bin"
    with gallery_path.open("wb") as f:
        f.write(struct.pack("<II",len(known_rows),known_features.shape[1]))
        f.write(bytes(int(v) for v in known_labels))
        f.write(known_features.astype("<f4").tobytes())
    metadata={"num_reference_originals":len(known_rows),"embedding_dim":576,
      "class_indices":list(range(len(SIGNS))),"class_sign_numbers":SIGNS,
      "gallery_format":"uint32 LE count, uint32 LE dimension, uint8 class indices, float32 LE row-major unit vectors",
      "threshold_min_similarity":report["deployed_rejection_similarity_threshold"],
      "threshold_min_margin":report["deployed_rejection_margin_threshold"],
      "gallery_sha256":hashlib.sha256(gallery_path.read_bytes()).hexdigest(),
      "accuracy_claim":"TWKM in-catalogue cross-validation only; no independent photo accuracy"}
    (OUT/"metadata.json").write_text(json.dumps(metadata,indent=2))
    print("SUMMARY",json.dumps({k:report[k] for k in [
      "closed_set_known_correct","closed_set_known_total","screened_known_accuracy",
      "unknown_rejection_mean_over_folds","balanced_known_correct_plus_unknown_rejection",
      "deployed_rejection_similarity_threshold","deployed_rejection_margin_threshold"]},indent=2),flush=True)
    print("Gallery bytes",gallery_path.stat().st_size,"elapsed",report["runtime_seconds"],flush=True)

if __name__=="__main__":main()
