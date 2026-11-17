#!/usr/bin/env python3
"""Audit expert-labelled photos and stage TEST images for the actual Android runner.

No automatic labels/downloads. Training/validation images are audited for source
leakage but never copied into the test APK. Generated assets are gitignored;
never upload this APK or reports without permission from the photo owners.
"""
from __future__ import annotations
import argparse
import csv
import hashlib
import json
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT/'dataset/scripts'))
from validate_real_glyph_photos import audit

DESTINATION = ROOT/'app/src/androidTest/assets/independent_photos'

def prepare(manifest: Path, photo_root: Path, destination: Path = DESTINATION):
    index_path = ROOT/'app/src/main/assets/maya_twkm_logogram_index.json'
    index_bytes = index_path.read_bytes()
    allowed = {int(row['sign']) for row in json.loads(index_bytes)['entries']}
    report = audit(manifest, photo_root, allowed_signs=allowed)
    if report['errors'] or report['warnings']:
        raise ValueError('Resolve manifest errors/duplicate-review warnings first: ' +
                         '; '.join((report['errors'] + report['warnings'])[:5]))
    with manifest.open(newline='', encoding='utf-8-sig') as handle:
        rows = [r for r in csv.DictReader(handle) if r['split'].strip().lower() == 'test']
    if not rows:
        raise ValueError('No held-out test photos. Training/validation images are not evaluation inputs.')
    if len(rows) > 5000:
        raise ValueError('Limit each on-device evaluation batch to 5000 images.')
    if destination.exists() and any(destination.iterdir()):
        raise ValueError('Staging directory is not empty; archive/remove the previous staged batch first.')
    destination.mkdir(parents=True, exist_ok=True)
    samples = []
    for i, row in enumerate(rows):
        original = (photo_root/row['image_path'].strip()).resolve()
        name = f'photo-{i:05d}{original.suffix.lower()}'
        shutil.copy2(original, destination/name)
        samples.append({'file': name, 'sha256': hashlib.sha256(original.read_bytes()).hexdigest(),
                        'expected_sign': row['twkm_sign'].strip(),
                        'source_group': row['source_group'].strip()})
    staged = {'format_version': 1, 'catalogue_index_sha256': hashlib.sha256(index_bytes).hexdigest(),
              'source_manifest_sha256': hashlib.sha256(manifest.read_bytes()).hexdigest(),
              'audit': report, 'samples': samples,
              'warning': 'Expert labels/provenance supplied by dataset owner; audit is not an expert review. Small datasets do not establish reliability.'}
    (destination/'manifest.json').write_text(json.dumps(staged, indent=2)+'\n')
    return staged

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', required=True, type=Path)
    parser.add_argument('--root', required=True, type=Path)
    args = parser.parse_args()
    result = prepare(args.manifest, args.root)
    print(f"Staged {len(result['samples'])} TEST photos in the gitignored Android test assets.")
    print(f"Readiness gaps: {len(result['audit']['readiness_gaps'])}; independent device results still required.")

if __name__ == '__main__':
    main()
