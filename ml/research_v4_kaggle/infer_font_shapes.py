#!/usr/bin/env python3
"""Test the auxiliary *font-codepoint* classifier. NOT Maya-English translation.

Usage: python infer_font_shapes.py --checkpoint results/research_v4_aux_font_shapes.pt --image myglyph.png
Never equate a high font-key score with a verified Maya reading.
"""
import argparse, json
from pathlib import Path
import numpy as np
from PIL import Image
import torch
from train_research_v4_font_shapes import FontNet

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--checkpoint',required=True)
    ap.add_argument('--image',required=True)
    ap.add_argument('--top',type=int,default=5)
    args=ap.parse_args()
    saved=torch.load(args.checkpoint,map_location='cpu',weights_only=True)
    labels=saved['font_keys']
    net=FontNet(len(labels))
    net.load_state_dict(saved['model']);net.eval()
    with Image.open(args.image) as im:
        # White background expected. Resize for font-look-up only; photo/crop
        # quality and sign/phonetic identity are never independently verified.
        pixels=np.array(im.convert('L').resize((64,64),Image.Resampling.LANCZOS),dtype=np.float32)
    if pixels.max()-pixels.min()<8:
        raise SystemExit('No distinct drawing found; image is blank or low contrast.')
    input=torch.from_numpy(pixels[None,None,:,:]/255)
    with torch.no_grad():
        logits=net(input).squeeze(0)
        scores=torch.softmax(logits,dim=0)
        indices=torch.argsort(scores,descending=True)[:args.top].tolist()
    print(json.dumps({
        'input':str(Path(args.image)),
        'meaning':'Font glyph-key recognition ONLY; predictions are not Maya readings.',
        'font_key_guesses':[{'key':labels[i],'softmax_score_not_calibrated':round(float(scores[i]),5)} for i in indices]
    },indent=2))

if __name__=='__main__': main()
