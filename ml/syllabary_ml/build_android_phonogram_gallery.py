#!/usr/bin/env python3
"""Build a small OFFLINE Android gallery of source-attributed TWKM phonograms.

Only copyrighted graph drawings explicitly labelled CC BY 4.0 are included.
Never package the four user-supplied syllabary chart images: their reuse
permission is unresolved. Labels are TWKM PROPOSED syllabic values, not
verified word translations. Pinned revision for reproducibility.
"""
from __future__ import annotations
from collections import defaultdict
import hashlib
import io
import json
from pathlib import Path
import sys

import numpy as np
from PIL import Image, ImageOps
from datasets import load_dataset, Image as DatasetImage

from twkm_phonograms_audit import SRC, REV, LABELS, proposed_reads

SIDE = 64
MARGIN = 4
OUT = Path("app/src/main/assets")


def otsu(gray: np.ndarray) -> int:
    hist = np.bincount(gray.ravel(), minlength=256).astype(np.float64)
    total = float(hist.sum())
    accum = 0.
    weighted = 0.
    mean = float(np.arange(256).dot(hist))
    best, best_score = 128, -1.
    for threshold in range(255):
        count = hist[threshold]
        accum += count
        weighted += count * threshold
        if accum <= 0. or accum >= total: continue
        a = weighted / accum
        b = (mean - weighted) / (total - accum)
        score = accum * (total - accum) * (a-b)*(a-b)
        if score > best_score:
            best, best_score = threshold, score
    return best


def canonical_bytes(raw: bytes) -> bytes | None:
    with Image.open(io.BytesIO(raw)) as image:
        if min(image.size) < 24 or image.width * image.height > 16_000_000:
            return None
        rgba = image.convert("RGBA")
        white = Image.new("RGBA", rgba.size, (255,255,255,255))
        white.alpha_composite(rgba)
        gray = np.asarray(white.convert("L"))
    border = np.r_[gray[0],gray[-1],gray[:,0],gray[:,-1]]
    if float(np.median(border)) < 110:
        gray = 255-gray
    ink = gray <= otsu(gray)
    density = float(ink.mean())
    if not 0.003 <= density <= 0.85:
        return None
    yy, xx = np.nonzero(ink)
    left,right,top,bottom = xx.min(),xx.max()+1,yy.min(),yy.max()+1
    crop = Image.fromarray((ink[top:bottom,left:right]*255).astype(np.uint8))
    ratio = (SIDE - 2*MARGIN)/max(crop.size)
    new_size = tuple(max(1,round(d*ratio)) for d in crop.size)
    resized = np.asarray(crop.resize(new_size,Image.Resampling.BILINEAR))>127
    centered = np.zeros((SIDE,SIDE),dtype=np.uint8)
    offset_x = (SIDE-new_size[0])//2
    offset_y = (SIDE-new_size[1])//2
    centered[offset_y:offset_y+new_size[1],
             offset_x:offset_x+new_size[0]] = resized
    if centered.sum() < 6:
        return None
    return np.packbits(centered,bitorder="big").tobytes()


def main():
    words = load_dataset(SRC,"decipherments",split="train",revision=REV)
    clean, ambiguous, _ = proposed_reads(words)
    graphrows = load_dataset(SRC,"graphs",split="train",revision=REV)
    graphrows = graphrows.cast_column("image",DatasetImage(decode=False))
    provenance_by_sha: dict[str,list[dict]] = defaultdict(list)
    raw_by_sha = {}
    for row in graphrows:
        sign = int(row["sign_no"])
        if sign not in clean or str(row.get("drawing_license") or "").strip() != "CC BY 4.0":
            continue
        record = row.get("image")
        if not record: continue
        raw = record.get("bytes")
        if raw is None and record.get("path"):
            raw = Path(record["path"]).read_bytes()
        if not raw: continue
        sha = hashlib.sha256(raw).hexdigest()
        provenance_by_sha[sha].append({
            "syllable":clean[sign], "twkm_sign":sign,
            "graph":str(row["graph_no"]),
        })
        raw_by_sha[sha] = raw

    records=[]
    bitplanes=bytearray()
    discarded_conflicts = 0
    for sha, candidates in sorted(provenance_by_sha.items()):
        if len({r["syllable"] for r in candidates}) != 1:
            discarded_conflicts += 1
            continue
        candidate = sorted(candidates,key=lambda r:(r["twkm_sign"],r["graph"]))[0]
        try:
            plane=canonical_bytes(raw_by_sha[sha])
        except (OSError, ValueError):
            continue
        if plane is None: continue
        assert len(plane) == SIDE*SIDE//8
        bitplanes.extend(plane)
        records.append({**candidate, "sha256":sha})

    syllables=sorted(set(r["syllable"] for r in records))
    assert len(syllables) >= 80, f"Too few syllabus values: {len(syllables)}"
    assert len(records) >= 250, f"Too few licensed references: {len(records)}"
    assert all(r["syllable"] in LABELS for r in records)
    assert len(bitplanes) == len(records)*512

    OUT.mkdir(parents=True,exist_ok=True)
    (OUT/"maya_twkm_phonogram_bitmap.bin").write_bytes(bitplanes)
    index={
        "format_version":1,"side":SIDE,"count":len(records),
        "bytes_per_image":512,"syllables":syllables,"entries":records,
        "source":"TWKM / Text Database and Dictionary of Classic Mayan, University of Bonn",
        "drawing_author":"Christian Prager",
        "drawing_license":"CC BY 4.0",
        "attribution_url":"https://classicmayan.org/portal/signcatalog",
        "dataset":"https://huggingface.co/datasets/tadad/twkm-maya-glyphs",
        "dataset_revision":REV,
        "reading_status":"PROPOSED TWKM PHONOGRAM VALUES; NOT VERIFIED TRANSLATIONS",
        "independent_photo_accuracy_established":False,
        "excluded_ambiguous_ids":len(ambiguous),
        "dropped_cross_label_duplicates":discarded_conflicts,
    }
    (OUT/"maya_twkm_phonogram_index.json").write_text(
        json.dumps(index,ensure_ascii=False,separators=(",",":")),encoding="utf-8")
    print("ANDROID_CATALOGUE",json.dumps({
        "licensed_drawings":len(records),"syllables":len(syllables),
        "binary_bytes":len(bitplanes),
        "excluded_ambiguous_ids":len(ambiguous),
        "sha256_bitmap":hashlib.sha256(bitplanes).hexdigest()
    }),flush=True)


if __name__ == "__main__":
    main()
