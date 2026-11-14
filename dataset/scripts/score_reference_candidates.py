#!/usr/bin/env python3
"""Measure dictionary coverage of independently sourced candidate pairs.

This is a coverage/regression diagnostic, NOT a final linguistic-accuracy
score. Candidate rows remain pending human approval unless explicitly reviewed.
"""

from __future__ import annotations

import argparse
import csv
import json
import unicodedata
from collections import defaultdict
from pathlib import Path


def normalize(value: str) -> str:
    text = unicodedata.normalize("NFC", value.strip().lower())
    for mark in ("'", "’", "ʻ", "ʹ"):
        text = text.replace(mark, "ʼ")
    for mark in "?!.,;:":
        text = text.replace(mark, "")
    return " ".join(text.split())


def aliases(row: dict[str, str]) -> set[str]:
    raw = row.get("accepted_english_inputs_json", "").strip()
    if not raw:
        return set()
    try:
        values = json.loads(raw)
    except json.JSONDecodeError:
        return set()
    return {
        normalize(str(value))
        for value in values
        if normalize(str(value))
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("dictionary", type=Path)
    parser.add_argument("candidates", type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()

    maya_to_english: dict[str, set[str]] = defaultdict(set)
    english_to_maya: dict[str, set[str]] = defaultdict(set)

    with args.dictionary.open(encoding="utf-8", newline="") as file:
        for row in csv.DictReader(file):
            maya = normalize(row.get("maya_text", ""))
            english = normalize(row.get("english_text", ""))

            if not maya or not english:
                continue

            maya_to_english[maya].add(english)
            for alias in aliases(row):
                maya_to_english[maya].add(alias)

            english_to_maya[english].add(maya)
            for alias in aliases(row):
                english_to_maya[alias].add(maya)

    with args.candidates.open(encoding="utf-8", newline="") as file:
        candidates = list(csv.DictReader(file))

    results = []
    supported = 0

    for row in candidates:
        direction = row.get("direction", "").strip()
        source = normalize(row.get("input_text", ""))
        expected = normalize(row.get("expected_output", ""))

        if direction == "maya_to_english":
            outputs = maya_to_english.get(source, set())
        elif direction == "english_to_maya":
            outputs = english_to_maya.get(source, set())
        else:
            raise SystemExit(
                f"Unsupported direction for {row.get('case_id')}: {direction}"
            )

        is_supported = expected in outputs
        supported += int(is_supported)

        results.append(
            {
                "case_id": row.get("case_id", ""),
                "direction": direction,
                "input_text": row.get("input_text", ""),
                "expected_output": row.get("expected_output", ""),
                "supported": is_supported,
                "dictionary_outputs": sorted(outputs),
                "candidate_status": row.get("status", ""),
            }
        )

    total = len(results)
    report = {
        "candidate_count": total,
        "supported_count": supported,
        "unsupported_count": total - supported,
        "coverage_rate": round(
            supported / total if total else 0.0,
            4,
        ),
        "interpretation": (
            "Coverage against independently sourced candidate pairs. "
            "This is not final translation accuracy because candidates "
            "still require human approval and natural-language alternatives "
            "may need sense-level judgement."
        ),
        "cases": results,
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
