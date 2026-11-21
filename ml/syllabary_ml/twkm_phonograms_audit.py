#!/usr/bin/env python3
"""Audit exact syllabogram labels from TWKM's pinned phonogram decipherments.

Research only. Claims only the source-proposed readings, not independently
verified linguistic certainty. Does not publish or distribute third-party
photographs or the full TWKM metadata database.
"""
from __future__ import annotations
import collections
import hashlib
import json
from pathlib import Path
from datasets import Image, load_dataset

SRC = 'tadad/twkm-maya-glyphs'
REV = '73fe9da1d975a5f1e588c7d42c952dfda53cefd0'
LABELS = set("a,ba,be,bi,bo,bu,ch'a,ch'o,cha,che,chi,cho,chu,e,ha,he,hi,ho,hu,i,ja,je,ji,jo,ju,k'a,k'e,k'i,k'o,k'u,ka,ke,ki,ko,ku,la,le,li,lo,lu,ma,me,mi,mo,mu,na,ne,ni,no,nu,o,pa,pe,pi,po,pu,sa,si,su,t'o,t'u,ta,te,ti,to,tu,tz'a,tz'i,tz'o,tz'u,tza,tzi,tzu,u,wa,wi,wo,xa,xi,xo,xu,ya,ye,yi,yo,yu".split(','))


def norm(value):
    return str(value or "").strip().lower().replace('’', "'").replace('ʼ', "'").replace('‘', "'")


def proposed_reads(decipherments):
    bysign = collections.defaultdict(set)
    refs = collections.defaultdict(list)
    for row in decipherments:
        if row.get("type") != "phonogram" or not row.get("sign_reference_resolved"):
            continue
        label = norm(row.get("value"))
        if label not in LABELS:
            continue
        sign = int(row["sign_no"])
        bysign[sign].add(label)
        refs[sign].append({"value": label, "confidence_level": row.get("confidence_level")})
    # If same sign ID has conflicting syllabic readings in the dataset, do
    # not force the classifier to pick one by silently discarding alternatives.
    clean = {s: next(iter(values)) for s, values in bysign.items() if len(values) == 1}
    ambiguous = {str(s): sorted(values) for s, values in bysign.items() if len(values)>1}
    return clean, ambiguous, refs


def audit():
    reads = load_dataset(SRC, 'decipherments', split='train', revision=REV)
    graphs = load_dataset(SRC, 'graphs', split='train', revision=REV).cast_column(
        'image', Image(decode=False))
    labels, ambiguous, refs = proposed_reads(reads)
    by_label = collections.defaultdict(list)
    bysha = collections.defaultdict(list)
    dropped = collections.Counter()
    for row in graphs:
        sign = int(row["sign_no"])
        if sign not in labels:
            continue
        if str(row.get("drawing_license") or "").strip() != "CC BY 4.0":
            dropped['license_not_verified_ccby4'] += 1
            continue
        item = row.get('image')
        if not item:
            dropped['no_image'] += 1
            continue
        data=item.get('bytes')
        if data is None and item.get('path'):
            data=Path(item['path']).read_bytes()
        if not data:
            dropped['no_bytes'] += 1
            continue
        sha=hashlib.sha256(data).hexdigest()
        bysha[sha].append({"label": labels[sign], "sign": sign, "graph": str(row["graph_no"]),
                           "sha256": sha, "license": "CC BY 4.0"})
    for sha, entries in bysha.items():
        if len(set(e['label'] for e in entries)) != 1:
            dropped['exact_image_conflicting_syllabic_values']+=1
            continue
        one = entries[0]
        by_label[one['label']].append(one)
        dropped['same_label_duplicate_images']+=len(entries)-1
    summary = []
    for label in sorted(LABELS):
        examples=by_label.get(label,[])
        source_ids=sorted(set(e['sign'] for e in examples))
        summary.append({"syllable":label,"image_count":len(examples),
                        "distinct_sign_id_count":len(source_ids),
                        "example_twkm_sign_ids":source_ids[:20],
                        "example_graph_ids":[r['graph'] for r in examples[:5]],
                        "decipherment_confidence_levels": sorted(set(
                          c['confidence_level'] for s in source_ids for c in refs[s]
                          if c['value']==label and c['confidence_level'] is not None))
                        })
    totals = {str(n):sum(x['image_count']>=n for x in summary) for n in (1,2,3,4,5,8)}
    report={
        "source":SRC,"revision":REV,"expected_syllables":len(LABELS),
        "source_phonogram_decipherments":sum(x['type']=="phonogram" for x in reads),
        "unambiguous_pinned_proposed_readings":len(labels),
        "excluded_ambiguous_signs":ambiguous,
        "usable_reference_image_count":sum(x['image_count'] for x in summary),
        "syllables_by_min_reference_count":totals,
        "per_syllable":summary,
        "dropped":dict(dropped),
        "meaning":"Exact linked proposed TWKM phonogram readings; some signs can be allographs or context-dependent",
        "accuracy_measured":False,"android_assets_modified":False,
        "rights":"Embedded drawings marked CC BY 4.0, Christian Prager/TWKM; other metadata reuse rights need review"
    }
    Path("ml/syllabary_ml/output").mkdir(parents=True,exist_ok=True)
    Path("ml/syllabary_ml/output/twkm_phonograms_audit.json").write_text(
        json.dumps(report,indent=2,ensure_ascii=False),encoding='utf8')
    print("SUMMARY",json.dumps({k:report[k] for k in (
      "expected_syllables","source_phonogram_decipherments",
      "unambiguous_pinned_proposed_readings","usable_reference_image_count",
      "syllables_by_min_reference_count","dropped")},indent=2),flush=True)
    print("SYLLABLE_COUNTS", json.dumps({r['syllable']:r['image_count'] for r in summary},ensure_ascii=False),flush=True)


if __name__=='__main__':
    audit()
