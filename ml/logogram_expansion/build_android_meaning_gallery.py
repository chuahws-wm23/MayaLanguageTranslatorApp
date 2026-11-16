#!/usr/bin/env python3
"""Build a conservative ENGLISH-glossed logogram reference gallery for Android.

Use only pinned TWKM sign IDs with source-linked logogram readings at
confidence levels 1–3, unambiguous source English glosses, and >=2
explicitly CC BY 4.0 standardized drawings. These are CATALOGUE meanings;
visual matching requires separate testing on truly novel photographs.
"""
from __future__ import annotations
from collections import defaultdict
import hashlib
import json
from pathlib import Path
import sys

from datasets import load_dataset, Image as DatasetImage

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / 'ml' / 'syllabary_ml'))
from ml.syllabary_ml.build_android_phonogram_gallery import canonical_bytes

SRC="tadad/twkm-maya-glyphs"
REV="73fe9da1d975a5f1e588c7d42c952dfda53cefd0"
OUT=ROOT / "app/src/main/assets"
MAX_SOURCE_CONFIDENCE=3
MIN_GRAPHS=2
IMAGE_BYTES=512

def normalized(value):
    return " ".join(str(value or "").strip().split())

def passable_gloss(text):
    t=text.strip().lower()
    return (3 <= len(t) <= 100 and
            "unknown" not in t and
            "unread" not in t and
            "undecipher" not in t and
            "?" not in t and
            "<" not in t and ">" not in t)

def classify(english):
    t=english.lower()
    if "deity" in t or "god" in t:
        return "Divine name or religious term"
    if "year" in t or "period" in t or "day" in t or "month" in t:
        return "Calendar or time"
    if t.startswith("to ") or " | to " in t:
        return "Action or verb"
    if "name" in t or "ruler" in t or "lord" in t or "person" in t:
        return "Person, name or title"
    if "number" in t or "zero" in t:
        return "Number"
    return "Word meaning"

def run():
    reads=load_dataset(SRC,"decipherments",split="train",revision=REV)
    graphs=load_dataset(SRC,"graphs",split="train",revision=REV)
    graphs=graphs.cast_column("image", DatasetImage(decode=False))
    labels=defaultdict(list)
    for r in reads:
        if (r.get("type") != "logogram" or not r.get("sign_reference_resolved") or
            not normalized(r.get("value"))):
            continue
        try:
            level=int(r.get("confidence_level"))
        except (TypeError, ValueError):
            continue
        if not 1 <= level <= MAX_SOURCE_CONFIDENCE:
            continue
        labels[int(r["sign_no"])].append((normalized(r["value"]),level))
    variants=defaultdict(list)
    for row in graphs:
        sign=int(row["sign_no"])
        if sign not in labels or normalized(row.get("drawing_license")) != "CC BY 4.0":
            continue
        gloss=normalized(row.get("translation"))
        if not passable_gloss(gloss):continue
        img=row.get("image")
        if not img:continue
        raw=img.get("bytes")
        if raw is None and img.get("path"):
            raw=Path(img["path"]).read_bytes()
        if not raw:continue
        try:
            binary=canonical_bytes(raw)
        except (OSError, ValueError):
            continue
        if not binary:continue
        sha=hashlib.sha256(raw).hexdigest()
        variants[sign].append((gloss,str(row["graph_no"]),sha,binary))
    rows=[]
    for sign in sorted(variants):
        exemplars=variants[sign]
        meanings={x[0] for x in exemplars}
        # Different English glosses on allographs can be legitimate but are
        # context-dependent; exclude rather than silently pick one.
        if len(meanings)!=1:continue
        distinct={}
        for gloss,graph,sha,bits in exemplars:
            distinct.setdefault(sha,(gloss,graph,sha,bits))
        if len(distinct)<MIN_GRAPHS:continue
        reading=" / ".join(sorted({r for r,c in labels[sign]}))
        if len(reading)>90:continue
        confidence=min(c for r,c in labels[sign])
        meaning=next(iter(meanings))
        for gloss,graph,sha,bits in distinct.values():
            rows.append({
                "sign":sign,
                "reading":reading,
                "english":meaning,
                "category":classify(meaning),
                "level":confidence,
                "graph":graph,
                "sha":sha,
                "bits":bits,
            })
    # Identical published drawing reused under different proposed meanings
    # is ambiguous and cannot be assigned one label by the photo search.
    by_hash=defaultdict(set)
    for row in rows:by_hash[row["sha"]].add((row["sign"],row["english"]))
    collisions={sha for sha,identities in by_hash.items() if len(identities)>1}
    rows=[r for r in rows if r["sha"] not in collisions]
    # One more per-sign minimum check after dropping cross-label collisions.
    counts=defaultdict(int)
    for row in rows:counts[row["sign"]]+=1
    rows=[r for r in rows if counts[r["sign"]]>=MIN_GRAPHS]
    image_data=bytearray()
    meta=[]
    for row in rows:
        image_data.extend(row["bits"])
        meta.append({k:v for k,v in row.items() if k!="bits"})
    n_signs=len(set(r["sign"] for r in rows))
    assert n_signs>=50, f"Too few source-backed English logograms: {n_signs}"
    assert len(image_data)==len(rows)*IMAGE_BYTES
    OUT.mkdir(parents=True,exist_ok=True)
    (OUT/"maya_twkm_logogram_bitmap.bin").write_bytes(image_data)
    index={
        "format_version":1,
        "side":64,
        "bytes_per_image":IMAGE_BYTES,
        "count":len(meta),
        "meaning_sign_count":n_signs,
        "source":"Text Database and Dictionary of Classic Mayan (TWKM), University of Bonn",
        "dataset_revision":REV,
        "source_url":"https://classicmayan.org/portal/signcatalog",
        "attribution":"Standardized drawings: Christian Prager / University of Bonn, CC BY 4.0",
        "drawing_license":"CC BY 4.0",
        "max_source_decipherment_confidence":MAX_SOURCE_CONFIDENCE,
        "min_distinct_drawings_per_sign":MIN_GRAPHS,
        "source_confidence_scale":"1 most established; 8 most speculative",
        "english_reading_status":"Proposed catalogue gloss, contextual; not photo recognition confidence",
        "real_photo_accuracy_measured":False,
        "entries":meta,
    }
    (OUT/"maya_twkm_logogram_index.json").write_text(
        json.dumps(index,ensure_ascii=False,separators=(",",":")),encoding="utf8")
    print("ENGLISH_MEANING_GALLERY", json.dumps({
        "meaning_sign_ids":n_signs,"licensed_reference_drawings":len(meta),
        "bytes":len(image_data),"sha256":hashlib.sha256(image_data).hexdigest(),
        "sample_english_meanings":[
            {"sign":r["sign"],"reading":r["reading"],"english":r["english"]}
            for r in meta if r["graph"] in ("168bt","115br","44bt","548st")
        ],
        "excluded_ambiguous_raw_drawings":len(collisions),
    },ensure_ascii=False),flush=True)

if __name__=="__main__":
    run()
