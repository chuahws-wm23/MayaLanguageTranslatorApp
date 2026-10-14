#!/usr/bin/env python3
"""Joint mT5-small + LoRA training for English/Spanish ↔ Yucatec Maya."""

import argparse
import csv
from pathlib import Path

import numpy as np
import torch
from datasets import Dataset
from peft import LoraConfig, TaskType, get_peft_model
from sacrebleu.metrics import BLEU, CHRF
from transformers import (
    AutoModelForSeq2SeqLM,
    AutoTokenizer,
    DataCollatorForSeq2Seq,
    EarlyStoppingCallback,
    Seq2SeqTrainer,
    Seq2SeqTrainingArguments,
    set_seed,
)

DEFAULT_MODEL = "google/mt5-small"
MAX_LENGTH = 128

PREFIXES = {
    "en_to_yua": "English to Yucatec Maya: ",
    "yua_to_en": "Yucatec Maya to English: ",
    "es_to_yua": "Spanish to Yucatec Maya: ",
    "yua_to_es": "Yucatec Maya to Spanish: ",
}


def prompt(direction: str, text: str) -> str:
    return PREFIXES[direction] + text


def read_english(path: str, limit: int | None, repeat: int) -> list[dict]:
    with Path(path).open("r", encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))
    if limit is not None:
        rows = rows[:limit]

    result = []
    for _ in range(max(1, repeat)):
        for row in rows:
            en = row["english"].strip()
            yua = row["yucatec"].strip()
            if en and yua:
                result.extend([
                    {"input_text": prompt("en_to_yua", en), "target_text": yua},
                    {"input_text": prompt("yua_to_en", yua), "target_text": en},
                ])
    return result


def read_spanish(path: str, limit: int | None) -> list[dict]:
    with Path(path).open("r", encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))
    if limit is not None:
        rows = rows[:limit]

    result = []
    for row in rows:
        es = row["spanish"].strip()
        yua = row["yucatec"].strip()
        if es and yua:
            result.extend([
                {"input_text": prompt("es_to_yua", es), "target_text": yua},
                {"input_text": prompt("yua_to_es", yua), "target_text": es},
            ])
    return result


