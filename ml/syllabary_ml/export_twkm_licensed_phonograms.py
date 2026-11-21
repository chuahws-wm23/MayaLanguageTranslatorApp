#!/usr/bin/env python3
"""Export only CC BY 4.0 TWKM standardized drawings with source-linked phonogram labels.

No chart images or unlicensed third-party photographs. The TWKM phonogram
values are PROPOSED scholarly readings, not unconditionally verified truth.
This experimental pack is source-linked, attributed, and stays separate
from any claimed Android production recognition release.
"""
from __future__ import annotations
import collections
import csv
import hashlib
import io
import json
from pathlib import Path
from PIL import Image as PILImage
from datasets import Image, load_dataset
from twkm_phonograms_audit import SRC, REV, proposed_reads

OUT=Path("ml/syllabary_ml/output/twkm_licensed_phonograms")

def main():
    d=load_dataset(SRC, 'decipherments',split='train',revision=REV)
    signs,ambiguities,_=proposed_reads(d)
    graphs=load_dataset(SRC,'graphs',split='train',revision=REV).cast_column(
        'image',Image(decode=False))
    seen=collections.defaultdict(list)
    for row in graphs:
        sign=int(row['sign_no'])
        if sign not in signs or str(row.get('drawing_license') or '').strip()!='CC BY 4.0':
            continue
        im=row.get('image')
        if not im:continue
        raw=im.get('bytes')
        if raw is None and im.get('path'):
            raw=Path(im['path']).read_bytes()
        if not raw:continue
        sha=hashlib.sha256(raw).hexdigest()
        seen[sha].append((sign,signs[sign],str(row['graph_no']),raw))
    OUT.mkdir(parents=True,exist_ok=True)
    fields=['image_path','syllable','twkm_sign_no','graph_no',
            'source_sha256','source_group','source_url','license','label_status']
    records=[]
    for sha,examples in sorted(seen.items()):
        if len({e[1] for e in examples})>1:continue
        sign,label,graph,raw=examples[0]
        try:
            with PILImage.open(io.BytesIO(raw)) as img:
                img.load()
                if min(img.size)<24 or img.width*img.height>16_000_000:
                    continue
                name=f'{sha[:20]}.png'
                img.convert('L').save(OUT/name,optimize=True)
        except (OSError,ValueError):
            continue
        records.append({
            'image_path':name,'syllable':label,'twkm_sign_no':sign,
            'graph_no':graph,'source_sha256':sha,
            'source_group':f'TWKM-sign-{sign}',
            'source_url':'https://classicmayan.org/portal/signcatalog',
            'license':'CC BY 4.0',
            'label_status':'SOURCE_PROPOSED_PHONOGRAM_READING_NOT_INDEPENDENTLY_VERIFIED'
        })
    with (OUT/'labels.csv').open('w',encoding='utf-8',newline='') as f:
        writer=csv.DictWriter(f,fieldnames=fields)
        writer.writeheader()
        writer.writerows(records)
    n=collections.Counter(r['syllable'] for r in records)
    info={'images':len(records),'phonogram_syllable_classes':len(n),
          'per_class_counts':dict(sorted(n.items())),
          'dataset':SRC,'pinned_revision':REV,
          'rights':'Graph drawings by Christian Prager, TWKM University of Bonn, CC BY 4.0',
          'notes':['Proposed scholarly phonogram values may change; do not call verified translations.',
                   'Every image includes source TWKM sign no and graph no.',
                   'A graph variant is not an independent photograph/artifact.',
                   'Never copy the images of the user-provided syllabary charts into GitHub.']}
    (OUT/'provenance.json').write_text(json.dumps(info,indent=2),encoding='utf8')
    (OUT/'ATTRIBUTION.txt').write_text(
        'TWKM Text Database and Dictionary of Classic Mayan, University of Bonn.\n'
        'Standardized graph drawings: Christian Prager. CC BY 4.0.\n'
        'https://classicmayan.org/portal/signcatalog\n'
        'Source data via https://huggingface.co/datasets/tadad/twkm-maya-glyphs\n'
        'Syllabic values are proposed readings; verify context before translation.\n',
        encoding='utf8')
    print('SUMMARY',json.dumps({'images':len(records),'labels':len(n),
         'per_syllable':dict(sorted(n.items()))}),flush=True)

if __name__=='__main__':
    main()
