#!/usr/bin/env python3
"""Train class-agnostic glyph COMPONENT segmentation on converted v11 dataset.

This is the *location* part of a two-stage model, not a TWKM/phonetic reader.
GPU strongly recommended. See README.md for usage and limits.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path


def prepare_config(data_dir: Path) -> Path:
    data_dir = data_dir.resolve()
    for split in ('train', 'valid', 'test'):
        for typ in ('images', 'labels'):
            root = data_dir / typ / split
            if not root.is_dir() or not any(root.iterdir()):
                raise ValueError(f'Missing non-empty {root}; extract the v11 training kit first')
    config = data_dir / 'data_absolute.yaml'
    # JSON quoting avoids YAML escaping issues with Windows drive letters.
    config.write_text(
        'path: ' + json.dumps(data_dir.as_posix()) + '\n' +
        'train: images/train\nval: images/valid\ntest: images/test\n' +
        'nc: 1\nnames: [glyph_component]\n',
        encoding='utf8'
    )
    return config


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--data-dir', type=Path, required=True)
    p.add_argument('--model', default='yolo11n-seg.pt')
    p.add_argument('--epochs', type=int, default=50)
    p.add_argument('--imgsz', type=int, default=512)
    p.add_argument('--batch', type=int, default=8)
    p.add_argument('--device', default='0', help='CUDA GPU 0, or cpu')
    p.add_argument('--out', type=Path, default=Path('ml/runs/maya_v11_sign_segmentation'))
    p.add_argument('--dry-run', action='store_true')
    a = p.parse_args()
    if a.epochs < 1 or a.batch < 1 or a.imgsz < 128:
        p.error('Invalid epochs, batch, or image size')

    config = prepare_config(a.data_dir)
    print('Training config:', config, 'model:', a.model,
          'epochs:', a.epochs, 'imgsz:', a.imgsz,
          'device:', a.device, flush=True)
    if a.dry_run:
        print('Dataset preparation verified; model not trained (--dry-run).')
        return

    try:
        from ultralytics import YOLO
    except ImportError as exc:
        raise SystemExit('Install ultralytics: python -m pip install ultralytics') from exc

    a.out.mkdir(parents=True, exist_ok=True)
    model = YOLO(a.model)
    model.train(
        data=str(config), epochs=a.epochs, imgsz=a.imgsz,
        batch=a.batch, device=a.device, seed=20261116,
        deterministic=True, workers=2, project=str(a.out.resolve()),
        name='fit', exist_ok=False, patience=15, save=True, plots=True
    )
    run_dirs = sorted(
        a.out.glob('fit*'), key=lambda x: x.stat().st_mtime, reverse=True)
    if not run_dirs:
        raise SystemExit('No training folder; inspect console output')
    best = run_dirs[0] / 'weights' / 'best.pt'
    if not best.is_file():
        raise SystemExit('No best.pt checkpoint after training')

    selected = YOLO(str(best))
    metrics = selected.val(
        data=str(config), split='test', imgsz=a.imgsz,
        device=a.device, project=str(a.out.resolve()),
        name='one_time_test', plots=True
    )
    results = {
        'training_source': 'Roboflow v11 one-class polygon segmentation',
        'test_split': 'Original v11 held-out test; NOT real-photo recognition',
        'tested_images': len(list((a.data_dir / 'images' / 'test').glob('*.jpg'))),
        'box_map50': float(metrics.box.map50),
        'mask_map50': float(metrics.seg.map50),
        'mask_map50_95': float(metrics.seg.map),
        'best_pt': str(best),
        'notes': 'Instance segmentation only; no verified sign readings'
    }
    report = a.out / 'segmentation_test_report.json'
    report.write_text(json.dumps(results, indent=2), encoding='utf8')
    print('HELD-OUT SEGMENTATION EVALUATION:', json.dumps(results, indent=2), flush=True)
    export = selected.export(
        format='onnx', imgsz=a.imgsz,
        opset=13, dynamic=False, simplify=False, nms=False
    )
    print('ONNX exported:', export, flush=True)


if __name__ == '__main__':
    main()
