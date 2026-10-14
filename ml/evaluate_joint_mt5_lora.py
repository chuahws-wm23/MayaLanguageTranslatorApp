#!/usr/bin/env python3
"""Evaluate joint mT5-small LoRA translation."""

import argparse
import csv
from pathlib import Path

import torch
from peft import PeftConfig, PeftModel
from sacrebleu.metrics import BLEU, CHRF
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer

PREFIXES = {
    "en_to_yua": "English to Yucatec Maya: ",
    "yua_to_en": "Yucatec Maya to English: ",
    "es_to_yua": "Spanish to Yucatec Maya: ",
    "yua_to_es": "Yucatec Maya to Spanish: ",
}


def read_csv(path):
    with Path(path).open("r", encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))


def generate(texts, direction, tokenizer, model, device):
    encoded = tokenizer(
        [PREFIXES[direction] + text for text in texts],
        return_tensors="pt",
        padding=True,
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
    return tokenizer.batch_decode(ids, skip_special_tokens=True)


def score(predictions, references):
    return (
        CHRF(word_order=2).corpus_score(predictions, [references]).score,
        BLEU().corpus_score(predictions, [references]).score,
    )


def evaluate_direction(
    rows,
    source_key,
    target_key,
    direction,
    tokenizer,
    model,
    device,
    batch_size,
):
    predictions = []
    references = []
    for start in range(0, len(rows), batch_size):
        batch = rows[start:start + batch_size]
        sources = [row[source_key] for row in batch]
        targets = [row[target_key] for row in batch]
        predictions.extend(
            generate(
                sources,
                direction,
                tokenizer,
                model,
                device,
            )
        )
        references.extend(targets)

    chrf, bleu = score(predictions, references)
    print(
        f"{direction}: n={len(rows)} "
        f"ChrF++={chrf:.2f} BLEU={bleu:.2f}"
    )


def main(args):
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    config = PeftConfig.from_pretrained(args.adapter_dir)
    tokenizer = AutoTokenizer.from_pretrained(args.adapter_dir)

    dtype = torch.float16 if torch.cuda.is_available() else torch.float32
    base = AutoModelForSeq2SeqLM.from_pretrained(
        config.base_model_name_or_path,
        torch_dtype=dtype,
        low_cpu_mem_usage=True,
    )
    model = PeftModel.from_pretrained(base, args.adapter_dir)
    model.to(device)
    model.eval()

    english = read_csv(args.english_test_file)
    spanish = read_csv(args.spanish_test_file)

    evaluate_direction(
        english, "english", "yucatec", "en_to_yua",
        tokenizer, model, device, args.batch_size
    )
    evaluate_direction(
        english, "yucatec", "english", "yua_to_en",
        tokenizer, model, device, args.batch_size
    )
    evaluate_direction(
        spanish, "spanish", "yucatec", "es_to_yua",
        tokenizer, model, device, args.batch_size
    )
    evaluate_direction(
        spanish, "yucatec", "spanish", "yua_to_es",
        tokenizer, model, device, args.batch_size
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--adapter-dir", required=True)
    parser.add_argument(
        "--english-test-file",
        default="ml/data/processed/gatitos_test.csv",
    )
    parser.add_argument(
        "--spanish-test-file",
        default="ml/data/processed/yua_es_test.csv",
    )
    parser.add_argument("--batch-size", type=int, default=4)
    main(parser.parse_args())
