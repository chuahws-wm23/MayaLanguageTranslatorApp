#!/usr/bin/env python3
"""Research-only frozen MobileNetV3 retrieval on LOCAL syllabary chart drawings.

Does not upload user images. First run may download public ImageNet weights.
Chart-variant results are exploratory, NOT real-phone recognition accuracy.
"""
import argparse, json
from collections import defaultdict
from pathlib import Path
import numpy as np
import torch
from torch import nn
from torchvision.models import mobilenet_v3_small, MobileNet_V3_Small_Weights
from PIL import Image


@torch.no_grad()
def embeddings(images, model):
    mean = torch.tensor([.485, .456, .406]).reshape(3, 1, 1)
    std = torch.tensor([.229, .224, .225]).reshape(3, 1, 1)
    output = []
    for start in range(0, len(images), 16):
        batch = []
        for mask in images[start:start + 16]:
            # Local gallery: bright strokes on black; ImageNet expects white background.
            im = Image.fromarray(255 - mask).resize((224, 224), Image.Resampling.BILINEAR)
            pixels = np.asarray(im, dtype=np.float32) / 255.
            batch.append(torch.from_numpy(pixels).unsqueeze(0).expand(3, -1, -1))
        tensor = (torch.stack(batch) - mean) / std
        vec = nn.functional.normalize(model(tensor).flatten(1), dim=1)
        output.append(vec.numpy())
    return np.concatenate(output).astype(np.float32)


def evaluate(index):
    with np.load(index, allow_pickle=False) as data:
        labels = data["labels"].astype(str)
        images = data["images"]
    if len(images) != 152 or len(set(labels)) != 86:
        raise ValueError("Expected user-provided 152-crop, 86-syllable index")
    grouped = defaultdict(list)
    for i in range(86, len(labels)):
        grouped[labels[i]].append(i)
    holdouts = np.asarray([ids[0] for _, ids in sorted(grouped.items())])
    torch.set_num_threads(min(4, torch.get_num_threads()))
    net = mobilenet_v3_small(weights=MobileNet_V3_Small_Weights.DEFAULT)
    net.classifier = nn.Identity()
    net.eval()
    features = embeddings(images, net)
    scores = features[holdouts] @ features.T
    scores[np.arange(len(holdouts)), holdouts] = -np.inf
    hits1 = hits3 = 0
    rows = []
    for i, order in enumerate(np.argsort(-scores, axis=1)):
        top = []
        for idx in order:
            if labels[idx] not in top:
                top.append(labels[idx])
            if len(top) >= 3:
                break
        truth = labels[holdouts[i]]
        hits1 += int(truth == top[0])
        hits3 += int(truth in top)
        rows.append({"chart_syllable": truth, "top3": top})
    return {
        "method": "ImageNet-pretrained frozen MobileNetV3-Small embeddings",
        "holdout_chart_variants": len(holdouts),
        "top1_correct": hits1, "top3_correct": hits3,
        "individual_results": rows,
        "independent_real_photo_accuracy": "NOT MEASURED",
        "artwork_redistribution_permission": "NOT CONFIRMED",
        "android_ready": False
    }


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--index", type=Path, required=True)
    p.add_argument("--output", type=Path, required=True)
    args = p.parse_args()
    result = evaluate(args.index)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2), encoding="utf8")
    print(json.dumps({k: v for k, v in result.items() if k != "individual_results"}, indent=2))


if __name__ == "__main__":
    main()
