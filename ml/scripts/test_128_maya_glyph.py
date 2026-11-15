#!/usr/bin/env python3
"""Test the two exported 128px research ONNX candidates on ONE glyph.

The model is restricted to ten supported TWKM sign categories and cannot
reliably reject unfamiliar signs. Relative softmax scores are not accuracy.
"""
import argparse,hashlib,json
from pathlib import Path

import cv2
import numpy as np
from PIL import Image,ImageOps

SIGNS=[32,184,291,4,44,507,548,122,187,1030]
READINGS=["K'UH","K'INICH","SIIP","NAAH","TOK",
          "TZIMAH","HAAB","K'AK'","K'ABA'","K'AWIIL"]
GLOSSES=["God/deity","Resplendent/sunny","Deity name","House/first",
         "Cloud/mist","Gourd","Year","Fire","Name","Deity name"]
SHA={
 "81cf64821b251503832a304f4f22fd21b379c0df1fd5bc9fd372cf5019a137f2":"Kaggle+TWKM 128px",
 "aa0184b5137eb473a37f1acc38312072843532c0ac6ad4e45b8881006b139e9e":"TWKM-only 128px",
}

def canonical(gray):
    if gray.dtype!=np.uint8 or gray.ndim!=2 or min(gray.shape)<25:
        raise ValueError("Provide one grayscale glyph at least 25px across")
    border=np.r_[gray[0],gray[-1],gray[:,0],gray[:,-1]]
    oriented=255-gray if np.median(border)<110 else gray
    threshold,_=cv2.threshold(oriented,0,255,cv2.THRESH_BINARY+cv2.THRESH_OTSU)
    ink=oriented<max(1,int(threshold))
    if float(ink.mean())<.003:
        ink=oriented<=int(threshold)
    frac=float(ink.mean())
    if not (.003<=frac<=.85) or ink.sum()<30:
        raise ValueError("No clear single glyph; crop the complete sign")
    y,x=np.nonzero(ink)
    x0,x1,y0,y1=int(x.min()),int(x.max())+1,int(y.min()),int(y.max())+1
    pad=max(2,int(max(x1-x0,y1-y0)*.035))
    x0,x1=max(0,x0-pad),min(gray.shape[1],x1+pad)
    y0,y1=max(0,y0-pad),min(gray.shape[0],y1+pad)
    crop=ink[y0:y1,x0:x1].astype(np.uint8)*255
    ratio=112/max(crop.shape)
    w,h=max(1,round(crop.shape[1]*ratio)),max(1,round(crop.shape[0]*ratio))
    scaled=cv2.resize(crop,(w,h),interpolation=cv2.INTER_AREA if ratio<1 else cv2.INTER_LINEAR)
    result=np.zeros((128,128),dtype=np.uint8)
    result[(128-h)//2:(128-h)//2+h,(128-w)//2:(128-w)//2+w]=scaled
    return result

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--image",required=True)
    p.add_argument("--model",default="ml/research_128_candidate/out/kaggle_twkm_128_candidate.onnx")
    p.add_argument("--expected",type=int,help="Known TWKM number, e.g. 44 for TOK")
    p.add_argument("--debug-output",help="Optional path to save processed 128px PNG")
    a=p.parse_args()
    model=Path(a.model)
    if not model.is_file():p.error("Missing model: "+str(model)+". Download the Actions model artifact first.")
    digest=hashlib.sha256(model.read_bytes()).hexdigest()
    if digest not in SHA:p.error("Unrecognized ONNX checksum; cannot guarantee label order.")
    try:
        with Image.open(a.image) as photo:
            im=ImageOps.exif_transpose(photo).convert("RGBA")
            white=Image.new("RGBA",im.size,(255,255,255,255))
            gray=cv2.cvtColor(np.asarray(Image.alpha_composite(white,im).convert("RGB")),cv2.COLOR_RGB2GRAY)
        image=canonical(gray)
    except (OSError,ValueError,cv2.error) as e:p.error(str(e))
    if a.debug_output:
        if not cv2.imwrite(a.debug_output,image):p.error("Could not save preview")
    values=(255-image).astype(np.float32)/255
    x=np.stack([(values-.485)/.229,(values-.456)/.224,(values-.406)/.225],axis=0)[None]
    try:
        import onnxruntime as ort
    except ImportError:
        p.error("Install onnxruntime: python -m pip install onnxruntime numpy pillow opencv-python-headless")
    session=ort.InferenceSession(str(model),providers=["CPUExecutionProvider"])
    logits=session.run(["logits"],{"image":x})[0]
    if logits.shape!=(1,10) or not np.isfinite(logits).all():
        p.error("Invalid 10-class model output")
    z=logits[0].astype(np.float64)
    prob=np.exp(z-z.max());prob=100*prob/prob.sum()
    idx=np.argsort(-prob,kind="stable")[:3]
    result={
        "model":SHA[digest],"status":"experimental_unverified",
        "top3":[{"twkm_sign":SIGNS[int(i)],"reading":READINGS[int(i)],
                 "gloss":GLOSSES[int(i)],"relative_model_score_percent":round(float(prob[i]),2)}
                 for i in idx],
        "warning":"Unknown signs may be confidently misclassified. Relative model scores are NOT calibrated accuracy."
    }
    if a.expected is not None:
        result["expected_twkm_sign"]=a.expected
        result["top1_correct"]=SIGNS[int(idx[0])]==a.expected
    print(json.dumps(result,indent=2))

if __name__=="__main__":main()
