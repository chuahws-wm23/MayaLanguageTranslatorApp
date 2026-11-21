#!/usr/bin/env python3
"""Download the current Kaikki Yucatec Maya JSONL source file.

The source is public machine-readable data extracted from English Wiktionary.
The postprocessed per-language download is convenient but Kaikki labels it
deprecated, so keep the URL isolated in this script for easy replacement.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path


DEFAULT_URL = (
    "https://kaikki.org/dictionary/Yucatec%20Maya/"
    "kaikki.org-dictionary-YucatecMaya.jsonl"
)
DEFAULT_OUTPUT = Path("dataset/raw/kaikki-yucatec-maya.jsonl")
DEFAULT_METADATA = Path("dataset/raw/kaikki-yucatec-maya.metadata.json")
USER_AGENT = "MayaLanguageTranslatorApp-FYP/1.0 (+academic dataset preparation)"


def download(url: str, output: Path, retries: int = 3) -> tuple[int, str]:
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_suffix(output.suffix + ".part")

    for attempt in range(1, retries + 1):
        try:
            request = urllib.request.Request(
                url,
                headers={"User-Agent": USER_AGENT},
            )
            digest = hashlib.sha256()
            total = 0

            with urllib.request.urlopen(request, timeout=45) as response:
                with temporary.open("wb") as target:
                    while True:
                        chunk = response.read(1024 * 128)
                        if not chunk:
                            break
                        target.write(chunk)
                        digest.update(chunk)
                        total += len(chunk)

            if total < 1_000:
                raise RuntimeError(
                    f"Downloaded file is unexpectedly small: {total} bytes"
                )

            temporary.replace(output)
            return total, digest.hexdigest()

        except (
            urllib.error.URLError,
            TimeoutError,
            RuntimeError,
        ) as error:
            if temporary.exists():
                temporary.unlink()

            if attempt == retries:
                raise SystemExit(
                    f"Could not download Kaikki data after {retries} attempts: {error}"
                )

            time.sleep(2 * attempt)

    raise AssertionError("unreachable")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", default=DEFAULT_URL)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--metadata", type=Path, default=DEFAULT_METADATA)
    args = parser.parse_args()

    size_bytes, sha256 = download(args.url, args.output)

    metadata = {
        "source_name": "Kaikki / English Wiktionary — Yucatec Maya",
        "source_url": args.url,
        "downloaded_at_utc": datetime.now(timezone.utc).isoformat(),
        "size_bytes": size_bytes,
        "sha256": sha256,
        "language": "Yucatec Maya",
        "iso_639_3": "yua",
        "license_note": (
            "Kaikki states that extracted Wiktionary data is available "
            "under the same licenses as Wiktionary: CC BY-SA and GFDL."
        ),
    }

    args.metadata.parent.mkdir(parents=True, exist_ok=True)
    args.metadata.write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    print(f"Downloaded {size_bytes:,} bytes to {args.output}")
    print(f"SHA-256: {sha256}")
    print(f"Metadata: {args.metadata}")


if __name__ == "__main__":
    main()
