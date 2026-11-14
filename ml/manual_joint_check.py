#!/usr/bin/env python3
"""Qualitative English↔Yucatec checks for the joint multilingual model."""

import argparse

import torch
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer

TESTS = [
    ("en_to_yua", "What is your name?"),
    ("en_to_yua", "Thank you."),
    ("en_to_yua", "Good morning."),
    ("en_to_yua", "Where is the house?"),
    ("en_to_yua", "I want water."),
    ("en_to_yua", "The moon is beautiful."),
    ("yua_to_en", "Bix a k’aaba’?"),
    ("yua_to_en", "Dios bo’otik"),
    ("yua_to_en", "Bix a bel?"),
    ("yua_to_en", "Kíimak óolal"),
]

PROMPTS = {
    "en_to_yua": "translate English to Yucatec Maya: ",
    "yua_to_en": "translate Yucatec Maya to English: ",
}

def main(model_dir: str) -> None:
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    tokenizer = AutoTokenizer.from_pretrained(model_dir)
    model = AutoModelForSeq2SeqLM.from_pretrained(model_dir)
    model.to(device)
    model.eval()

    for direction, text in TESTS:
        encoded = tokenizer(
            PROMPTS[direction] + text,
            return_tensors="pt",
            truncation=True,
            max_length=128,
        )
        encoded = {key: value.to(device) for key, value in encoded.items()}
        with torch.inference_mode():
            ids = model.generate(
                **encoded,
                num_beams=4,
                max_new_tokens=128,
            )
        output = tokenizer.decode(ids[0], skip_special_tokens=True).strip()
        print(f"[{direction}] {text}")
        print("->", output)
        print()

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-dir", required=True)
    args = parser.parse_args()
    main(args.model_dir)
