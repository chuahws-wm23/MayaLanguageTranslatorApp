#!/usr/bin/env python3
"""Draw sample YOLO v18 block boxes from held-out images for human inspection.

This is distinct from quantitative mAP. Boxes contain BLOCKS, potentially
multiple Maya signs. It works with .pt or .onnx weights produced by train_export.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset",required=True,type=Path)
    parser.add_argument("--model",required=True,type=Path)
    parser.add_argument("--output",type=Path,default=Path("ml/blocks_v18/output/previews"))
    parser.add_argument("--count",type=int,default=6)
    parser.add_argument("--confidence",type=float,default=.35)
    args=parser.parse_args()
    if not (1<=args.count<=50 and 0<args.confidence<1):
        parser.error("count 1..50; confidence (0,1)")
    from ultralytics import YOLO
    import cv2
    imgs=sorted(
        x for x in (args.dataset.resolve()/"test"/"images").iterdir()
        if x.suffix.lower() in (".jpg",".jpeg",".png",".webp")
    )
    if not imgs:
        raise SystemExit("No held-out test images in v18/test/images")
    # Samples spaced across the official test filenames. Avoid first-six bias.
    chosen=[imgs[min(len(imgs)-1, int(i*(len(imgs)-1)/max(1,args.count-1)))]
            for i in range(min(args.count,len(imgs)))]
    args.output.mkdir(parents=True,exist_ok=True)
    model=YOLO(str(args.model.resolve()),task="detect")
    rows=[]
    for i,p in enumerate(chosen,1):
        prediction=model.predict(source=str(p),imgsz=512,conf=args.confidence,
                                 device="cpu",verbose=False)[0]
        saved=args.output/f"block_preview_{i:02d}.jpg"
        if not cv2.imwrite(str(saved),prediction.plot()):
            raise RuntimeError(f"Could not save {saved}")
        rows.append({
            "source_name":p.name,
            "preview_path":str(saved),
            "detected_block_count":len(prediction.boxes),
            "detection_confidences":[round(float(v),3) for v in
                                      prediction.boxes.conf.cpu().tolist()],
        })
    (args.output/"preview_manifest.json").write_text(json.dumps(rows,indent=2),encoding="utf8")
    print(json.dumps(rows,indent=2))
    print("Inspect each yellow-model box visually before activating Android detector.")


if __name__ == "__main__":
    main()
