#!/usr/bin/env python3
"""Reproducible DEVELOPMENT diagnostic; related TWKM drawings, NOT phone accuracy.

One graph per sign is excluded from the retrieval gallery. Binary-identical
queries are excluded from both methods' search candidates. The deployed feature
asset contains all references; this evaluation explicitly masks held-out rows.
This development benchmark has been inspected during method selection and must
NOT be presented as a blind final test. Independent photo gates remain unmet.
"""
import argparse
import hashlib
import json
from pathlib import Path
import numpy as np
from sklearn.metrics import accuracy_score, precision_recall_fscore_support, confusion_matrix

ROOT = Path(__file__).resolve().parents[2]

def evaluate(kind):
    assets = ROOT/'app/src/main/assets'
    index = json.loads((assets/f'maya_twkm_{kind}_index.json').read_text())
    refs = index['entries']
    key = 'sign' if kind == 'logogram' else 'twkm_sign'
    labels = np.array([r[key] if kind == 'logogram' else r['syllable'] for r in refs])
    scalar = int if kind == 'logogram' else str
    raw = (assets/f'maya_twkm_{kind}_bitmap.bin').read_bytes()
    packed = np.frombuffer(raw, dtype=np.uint8).reshape(-1, 512)
    masks = np.unpackbits(packed, axis=1).astype(np.float32)
    features = np.fromfile(assets/f'maya_{kind}_embeddings.bin', dtype='<f4').reshape(-1, 576)
    assert len(features) == len(refs) and np.isfinite(features).all()
    groups = {scalar(s): list(np.flatnonzero(labels == s)) for s in sorted(set(labels))}
    # Match the existing repository's graph-variant diagnostic for logograms.
    test = [sorted(ids, key=lambda i: refs[i]['graph'])[-1] for ids in groups.values() if len(ids) >= 2]
    train = np.array([i for i in range(len(refs)) if i not in set(test)])
    hashes = [hashlib.sha256(row.tobytes()).hexdigest() for row in packed]
    outcomes = []
    for q in test:
        candidates = train[np.array([hashes[i] != hashes[q] for i in train])]
        if not np.any(labels[candidates] == labels[q]):
            continue
        denominator = np.sqrt(masks[q].sum()*masks[candidates].sum(1))
        baseline = (masks[candidates] @ masks[q])/np.maximum(denominator, 1)
        embedding = features[candidates] @ features[q]
        row = {'expected': scalar(labels[q]), 'graph': refs[q]['graph']}
        for name, scores in [('overlap', baseline), ('embedding', embedding)]:
            ranked = sorted(set(labels[candidates]), key=lambda s: (-float(scores[labels[candidates] == s].max()), scalar(s)))
            row[name] = [scalar(s) for s in ranked[:5]]
        outcomes.append(row)
    expected = [r['expected'] for r in outcomes]
    classes = sorted(set(expected) | {r[m][0] for r in outcomes for m in ['overlap', 'embedding']})
    results = {}
    for method in ['overlap', 'embedding']:
        predicted = [r[method][0] for r in outcomes]
        precision, recall, f1, _ = precision_recall_fscore_support(expected, predicted, average='macro', zero_division=0)
        results[method] = {'top1_accuracy': accuracy_score(expected, predicted),
            'macro_precision': precision, 'macro_recall': recall, 'macro_f1': f1,
            'top3_recall': sum(r['expected'] in r[method][:3] for r in outcomes)/len(outcomes),
            'top5_recall': sum(r['expected'] in r[method] for r in outcomes)/len(outcomes),
            'confusion_labels': classes,
            'confusion_matrix': confusion_matrix(expected, predicted, labels=classes).tolist()}
    return {'kind': kind, 'queries': len(outcomes), 'gallery_rows': len(train),
            'status': 'development diagnostic, same-source drawings; no independent photo accuracy claim',
            'results': results, 'outcomes': outcomes}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, default=ROOT/'ml/logogram_expansion/reports/reference_embeddings.json')
    args = parser.parse_args()
    result = {'warning': 'No calibrated confidence or unknown-sign threshold. Do not tune on final phone-photo test data.',
              'catalogues': [evaluate(k) for k in ['logogram', 'phonogram']]}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    # Keep sparse confusion matrices readable without bloating the review diff.
    for c in result['catalogues']:
        for method, metrics in c['results'].items():
            matrix = metrics.pop('confusion_matrix')
            name = f"{args.output.stem}_{c['kind']}_{method}_confusion.csv"
            np.savetxt(args.output.parent / name, matrix, delimiter=',', fmt='%d',
                       header=','.join(map(str, metrics['confusion_labels'])), comments='')
            metrics['confusion_matrix_csv'] = name
    args.output.write_text(json.dumps(result, indent=2)+'\n')
    for c in result['catalogues']:
        print(c['kind'], c['queries'], 'queries')
        for method, metrics in c['results'].items():
            print(method, {k: round(v, 4) for k, v in metrics.items() if not k.startswith('confusion')})

if __name__ == '__main__':
    main()
