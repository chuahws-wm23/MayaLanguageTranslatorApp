#!/usr/bin/env python3
"""Offline conversion of Roboflow v11 COCO-seg ZIP to one-class YOLO11-seg.

Requires only Python's standard library. Does not translate Maya glyphs.
Preserves source train/valid/test split; rejects overlapping original names.
"""
import argparse
from collections import defaultdict
import csv
import json
from pathlib import Path, PurePosixPath
import re
import zipfile

SPLITS = ('train', 'valid', 'test')


def source_name(image):
    name = image.get('extra', {}).get('name') or re.sub(
        r'\.rf\.[^.]+(?=\.[^.]+$)', '', str(image['file_name']))
    return PurePosixPath(str(name).replace('\\', '/')).name


def polygon(ann, width, height):
    parts = ann.get('segmentation', [])
    if not isinstance(parts, list) or len(parts) != 1 or len(parts[0]) < 6 or len(parts[0]) % 2:
        raise ValueError(f"Unsupported polygon in annotation {ann.get('id')}")
    pts = []
    for x, y in zip(parts[0][::2], parts[0][1::2]):
        x, y = float(x), float(y)
        if not (-1 <= x <= width + 1 and -1 <= y <= height + 1):
            raise ValueError(f"Out-of-bounds annotation {ann.get('id')}")
        pts.extend((min(1., max(0., x / width)), min(1., max(0., y / height))))
    if max(pts[0::2]) - min(pts[0::2]) < .001 or max(pts[1::2]) - min(pts[1::2]) < .001:
        return None  # Discard near-zero-area annotations, keep image.
    return pts


def convert(zip_path, output, audit_only=False):
    with zipfile.ZipFile(zip_path) as z:
        if sum(i.file_size for i in z.infolist()) > 2_000_000_000:
            raise ValueError('Unexpectedly large ZIP; inspect before conversion')
        members = set(z.namelist())
        data = {}
        sources = {}
        total_images, total_masks, tiny = 0, 0, 0
        for split in SPLITS:
            meta_path = f'{split}/_annotations.coco.json'
            if meta_path not in members or z.getinfo(meta_path).file_size > 25_000_000:
                raise ValueError(f'Missing or oversized {meta_path}')
            d = json.loads(z.read(meta_path))
            cats = {int(c['id']): str(c['name']) for c in d['categories']}
            images = {int(i['id']): i for i in d['images']}
            if len(images) != len(d['images']):
                raise ValueError('Duplicate COCO image IDs')
            groups = defaultdict(list)
            local_origins, filenames = set(), set()
            for im in d['images']:
                n = str(im['file_name']).replace('\\', '/')
                if (PurePosixPath(n).name != n or n in ('.', '..') or n in filenames or
                        not n.lower().endswith(('.jpg', '.jpeg', '.png'))):
                    raise ValueError(f'Unsafe or duplicated image filename: {n}')
                if f'{split}/{n}' not in members:
                    raise ValueError(f'Missing image: {split}/{n}')
                if not (1 <= int(im['width']) <= 8000 and 1 <= int(im['height']) <= 8000):
                    raise ValueError('Invalid image dimensions')
                filenames.add(n)
                local_origins.add(source_name(im))
            for ann in d['annotations']:
                im = images.get(int(ann['image_id']))
                if im is None:
                    raise ValueError('Orphan COCO annotation')
                name = cats.get(int(ann['category_id']), '')
                if not name or name.lower() == 'maya-glyphs':
                    raise ValueError(f'Invalid source category: {name}')
                coords = polygon(ann, int(im['width']), int(im['height']))
                if coords is None:
                    tiny += 1
                    continue
                groups[int(ann['image_id'])].append((coords, name, ann['id']))
                total_masks += 1
            sources[split] = local_origins
            data[split] = (d['images'], groups)
            total_images += len(d['images'])
        for index, a in enumerate(SPLITS):
            for b in SPLITS[index + 1:]:
                overlapping = sources[a] & sources[b]
                if overlapping:
                    raise ValueError(f'Source leakage {a}/{b}: {list(overlapping)[:3]}')
        report = {
            'task': 'one-class individual-sign region segmentation (NOT reading)',
            'images': total_images, 'usable_polygons': total_masks,
            'skipped_degenerate_polygons': tiny,
            'split_images': {s: len(data[s][0]) for s in SPLITS},
            'warning': 'Source-name split disjointness does not prove independence of original artifacts.'
        }
        if audit_only:
            print(json.dumps(report, indent=2))
            return report
        if output.exists() and any(output.iterdir()):
            raise ValueError(f'Output folder already contains files: {output}; use a NEW output folder')
        output.mkdir(parents=True, exist_ok=True)
        records = []
        for split in SPLITS:
            im_dir = output / 'images' / split
            label_dir = output / 'labels' / split
            im_dir.mkdir(parents=True, exist_ok=True)
            label_dir.mkdir(parents=True, exist_ok=True)
            for im in data[split][0]:
                name = str(im['file_name']).replace('\\', '/')
                (im_dir / name).write_bytes(z.read(f'{split}/{name}'))
                labels = []
                for coords, original_code, ann_id in data[split][1].get(int(im['id']), []):
                    labels.append('0 ' + ' '.join(f'{p:.6f}' for p in coords))
                    records.append([split, name, source_name(im), ann_id, original_code])
                (label_dir / (Path(name).stem + '.txt')).write_text('\n'.join(labels) + '\n', encoding='utf-8')
        with (output / 'source_provenance.csv').open('w', newline='', encoding='utf-8') as file:
            w = csv.writer(file)
            w.writerow(['split', 'image', 'source_name', 'annotation_id', 'original_roboflow_code'])
            w.writerows(records)
        (output / 'data.yaml').write_text(
            'path: ' + json.dumps(output.resolve().as_posix()) + '\n'
            'train: images/train\nval: images/valid\ntest: images/test\n'
            'nc: 1\nnames: [glyph_component]\n', encoding='utf-8')
        (output / 'audit.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
        print(json.dumps(report, indent=2))
        return report


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--zip', type=Path, required=True, help='Original Roboflow v11 COCO segmentation ZIP')
    p.add_argument('--output', type=Path, default=Path('yolo_v11_components'))
    p.add_argument('--audit-only', action='store_true')
    a = p.parse_args()
    if not a.zip.is_file():
        p.error(f'Source ZIP not found: {a.zip}')
    convert(a.zip, a.output, a.audit_only)
