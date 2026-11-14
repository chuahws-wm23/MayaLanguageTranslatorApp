#!/usr/bin/env python3
"""Audit the Kaggle Mayan Glyphs PNG ZIP; NEVER infer Maya readings from U_XXXX.

Usage: python dataset/scripts/audit_kaggle_font_dataset.py archive.zip
Requires Pillow. Does not modify or redistribute the input images.
"""
from collections import Counter, defaultdict
from hashlib import sha256
from io import BytesIO
from pathlib import Path
import json
import re
import sys
from zipfile import ZipFile

from PIL import Image

PATH = re.compile(
    r"^[^/]+/(?P<style>mayanglyphs(?:fill|outline)_regular)/"
    r"(?P<split>train|test)/(?P<label>U_[0-9A-Fa-f]{4,6})/[^/]+[.]png$"
)


def audit(filename):
    records = []
    ignored = []
    with ZipFile(filename) as archive:
        for entry in archive.namelist():
            if entry.endswith("/"):
                continue
            match = PATH.fullmatch(entry)
            if not match:
                ignored.append(entry)
                continue
            with Image.open(BytesIO(archive.read(entry))) as source:
                image = source.convert("L")
                extrema = image.getextrema()
                pixels = image.tobytes()
                size = image.size
            records.append({
                **match.groupdict(),
                "path": entry,
                "pixels_sha256": sha256(pixels).hexdigest(),
                "blank": extrema[0] == extrema[1],
                "size": list(size),
            })

    train_hashes = {r["pixels_sha256"] for r in records if r["split"] == "train"}
    test = [r for r in records if r["split"] == "test"]
    groups = defaultdict(list)
    label_groups = defaultdict(set)
    for record in records:
        groups[record["pixels_sha256"]].append(record)
        if not record["blank"]:
            label_groups[record["style"]].add(record["label"])

    shared = set.intersection(*label_groups.values()) if label_groups else set()
    blanks = Counter(
        (r["style"], r["label"]) for r in records if r["blank"]
    )
    report = {
        "image_count": len(records),
        "ignored_paths": ignored,
        "image_splits": dict(Counter(r["split"] for r in records)),
        "style_counts": dict(Counter(r["style"] for r in records)),
        "blank_count": sum(r["blank"] for r in records),
        "blank_styles_and_keys": [
            {"style": style, "key": key, "count": count}
            for (style, key), count in sorted(blanks.items())
        ],
        "unique_pixel_images": len(groups),
        "exact_pixel_duplicates": len(records) - len(groups),
        "test_duplicate_of_training_count": sum(r["pixels_sha256"] in train_hashes for r in test),
        "nonblank_keys_shared_across_styles": len(shared),
        "warning": "Font codepoints are not Maya readings; do not use supplied train/test for independent accuracy.",
    }
    return report


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: python audit_kaggle_font_dataset.py <archive.zip>")
    print(json.dumps(audit(Path(sys.argv[1])), indent=2))
