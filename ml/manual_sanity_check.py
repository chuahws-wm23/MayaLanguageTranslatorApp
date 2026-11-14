#!/usr/bin/env python3
"""Run a small qualitative sanity check on a trained Yucatec↔English model."""

import argparse

import torch
from transformers import (
    AutoModelForSeq2SeqLM,
    AutoTokenizer,
)


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


def translate(
    text: str,
    direction: str,
    tokenizer,
    model,
    device: torch.device,
) -> str:
    encoded = tokenizer(
        build_prompt(direction, text),
        return_tensors="pt",
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

    return tokenizer.decode(
        output_ids[0],
        skip_special_tokens=True,
    ).strip()


def main(model_dir: str) -> None:
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

    print("=== English → Yucatec Maya ===")
    for text in ENGLISH_TESTS:
        print(text)
        print(
            "→",
            translate(
                text,
                "en_to_yua",
                tokenizer,
                model,
                device,
            ),
        )
        print()

    print("=== Yucatec Maya → English ===")
    for text in MAYA_TESTS:
        print(text)
        print(
            "→",
            translate(
                text,
                "yua_to_en",
                tokenizer,
                model,
                device,
            ),
        )
        print()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--model-dir",
        required=True,
    )
    args = parser.parse_args()
    main(args.model_dir)
