#!/usr/bin/env python3
"""Evaluate one joint model on English↔Yucatec and Spanish↔Yucatec tests."""

import argparse
import csv
from pathlib import Path

import torch
from sacrebleu.metrics import BLEU, CHRF
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer

PROMPTS = {
    "en_to_yua": "translate English to Yucatec Maya: ",
    "yua_to_en": "translate Yucatec Maya to English: ",
    "es_to_yua": "translate Spanish to Yucatec Maya: ",
    "yua_to_es": "translate Yucatec Maya to Spanish: ",
}

def generate(texts, direction, tokenizer, model, device):
    encoded = tokenizer(
        [PROMPTS[direction] + text for text in texts],
        return_tensors="pt",
        padding=True,
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
    return tokenizer.batch_decode(ids, skip_special_tokens=True)

def metrics(predictions, references):
    return (
        CHRF(word_order=2).corpus_score(predictions, [references]).score,
        BLEU().corpus_score(predictions, [references]).score,
    )

def read_csv(path):
    with Path(path).open("r", encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))

def evaluate_direction(rows, source_key, target_key, direction, tokenizer, model, device, batch_size):
    predictions = []
    references = []
    for start in range(0, len(rows), batch_size):
        batch = rows[start : start + batch_size]
        source = [row[source_key] for row in batch]
        target = [row[target_key] for row in batch]
        predictions.extend(generate(source, direction, tokenizer, model, device))
        references.extend(target)
    chrf, bleu = metrics(predictions, references)
    print(f"{direction}: n={len(rows)} ChrF++={chrf:.2f} BLEU={bleu:.2f}")

def main(args):
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    tokenizer = AutoTokenizer.from_pretrained(args.model_dir)
    model = AutoModelForSeq2SeqLM.from_pretrained(args.model_dir)
    model.to(device)
    model.eval()

    english_rows = read_csv(args.english_test_file)
    spanish_rows = read_csv(args.spanish_test_file)

    evaluate_direction(english_rows, "english", "yucatec", "en_to_yua", tokenizer, model, device, args.batch_size)
    evaluate_direction(english_rows, "yucatec", "english", "yua_to_en", tokenizer, model, device, args.batch_size)
    evaluate_direction(spanish_rows, "spanish", "yucatec", "es_to_yua", tokenizer, model, device, args.batch_size)
    evaluate_direction(spanish_rows, "yucatec", "spanish", "yua_to_es", tokenizer, model, device, args.batch_size)

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-dir", required=True)
    parser.add_argument("--english-test-file", default="ml/data/processed/gatitos_test.csv")
    parser.add_argument("--spanish-test-file", default="ml/data/processed/yua_es_test.csv")
    parser.add_argument("--batch-size", type=int, default=16)
    main(parser.parse_args())
