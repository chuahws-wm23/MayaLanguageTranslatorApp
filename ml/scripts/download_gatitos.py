#!/usr/bin/env python3
"""Download direct human English↔Yucatec Maya GATITOS entries.

The source is the SMOL dataset on Hugging Face. GATITOS is a high-quality
multilingual lexicon/short-phrase dataset. It is useful direct supervision, but
it is not a large sentence-level translation corpus.
"""

import argparse
import csv
from pathlib import Path

from datasets import get_dataset_config_names, load_dataset


DATASET_NAME = "mabsoutwassim/smol"


def find_yucatec_config() -> str:
    configs = get_dataset_config_names(DATASET_NAME)

    preferred = [
        "gatitos__en_yua",
        "gatitos_en_yua",
        "en_yua",
    ]

    lowered = {name.lower(): name for name in configs}

    for candidate in preferred:
        if candidate.lower() in lowered:
            return lowered[candidate.lower()]

    matches = [
        name
        for name in configs
        if "gatitos" in name.lower()
        and "en_yua" in name.lower()
    ]

    if not matches:
        raise RuntimeError(
            "Could not find the English-Yucatec GATITOS subset. "
            "Available SMOL dataset configuration names may have changed."
        )

    return matches[0]


def iter_targets(row: dict):
    values = row.get("trgs")

    if values is None:
        values = row.get("trg")

    if values is None:
        return

    if isinstance(values, str):
        values = [values]

    for value in values:
        if value is None:
            continue

        # Some lexical resources store alternative translations on separate
        # lines. Treat them as separate training pairs.
        for target in str(value).splitlines():
            target = target.strip()

            if target:
                yield target


def main(output_path: str) -> None:
    config = find_yucatec_config()

    print(f"Loading {DATASET_NAME} / {config}")
    dataset = load_dataset(
        DATASET_NAME,
        config,
        split="train",
    )

    rows = []
    seen = set()

    for row in dataset:
        english = str(row.get("src") or "").strip()

        if not english:
            continue

        for yucatec in iter_targets(row):
            key = (english.casefold(), yucatec.casefold())

            if key in seen:
                continue

            seen.add(key)

            rows.append(
                {
                    "english": english,
                    "yucatec": yucatec,
                    "source": "SMOL/GATITOS",
                    "quality": "human_direct",
                    "context": "",
                }
            )

    output = Path(output_path)
    output.parent.mkdir(parents=True, exist_ok=True)

    with output.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=[
                "english",
                "yucatec",
                "source",
                "quality",
                "context",
            ],
        )
        writer.writeheader()
        writer.writerows(rows)

    print(f"Wrote {len(rows)} direct human pairs to {output}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--output",
        default="ml/data/processed/gatitos_en_yua.csv",
    )
    args = parser.parse_args()
    main(args.output)
