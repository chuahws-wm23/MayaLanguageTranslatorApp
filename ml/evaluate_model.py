#!/usr/bin/env python3
"""Evaluate a trained direct Yucatec Maya ↔ English model."""

import argparse
import csv
from collections import defaultdict
from pathlib import Path

import torch
from sacrebleu.metrics import BLEU, CHRF
from transformers import (
    AutoModelForSeq2SeqLM,
    AutoTokenizer,
)


def build_prompt(
    direction: str,
    text: str,
) -> str:
    if direction == "en_to_yua":
        return (
            "translate English to Yucatec Maya: "
            + text
        )

    return (
        "translate Yucatec Maya to English: "
        + text
    )


def translate_batch(
    texts: list[str],
    direction: str,
    tokenizer,
    model,
    device: torch.device,
) -> list[str]:
    inputs = [
        build_prompt(direction, text)
        for text in texts
    ]

    encoded = tokenizer(
        inputs,
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
) -> dict:
    chrf = CHRF(word_order=2)
    bleu = BLEU()

    return {
        "chrf++": chrf.corpus_score(
            predictions,
            [references],
        ).score,
        "bleu": bleu.corpus_score(
            predictions,
            [references],
        ).score,
    }


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
        "cuda" if torch.cuda.is_available() else "cpu"
    )
    model.to(device)
    model.eval()

    with Path(test_file).open(
        "r",
        encoding="utf-8",
        newline="",
    ) as handle:
        rows = list(csv.DictReader(handle))

    results = defaultdict(
        lambda: {
            "predictions": [],
            "references": [],
        }
    )

    for direction in [
        "en_to_yua",
        "yua_to_en",
    ]:
        for start in range(0, len(rows), batch_size):
            batch = rows[start : start + batch_size]

            if direction == "en_to_yua":
                sources = [
                    row["english"] for row in batch
                ]
                references = [
                    row["yucatec"] for row in batch
                ]
            else:
                sources = [
                    row["yucatec"] for row in batch
                ]
                references = [
                    row["english"] for row in batch
                ]

            predictions = translate_batch(
                sources,
                direction,
                tokenizer,
                model,
                device,
            )

            for row, prediction, reference in zip(
                batch,
                predictions,
                references,
                strict=True,
            ):
                keys = [
                    direction,
                    (
                        direction
                        + "::"
                        + row.get("quality", "unknown")
                    ),
                ]

                for key in keys:
                    results[key]["predictions"].append(
                        prediction
                    )
                    results[key]["references"].append(
                        reference
                    )

    for key in sorted(results):
        item = results[key]
        metrics = score(
            item["predictions"],
            item["references"],
        )

        print(
            f"{key}: "
            f"n={len(item['predictions'])} "
            f"ChrF++={metrics['chrf++']:.2f} "
            f"BLEU={metrics['bleu']:.2f}"
        )


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--model-dir",
        default=(
            "ml/artifacts/m2m100-yua-en/"
            "best_model"
        ),
    )
    parser.add_argument(
        "--test-file",
        default="ml/data/processed/test.csv",
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
