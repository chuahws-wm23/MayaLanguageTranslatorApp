#!/usr/bin/env python3
"""Convert Kaikki/Wiktextract Yucatec Maya JSONL into the FYP dictionary CSV."""

from __future__ import annotations

import csv
import hashlib
import json
import sys
import unicodedata
from pathlib import Path


SOURCE_NAME = "Kaikki / English Wiktionary"
SOURCE_PAGE = "https://kaikki.org/dictionary/Yucatec%20Maya/index.html"
SOURCE_LICENSE = "CC BY-SA / GFDL (Wiktionary-derived data)"

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
    "source_url",
    "source_license",
    "source_record_id",
    "accepted_english_inputs_json",
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
        if isinstance(ipa, str) and ipa.strip():
            return ipa.strip()
    return ""


def first_category(sense: dict) -> str:
    categories = sense.get("categories", []) or []
    for category in categories:
        if isinstance(category, str) and category.strip():
            return category.strip()
        if isinstance(category, dict):
            name = category.get("name")
            if isinstance(name, str) and name.strip():
                return name.strip()
    return ""


def english_aliases(gloss: str) -> list[str]:
    """Return conservative lookup aliases for a dictionary gloss.

    Examples:
    - "hand, arm" -> ["hand", "arm"]
    - "back (of the body)" -> ["back"]

    Explanatory comma clauses such as "land, as opposed to sea" are not
    promoted to aliases.
    """
    candidates: list[str] = []

    head = gloss.split("(", 1)[0].strip()
    if head and head != gloss.strip():
        candidates.append(head)

    parts = [part.strip() for part in gloss.split(",")]
    blocked_prefixes = (
        "as ",
        "especially ",
        "usually ",
        "when ",
        "with ",
        "without ",
        "of ",
        "in ",
        "on ",
        "for ",
        "by ",
    )

    if len(parts) > 1:
        for part in parts:
            lowered = part.lower()
            if (
                part
                and len(part.split()) <= 4
                and not lowered.startswith(blocked_prefixes)
            ):
                candidates.append(part)

    normalized_gloss = normalize(gloss)
    return sorted({
        normalize(candidate)
        for candidate in candidates
        if normalize(candidate)
        and normalize(candidate) != normalized_gloss
    })


def stable_entry_id(
    maya_normalized: str,
    english_normalized: str,
    part_of_speech: str,
) -> str:
    material = "\u241f".join(
        [maya_normalized, english_normalized, part_of_speech.strip().lower()]
    )
    digest = hashlib.sha1(material.encode("utf-8")).hexdigest()[:16]
    return f"kaikki-{digest}"


def main(source_path: str, output_path: str) -> None:
    source = Path(source_path)
    output = Path(output_path)
    output.parent.mkdir(parents=True, exist_ok=True)

    records: list[dict[str, str]] = []
    seen: set[tuple[str, str, str]] = set()

    with source.open("r", encoding="utf-8") as source_file:
        for line_number, line in enumerate(source_file, start=1):
            if not line.strip():
                continue

            try:
                record = json.loads(line)
            except json.JSONDecodeError as error:
                raise SystemExit(
                    f"Invalid JSON on line {line_number}: {error}"
                ) from error

            language_code = record.get("lang_code")
            if language_code not in (None, "yua"):
                continue

            maya_text = (record.get("word") or "").strip()
            part_of_speech = (record.get("pos") or "").strip()

            if not maya_text:
                continue

            maya_normalized = normalize(maya_text)
            pronunciation = first_ipa(record)

            for sense in record.get("senses", []) or []:
                tags = set(sense.get("tags", []) or [])
                if "form-of" in tags:
                    continue

                source_record_id = str(sense.get("id") or "")
                category = first_category(sense)

                glosses = sense.get("glosses", []) or []
                for gloss in glosses:
                    if not isinstance(gloss, str):
                        continue

                    english_text = gloss.strip()
                    if not english_text:
                        continue

                    english_normalized = normalize(english_text)
                    key = (
                        maya_normalized,
                        english_normalized,
                        part_of_speech.lower(),
                    )

                    if key in seen:
                        continue
                    seen.add(key)

                    records.append(
                        {
                            "entry_id": stable_entry_id(*key),
                            "maya_text": maya_text,
                            "maya_normalized": maya_normalized,
                            "english_text": english_text,
                            "english_normalized": english_normalized,
                            "part_of_speech": part_of_speech,
                            "pronunciation": pronunciation,
                            "example_sentence": "",
                            "category": category,
                            "source": SOURCE_NAME,
                            "source_url": SOURCE_PAGE,
                            "source_license": SOURCE_LICENSE,
                            "source_record_id": source_record_id,
                            "accepted_english_inputs_json": json.dumps(
                                english_aliases(english_text),
                                ensure_ascii=False,
                            ),
                            # Machine extraction is not treated as manual verification.
                            "verified": "false",
                            "active": "true",
                        }
                    )

    records.sort(
        key=lambda row: (
            row["maya_normalized"],
            row["english_normalized"],
            row["part_of_speech"],
        )
    )

    with output.open("w", encoding="utf-8", newline="") as output_file:
        writer = csv.DictWriter(output_file, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows(records)

    print(f"Wrote {len(records):,} rows to {output}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(
            "Usage: python convert_kaikki.py INPUT.jsonl OUTPUT.csv"
        )

    main(sys.argv[1], sys.argv[2])
