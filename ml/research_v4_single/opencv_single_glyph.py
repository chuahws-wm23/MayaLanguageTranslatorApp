#!/usr/bin/env python3
"""Single Maya glyph image processing for the existing Research v4 ONNX CNN.

Keeps small sign details in the original image. Comparison modes are optional
research candidates, not a claim that OpenCV can recognise unsupported signs.
Run: python ml/research_v4_single/opencv_single_glyph.py --image glyph.png
"""
from __future__ import annotations
import argparse
import io
import json
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageOps

SIGNS = [32, 184, 291, 4, 44, 507, 548, 122, 187, 1030]
READINGS = ["K'UH", "K'INICH", "SIIP", "NAAH", "TOK", "TZIMAH",
            "HAAB", "K'AK'", "K'ABA'", "K'AWIIL"]
GLOSSES = ["God / deity", "Resplendent / sunny", "Name of a deity",
           "House / first", "Cloud / mist", "Gourd", "Year", "Fire",
           "Name", "Name of a deity"]
VARIANTS = ("original", "clahe", "clahe_bilateral", "adaptive", "shadow")
MODEL_SIDE = 160
CANONICAL_SIDE = 64
MODEL_SHA256 = "b9bb6bcc816f9c7957792302d7a1e50aaee2a1ecf3d44b8ae9b9b493cf4efba9"

class UnreadableGlyph(ValueError):
    """Image could not be safely normalized as one isolated glyph."""

def decode_image(payload: bytes | Path | str) -> np.ndarray:
    raw = (Path(payload).read_bytes() if isinstance(payload, (str, Path))
           else payload)
    if not raw:
        raise UnreadableGlyph("The image file is empty.")
    try:
        with Image.open(io.BytesIO(raw)) as img:
            corrected = ImageOps.exif_transpose(img).convert("RGBA")
            background = Image.new("RGBA", corrected.size, (255, 255, 255, 255))
            rgb = np.asarray(Image.alpha_composite(background, corrected).convert("RGB"))
    except (ValueError, OSError, TypeError) as exc:
        raise UnreadableGlyph("Choose a valid PNG, JPEG or compatible image.") from exc
    gray = cv2.cvtColor(rgb, cv2.COLOR_RGB2GRAY)
    if min(gray.shape) < 25:
        raise UnreadableGlyph("Use a larger photograph with one complete glyph.")
    # Bounding RAM use, while keeping aspect ratio and interpolation quality.
    h, w = gray.shape
    if max(h, w) > 1600:
        ratio = 1600 / max(h, w)
        gray = cv2.resize(gray, (max(25, round(w*ratio)),max(25, round(h*ratio))),
                          interpolation=cv2.INTER_AREA)
    return gray

