#!/usr/bin/env python3
"""Audit a LOCAL, expert-verified single-Maya-glyph photographic dataset.

No images are downloaded, uploaded, labelled automatically, or committed.
The single-sign model remains research-only until independent test metrics
justify deployment. Identical source drawings (including newly photographed
or recoloured copies) MUST share the same source_group.
"""
from __future__ import annotations
import argparse,csv,hashlib,json,sys
from collections import Counter,defaultdict
from pathlib import Path
import numpy as np
from PIL import Image,ImageOps

SIGNS={32,184,291,4,44,507,548,122,187,1030}
SPLITS={"train","validation","test"}
COLUMNS=("image_path","twkm_sign","source_group","source_url",
         "license","verified_by","split")
EXTS={".png",".jpg",".jpeg",".webp"}

def audit(manifest:Path,root:Path):
    root=root.resolve()
    with manifest.open(newline="",encoding="utf-8-sig") as f:
        reader=csv.DictReader(f)
        if not reader.fieldnames or set(COLUMNS)-set(reader.fieldnames):
            raise ValueError("Manifest needs columns: "+", ".join(COLUMNS))
        rows=list(reader)
    if not rows:raise ValueError("No labelled images. See the CSV template.")
    errors,warnings=[],[]
    groups=defaultdict(set)
    shas=defaultdict(list)
    phashes=[]
    class_counts=defaultdict(lambda:Counter())
    for number,row in enumerate(rows,start=2):
        path_string=row["image_path"].strip()
        sign=row["twkm_sign"].strip()
        group=row["source_group"].strip()
        split=row["split"].strip().lower()
        missing=[s for s in ("image_path","twkm_sign","source_group","source_url",
                             "license","verified_by","split") if not row[s].strip()]
        if missing:
            errors.append(f"row {number}: missing {', '.join(missing)}")
            continue
        if sign!="UNKNOWN":
            if not sign.isdigit() or int(sign) not in SIGNS:
                errors.append(f"row {number}: unsupported or unverified sign {sign!r}; use UNKNOWN for explicit out-of-scope")
                continue
        if split not in SPLITS:
            errors.append(f"row {number}: split must be train, validation or test")
            continue
        try:
            image_path=(root/path_string).resolve()
            image_path.relative_to(root)
        except (ValueError,OSError):
            errors.append(f"row {number}: image path escapes photo folder")
            continue
        if image_path.suffix.lower() not in EXTS or not image_path.is_file():
            errors.append(f"row {number}: missing PNG/JPEG/WEBP image {path_string}")
            continue
        try:
            with Image.open(image_path) as src:
                photo=ImageOps.exif_transpose(src).convert("L")
                width,height=photo.size
                if min(width,height)<96:
                    errors.append(f"row {number}: image is only {width}x{height}px (minimum 96px each side)")
                    continue
                if width*height>20_000_000:
                    errors.append(f"row {number}: image dimensions are too large")
                    continue
                img=np.asarray(photo)
                if float(img.std())<6:
                    errors.append(f"row {number}: near-blank image")
                    continue
                # Perceptual hash for REVIEW only; similar glyph shapes can
                # legitimately have similar hashes, so do not equate them.
                thumb=np.asarray(photo.resize((9,8),Image.Resampling.LANCZOS))
                bits=thumb[:,:8]>thumb[:,1:]
                pvalue=sum(int(bit)<<k for k,bit in enumerate(bits.flat))
        except (OSError,ValueError) as e:
            errors.append(f"row {number}: invalid image: {str(e)[:70]}")
            continue
        digest=hashlib.sha256(image_path.read_bytes()).hexdigest()
        groups[group].add(split)
        shas[digest].append((number,split,sign,group))
        phashes.append((number,split,sign,group,pvalue))
        class_counts[sign][split]+=1
    for group,splits in groups.items():
        if len(splits)>1:
            errors.append(f"source_group {group!r} appears in multiple splits: {sorted(splits)}")
    for digest,records in shas.items():
        if len(records)>1:
            splits={r[1] for r in records}
            signs={r[2] for r in records}
            if len(splits)>1 or len(signs)>1:
                errors.append(f"identical image SHA {digest[:12]} is used across splits or conflicting signs: {records}")
            else:
                warnings.append(f"duplicate identical image SHA {digest[:12]} within {next(iter(splits))}")
    # Near-duplicate candidates with the SAME claimed sign in different
    # splits require human review; do NOT silently discard valid allographs.
    for i in range(len(phashes)):
        for j in range(i+1,len(phashes)):
            a,b=phashes[i],phashes[j]
            if a[1]!=b[1] and a[2]==b[2] and (a[4]^b[4]).bit_count()<=3:
                warnings.append(f"Possible near-duplicate across splits for sign {a[2]} (rows {a[0]}, {b[0]}); check source_group")
    readiness=[]
    for sign in sorted(SIGNS):
        n=class_counts[str(sign)]
        if n["train"]<8 or n["validation"]<3 or n["test"]<5:
            readiness.append(f"TWKM {sign}: need >=8 training, >=3 validation and >=5 independent test photos; now {dict(n)}")
    unknown=class_counts["UNKNOWN"]
    if unknown["validation"]<20 or unknown["test"]<50:
        readiness.append(f"UNKNOWN negatives: need >=20 validation and >=50 independent test photos; now {dict(unknown)}")
    report={
        "total_manifest_rows":len(rows),
        "valid_photo_rows":sum(sum(v.values()) for v in class_counts.values()),
        "class_counts":{s:dict(v) for s,v in sorted(class_counts.items())},
        "distinct_source_groups":len(groups),
        "exact_unique_file_hashes":len(shas),
        "errors":errors,
        "warnings":warnings[:100],
        "readiness_gaps":readiness,
        "ready_for_independent_evaluation":not errors and not readiness and not warnings,
        "note":"Counts are provisional minimum research gates, not proof of reliability. Expert-labelled real-world photos and independent device evaluation are still necessary.",
    }
    return report

def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--manifest",required=True,type=Path)
    ap.add_argument("--root",required=True,type=Path)
    ap.add_argument("--out",type=Path,default=None)
    ap.add_argument("--strict",action="store_true",
        help="also fail when per-class counts or near-duplicate review gaps remain")
    args=ap.parse_args()
    report=audit(args.manifest,args.root)
    if args.out:
        args.out.parent.mkdir(parents=True,exist_ok=True)
        args.out.write_text(json.dumps(report,indent=2),encoding="utf-8")
    print(json.dumps(report,indent=2))
    if report["errors"] or (args.strict and not report["ready_for_independent_evaluation"]):
        sys.exit(2)

if __name__=="__main__":main()
