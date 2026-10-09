#!/usr/bin/env python3

import csv
import sys
from collections import Counter


REQUIRED_COLUMNS = {
    "entry_id",
    "maya_text",
    "maya_normalized",
    "english_text",
    "english_normalized",
    "source",
}


def main(path: str) -> None:
    with open(path, encoding="utf-8", newline="") as file:
        rows = list(csv.DictReader(file))

    if not rows:
        raise SystemExit("Dictionary CSV contains no records.")

    missing = REQUIRED_COLUMNS - set(rows[0].keys())

    if missing:
        raise SystemExit(
            f"Missing columns: {sorted(missing)}"
        )

    empty_rows = [
        row["entry_id"]
        for row in rows
        if not row["maya_text"].strip()
        or not row["english_text"].strip()
    ]

    pair_counts = Counter(
        (
            row["maya_normalized"],
            row["english_normalized"],
        )
        for row in rows
    )

    duplicate_pairs = [
        pair
        for pair, count in pair_counts.items()
        if count > 1
    ]

    verified = sum(
        row.get("verified", "").lower() == "true"
        for row in rows
    )

    print(f"Rows: {len(rows)}")
    print(f"Empty Maya/English rows: {len(empty_rows)}")
    print(f"Duplicate Maya-English pairs: {len(duplicate_pairs)}")
    print(f"Verified rows: {verified}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit(
            "Usage: python validate_dictionary.py FILE.csv"
        )

    main(sys.argv[1])
