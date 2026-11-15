#!/usr/bin/env python3
"""Compare glyph-BLOCK boxes with verified YOLO v18 annotations.

Generates 2/3-panel images:
  GREEN = labelled ground truth, BLUE = baseline 512px, ORANGE = candidate.
Candidate inference uses a 512px detector on rotated full images or overlapping
native-resolution tiles. Candidate behavior is NOT deployed to Android.

Use validation data to compare settings; test screenshots are already exposed.
All reported TP/FP/FN are at IoU>=.50 for the chosen fixed score threshold,
NOT mAP50, a calibration of model confidence, or sign translation accuracy.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path


def box_iou(a, b):
    l, t = max(a[0], b[0]), max(a[1], b[1])
    r, d = min(a[2], b[2]), min(a[3], b[3])
    overlap = max(0., r - l) * max(0., d - t)
    area_a = max(0., a[2]-a[0]) * max(0., a[3]-a[1])
    area_b = max(0., b[2]-b[0]) * max(0., b[3]-b[1])
    denom = area_a + area_b - overlap
    return overlap/denom if denom > 0 else 0.


def suppress(boxes, threshold=.45, limit=48):
    """One-class NMS; coordinates in ORIGINAL image pixels, not crop pixels."""
    if not 0 <= threshold <= 1:
        raise ValueError("Invalid overlap threshold")
    ordered = sorted(boxes, key=lambda b: (-b[4], b[1], b[0]))
    out = []
    for box in ordered:
        if box[2] <= box[0] or box[3] <= box[1]:
            continue
        if all(box_iou(box, other) <= threshold for other in out):
            out.append(box)
            if len(out) >= limit:
                break
    return out


def unrotate(box, clockwise_quarters, original_width, original_height):
    """Map rotated-image xyxy back to the original orientation."""
    k = clockwise_quarters % 4
    x1, y1, x2, y2, conf = box
    w, h = original_width, original_height
    if k == 0:
        ans = (x1, y1, x2, y2, conf)
    elif k == 1:
        ans = (y1, h-x2, y2, h-x1, conf)
    elif k == 2:
        ans = (w-x2, h-y2, w-x1, h-y1, conf)
    else:
        ans = (w-y2, x1, w-y1, x2, conf)
    return (max(0., min(w, ans[0])), max(0., min(h, ans[1])),
            max(0., min(w, ans[2])), max(0., min(h, ans[3])), ans[4])


def tile_starts(length, tile, overlap=.25):
    if length <= 0 or tile <= 0 or not (0 <= overlap < 1):
        raise ValueError("Invalid sliding-window dimensions")
    if length <= tile:
        return [0]
    stride = max(1, int(tile * (1-overlap)))
    locations = list(range(0, length-tile+1, stride))
    if locations[-1] != length-tile:
        locations.append(length-tile)
    return locations


def read_yolo_boxes(label_path: Path, width: int, height: int):
    """Return whole-image ground truth. Does not modify the source annotation."""
    if not label_path.is_file():
        raise FileNotFoundError("Ground-truth label not found: " + str(label_path))
    result = []
    for line in label_path.read_text(encoding="utf-8-sig").splitlines():
        if not line.strip():
            continue
        parts = line.split()
        if len(parts) != 5 or parts[0] != "0":
            raise ValueError("Expected five-number one-class YOLO DETECTION labels.")
        cx, cy, bw, bh = map(float, parts[1:])
        if not all(0 <= n <= 1 for n in (cx, cy, bw, bh)) or bw <= 0 or bh <= 0:
            raise ValueError("Invalid normalized ground-truth box.")
        result.append((
            max(0., (cx-bw/2)*width), max(0., (cy-bh/2)*height),
            min(width*1., (cx+bw/2)*width), min(height*1., (cy+bh/2)*height), 1.,
        ))
    return result


def count_matches(guesses, truth, minimum_iou=.5):
    """Greedy one-to-one matching; reports TP/FP/FN for the fixed cutoff."""
    matched = set()
    tp = 0
    for guess in sorted(guesses, key=lambda b: -b[4]):
        ranking = sorted(
            ((box_iou(guess, t), index) for index, t in enumerate(truth)
             if index not in matched), reverse=True,
        )
        if ranking and ranking[0][0] >= minimum_iou:
            tp += 1
            matched.add(ranking[0][1])
    return {"tp": tp, "fp": len(guesses)-tp, "fn": len(truth)-tp}


def measure(counts):
    tp, fp, fn = (sum(row[k] for row in counts) for k in ("tp", "fp", "fn"))
    precision = tp/(tp+fp) if tp+fp else 0.
    recall = tp/(tp+fn) if tp+fn else 0.
    f1 = 2*precision*recall/(precision+recall) if precision+recall else 0.
    return {
        "tp":tp, "fp":fp, "fn":fn,
        "precision": round(precision,4),
        "recall": round(recall,4),
        "f1_at_iou_0_5": round(f1,4),
    }


def yolo_predict(model, image, confidence, device):
    pred = model.predict(
        source=image, imgsz=512, conf=confidence, iou=.45,
        rect=False, device=device, verbose=False, max_det=300,
    )[0]
    if pred.boxes is None or len(pred.boxes) == 0:
        return []
    boxes = pred.boxes.xyxy.cpu().numpy().tolist()
    conf = pred.boxes.conf.cpu().numpy().tolist()
    return [tuple(float(v) for v in row)+(float(p),) for row, p in zip(boxes, conf)]


def candidate_boxes(model, image, mode, confidence, device, iou=.45):
    """Start with baseline. Merge rotated or tiled proposals in original pixels."""
    import cv2
    h, w = image.shape[:2]
    found = yolo_predict(model, image, confidence, device)
    if mode in ("rotated", "hybrid"):
        for k in (1,2,3):
            rotation = [None, cv2.ROTATE_90_CLOCKWISE,
                        cv2.ROTATE_180, cv2.ROTATE_90_COUNTERCLOCKWISE][k]
            rotated = cv2.rotate(image, rotation)
            found.extend(unrotate(box, k, w, h) for box in
                         yolo_predict(model, rotated, confidence, device))
    if mode in ("tiled", "hybrid"):
        # Always include the whole image above. Tiling keeps small signs
        # visible instead of shrinking a 2–10MP scene to 512px.
        tile = max(512, (max(w,h)+2)//3)
        xs = tile_starts(w, tile)
        ys = tile_starts(h, tile)
        if len(xs)*len(ys) > 30:
            raise ValueError("Unexpectedly many tiles; check image dimensions.")
        if len(xs)*len(ys) > 1:
            for top in ys:
                for left in xs:
                    right, bottom = min(left+tile, w), min(top+tile, h)
                    crop = image[top:bottom, left:right]
                    for x1,y1,x2,y2,p in yolo_predict(model,crop,confidence,device):
                        # Incomplete blocks cut by tile edges tend to cause
                        # large spurious boxes. Overlap ensures another tile
                        # gets a better look at signs near internal boundaries.
                        if (left and x1 <= 3 or top and y1 <= 3 or
                            right<w and x2 >= crop.shape[1]-3 or
                            bottom<h and y2 >= crop.shape[0]-3):
                            continue
                        found.append((x1+left,y1+top,x2+left,y2+top,p))
    return suppress(found, iou, 48)


def render_view(image, boxes, title, color, max_long=900):
    import cv2
    h,w=image.shape[:2]
    factor=min(1.0, max_long/max(w,h))
    frame = (cv2.resize(image, (round(w*factor),round(h*factor)),
                        interpolation=cv2.INTER_AREA) if factor<1 else image.copy())
    for idx, (x1,y1,x2,y2,*score) in enumerate(boxes,1):
        a=(round(x1*factor),round(y1*factor))
        b=(round(x2*factor),round(y2*factor))
        cv2.rectangle(frame,a,b,color,2)
        label=str(idx)
        textx=min(frame.shape[1]-25,max(3,a[0]+3))
        texty=max(17,a[1]+16)
        cv2.putText(frame,label,(textx,texty),cv2.FONT_HERSHEY_SIMPLEX,.53,
                    (255,255,255),3,cv2.LINE_AA)
        cv2.putText(frame,label,(textx,texty),cv2.FONT_HERSHEY_SIMPLEX,.53,
                    (0,0,0),1,cv2.LINE_AA)
    bar=45
    canvas=cv2.copyMakeBorder(frame,bar,0,0,0,cv2.BORDER_CONSTANT,value=(35,35,35))
    cv2.putText(canvas,title[:62],(12,30),cv2.FONT_HERSHEY_SIMPLEX,.56,
                color,2,cv2.LINE_AA)
    return canvas


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset",type=Path,required=True)
    parser.add_argument("--model",type=Path,required=True)
    parser.add_argument("--split",choices=("valid","test"),default="valid")
    parser.add_argument("--mode",choices=("baseline","tiled","rotated","hybrid"),default="tiled")
    parser.add_argument("--count",type=int,default=8,help="0 means all images in this split")
    parser.add_argument("--confidence",type=float,default=.35)
    parser.add_argument("--device",default="cpu",help="CPU avoids your missing CUDA TorchVision NMS kernel")
    parser.add_argument("--output",type=Path,default=Path("ml/blocks_v18/output/diagnostics"))
    args=parser.parse_args()
    if args.count < 0 or args.count > 150 or not 0 < args.confidence < 1:
        parser.error("Count must be 0..150 and confidence between 0 and 1.")
    import cv2
    from ultralytics import YOLO
    root=args.dataset.expanduser().resolve()
    images=sorted(x for x in (root/args.split/"images").iterdir()
                  if x.suffix.lower() in (".jpg",".jpeg",".png",".webp"))
    if not images:
        raise SystemExit("No images in selected dataset split.")
    if args.count:
        total=min(args.count,len(images))
        images=[images[round(i*(len(images)-1)/max(total-1,1))] for i in range(total)]
    model=YOLO(str(args.model.expanduser().resolve()),task="detect")
    args.output.mkdir(parents=True,exist_ok=True)
    rows=[]
    for idx,path in enumerate(images,1):
        # cv2 loads actual pixels; don't auto-rotate source after labels are set.
        original=cv2.imread(str(path), cv2.IMREAD_COLOR)
        if original is None:
            raise RuntimeError("Cannot read image: " + str(path))
        height,width=original.shape[:2]
        truth=read_yolo_boxes(root/args.split/"labels"/(path.stem+".txt"),width,height)
        baseline=suppress(yolo_predict(model,original,args.confidence,args.device))
        advanced=(baseline if args.mode=="baseline" else
                  candidate_boxes(model,original,args.mode,args.confidence,args.device))
        bscore=count_matches(baseline,truth)
        ascore=count_matches(advanced,truth)
        if args.count:  # produce annotated contact sheets for a few varied images
            gt_view=render_view(original,truth,"LABELS: "+str(len(truth)),(65,218,104))
            base_view=render_view(original,baseline,
                                  "BASE TP=%d FP=%d FN=%d" %
                                  (bscore["tp"],bscore["fp"],bscore["fn"]),
                                  (250,158,40))
            panels=[gt_view,base_view]
            if args.mode!="baseline":
                panels.append(render_view(original,advanced,
                             args.mode.upper()+" TP=%d FP=%d FN=%d" %
                             (ascore["tp"],ascore["fp"],ascore["fn"]), (49,174,241)))
            preview=args.output/("compare_%s_%s_%02d.jpg" % (args.split,args.mode,idx))
            if not cv2.imwrite(str(preview),cv2.hconcat(panels)):
                raise RuntimeError("Cannot save preview: "+str(preview))
        rows.append({
            "image":path.name,"true_blocks":len(truth),
            "baseline_predicted":len(baseline),
            "candidate_predicted":len(advanced),
            "baseline":bscore,"candidate":ascore
        })
        print("[%d/%d] %s GT=%d base(TP/FP/FN)=%s %s=%s" % (
              idx,len(images),path.name[:70],len(truth),list(bscore.values()),
              args.mode,list(ascore.values())),flush=True)
    report={
        "dataset_split":args.split,"model":str(args.model),"mode":args.mode,
        "fixed_confidence":args.confidence,"nms_iou":.45,"match_iou":.50,
        "only_selected_images":bool(args.count),
        "selected_image_count":len(images),
        "baseline":measure([r["baseline"] for r in rows]),
        "candidate":measure([r["candidate"] for r in rows]),
        "all_images":[{"name":r["image"],"ground_truth":r["true_blocks"],
                       "baseline":r["baseline"],"candidate":r["candidate"]}
                      for r in rows],
        "warning":"Not model mAP, not translation accuracy. Similar original drawings may occur "
                  "across split filenames. The test examples were already inspected. "
                  "Choose methods/settings on FULL validation, not cherry-picked previews.",
        "android_change":"NONE: these alternative inference modes are not deployed."
    }
    output=args.output/("comparison_%s_%s.json" % (args.split,args.mode))
    output.write_text(json.dumps(report,indent=2),encoding="utf8")
    print("\nSUMMARY",json.dumps({k:report[k] for k in
         ("dataset_split","mode","selected_image_count","baseline","candidate")},indent=2))
    print("Saved:",output)
    if args.count: print("Contact sheets in",args.output)


if __name__=="__main__":
    main()
