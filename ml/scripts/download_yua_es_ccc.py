#!/usr/bin/env python3
"""Download the open YUA-ES Communicative Contexts Corpus."""

import argparse
import csv
from pathlib import Path
from urllib.request import Request, urlopen


CORPUS_URL = (
    "https://raw.githubusercontent.com/alemol/yua-es-ccc/main/"
    "YUA_ES_communicative_contexts_corpus_v1.tsv"
)


def download(url: str, destination: Path) -> None:
    request = Request(
        url,
        headers={"User-Agent": "MayaLanguageTranslatorApp-FYP/1.0"},
    )

    with urlopen(request, timeout=120) as response:
        destination.write_bytes(response.read())


def validate(path: Path) -> int:
    with path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle, delimiter="\t")
        expected = {
            "Phrase_ID",
            "YUA",
            "ES",
            "Communicative_Context",
        }

        if not expected.issubset(set(reader.fieldnames or [])):
            raise RuntimeError(
                "Downloaded YUA-ES corpus schema does not match the "
                "expected four-column release."
            )

        count = sum(1 for _ in reader)

    if count < 1000:
        raise RuntimeError(
            f"Downloaded corpus looks incomplete: only {count} rows."
        )

    return count


def main(output_path: str, force: bool) -> None:
    output = Path(output_path)
    output.parent.mkdir(parents=True, exist_ok=True)

    if output.exists() and not force:
        count = validate(output)
        print(f"Already present: {output} ({count} rows)")
        return

    download(CORPUS_URL, output)
    count = validate(output)
    print(f"Downloaded {count} Yucatec Maya-Spanish pairs to {output}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--output",
        default="ml/data/raw/YUA_ES_communicative_contexts_corpus_v1.tsv",
    )
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()
    main(args.output, args.force)
