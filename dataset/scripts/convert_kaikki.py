#!/usr/bin/env python3
"""Convert Kaikki/Wiktextract Yucatec Maya JSONL into FYP dictionary CSV."""

import csv
import json
import sys
import unicodedata
from pathlib import Path


FIELDS = [
    "entry_id",
    "maya_text",
    "maya_normalized",
    "english_text",
    "english_normalized",
    "part_of_speech",
    "pronunciation",
    "example_sentence",
    "category",
    "source",
    "verified",
    "active",
]


def normalize(text: str) -> str:
    return " ".join(
        unicodedata.normalize("NFC", text.strip()).lower().split()
    )


def first_ipa(record: dict) -> str:
    for sound in record.get("sounds", []) or []:
        ipa = sound.get("ipa")
        if ipa:
            return ipa
    return ""


def main(source_path: str, output_path: str) -> None:
    source = Path(source_path)
    output = Path(output_path)
    output.parent.mkdir(parents=True, exist_ok=True)

    records = []
    seen = set()

    with source.open("r", encoding="utf-8") as source_file:
        for line in source_file:
            if not line.strip():
                continue

            record = json.loads(line)

            if record.get("lang_code") != "yua":
                continue

            maya_text = (record.get("word") or "").strip()
            part_of_speech = record.get("pos") or ""

            if not maya_text:
                continue

            for sense in record.get("senses", []) or []:
                tags = set(sense.get("tags", []) or [])

                if "form-of" in tags:
                    continue

                for gloss in sense.get("glosses", []) or []:
                    english_text = gloss.strip()

                    if not english_text:
                        continue

                    key = (
                        normalize(maya_text),
                        normalize(english_text),
                        part_of_speech,
                    )

                    if key in seen:
                        continue

                    seen.add(key)

                    records.append({
                        "entry_id": f"YM{len(records) + 1:06d}",
                        "maya_text": maya_text,
                        "maya_normalized": normalize(maya_text),
                        "english_text": english_text,
                        "english_normalized": normalize(english_text),
                        "part_of_speech": part_of_speech,
                        "pronunciation": first_ipa(record),
                        "example_sentence": "",
                        "category": "",
                        "source": "Kaikki/Wiktionary",
                        "verified": "false",
                        "active": "true",
                    })

    with output.open("w", encoding="utf-8", newline="") as output_file:
        writer = csv.DictWriter(output_file, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows(records)

    print(f"Wrote {len(records)} rows to {output}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(
            "Usage: python convert_kaikki.py INPUT.jsonl OUTPUT.csv"
        )

    main(sys.argv[1], sys.argv[2])
