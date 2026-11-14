#!/usr/bin/env python3
"""Evaluate end-to-end English ↔ Yucatec Maya through Spanish.

English → Spanish uses Helsinki-NLP/opus-mt-en-es.
Spanish → English uses Helsinki-NLP/opus-mt-es-en.
Yucatec Maya ↔ Spanish uses the locally fine-tuned M2M100 model.
"""

import argparse
import csv
from pathlib import Path

import torch
from sacrebleu.metrics import BLEU, CHRF
from transformers import (
    AutoModelForSeq2SeqLM,
    AutoTokenizer,
)


EN_ES_MODEL = "Helsinki-NLP/opus-mt-en-es"
ES_EN_MODEL = "Helsinki-NLP/opus-mt-es-en"


def yua_es_prompt(
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


def generate(
    texts: list[str],
    tokenizer,
    model,
    device,
    prompt_direction: str | None = None,
) -> list[str]:
    if prompt_direction is not None:
        texts = [
            yua_es_prompt(
                prompt_direction,
                text,
            )
            for text in texts
        ]

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
        ids = model.generate(
            **encoded,
            num_beams=4,
            max_new_tokens=128,
        )

    return tokenizer.batch_decode(
        ids,
        skip_special_tokens=True,
    )


def score(
    predictions: list[str],
    references: list[str],
) -> tuple[float, float]:
    return (
        CHRF(word_order=2).corpus_score(
            predictions,
            [references],
        ).score,
        BLEU().corpus_score(
            predictions,
            [references],
        ).score,
    )


def main(
    yua_es_model_dir: str,
    test_file: str,
    batch_size: int,
) -> None:
    device = torch.device(
        "cuda"
        if torch.cuda.is_available()
        else "cpu"
    )

    yua_es_tokenizer = AutoTokenizer.from_pretrained(
        yua_es_model_dir
    )
    yua_es_model = AutoModelForSeq2SeqLM.from_pretrained(
        yua_es_model_dir
    ).to(device)
    yua_es_model.eval()

    en_es_tokenizer = AutoTokenizer.from_pretrained(
        EN_ES_MODEL
    )
    en_es_model = AutoModelForSeq2SeqLM.from_pretrained(
        EN_ES_MODEL
    ).to(device)
    en_es_model.eval()

    es_en_tokenizer = AutoTokenizer.from_pretrained(
        ES_EN_MODEL
    )
    es_en_model = AutoModelForSeq2SeqLM.from_pretrained(
        ES_EN_MODEL
    ).to(device)
    es_en_model.eval()

    with Path(test_file).open(
        "r",
        encoding="utf-8",
        newline="",
    ) as handle:
        rows = list(csv.DictReader(handle))

    en_to_yua_predictions = []
    yua_to_en_predictions = []
    yua_references = []
    en_references = []

    for start in range(
        0,
        len(rows),
        batch_size,
    ):
        batch = rows[
            start : start + batch_size
        ]

        english = [
            row["english"]
            for row in batch
        ]
        yucatec = [
            row["yucatec"]
            for row in batch
        ]

        spanish_from_english = generate(
            english,
            en_es_tokenizer,
            en_es_model,
            device,
        )
        maya_from_spanish = generate(
            spanish_from_english,
            yua_es_tokenizer,
            yua_es_model,
            device,
            prompt_direction="es_to_yua",
        )

        spanish_from_maya = generate(
            yucatec,
            yua_es_tokenizer,
            yua_es_model,
            device,
            prompt_direction="yua_to_es",
        )
        english_from_spanish = generate(
            spanish_from_maya,
            es_en_tokenizer,
            es_en_model,
            device,
        )

        en_to_yua_predictions.extend(
            maya_from_spanish
        )
        yua_to_en_predictions.extend(
            english_from_spanish
        )
        yua_references.extend(yucatec)
        en_references.extend(english)

    en_yua_chrf, en_yua_bleu = score(
        en_to_yua_predictions,
        yua_references,
    )
    yua_en_chrf, yua_en_bleu = score(
        yua_to_en_predictions,
        en_references,
    )

    print(
        f"pivot en_to_yua: n={len(rows)} "
        f"ChrF++={en_yua_chrf:.2f} "
        f"BLEU={en_yua_bleu:.2f}"
    )
    print(
        f"pivot yua_to_en: n={len(rows)} "
        f"ChrF++={yua_en_chrf:.2f} "
        f"BLEU={yua_en_bleu:.2f}"
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--yua-es-model-dir",
        required=True,
    )
    parser.add_argument(
        "--test-file",
        default=(
            "ml/data/processed/"
            "human_test.csv"
        ),
    )
    parser.add_argument(
        "--batch-size",
        type=int,
        default=16,
    )
    args = parser.parse_args()
    main(
        args.yua_es_model_dir,
        args.test_file,
        args.batch_size,
    )
