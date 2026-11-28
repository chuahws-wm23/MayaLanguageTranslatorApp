#!/usr/bin/env python3
"""Score OCR output against manually labelled ground-truth text.

The script uses character error rate (CER) plus exact/normalised exact match.
It does not run ML Kit itself; record the app's OCR output for each labelled
image, then use this script to score the fixed test set.
"""

from __future__ import annotations

import argparse
import csv
import json
import unicodedata
from pathlib import Path


def normalize(text: str) -> str:
    value = unicodedata.normalize("NFC", text.strip().lower())
    for mark in ("'", "’", "ʻ", "ʹ"):
        value = value.replace(mark, "ʼ")
    return " ".join(value.split())


def levenshtein(left: str, right: str) -> int:
    if len(left) < len(right):
        left, right = right, left

    previous = list(range(len(right) + 1))
    for i, left_char in enumerate(left, start=1):
        current = [i]
        for j, right_char in enumerate(right, start=1):
            current.append(
                min(
                    current[-1] + 1,
                    previous[j] + 1,
                    previous[j - 1] + (
                        0 if left_char == right_char else 1
                    ),
                )
            )
        previous = current

    return previous[-1]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("csv_path", type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()

    with args.csv_path.open(encoding="utf-8", newline="") as file:
        rows = list(csv.DictReader(file))

    scored = [
        row for row in rows
        if row.get("ground_truth_text", "").strip()
        and row.get("ocr_output", "").strip()
    ]

    if not scored:
        raise SystemExit(
            "No rows contain both ground_truth_text and ocr_output."
        )

    exact = 0
    normalized_exact = 0
    total_distance = 0
    total_reference_chars = 0
    per_case = []

    for row in scored:
        reference = row["ground_truth_text"]
        prediction = row["ocr_output"]

        if prediction == reference:
            exact += 1

        normalized_reference = normalize(reference)
        normalized_prediction = normalize(prediction)

        if normalized_prediction == normalized_reference:
            normalized_exact += 1

        distance = levenshtein(
            normalized_reference,
            normalized_prediction,
        )
        reference_length = max(len(normalized_reference), 1)

        total_distance += distance
        total_reference_chars += reference_length

        per_case.append(
            {
                "case_id": row.get("case_id", ""),
                "condition": row.get("condition", ""),
                "distance": distance,
                "reference_characters": reference_length,
                "cer": round(distance / reference_length, 4),
            }
        )

    total = len(scored)
    report = {
        "scored_cases": total,
        "exact_match_count": exact,
        "exact_match_rate": round(exact / total, 4),
        "normalized_exact_match_count": normalized_exact,
        "normalized_exact_match_rate": round(
            normalized_exact / total,
            4,
        ),
        "character_error_rate": round(
            total_distance / max(total_reference_chars, 1),
            4,
        ),
        "cases": per_case,
    }

    print(json.dumps(report, ensure_ascii=False, indent=2))

    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )


if __name__ == "__main__":
    main()
