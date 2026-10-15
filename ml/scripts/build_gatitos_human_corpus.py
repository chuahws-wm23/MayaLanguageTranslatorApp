#!/usr/bin/env python3
"""Create deterministic train/validation/test splits from direct human GATITOS pairs.

This avoids rebuilding the synthetic-English corpus when only the joint
multilingual experiment is needed.
"""

import argparse
import csv
import hashlib
from pathlib import Path


FIELDS = [
    "english",
    "yucatec",
    "source",
    "quality",
    "context",
]


def split_name(english: str, yucatec: str) -> str:
    key = (
        english.casefold()
        + "\u0000"
        + yucatec.casefold()
    ).encode("utf-8")

    bucket = int(
        hashlib.sha256(key).hexdigest()[:8],
        16,
    ) % 1000

    if bucket < 800:
        return "train"
    if bucket < 900:
        return "validation"
    return "test"


def main(
    input_path: str,
    output_dir: str,
) -> None:
    source = Path(input_path)
    output = Path(output_dir)
    output.mkdir(parents=True, exist_ok=True)

    splits = {
        "train": [],
        "validation": [],
        "test": [],
    }
    seen = set()

    with source.open(
        "r",
        encoding="utf-8",
        newline="",
    ) as handle:
        for row in csv.DictReader(handle):
            english = row["english"].strip()
            yucatec = row["yucatec"].strip()

            if not english or not yucatec:
                continue

            key = (
                english.casefold(),
                yucatec.casefold(),
            )

            if key in seen:
                continue

            seen.add(key)

            item = {
                "english": english,
                "yucatec": yucatec,
                "source": row.get(
                    "source",
                    "SMOL/GATITOS",
                ),
                "quality": "human_direct",
                "context": row.get(
                    "context",
                    "",
                ),
            }

            splits[
                split_name(
                    english,
                    yucatec,
                )
            ].append(item)

    for name, rows in splits.items():
        path = output / f"gatitos_{name}.csv"

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

        print(
            f"gatitos_{name}: "
            f"{len(rows)} pairs"
        )

    print(
        "Total unique direct human pairs:",
        sum(len(rows) for rows in splits.values()),
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--input",
        default=(
            "ml/data/processed/"
            "gatitos_en_yua.csv"
        ),
    )
    parser.add_argument(
        "--output-dir",
        default="ml/data/processed",
    )
    args = parser.parse_args()

    main(
        args.input,
        args.output_dir,
    )
