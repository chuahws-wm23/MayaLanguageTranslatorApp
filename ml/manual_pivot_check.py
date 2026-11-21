#!/usr/bin/env python3
"""Qualitative English ↔ Yucatec Maya checks through the Spanish pivot."""

import argparse

import torch
from transformers import (
    AutoModelForSeq2SeqLM,
    AutoTokenizer,
)


EN_ES_MODEL = "Helsinki-NLP/opus-mt-en-es"
ES_EN_MODEL = "Helsinki-NLP/opus-mt-es-en"

ENGLISH_TESTS = [
    "What is your name?",
    "Thank you.",
    "Good morning.",
    "Where is the house?",
    "I want water.",
    "The moon is beautiful.",
]

MAYA_TESTS = [
    "Bix a k’aaba’?",
    "Dios bo’otik",
    "Bix a bel?",
    "Kíimak óolal",
]


def yua_prompt(
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


def one(
    text: str,
    tokenizer,
    model,
    device,
) -> str:
    encoded = tokenizer(
        text,
        return_tensors="pt",
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

    return tokenizer.decode(
        ids[0],
        skip_special_tokens=True,
    ).strip()


def main(
    yua_es_model_dir: str,
) -> None:
    device = torch.device(
        "cuda"
        if torch.cuda.is_available()
        else "cpu"
    )

    yua_tok = AutoTokenizer.from_pretrained(
        yua_es_model_dir
    )
    yua_model = AutoModelForSeq2SeqLM.from_pretrained(
        yua_es_model_dir
    ).to(device)

    en_es_tok = AutoTokenizer.from_pretrained(
        EN_ES_MODEL
    )
    en_es_model = AutoModelForSeq2SeqLM.from_pretrained(
        EN_ES_MODEL
    ).to(device)

    es_en_tok = AutoTokenizer.from_pretrained(
        ES_EN_MODEL
    )
    es_en_model = AutoModelForSeq2SeqLM.from_pretrained(
        ES_EN_MODEL
    ).to(device)

    print(
        "=== English → Spanish → Yucatec Maya ==="
    )
    for text in ENGLISH_TESTS:
        spanish = one(
            text,
            en_es_tok,
            en_es_model,
            device,
        )
        maya = one(
            yua_prompt(
                "es_to_yua",
                spanish,
            ),
            yua_tok,
            yua_model,
            device,
        )
        print(text)
        print("Spanish:", spanish)
        print("Yucatec Maya:", maya)
        print()

    print(
        "=== Yucatec Maya → Spanish → English ==="
    )
    for text in MAYA_TESTS:
        spanish = one(
            yua_prompt(
                "yua_to_es",
                text,
            ),
            yua_tok,
            yua_model,
            device,
        )
        english = one(
            spanish,
            es_en_tok,
            es_en_model,
            device,
        )
        print(text)
        print("Spanish:", spanish)
        print("English:", english)
        print()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--yua-es-model-dir",
        required=True,
    )
    args = parser.parse_args()
    main(args.yua_es_model_dir)
