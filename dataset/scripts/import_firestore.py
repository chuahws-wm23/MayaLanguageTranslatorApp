#!/usr/bin/env python3
"""Import processed Yucatec Maya dictionary CSV into Cloud Firestore."""

from __future__ import annotations

import argparse
import csv
import json
import os
from pathlib import Path

import firebase_admin
from firebase_admin import credentials, firestore


def as_bool(value: str) -> bool:
    return str(value).strip().lower() == "true"


def optional(row: dict[str, str], key: str):
    value = row.get(key)
    return value if value not in (None, "") else None


def parse_aliases(row: dict[str, str]) -> list[str]:
    raw = row.get("accepted_english_inputs_json", "").strip()
    if not raw:
        return []

    try:
        values = json.loads(raw)
    except json.JSONDecodeError as error:
        raise ValueError(
            f"Invalid accepted_english_inputs_json for {row.get('entry_id')}"
        ) from error

    if not isinstance(values, list):
        raise ValueError(
            f"accepted_english_inputs_json must be a list for {row.get('entry_id')}"
        )

    return sorted({
        str(value).strip()
        for value in values
        if str(value).strip()
    })


def build_document(row: dict[str, str]) -> dict:
    return {
        "entryId": row["entry_id"],
        "mayaText": row["maya_text"],
        "mayaNormalized": row["maya_normalized"],
        "englishText": row["english_text"],
        "englishNormalized": row["english_normalized"],
        "partOfSpeech": optional(row, "part_of_speech"),
        "pronunciation": optional(row, "pronunciation"),
        "exampleSentence": optional(row, "example_sentence"),
        "category": optional(row, "category"),
        "source": optional(row, "source"),
        "sourceUrl": optional(row, "source_url"),
        "sourceLicense": optional(row, "source_license"),
        "sourceRecordId": optional(row, "source_record_id"),
        "acceptedEnglishInputs": parse_aliases(row),
        "verified": as_bool(row.get("verified", "false")),
        "active": as_bool(row.get("active", "true")),
        "updatedAt": firestore.SERVER_TIMESTAMP,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("path", type=Path)
    parser.add_argument("--collection", default="dictionary")
    parser.add_argument("--limit", type=int)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument(
        "--verified-only",
        action="store_true",
        help="Import only rows manually marked verified=true.",
    )
    args = parser.parse_args()

    with args.path.open(encoding="utf-8", newline="") as file:
        rows = list(csv.DictReader(file))

    if args.verified_only:
        rows = [
            row
            for row in rows
            if as_bool(row.get("verified", "false"))
        ]

    if args.limit is not None:
        rows = rows[: args.limit]

    if args.dry_run:
        print(
            f"Dry run: {len(rows)} rows would be written to "
            f"collection '{args.collection}'."
        )
        return

    service_account_path = os.environ.get(
        "GOOGLE_APPLICATION_CREDENTIALS"
    )
    if not service_account_path:
        raise SystemExit(
            "Set GOOGLE_APPLICATION_CREDENTIALS to a Firebase Admin "
            "service-account JSON file stored outside the repository."
        )

    if not firebase_admin._apps:
        firebase_admin.initialize_app(
            credentials.Certificate(service_account_path)
        )

    db = firestore.client()
    batch = db.batch()
    batch_size = 0
    imported = 0

    for row in rows:
        reference = (
            db.collection(args.collection)
            .document(row["entry_id"])
        )
        batch.set(reference, build_document(row), merge=True)

        imported += 1
        batch_size += 1

        if batch_size == 400:
            batch.commit()
            batch = db.batch()
            batch_size = 0

    if batch_size:
        batch.commit()

    print(
        f"Imported {imported} rows into Firestore collection "
        f"'{args.collection}'."
    )


if __name__ == "__main__":
    main()
