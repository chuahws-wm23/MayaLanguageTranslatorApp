#!/usr/bin/env python3
"""Score a manually verified translation evaluation set against the CSV dictionary."""

from __future__ import annotations

import argparse
import csv
import json
import unicodedata
from collections import defaultdict
from pathlib import Path


def normalize(value: str) -> str:
    return " ".join(
        unicodedata.normalize("NFC", value.strip()).lower().split()
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("dictionary", type=Path)
    parser.add_argument("evaluation", type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()

    maya_to_english: dict[str, set[str]] = defaultdict(set)
    english_to_maya: dict[str, set[str]] = defaultdict(set)

    with args.dictionary.open(encoding="utf-8", newline="") as file:
        for row in csv.DictReader(file):
            maya = normalize(row.get("maya_text", ""))
            english = normalize(row.get("english_text", ""))
            if maya and english:
                maya_to_english[maya].add(english)
                english_to_maya[english].add(maya)

    with args.evaluation.open(encoding="utf-8", newline="") as file:
        all_cases = list(csv.DictReader(file))

    cases = [
        row
        for row in all_cases
        if row.get("verified", "").strip().lower() == "true"
    ]

    if not cases:
        raise SystemExit(
            "No verified evaluation cases. Manually review the evaluation CSV "
            "and set verified=true for approved rows first."
        )

    correct = 0
    no_match = 0
    mismatches = []

    for row in cases:
        direction = row.get("direction", "").strip()
        input_text = normalize(row.get("input_text", ""))
        expected = normalize(row.get("expected_output", ""))

        if direction == "maya_to_english":
            predictions = maya_to_english.get(input_text, set())
        elif direction == "english_to_maya":
            predictions = english_to_maya.get(input_text, set())
        else:
            raise SystemExit(
                f"Unsupported direction in {row.get('case_id')}: {direction}"
            )

        if not predictions:
            no_match += 1
            mismatches.append(
                {
                    "case_id": row.get("case_id"),
                    "status": "no_match",
                    "input": row.get("input_text"),
                    "expected": row.get("expected_output"),
                    "predictions": [],
                }
            )
        elif expected in predictions:
            correct += 1
        else:
            mismatches.append(
                {
                    "case_id": row.get("case_id"),
                    "status": "wrong_output",
                    "input": row.get("input_text"),
                    "expected": row.get("expected_output"),
                    "predictions": sorted(predictions),
                }
            )

    total = len(cases)
    accuracy = correct / total if total else 0.0
    coverage = (total - no_match) / total if total else 0.0

    report = {
        "verified_cases": total,
        "correct": correct,
        "incorrect": total - correct,
        "no_match": no_match,
        "exact_match_accuracy": round(accuracy, 4),
        "dictionary_coverage": round(coverage, 4),
        "mismatches": mismatches,
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