def prepare_variant(gray: np.ndarray, variant: str) -> np.ndarray:
    if variant not in VARIANTS:
        raise ValueError(f"Unknown treatment: {variant}")
    if gray.ndim != 2 or gray.dtype != np.uint8 or min(gray.shape) < 25:
        raise UnreadableGlyph("Expected a grayscale glyph image of at least 25px.")
    if variant == "original":
        return gray.copy()
    if variant == "clahe":
        return cv2.createCLAHE(clipLimit=1.8, tileGridSize=(8,8)).apply(gray)
    if variant == "clahe_bilateral":
        contrasted = cv2.createCLAHE(clipLimit=1.8, tileGridSize=(8,8)).apply(gray)
        return cv2.bilateralFilter(contrasted, d=5, sigmaColor=25, sigmaSpace=25)
    if variant == "shadow":
        # Large-scale illumination estimate; preserve small structure.
        radius = max(12, min(96, min(gray.shape)//6))
        border = np.r_[gray[0], gray[-1], gray[:,0], gray[:,-1]]
        oriented = 255-gray if float(np.median(border)) < 110 else gray
        mean = cv2.boxFilter(oriented.astype(np.float32), -1,
                             (2*radius+1, 2*radius+1),
                             borderType=cv2.BORDER_REPLICATE)
        return np.clip(oriented.astype(np.float32)-mean+212,0,255).astype(np.uint8)
    # Local thresholding is an alternative, never applied by default.
    border = np.r_[gray[0], gray[-1], gray[:,0], gray[:,-1]]
    oriented = 255-gray if float(np.median(border)) < 110 else gray
    size = min(31, (min(gray.shape)//2)*2-1)
    size = max(3, size)
    return cv2.adaptiveThreshold(oriented,255,cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
                                 cv2.THRESH_BINARY,size,7)

def canonical_glyph(gray: np.ndarray) -> np.ndarray:
    """Convert to 64x64 bright-ink-on-black to match TWKM training.

    56px largest-side foreground, preserving sign aspect ratio and stroke
    topology as much as possible. Do not blur, rotate, flip or morph a sign
    automatically: those changes can alter its identity.
    """
    if gray.ndim!=2 or gray.dtype!=np.uint8 or min(gray.shape)<25:
        raise UnreadableGlyph("Image is too small or has an invalid format.")
    h,w = gray.shape
    border = np.r_[gray[0,:],gray[-1,:],gray[:,0],gray[:,-1]]
    oriented = 255-gray if float(np.median(border)) < 110 else gray
    threshold,_ = cv2.threshold(oriented,0,255,cv2.THRESH_BINARY+cv2.THRESH_OTSU)
    mask = oriented <= int(threshold)  # include ink equal to Otsu cutoff (e.g., clean two-tone glyphs)
    amount = int(mask.sum())
    if amount < 30 or amount/(h*w) < .003 or amount/(h*w) > .85:
        raise UnreadableGlyph("No clear isolated sign. Crop around one complete glyph.")
    yy,xx=np.nonzero(mask)
    left,right,top,bottom=int(xx.min()),int(xx.max())+1,int(yy.min()),int(yy.max())+1
    pad=max(2,int(max(right-left,bottom-top)*.035))
    left=max(0,left-pad);right=min(w,right+pad)
    top=max(0,top-pad);bottom=min(h,bottom+pad)
    strokes=(mask[top:bottom,left:right].astype(np.uint8)*255)
    ch,cw=strokes.shape
    scale=56/max(ch,cw)
    nw,nh=max(1,round(cw*scale)),max(1,round(ch*scale))
    interp=cv2.INTER_AREA if scale<=1 else cv2.INTER_LINEAR
    small=cv2.resize(strokes,(nw,nh),interpolation=interp)
    out=np.zeros((CANONICAL_SIDE,CANONICAL_SIDE),dtype=np.uint8)
    oy,ox=(CANONICAL_SIDE-nh)//2,(CANONICAL_SIDE-nw)//2
    out[oy:oy+nh,ox:ox+nw]=small
    return out

def model_tensor(canonical: np.ndarray) -> np.ndarray:
    if canonical.shape!=(64,64) or canonical.dtype!=np.uint8:
        raise ValueError("Expected canonical grayscale glyph with size 64x64.")
    bright=255-cv2.resize(canonical,(MODEL_SIDE,MODEL_SIDE),
                          interpolation=cv2.INTER_LINEAR)
    v=bright.astype(np.float32)/255.0
    return np.stack([(v-.485)/.229,(v-.456)/.224,(v-.406)/.225],axis=0)

def logits_for(session, grays: list[np.ndarray], variant: str) -> np.ndarray:
    """Return NaN rows for unrecognisable inputs; never invent predictions."""
    output=np.full((len(grays),len(SIGNS)),np.nan,dtype=np.float32)
    items=[]
    positions=[]
    for i,gray in enumerate(grays):
        try:
            tens=model_tensor(canonical_glyph(prepare_variant(gray,variant)))
        except (UnreadableGlyph,cv2.error,ValueError):
            continue
        items.append(tens)
        positions.append(i)
    for start in range(0,len(items),24):
        group=np.stack(items[start:start+24]).astype(np.float32)
        scores=session.run(["logits"],{"image":group})[0]
        if scores.shape!=(len(group),len(SIGNS)) or not np.isfinite(scores).all():
            raise RuntimeError("Unexpected ONNX outputs; inference discarded.")
        output[positions[start:start+24]]=scores
    return output

def top_candidates(logits: np.ndarray, amount=3) -> list[dict]:
    if logits.shape!=(10,) or not np.isfinite(logits).all():
        return []
    weights=np.exp(logits.astype(np.float64)-float(np.max(logits)))
    p=weights/weights.sum()
    ids=np.argsort(-p,kind="stable")[:amount]
    return [{"twkm_sign":SIGNS[int(k)],"reading":READINGS[int(k)],
             "catalogue_gloss":GLOSSES[int(k)],
             "relative_score_percent":round(float(p[k]*100),2)} for k in ids]

def main():
    parser=argparse.ArgumentParser(description="Experimental single Maya glyph CNN + OpenCV")
    parser.add_argument("--image",required=True)
    parser.add_argument("--model",default="app/src/main/assets/maya_glyph_v4.onnx")
    parser.add_argument("--treatment",choices=VARIANTS,default="original")
    parser.add_argument("--debug-output",help="Save the processed 64px glyph for inspection")
    args=parser.parse_args()
    import hashlib
    import onnxruntime as ort
    model=Path(args.model)
    if hashlib.sha256(model.read_bytes()).hexdigest()!=MODEL_SHA256:
        raise SystemExit("Unexpected v4 model hash; do not use mismatched labels.")
    gray=decode_image(args.image)
    try:
        processed=canonical_glyph(prepare_variant(gray,args.treatment))
    except UnreadableGlyph as exc:
        print(json.dumps({"status":"unrecognized","reason":str(exc)}))
        return
    if args.debug_output:
        cv2.imwrite(args.debug_output,processed)
    session=ort.InferenceSession(str(model),providers=["CPUExecutionProvider"])
    logits=session.run(["logits"],{"image":model_tensor(processed)[None]})[0][0]
    print(json.dumps({"status":"unverified_candidate",
          "treatment":args.treatment,"candidates":top_candidates(logits),
          "warning":"Relative scores are not calibrated confidence. Unsupported signs can be misclassified."},
          indent=2))

if __name__=="__main__":
    main()
