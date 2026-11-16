#!/usr/bin/env python3
"""Summarize REAL Android retrieval output. Empty/error known queries count as failures.

Unknown images can still yield unconfirmed neighbours. That is not an unknown
rejection/false-acceptance result: this app does not confirm semantic identities.
"""
import argparse
import json
from pathlib import Path
from collections import defaultdict
from statistics import mean


def summarize(report):
    if report.get('format_version') != 1:
        raise ValueError('Unsupported report format')
    rows = report.get('predictions', [])
    if not rows:
        raise ValueError('No evaluated photos. Cannot report photo accuracy.')
    known = [r for r in rows if str(r['expected_sign']) != 'UNKNOWN']
    unknown = [r for r in rows if str(r['expected_sign']) == 'UNKNOWN']
    if any(r.get('confirmed_identification') is not False for r in rows):
        raise ValueError('Unexpected confirmation policy; review the runtime before reporting unknown handling.')
    ranks = []
    group_scores = defaultdict(list)
    for row in known:
        predicted = [str(c['sign']) for c in row['candidates']]
        if len(predicted) != len(set(predicted)) or len(predicted) > 5:
            raise ValueError('Predictions must contain up to five distinct sign identities.')
        rank = predicted.index(str(row['expected_sign'])) + 1 if str(row['expected_sign']) in predicted else None
        ranks.append(rank)
        group_scores[row['source_group']].append(float(rank == 1))
    result = {
        'study': 'Expert-labelled held-out photo retrieval on Android',
        'source_manifest_sha256': report['source_manifest_sha256'],
        'known_photos': len(known), 'unknown_photos': len(unknown),
        'known_source_groups': len(group_scores),
        'known_top1': mean(r == 1 for r in ranks) if known else None,
        'known_top3': mean(r is not None and r <= 3 for r in ranks) if known else None,
        'known_top5': mean(r is not None for r in ranks) if known else None,
        'known_mrr_at_5': mean(1/r if r else 0 for r in ranks) if known else None,
        'known_top1_mean_across_source_groups': mean(mean(s) for s in group_scores.values()) if group_scores else None,
        'error_photos': sum('error' in r for r in rows),
        'unknown_photos_with_unconfirmed_neighbours': sum(bool(r['candidates']) for r in unknown),
        'unknown_rejection_accuracy': None,
        'calibrated_confidence_available': False,
        'release_approved': False,
        'limitations': 'Image-level top-k retrieval only; source-group means also shown. No calibrated confirmation or unknown rejection. Expert/rights review, sufficient independent sources, full device coverage and validation-only threshold selection remain required.',
    }
    per_sign = defaultdict(list)
    for row, rank in zip(known, ranks):
        per_sign[str(row['expected_sign'])].append(rank)
    result['per_sign'] = {sign: {'photos': len(rs), 'top1': mean(r == 1 for r in rs),
                                      'top5': mean(r is not None for r in rs)} for sign, rs in sorted(per_sign.items())}
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('report', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    result = summarize(json.loads(args.report.read_text()))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2)+'\n')
    print(json.dumps(result, indent=2))

if __name__ == '__main__':
    main()
