#!/usr/bin/env python3
"""Package original licensed TWKM artwork for display, without changing inference assets.

Fetch data/graphs/train-00000-of-00001.parquet from pinned revision
73fe9da1d975a5f1e588c7d42c952dfda53cefd0 of tadad/twkm-maya-glyphs.
Requires pyarrow and Pillow. Exact source SHA and CC BY 4.0 are mandatory.
"""
import argparse,hashlib,io,json
from pathlib import Path
import pyarrow.parquet as pq
from PIL import Image
ROOT=Path(__file__).resolve().parents[2]

def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--parquet',required=True,type=Path)
    args=ap.parse_args()
    assets=ROOT/'app/src/main/assets';out=assets/'reference_art';out.mkdir(exist_ok=True)
    required={}
    for kind in ['logogram','phonogram']:
        for row in json.loads((assets/f'maya_twkm_{kind}_index.json').read_text())['entries']:
            sha=row.get('sha',row.get('sha256')); graph=row['graph']
            if graph in required:assert required[graph]==sha
            required[graph]=sha
    source={str(r['graph_no']):r for r in pq.read_table(args.parquet).to_pylist()}
    entries={}
    for graph,expected in sorted(required.items()):
        row=source[graph]
        assert row['drawing_license']=='CC BY 4.0',graph
        raw=row['image']['bytes'];actual=hashlib.sha256(raw).hexdigest()
        assert actual==expected==row['image_sha256'],graph
        with Image.open(io.BytesIO(raw)) as image:
            width,height=image.size;extension={'JPEG':'jpg','PNG':'png','WEBP':'webp'}[image.format]
            assert 1<=width<=4096 and 1<=height<=4096
        name=f'{actual}.{extension}'
        (out/name).write_bytes(raw)
        entries[graph]={'file':name,'sha256':actual,'width':width,'height':height,
            'creator':row['drawing_creator'],'license':row['drawing_license'],'source_url':row['source_image_url']}
    meta={'format_version':1,'source_dataset':'tadad/twkm-maya-glyphs',
        'source_revision':'73fe9da1d975a5f1e588c7d42c952dfda53cefd0','entries':entries}
    (out/'index.json').write_text(json.dumps(meta,indent=2)+'\n')
    print('Original graph drawings:',len(entries),'bytes:',sum(p.stat().st_size for p in out.iterdir()))
    print('Creators:',sorted({str(e['creator']) for e in entries.values()}))
    print('Pixel range:',min(max(e['width'],e['height']) for e in entries.values()),max(max(e['width'],e['height']) for e in entries.values()))
if __name__=='__main__':main()
