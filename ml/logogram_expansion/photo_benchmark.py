#!/usr/bin/env python3
"""Audit public development-photo runs and prepare a truth-free review pack.

Museum/photographer readings are diagnostic clues, never expert TWKM labels.
No model fitting, threshold tuning, confidence calibration or release approval.
"""
from __future__ import annotations
import argparse
import csv
import hashlib
import json
import math
from statistics import median
from pathlib import Path


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _box(box):
    if len(box) != 4 or any(isinstance(v, bool) or not isinstance(v, (int, float)) or not math.isfinite(v) for v in box):
        raise ValueError('Boxes need four finite normalized coordinates.')
    left, top, right, bottom = box
    if not (0 <= left < right <= 1 and 0 <= top < bottom <= 1):
        raise ValueError('Boxes must have positive area inside [0, 1].')
    return box


def box_iou(a, b):
    a, b = _box(a), _box(b)
    intersection = max(0, min(a[2], b[2]) - max(a[0], b[0])) * max(0, min(a[3], b[3]) - max(a[1], b[1]))
    return intersection / ((a[2]-a[0])*(a[3]-a[1]) + (b[2]-b[0])*(b[3]-b[1]) - intersection)


def detection_counts(predictions, truth, threshold=0.5):
    """One-to-one maximum-cardinality matching; duplicate boxes are false positives.

    Call only with reviewed, exhaustive region annotations. None is unlabelled;
    [] is an explicitly verified negative image, a different case.
    """
    if truth is None:
        return None
    if not 0 < threshold <= 1:
        raise ValueError('IoU threshold must be in (0, 1].')
    predictions = [_box(b) for b in predictions]
    truth = [_box(b) for b in truth]
    adjacency = [[j for j, t in enumerate(truth) if box_iou(p, t) >= threshold] for p in predictions]
    assigned = {}
    def augment(i, seen):
        for j in adjacency[i]:
            if j in seen:
                continue
            seen.add(j)
            if j not in assigned or augment(assigned[j], seen):
                assigned[j] = i
                return True
        return False
    tp = sum(augment(i, set()) for i in range(len(predictions)))
    return {'tp': tp, 'fp': len(predictions)-tp, 'fn': len(truth)-tp, 'iou_threshold': threshold}


def score_regions(report, annotations):
    """Score only explicitly reviewed exhaustive boxes, tied to exact image bytes."""
    if annotations.get('format_version') != 1:
        raise ValueError('Unsupported annotation format.')
    rows = {r['id']: r for r in report['results']}
    reviewed = annotations.get('images', [])
    if len({r['id'] for r in reviewed}) != len(reviewed):
        raise ValueError('Duplicate annotated image IDs.')
    totals = {threshold: {'tp': 0, 'fp': 0, 'fn': 0} for threshold in (0.5, 0.75)}
    for truth in reviewed:
        row = rows.get(truth['id'])
        if row is None or row['sha256'] != truth.get('sha256') or row['source_group'] != truth.get('source_group'):
            raise ValueError('Box annotations refer to different images or artifacts.')
        if not truth.get('reviewed_by', '').strip() or not truth.get('source_citation', '').strip() or truth.get('exhaustive') is not True:
            raise ValueError('Region scoring needs reviewer, citation and exhaustive annotations.')
        predictions = [] if row.get('runtime_error') else row.get('region_proposals')
        if predictions is None:
            raise ValueError('No detector output for an annotated image.')
        for threshold in totals:
            counts = detection_counts(predictions, truth['boxes'], threshold)
            if counts is None:
                raise ValueError('Unlabelled boxes are not an annotated negative.')
            for key in ('tp', 'fp', 'fn'):
                totals[threshold][key] += counts[key]
    return {'annotated_images': len(reviewed), 'unannotated_images': len(rows)-len(reviewed),
        'reviewer_identity_is_supplied_not_independently_verified': True,
        'metrics': {str(t): {**counts,
            'precision': counts['tp']/(counts['tp']+counts['fp']) if counts['tp']+counts['fp'] else None,
            'recall': counts['tp']/(counts['tp']+counts['fn']) if counts['tp']+counts['fn'] else None}
            for t, counts in totals.items()} if reviewed else None}


