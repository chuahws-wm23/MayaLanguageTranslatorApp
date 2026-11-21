#!/usr/bin/env python3
"""Evaluate Yucatec Maya ↔ Spanish on held-out human YUA-ES pairs."""

import argparse
import csv
from pathlib import Path

import torch
from sacrebleu.metrics import BLEU, CHRF
from transformers import (
    AutoModelForSeq2SeqLM,
    AutoTokenizer,
)


def make_prompt(
    direction: str,
    text: str,
) -> str:
    if direction == "es_to_yua":
        return (
            "translate Spanish to Yucatec Maya: "
            + text
        )

    return (
        "translate Yucatec Maya to Spanish: "
        + text
    )


def translate_batch(
    texts: list[str],
    direction: str,
    tokenizer,
    model,
    device,
) -> list[str]:
    encoded = tokenizer(
        [
            make_prompt(direction, text)
            for text in texts
        ],
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
        )

    return tokenizer.batch_decode(
        output_ids,
        skip_special_tokens=True,
    )


def score(
    predictions: list[str],
    references: list[str],
) -> tuple[float, float]:
    chrf = CHRF(word_order=2).corpus_score(
        predictions,
        [references],
    ).score
    bleu = BLEU().corpus_score(
        predictions,
        [references],
    ).score
    return chrf, bleu


def main(
    model_dir: str,
    test_file: str,
    batch_size: int,
) -> None:
    tokenizer = AutoTokenizer.from_pretrained(
        model_dir
    )
    model = AutoModelForSeq2SeqLM.from_pretrained(
        model_dir
    )
    device = torch.device(
        "cuda"
        if torch.cuda.is_available()
        else "cpu"
    )
    model.to(device)
    model.eval()

    with Path(test_file).open(
        "r",
        encoding="utf-8",
        newline="",
    ) as handle:
        rows = list(csv.DictReader(handle))

    for direction in [
        "es_to_yua",
        "yua_to_es",
    ]:
        predictions = []
        references = []

        for start in range(
            0,
            len(rows),
            batch_size,
        ):
            batch = rows[
                start : start + batch_size
            ]

            if direction == "es_to_yua":
                source = [
                    row["spanish"]
                    for row in batch
                ]
                target = [
                    row["yucatec"]
                    for row in batch
                ]
            else:
                source = [
                    row["yucatec"]
                    for row in batch
                ]
                target = [
                    row["spanish"]
                    for row in batch
                ]

            predictions.extend(
                translate_batch(
                    source,
                    direction,
                    tokenizer,
                    model,
                    device,
                )
            )
            references.extend(target)

        chrf, bleu = score(
            predictions,
            references,
        )

        print(
            f"{direction}: n={len(rows)} "
            f"ChrF++={chrf:.2f} "
            f"BLEU={bleu:.2f}"
        )


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--model-dir",
        required=True,
    )
    parser.add_argument(
        "--test-file",
        default=(
            "ml/data/processed/"
            "yua_es_test.csv"
        ),
    )
    parser.add_argument(
        "--batch-size",
        type=int,
        default=16,
    )
    args = parser.parse_args()
    main(
        args.model_dir,
        args.test_file,
        args.batch_size,
    )
