#!/usr/bin/env python3
"""Validate the processed application dictionary and optionally write a JSON report."""

from __future__ import annotations

import argparse
import csv
import json
import re
import unicodedata
from collections import Counter
from pathlib import Path


REQUIRED_COLUMNS = {
    "entry_id",
    "maya_text",
    "maya_normalized",
    "english_text",
    "english_normalized",
    "source",
    "verified",
    "active",
}
ENTRY_ID_PATTERN = re.compile(r"^[A-Za-z0-9._-]+$")


def is_nfc(value: str) -> bool:
    return unicodedata.normalize("NFC", value) == value


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("path", type=Path)
    parser.add_argument("--report", type=Path)
    parser.add_argument(
        "--minimum-rows",
        type=int,
        default=250,
        help="Fail if fewer rows are produced; protects against bad downloads/parsing.",
    )
    args = parser.parse_args()

    with args.path.open(encoding="utf-8", newline="") as file:
        rows = list(csv.DictReader(file))

    if not rows:
        raise SystemExit("Dictionary CSV contains no records.")

    columns = set(rows[0].keys())
    missing_columns = sorted(REQUIRED_COLUMNS - columns)

    empty_text = []
    invalid_ids = []
    normalization_mismatches = []
    non_nfc_values = []

    for row in rows:
        entry_id = row.get("entry_id", "").strip()
        maya = row.get("maya_text", "").strip()
        english = row.get("english_text", "").strip()

        if not maya or not english:
            empty_text.append(entry_id or "<missing-id>")

        if not entry_id or not ENTRY_ID_PATTERN.fullmatch(entry_id):
            invalid_ids.append(entry_id or "<missing-id>")

        expected_maya = " ".join(
            unicodedata.normalize("NFC", maya).lower().split()
        )
        expected_english = " ".join(
            unicodedata.normalize("NFC", english).lower().split()
        )

        if (
            row.get("maya_normalized", "") != expected_maya
            or row.get("english_normalized", "") != expected_english
        ):
            normalization_mismatches.append(entry_id)

        if not is_nfc(maya) or not is_nfc(english):
            non_nfc_values.append(entry_id)

    pair_counts = Counter(
        (
            row.get("maya_normalized", ""),
            row.get("english_normalized", ""),
            row.get("part_of_speech", "").lower(),
        )
        for row in rows
    )
    duplicate_pairs = [
        pair
        for pair, count in pair_counts.items()
        if count > 1
    ]

    id_counts = Counter(row.get("entry_id", "") for row in rows)
    duplicate_ids = [
        entry_id
        for entry_id, count in id_counts.items()
        if count > 1
    ]

    report = {
        "row_count": len(rows),
        "verified_count": sum(
            row.get("verified", "").lower() == "true"
            for row in rows
        ),
        "active_count": sum(
            row.get("active", "").lower() == "true"
            for row in rows
        ),
        "missing_columns": missing_columns,
        "empty_text_count": len(empty_text),
        "invalid_id_count": len(invalid_ids),
        "duplicate_id_count": len(duplicate_ids),
        "duplicate_pair_count": len(duplicate_pairs),
        "normalization_mismatch_count": len(normalization_mismatches),
        "non_nfc_count": len(non_nfc_values),
        "minimum_rows": args.minimum_rows,
    }

    print(json.dumps(report, indent=2))

    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(
            json.dumps(report, indent=2) + "\n",
            encoding="utf-8",
        )

    failures = []
    if len(rows) < args.minimum_rows:
        failures.append(
            f"expected at least {args.minimum_rows} rows, found {len(rows)}"
        )
    if missing_columns:
        failures.append(f"missing columns: {missing_columns}")
    if empty_text:
        failures.append(f"{len(empty_text)} rows have empty source/target text")
    if invalid_ids:
        failures.append(f"{len(invalid_ids)} invalid entry IDs")
    if duplicate_ids:
        failures.append(f"{len(duplicate_ids)} duplicate entry IDs")
    if duplicate_pairs:
        failures.append(f"{len(duplicate_pairs)} duplicate translation/POS pairs")
    if normalization_mismatches:
        failures.append(
            f"{len(normalization_mismatches)} normalization mismatches"
        )
    if non_nfc_values:
        failures.append(f"{len(non_nfc_values)} non-NFC text values")

    if failures:
        raise SystemExit("Validation failed: " + "; ".join(failures))


if __name__ == "__main__":
    main()