def evaluate_public(report, manifest):
    if report.get('format_version') != 1 or manifest.get('format_version') != 1:
        raise ValueError('Unsupported report or manifest format.')
    if manifest.get('independent_expert_verified_test') is not False:
        raise ValueError('This evaluator is for inspected DEVELOPMENT photos only.')
    samples = manifest['samples']
    ids = [r['id'] for r in samples]
    rows = report['results']
    if not ids or len(ids) != len(set(ids)) or len({r['id'] for r in rows}) != len(rows):
        raise ValueError('Empty or duplicate image IDs.')
    if set(ids) != {r['id'] for r in rows}:
        raise ValueError('Runtime output must account for every staged image exactly once.')
    staged = {r['id']: r for r in samples}
    cases = []
    for row in rows:
        source = staged[row['id']]
        for key in ('sha256', 'source_group', 'expected_readings'):
            if row.get(key) != source.get(key) or source.get(key) is None:
                raise ValueError('Runtime provenance/label mismatch: ' + key)
        if not source['source_group'] or source.get('label_is_expert_verified_exact_sign') is not False:
            raise ValueError('Missing source group or unsupported expert-label claim.')
        expected = source['expected_readings']
        case = {'id': row['id'], 'source_group': source['source_group'], 'sha256': source['sha256'],
                'scope': 'operator_closeup' if source.get('derived_from') else 'original_photo',
                'label_tier': 'source_described_reading' if expected else 'unlabelled',
                'region_proposals': row.get('region_proposal_count'), 'runtime_error': row.get('runtime_error'),
                'production_elapsed_ms': row.get('production_elapsed_ms')}
        for method in ('production', 'research_illumination'):
            candidates = row.get(method + '_candidates', [])
            signs = [c['sign'] for c in candidates]
            if len(signs) > 5 or len(signs) != len(set(signs)):
                raise ValueError('Expected up to five distinct sign identities per method.')
            for k in (1, 3, 5):
                hit = any(set(c['reading'].split(' / ')) & set(expected) for c in candidates[:k])
                case[method + '_reading_in_top' + str(k)] = bool(hit and not row.get('runtime_error')) if expected else None
        cases.append(case)
    timings = [r['production_elapsed_ms'] for r in cases if not r['runtime_error'] and r['production_elapsed_ms'] is not None]
    if any(not isinstance(t, (int, float)) or not math.isfinite(t) or t < 0 for t in timings):
        raise ValueError('Invalid runtime timing.')
    groups = sorted({r['source_group'] for r in samples})
    slices = {}
    for scope in ('original_photo', 'operator_closeup'):
        subset = [r for r in cases if r['scope'] == scope and r['label_tier'] == 'source_described_reading']
        slices[scope] = {'reading_described_inputs': len(subset),
            'artifact_groups': len({r['source_group'] for r in subset}),
            **{f'{method}_reading_compatible_top{k}_count': sum(r[f'{method}_reading_in_top{k}'] for r in subset)
               for method in ('production', 'research_illumination') for k in (1, 3, 5)}}
    return {'format_version': 1, 'study': 'Inspected public-photo DEVELOPMENT baseline',
        'inputs': len(rows), 'source_groups': len(groups),
        'emulator_retrieval_timing_ms': {'samples': len(timings), 'median': median(timings), 'min': min(timings), 'max': max(timings)} if timings else None,
        'runtime_errors': sum(bool(r.get('runtime_error')) for r in rows),
        'expert_verified_sign_labels': 0, 'expert_verified_box_labels': 0,
        'known_sign_top1_accuracy': None, 'known_sign_top5_accuracy': None,
        'unknown_rejection_accuracy': None, 'detection_precision': None, 'detection_recall': None,
        'model_sha256': report.get('model_sha256'),
        'embedding_manifest_sha256': report.get('embedding_manifest_sha256'),
        'slices': slices, 'cases': cases, 'release_approved': False,
        'limitations': ['Reading compatibility is not exact sign accuracy.',
            'Originals and operator close-ups are separate slices of shared artifacts.',
            'Unlabelled inputs are not verified UNKNOWN negatives.',
            'No expert boxes: proposal counts are not detection accuracy.',
            'These inspected artifacts cannot become an untouched final test.']}


def review_pack(manifest_path, photo_root, destination):
    manifest = json.loads(manifest_path.read_text())
    rows = manifest['samples']
    if manifest.get('independent_expert_verified_test') is not False:
        raise ValueError('Only development source photos belong in this pack.')
    # Verify every byte before producing any review output. Photos stay local.
    root = photo_root.resolve()
    output = []
    for row in rows:
        path = (root / row['file']).resolve()
        if not path.is_relative_to(root) or sha(path) != row['sha256']:
            raise ValueError('Photo path or SHA mismatch: ' + row['id'])
        output.append({'id': row['id'], 'image_path': row['file'], 'sha256': row['sha256'],
            'source_group': row['source_group'], 'source_url': row['source_url'], 'license': row['license'],
            'scope': 'operator_closeup' if row.get('derived_from') else 'original_photo',
            'source_reading_clue': ' | '.join(row['expected_readings']),
            'review_status': 'unreviewed', 'verified_by': '', 'twkm_sign': '',
            'exhaustive_boxes_normalized_xyxy': '', 'negative_confirmed_by': '', 'split': 'development'})
    destination.mkdir(parents=True, exist_ok=False)
    with (destination / 'review.csv').open('w', newline='') as stream:
        writer = csv.DictWriter(stream, fieldnames=list(output[0]))
        writer.writeheader(); writer.writerows(output)
    (destination / 'provenance.json').write_text(json.dumps(manifest, indent=2) + '\n')
    (destination / 'README.txt').write_text(
        'DEVELOPMENT REVIEW ONLY. All labels and boxes are intentionally blank.\n'
        'Photos remain in the supplied local root. Review original and close-up together by source_group.\n'
        'A specialist must supply sign identity, citation, reviewer and exhaustive normalized boxes.\n'
        'Do not infer UNKNOWN from missing labels. Do not copy reading clues into TWKM IDs.\n'
        'These previously inspected artifacts must not be used as an untouched final test.\n')
    return {'inputs': len(output), 'source_groups': len({r['source_group'] for r in output}), 'labels_assigned': 0}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', required=True, type=Path)
    parser.add_argument('--report', type=Path)
    parser.add_argument('--annotations', type=Path)
    parser.add_argument('--photo-root', type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    if args.report:
        if sha(args.manifest) != json.loads(args.report.read_text()).get('manifest_sha256'):
            raise ValueError('Runtime report was produced from a different staged manifest.')
        result = evaluate_public(json.loads(args.report.read_text()), json.loads(args.manifest.read_text()))
        if args.annotations:
            result['reviewed_region_evaluation'] = score_regions(json.loads(args.report.read_text()), json.loads(args.annotations.read_text()))
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2) + '\n')
    else:
        if not args.photo_root:
            parser.error('Review-pack mode needs --photo-root')
        result = review_pack(args.manifest, args.photo_root, args.output)
    print(json.dumps(result, indent=2))

if __name__ == '__main__':
    main()
