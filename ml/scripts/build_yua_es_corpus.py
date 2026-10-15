#!/usr/bin/env python3
"""Create deterministic train/validation/test splits from YUA-ES-CCC.

The original corpus is human Yucatec Maya ↔ Spanish data. No synthetic English
is introduced in this stage.
"""

import argparse
import csv
import hashlib
import unicodedata
from collections import Counter
from pathlib import Path


FIELDS = [
    "yucatec",
    "spanish",
    "context",
    "source",
]


def clean(value: str) -> str:
    return " ".join(
        unicodedata.normalize("NFC", value.strip()).split()
    )


def split_name(
    yucatec: str,
    spanish: str,
) -> str:
    key = (
        yucatec.casefold()
        + "\u0000"
        + spanish.casefold()
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
        reader = csv.DictReader(
            handle,
            delimiter="\t",
        )

        for row in reader:
            yucatec = clean(row["YUA"])
            spanish = clean(row["ES"])

            if not yucatec or not spanish:
                continue

            key = (
                yucatec.casefold(),
                spanish.casefold(),
            )
            if key in seen:
                continue
            seen.add(key)

            item = {
                "yucatec": yucatec,
                "spanish": spanish,
                "context": row.get(
                    "Communicative_Context",
                    "",
                ),
                "source": "YUA-ES-CCC",
            }

            splits[
                split_name(
                    yucatec,
                    spanish,
                )
            ].append(item)

    for name, rows in splits.items():
        path = output / f"yua_es_{name}.csv"

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

        contexts = Counter(
            row["context"]
            for row in rows
        )
        print(
            f"{name}: {len(rows)} pairs "
            f"across {len(contexts)} contexts"
        )

    print(
        "Total unique human Yucatec Maya-Spanish pairs:",
        sum(len(rows) for rows in splits.values()),
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--input",
        default=(
            "ml/data/raw/"
            "YUA_ES_communicative_contexts_corpus_v1.tsv"
        ),
    )
    parser.add_argument(
        "--output-dir",
        default="ml/data/processed",
    )
    args = parser.parse_args()
    main(args.input, args.output_dir)
