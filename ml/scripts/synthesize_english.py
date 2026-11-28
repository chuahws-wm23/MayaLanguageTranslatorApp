#!/usr/bin/env python3
"""Create experimental English targets from the human YUA↔Spanish corpus.

The Yucatec Maya text is human-created/curated. The English side produced by
this script is machine translated from Spanish and is therefore explicitly
marked as synthetic_english.
"""

import argparse
import csv
from pathlib import Path

import torch
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer


DEFAULT_MODEL = "Helsinki-NLP/opus-mt-es-en"


def load_rows(path: Path) -> list[dict]:
    with path.open("r", encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle, delimiter="\t"))


def existing_ids(path: Path) -> set[str]:
    if not path.exists():
        return set()

    with path.open("r", encoding="utf-8", newline="") as handle:
        return {
            row["phrase_id"]
            for row in csv.DictReader(handle)
            if row.get("phrase_id")
        }


def translate_batch(
    texts: list[str],
    tokenizer,
    model,
    device: torch.device,
) -> list[str]:
    encoded = tokenizer(
        texts,
        return_tensors="pt",
        padding=True,
        truncation=True,
        max_length=128,
    )
    encoded = {
        key: value.to(device)
        for key, value in encoded.items()
    }

    with torch.inference_mode():
        output_ids = model.generate(
            **encoded,
            num_beams=4,
            max_new_tokens=128,
            renormalize_logits=True,
        )

    return tokenizer.batch_decode(
        output_ids,
        skip_special_tokens=True,
    )


def main(
    input_path: str,
    output_path: str,
    model_name: str,
    batch_size: int,
) -> None:
    source = Path(input_path)
    output = Path(output_path)
    output.parent.mkdir(parents=True, exist_ok=True)

    rows = load_rows(source)
    completed = existing_ids(output)

    pending = [
        row
        for row in rows
        if row["Phrase_ID"] not in completed
    ]

    if not pending:
        print("Nothing to do. All rows are already synthesized.")
        return

    print(f"Loading Spanish→English model: {model_name}")
    tokenizer = AutoTokenizer.from_pretrained(model_name)
    model = AutoModelForSeq2SeqLM.from_pretrained(model_name)

    device = torch.device(
        "cuda" if torch.cuda.is_available() else "cpu"
    )
    model.to(device)
    model.eval()

    fieldnames = [
        "phrase_id",
        "english",
        "yucatec",
        "spanish",
        "source",
        "quality",
        "context",
    ]

    new_file = not output.exists()

    with output.open(
        "a",
        encoding="utf-8",
        newline="",
    ) as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=fieldnames,
        )

        if new_file:
            writer.writeheader()

        for start in range(0, len(pending), batch_size):
            batch = pending[start : start + batch_size]
            spanish = [row["ES"].strip() for row in batch]
            english = translate_batch(
                spanish,
                tokenizer,
                model,
                device,
            )

            for row, translated in zip(
                batch,
                english,
                strict=True,
            ):
                writer.writerow(
                    {
                        "phrase_id": row["Phrase_ID"],
                        "english": translated.strip(),
                        "yucatec": row["YUA"].strip(),
                        "spanish": row["ES"].strip(),
                        "source": (
                            "YUA-ES-CCC + "
                            f"{model_name}"
                        ),
                        "quality": "synthetic_english",
                        "context": row[
                            "Communicative_Context"
                        ],
                    }
                )

            handle.flush()
            done = min(start + batch_size, len(pending))
            print(
                f"Synthesized {done}/{len(pending)} pending rows"
            )

    print(f"Saved synthetic direct pairs to {output}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--input",
        default=(
            "ml/data/raw/"
            "YUA_ES_communicative_contexts_corpus_v1.tsv"
        ),
    )
    parser.add_argument(
        "--output",
        default=(
            "ml/data/processed/"
            "yua_es_ccc_synthetic_en.csv"
        ),
    )
    parser.add_argument(
        "--model",
        default=DEFAULT_MODEL,
    )
    parser.add_argument(
        "--batch-size",
        type=int,
        default=32,
    )
    args = parser.parse_args()

    main(
        args.input,
        args.output,
        args.model,
        args.batch_size,
    )
