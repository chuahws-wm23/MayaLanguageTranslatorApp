#!/usr/bin/env python3
"""Audit SOURCE-ATTRIBUTED English logogram glosses in pinned TWKM.

This is a semantics-data audit, NOT image-classifier accuracy. Prints
dictionary candidates, including source confidence/variant conflicts. Never
claims source-proposed meanings are epigraphically confirmed.
"""
from __future__ import annotations
from collections import defaultdict,Counter
from pathlib import Path
import csv
import json
from datasets import load_dataset, Image

SRC="tadad/twkm-maya-glyphs"
REV="73fe9da1d975a5f1e588c7d42c952dfda53cefd0"
OUT=Path("ml/logogram_expansion/output")

def normal(value):
    return " ".join(str(value or "").strip().split())

def run():
    graph=load_dataset(SRC,"graphs",split="train",revision=REV).cast_column("image", Image(decode=False))
    decipherments=load_dataset(SRC,"decipherments",split="train",revision=REV)
    signs=load_dataset(SRC,"signs",split="train",revision=REV)
    by_sign=defaultdict(list)
    for row in decipherments:
        if row.get("type") != "logogram" or not row.get("sign_reference_resolved"):
            continue
        by_sign[int(row["sign_no"])].append({
            "reading":normal(row.get("value")),
            "confidence":row.get("confidence_level"),
            "criteria":row.get("criteria") or row.get("evidence_criteria")
        })
    translations=defaultdict(lambda:Counter())
    graphs=defaultdict(list)
    sign_metas={}
    for row in signs:
        sign_metas[int(row["sign_no"])]={
            "translation":normal(row.get("translation")),
            "fields":{k:row.get(k) for k in ('sign_no','translation')},
        }
    for row in graph:
        sign=int(row["sign_no"])
        phrase=normal(row.get("translation"))
        if phrase: translations[sign][phrase]+=1
        if (str(row.get("drawing_license") or "").strip()=="CC BY 4.0" and
            row.get("image") and (row["image"].get("bytes") or row["image"].get("path"))):
            graphs[sign].append(str(row["graph_no"]))
    candidates=[]
    for sign, dec in sorted(by_sign.items()):
        mapped=sorted({x["reading"] for x in dec if x["reading"]})
        glosses=[{"meaning":meaning, "n_graphs":count}
                for meaning,count in translations.get(sign,Counter()).most_common()]
        if glosses and graphs[sign]:
            candidates.append({
                "sign":sign,"logogram_readings":mapped,
                "english_glosses_from_graphs":glosses,
                "decipherments":dec,
                "licensed_graphs":len(graphs[sign]),
                "graphs":graphs[sign][:12],
                "sign_row_translation":sign_metas.get(sign,{}).get("translation",""),
            })
    OUT.mkdir(parents=True,exist_ok=True)
    (OUT/"twkm_logogram_meaning_candidates.json").write_text(
        json.dumps({"source":SRC,"revision":REV,
                    "note":"Source-proposed logogram values, not verified English accuracy",
                    "candidates":candidates},indent=2,ensure_ascii=False),encoding="utf8")
    report={
        "signs":len(signs),"graph_records":len(graph),
        "decipherment_rows":len(decipherments),
        "logographic_sign_ids":len(by_sign),
        "with_graph_english_and_CC_BY_drawing":len(candidates),
        "single_english_gloss":sum(len(x["english_glosses_from_graphs"])==1 for x in candidates),
        "at_least_2_ccby_graphs":sum(x["licensed_graphs"]>=2 for x in candidates),
        "at_least_3_ccby_graphs":sum(x["licensed_graphs"]>=3 for x in candidates),
        "at_least_5_ccby_graphs":sum(x["licensed_graphs"]>=5 for x in candidates),
    }
    print("REPORT",json.dumps(report,indent=2),flush=True)
    print("CANDIDATES",json.dumps(candidates[:90],ensure_ascii=False,default=str),flush=True)
    print("EXISTING_25",json.dumps(
        [x for x in candidates if x["sign"] in
         [32,184,291,4,44,507,548,122,187,1030,74,178,1,21,28,102,325,595,630,126,177,204,569,586,604]],
        ensure_ascii=False,default=str),flush=True)

if __name__=="__main__":
    run()
