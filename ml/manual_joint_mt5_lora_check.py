#!/usr/bin/env python3
"""Manual sanity check for joint mT5-small + LoRA."""

import argparse

import torch
from peft import PeftConfig, PeftModel
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

PREFIXES = {
    "en_to_yua": "English to Yucatec Maya: ",
    "yua_to_en": "Yucatec Maya to English: ",
}


def main(adapter_dir: str) -> None:
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    config = PeftConfig.from_pretrained(adapter_dir)
    tokenizer = AutoTokenizer.from_pretrained(adapter_dir, use_fast=False)

    base = AutoModelForSeq2SeqLM.from_pretrained(
        config.base_model_name_or_path,
        torch_dtype=torch.float32,
        low_cpu_mem_usage=True,
    )

    if base.config.vocab_size != len(tokenizer):
        base.resize_token_embeddings(len(tokenizer))
    model = PeftModel.from_pretrained(base, adapter_dir)
    model.to(device)
    model.eval()

    for direction, text in TESTS:
        encoded = tokenizer(
            PREFIXES[direction] + text,
            return_tensors="pt",
            truncation=True,
            max_length=128,
        )
        encoded = {key: value.to(device) for key, value in encoded.items()}

        with torch.inference_mode():
            ids = model.generate(
                **encoded,
                num_beams=2,
                max_new_tokens=128,
            )

        output = tokenizer.decode(
            ids[0],
            skip_special_tokens=True,
        ).strip()

        print(f"[{direction}] {text}")
        print("->", output)
        print()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--adapter-dir", required=True)
    args = parser.parse_args()
    main(args.adapter_dir)
