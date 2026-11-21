#!/usr/bin/env python3
"""Audit ambiguity and machine-readable gloss quality in the FYP dictionary.

This is a diagnostic tool, not a linguistic accuracy scorer. It highlights
cases that need manual review or multi-sense handling before final evaluation.
"""

from __future__ import annotations

import argparse
import csv
import json
from collections import defaultdict
from pathlib import Path


META_GLOSS_MARKERS = (
    "obsolete spelling",
    "alternative spelling",
    "alternate spelling",
    "misspelling",
    "form of",
    "plural of",
)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("dictionary", type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()

    with args.dictionary.open(encoding="utf-8", newline="") as file:
        rows = list(csv.DictReader(file))

    maya_index: dict[str, list[dict[str, str]]] = defaultdict(list)
    english_index: dict[str, list[dict[str, str]]] = defaultdict(list)

    for row in rows:
        maya_index[row.get("maya_normalized", "")].append(row)
        english_index[row.get("english_normalized", "")].append(row)

    maya_ambiguous = {
        key: values
        for key, values in maya_index.items()
        if key and len(values) > 1
    }
    english_ambiguous = {
        key: values
        for key, values in english_index.items()
        if key and len(values) > 1
    }

    meta_gloss_rows = [
        row
        for row in rows
        if any(
            marker in row.get("english_text", "").lower()
            for marker in META_GLOSS_MARKERS
        )
    ]

    report = {
        "row_count": len(rows),
        "distinct_maya_inputs": len(maya_index),
        "distinct_english_inputs": len(english_index),
        "ambiguous_maya_input_count": len(maya_ambiguous),
        "ambiguous_english_input_count": len(english_ambiguous),
        "rows_with_dictionary_meta_glosses": len(meta_gloss_rows),
        "sample_ambiguous_maya": [
            {
                "input": key,
                "outputs": sorted({
                    row.get("english_text", "")
                    for row in values
                }),
            }
            for key, values in list(sorted(maya_ambiguous.items()))[:20]
        ],
        "sample_ambiguous_english": [
            {
                "input": key,
                "outputs": sorted({
                    row.get("maya_text", "")
                    for row in values
                }),
            }
            for key, values in list(sorted(english_ambiguous.items()))[:20]
        ],
        "notes": [
            "Ambiguous input is not automatically an error; natural languages have polysemy and synonyms.",
            "The current app repository API returns a single matching entry, so ambiguous inputs require explicit UX/selection rules.",
            "Dictionary meta-gloss rows should not be used blindly as final linguistic accuracy test cases.",
        ],
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
