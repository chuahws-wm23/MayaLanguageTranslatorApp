#!/usr/bin/env python3
"""Create a deterministic candidate subset for manual translation evaluation.

This script does NOT make rows gold-standard. A reviewer must check each row
and set verified=true before evaluate_dictionary.py will score it.
"""

from __future__ import annotations

import argparse
import csv
import random
from pathlib import Path


FIELDS = [
    "case_id",
    "direction",
    "input_text",
    "expected_output",
    "part_of_speech",
    "source",
    "verified",
    "reviewer",
    "notes",
]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("dictionary", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--size", type=int, default=200)
    parser.add_argument("--seed", type=int, default=20261015)
    args = parser.parse_args()

    with args.dictionary.open(encoding="utf-8", newline="") as file:
        rows = [
            row
            for row in csv.DictReader(file)
            if row.get("maya_text", "").strip()
            and row.get("english_text", "").strip()
        ]

    if not rows:
        raise SystemExit("Dictionary has no usable rows.")

    random.Random(args.seed).shuffle(rows)
    selected = rows[: min(args.size, len(rows))]

    evaluation_rows = []
    for index, row in enumerate(selected, start=1):
        # Alternate directions so both translation directions are represented.
        if index % 2:
            direction = "maya_to_english"
            input_text = row["maya_text"]
            expected_output = row["english_text"]
        else:
            direction = "english_to_maya"
            input_text = row["english_text"]
            expected_output = row["maya_text"]

        evaluation_rows.append(
            {
                "case_id": f"EVAL-{index:03d}",
                "direction": direction,
                "input_text": input_text,
                "expected_output": expected_output,
                "part_of_speech": row.get("part_of_speech", ""),
                "source": row.get("source", ""),
                "verified": "",
                "reviewer": "",
                "notes": "",
            }
        )

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows(evaluation_rows)

    print(
        f"Wrote {len(evaluation_rows)} candidate evaluation rows to {args.output}"
    )
    print(
        "Review every row manually and set verified=true only when the "
        "input/expected pair has been checked against an acceptable source."
    )


if __name__ == "__main__":
    main()
