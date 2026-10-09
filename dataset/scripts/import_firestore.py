#!/usr/bin/env python3
"""Import processed Yucatec Maya dictionary CSV into Cloud Firestore."""

import csv
import os
import sys

import firebase_admin
from firebase_admin import credentials, firestore


def as_bool(value: str) -> bool:
    return str(value).strip().lower() == "true"


def main(path: str) -> None:
    service_account_path = os.environ.get(
        "GOOGLE_APPLICATION_CREDENTIALS"
    )

    if not service_account_path:
        raise SystemExit(
            "Set GOOGLE_APPLICATION_CREDENTIALS first."
        )

    firebase_admin.initialize_app(
        credentials.Certificate(service_account_path)
    )

    db = firestore.client()
    batch = db.batch()
    batch_size = 0
    imported = 0

    with open(path, encoding="utf-8", newline="") as file:
        for row in csv.DictReader(file):
            document = {
                "entryId": row["entry_id"],
                "mayaText": row["maya_text"],
                "mayaNormalized": row["maya_normalized"],
                "englishText": row["english_text"],
                "englishNormalized": row["english_normalized"],
                "partOfSpeech": row.get("part_of_speech") or None,
                "pronunciation": row.get("pronunciation") or None,
                "exampleSentence": row.get("example_sentence") or None,
                "category": row.get("category") or None,
                "source": row.get("source") or None,
                "verified": as_bool(row.get("verified", "false")),
                "active": as_bool(row.get("active", "true")),
            }

            reference = (
                db.collection("dictionary")
                .document(row["entry_id"])
            )

            batch.set(reference, document)

            imported += 1
            batch_size += 1

            if batch_size == 400:
                batch.commit()
                batch = db.batch()
                batch_size = 0

    if batch_size:
        batch.commit()

    print(f"Imported {imported} dictionary rows.")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit(
            "Usage: python import_firestore.py FILE.csv"
        )

    main(sys.argv[1])
