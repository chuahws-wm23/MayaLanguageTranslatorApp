#!/usr/bin/env python3
"""Honest small TWKM graph-variant reference retrieval diagnostic.

One held-out graph variant per English logogram identity, all other graphs
in lookup gallery. Not artifact-independent and NOT phone photo accuracy.
"""
from __future__ import annotations
from collections import defaultdict
import json
import math
from pathlib import Path

ROOT=Path("app/src/main/assets")
OUT=Path("ml/logogram_expansion/output")

def run():
    index=json.loads((ROOT/"maya_twkm_logogram_index.json").read_text())
    raw=(ROOT/"maya_twkm_logogram_bitmap.bin").read_bytes()
    n=index["count"]
    refs=index["entries"]
    assert len(raw)==512*n==512*len(refs)
    bysign=defaultdict(list)
    for i,ref in enumerate(refs):
        bysign[int(ref["sign"])].append(i)
    assert len(bysign)>=50 and min(len(s) for s in bysign.values())>=2
    # Keep one graph variant (from the existing source) out per sign.
    test={sorted(ids,key=lambda i:refs[i]["graph"])[-1] for ids in bysign.values()}
    train=[i for i in range(n) if i not in test]
    ints=[int.from_bytes(raw[i*512:(i+1)*512],"big") for i in range(n)]
    counts=[x.bit_count() for x in ints]
    outcomes=[]
    for query_idx in sorted(test):
        q=ints[query_idx]
        qn=counts[query_idx]
        byclass={}
        for idx in train:
            r=ints[idx]
            score=(q & r).bit_count()/math.sqrt(qn*counts[idx]) if qn and counts[idx] else 0.
            sign=int(refs[idx]["sign"])
            if sign not in byclass or score>byclass[sign]["score"]:
                byclass[sign]={"sign":sign,"score":score,"drawing":refs[idx]["graph"]}
        ranked=sorted(byclass.values(),key=lambda x:(-x["score"],x["sign"]))
        expected=int(refs[query_idx]["sign"])
        outcomes.append({
            "expected_sign":expected,
            "expected_reading":refs[query_idx]["reading"],
            "expected_english":refs[query_idx]["english"],
            "heldout_graph":refs[query_idx]["graph"],
            "best_sign":ranked[0]["sign"],
            "best_score":round(ranked[0]["score"],5),
            "top3": [x["sign"] for x in ranked[:3]],
            "correct_top1":ranked[0]["sign"]==expected,
            "correct_top3":expected in [x["sign"] for x in ranked[:3]],
        })
    top1=sum(x["correct_top1"] for x in outcomes)
    top3=sum(x["correct_top3"] for x in outcomes)
    result={
        "task":"TWKM English logogram visual retrieval",
        "test_graph_variants":len(outcomes),
        "training_gallery_graphs":len(train),
        "represented_english_logogram_signs":len(bysign),
        "correct_top1":top1,
        "correct_top3":top3,
        "top1_fraction":round(top1/len(outcomes),5),
        "top3_fraction":round(top3/len(outcomes),5),
        "warning":"This is a SOURCE-RELATED graph-variant experiment. The images are TWKM drawings, not independent physical artifacts or camera photographs. Cannot claim image translation accuracy.",
        "not_an_on_device_classifier":True,
        "outcomes":outcomes,
    }
    OUT.mkdir(parents=True,exist_ok=True)
    (OUT/"english_gallery_graph_variant_evaluation.json").write_text(
        json.dumps(result,indent=2,ensure_ascii=False),encoding="utf8")
    print("TOP1",top1,"/",len(outcomes),"TOP3",top3,"/",len(outcomes))
    print("SUMMARY",json.dumps({k:v for k,v in result.items() if k!="outcomes"}),flush=True)

if __name__=="__main__":
    run()
