#!/usr/bin/env python3
"""Combine direct-human and synthetic-English pairs and make stable splits."""

import argparse
import csv
import hashlib
import unicodedata
from collections import Counter
from pathlib import Path


FIELDS = [
    "english",
    "yucatec",
    "source",
    "quality",
    "context",
]


def normalize(value: str) -> str:
    return " ".join(
        unicodedata.normalize(
            "NFC",
            value.strip(),
        ).split()
    )


def read_csv(path: Path) -> list[dict]:
    with path.open(
        "r",
        encoding="utf-8",
        newline="",
    ) as handle:
        return list(csv.DictReader(handle))


def split_name(english: str, yucatec: str) -> str:
    key = (
        english.casefold()
        + "\u0000"
        + yucatec.casefold()
    ).encode("utf-8")

    value = int(
        hashlib.sha256(key).hexdigest()[:8],
        16,
    ) % 1000

    if value < 800:
        return "train"

    if value < 900:
        return "validation"

    return "test"


def main(
    gatitos_path: str,
    synthetic_path: str,
    output_dir: str,
) -> None:
    candidates = []

    for row in read_csv(Path(gatitos_path)):
        candidates.append(
            {
                "english": row["english"],
                "yucatec": row["yucatec"],
                "source": row["source"],
                "quality": row["quality"],
                "context": row.get("context", ""),
            }
        )

    for row in read_csv(Path(synthetic_path)):
        candidates.append(
            {
                "english": row["english"],
                "yucatec": row["yucatec"],
                "source": row["source"],
                "quality": row["quality"],
                "context": row.get("context", ""),
            }
        )

    deduped = []
    seen = set()

    for row in candidates:
        english = normalize(row["english"])
        yucatec = normalize(row["yucatec"])

        if not english or not yucatec:
            continue

        key = (
            english.casefold(),
            yucatec.casefold(),
        )

        if key in seen:
            continue

        seen.add(key)
        row = dict(row)
        row["english"] = english
        row["yucatec"] = yucatec
        deduped.append(row)

    splits = {
        "train": [],
        "validation": [],
        "test": [],
    }

    for row in deduped:
        splits[
            split_name(
                row["english"],
                row["yucatec"],
            )
        ].append(row)

    output = Path(output_dir)
    output.mkdir(parents=True, exist_ok=True)

    for name, rows in splits.items():
        path = output / f"{name}.csv"

        with path.open(
            "w",
            encoding="utf-8",
            newline="",
        ) as handle:
            writer = csv.DictWriter(
                handle,
                fieldnames=FIELDS,
            )
            writer.writeheader()
            writer.writerows(rows)

        quality_counts = Counter(
            row["quality"] for row in rows
        )

        print(
            f"{name}: {len(rows)} pairs "
            f"{dict(quality_counts)}"
        )

    print(f"Total unique pairs: {len(deduped)}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--gatitos",
        default=(
            "ml/data/processed/"
            "gatitos_en_yua.csv"
        ),
    )
    parser.add_argument(
        "--synthetic",
        default=(
            "ml/data/processed/"
            "yua_es_ccc_synthetic_en.csv"
        ),
    )
    parser.add_argument(
        "--output-dir",
        default="ml/data/processed",
    )
    args = parser.parse_args()

    main(
        args.gatitos,
        args.synthetic,
        args.output_dir,
    )
