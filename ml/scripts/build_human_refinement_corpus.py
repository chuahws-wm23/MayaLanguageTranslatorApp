#!/usr/bin/env python3
"""Create human-direct refinement splits from the prepared corpus.

This keeps only rows whose quality label is 'human_direct'. The files are used
for a second fine-tuning stage after the mixed human + synthetic model.
"""

import argparse
import csv
from collections import Counter
from pathlib import Path


FIELDS = [
    "english",
    "yucatec",
    "source",
    "quality",
    "context",
]


def filter_split(
    input_path: Path,
    output_path: Path,
) -> int:
    with input_path.open(
        "r",
        encoding="utf-8",
        newline="",
    ) as handle:
        rows = [
            row
            for row in csv.DictReader(handle)
            if row.get("quality") == "human_direct"
        ]

    output_path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    with output_path.open(
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

    return len(rows)


def main(
    input_dir: str,
    output_dir: str,
) -> None:
    source = Path(input_dir)
    output = Path(output_dir)

    counts = Counter()

    for split in [
        "train",
        "validation",
        "test",
    ]:
        count = filter_split(
            source / f"{split}.csv",
            output / f"human_{split}.csv",
        )
        counts[split] = count
        print(
            f"human_{split}: {count} pairs"
        )

    print(
        "Human-direct refinement total:",
        sum(counts.values()),
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--input-dir",
        default="ml/data/processed",
    )
    parser.add_argument(
        "--output-dir",
        default="ml/data/processed",
    )
    args = parser.parse_args()

    main(
        args.input_dir,
        args.output_dir,
    )