def main(args) -> None:
    if not torch.cuda.is_available() and not args.allow_cpu:
        raise SystemExit("CUDA GPU not available.")

    set_seed(args.seed)

    if torch.cuda.is_available():
        print("GPU:", torch.cuda.get_device_name(0))
        total_gb = torch.cuda.get_device_properties(0).total_memory / 1024**3
        print(f"VRAM: {total_gb:.1f} GB")

    train_examples = (
        read_english(
            args.english_train_file,
            args.max_english_train_samples,
            args.english_repeat,
        )
        + read_spanish(
            args.spanish_train_file,
            args.max_spanish_train_samples,
        )
    )
    eval_examples = (
        read_english(
            args.english_validation_file,
            args.max_english_eval_samples,
            args.validation_english_repeat,
        )
        + read_spanish(
            args.spanish_validation_file,
            args.max_spanish_eval_samples,
        )
    )

    print("Train examples:", len(train_examples))
    print("Validation examples:", len(eval_examples))

    train_ds = Dataset.from_list(train_examples).shuffle(seed=args.seed)
    eval_ds = Dataset.from_list(eval_examples).shuffle(seed=args.seed)

    tokenizer = AutoTokenizer.from_pretrained(args.model_name)
    dtype = torch.float16 if torch.cuda.is_available() else torch.float32
    base_model = AutoModelForSeq2SeqLM.from_pretrained(
        args.model_name,
        torch_dtype=dtype,
        low_cpu_mem_usage=True,
    )
    base_model.config.use_cache = False

    lora_config = LoraConfig(
        task_type=TaskType.SEQ_2_SEQ_LM,
        r=args.lora_r,
        lora_alpha=args.lora_alpha,
        lora_dropout=args.lora_dropout,
        target_modules=["q", "v"],
        bias="none",
    )
    model = get_peft_model(base_model, lora_config)

    if args.gradient_checkpointing:
        model.gradient_checkpointing_enable(
            gradient_checkpointing_kwargs={"use_reentrant": False}
        )
        model.config.use_cache = False

    model.print_trainable_parameters()

    def preprocess(batch):
        model_inputs = tokenizer(
            batch["input_text"],
            max_length=MAX_LENGTH,
            truncation=True,
        )
        labels = tokenizer(
            text_target=batch["target_text"],
            max_length=MAX_LENGTH,
            truncation=True,
        )
        model_inputs["labels"] = labels["input_ids"]
        return model_inputs

    remove_columns = train_ds.column_names
    train_tok = train_ds.map(
        preprocess,
        batched=True,
        remove_columns=remove_columns,
        desc="Tokenizing train",
    )
    eval_tok = eval_ds.map(
        preprocess,
        batched=True,
        remove_columns=remove_columns,
        desc="Tokenizing validation",
    )

    collator = DataCollatorForSeq2Seq(tokenizer=tokenizer, model=model)
    chrf = CHRF(word_order=2)
    bleu = BLEU()

    def compute_metrics(eval_pred):
        predictions, labels = eval_pred
        if isinstance(predictions, tuple):
            predictions = predictions[0]
        labels = np.where(
            labels != -100,
            labels,
            tokenizer.pad_token_id,
        )
        pred_text = tokenizer.batch_decode(
            predictions,
            skip_special_tokens=True,
        )
        ref_text = tokenizer.batch_decode(
            labels,
            skip_special_tokens=True,
        )
        return {
            "chrf": chrf.corpus_score(pred_text, [ref_text]).score,
            "bleu": bleu.corpus_score(pred_text, [ref_text]).score,
        }

    training_args = Seq2SeqTrainingArguments(
        output_dir=args.output_dir,
        evaluation_strategy="epoch",
        save_strategy="epoch",
        learning_rate=args.learning_rate,
        per_device_train_batch_size=1,
        per_device_eval_batch_size=1,
        gradient_accumulation_steps=args.gradient_accumulation_steps,
        num_train_epochs=args.epochs,
        predict_with_generate=True,
        generation_max_length=MAX_LENGTH,
        generation_num_beams=2,
        load_best_model_at_end=True,
        metric_for_best_model="chrf",
        greater_is_better=True,
        save_total_limit=2,
        logging_steps=25,
        report_to="none",
        fp16=torch.cuda.is_available(),
        dataloader_num_workers=0,
        optim="adamw_torch",
    )

    trainer = Seq2SeqTrainer(
        model=model,
        args=training_args,
        train_dataset=train_tok,
        eval_dataset=eval_tok,
        tokenizer=tokenizer,
        data_collator=collator,
        compute_metrics=compute_metrics,
        callbacks=[EarlyStoppingCallback(early_stopping_patience=2)],
    )

    trainer.train()

    final_dir = Path(args.output_dir) / "best_adapter"
    trainer.save_model(final_dir)
    tokenizer.save_pretrained(final_dir)
    print(f"Saved mT5 LoRA adapter to {final_dir}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--english-train-file", default="ml/data/processed/gatitos_train.csv")
    parser.add_argument("--english-validation-file", default="ml/data/processed/gatitos_validation.csv")
    parser.add_argument("--spanish-train-file", default="ml/data/processed/yua_es_train.csv")
    parser.add_argument("--spanish-validation-file", default="ml/data/processed/yua_es_validation.csv")
    parser.add_argument("--model-name", default=DEFAULT_MODEL)
    parser.add_argument("--output-dir", default="ml/artifacts/mt5-yua-joint-lora")
    parser.add_argument("--epochs", type=float, default=1.0)
    parser.add_argument("--learning-rate", type=float, default=1e-4)
    parser.add_argument("--gradient-accumulation-steps", type=int, default=16)
    parser.add_argument("--english-repeat", type=int, default=2)
    parser.add_argument("--validation-english-repeat", type=int, default=2)
    parser.add_argument("--max-english-train-samples", type=int, default=None)
    parser.add_argument("--max-spanish-train-samples", type=int, default=None)
    parser.add_argument("--max-english-eval-samples", type=int, default=None)
    parser.add_argument("--max-spanish-eval-samples", type=int, default=None)
    parser.add_argument("--lora-r", type=int, default=8)
    parser.add_argument("--lora-alpha", type=int, default=16)
    parser.add_argument("--lora-dropout", type=float, default=0.05)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--allow-cpu", action="store_true")
    parser.add_argument(
        "--no-gradient-checkpointing",
        dest="gradient_checkpointing",
        action="store_false",
    )
    parser.set_defaults(gradient_checkpointing=True)
    main(parser.parse_args())
