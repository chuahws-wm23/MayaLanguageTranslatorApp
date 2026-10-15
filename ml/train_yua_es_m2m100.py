#!/usr/bin/env python3
"""Fine-tune bidirectional Yucatec Maya ↔ Spanish translation."""

import argparse
import csv
from pathlib import Path

import numpy as np
import torch
from datasets import Dataset
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


DEFAULT_MODEL = "facebook/m2m100_418M"
MAX_LENGTH = 128


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


def read_examples(
    path: str,
    max_pairs: int | None,
) -> list[dict]:
    with Path(path).open(
        "r",
        encoding="utf-8",
        newline="",
    ) as handle:
        rows = list(csv.DictReader(handle))

    if max_pairs is not None:
        rows = rows[:max_pairs]

    examples = []

    for row in rows:
        yucatec = row["yucatec"].strip()
        spanish = row["spanish"].strip()

        examples.extend(
            [
                {
                    "input_text": make_prompt(
                        "es_to_yua",
                        spanish,
                    ),
                    "target_text": yucatec,
                },
                {
                    "input_text": make_prompt(
                        "yua_to_es",
                        yucatec,
                    ),
                    "target_text": spanish,
                },
            ]
        )

    return examples


def check_gpu(
    allow_cpu: bool,
) -> None:
    print(
        "PyTorch CUDA available:",
        torch.cuda.is_available(),
    )

    if torch.cuda.is_available():
        print(
            "GPU:",
            torch.cuda.get_device_name(0),
        )
        return

    if not allow_cpu:
        raise SystemExit(
            "No CUDA GPU is available. In Colab choose "
            "Runtime → Change runtime type → T4 GPU. "
            "Use --allow-cpu only for debugging."
        )


def main(args) -> None:
    check_gpu(args.allow_cpu)
    set_seed(args.seed)

    tokenizer = AutoTokenizer.from_pretrained(
        args.model_name
    )
    model = AutoModelForSeq2SeqLM.from_pretrained(
        args.model_name
    )

    if args.gradient_checkpointing:
        model.gradient_checkpointing_enable()
        model.config.use_cache = False

    train_dataset = Dataset.from_list(
        read_examples(
            args.train_file,
            args.max_train_samples,
        )
    )
    validation_dataset = Dataset.from_list(
        read_examples(
            args.validation_file,
            args.max_eval_samples,
        )
    )

    def preprocess(batch):
        inputs = tokenizer(
            batch["input_text"],
            max_length=MAX_LENGTH,
            truncation=True,
        )

        # Yucatec Maya is not a built-in M2M100 language code. We therefore
        # keep this as prompt-conditioned seq2seq fine-tuning and tokenize
        # target text normally rather than using text_target/tgt_lang.
        labels = tokenizer(
            batch["target_text"],
            max_length=MAX_LENGTH,
            truncation=True,
        )
        inputs["labels"] = labels["input_ids"]
        return inputs

    remove_columns = train_dataset.column_names

    train_tokenized = train_dataset.map(
        preprocess,
        batched=True,
        remove_columns=remove_columns,
        desc="Tokenizing train",
    )
    validation_tokenized = validation_dataset.map(
        preprocess,
        batched=True,
        remove_columns=remove_columns,
        desc="Tokenizing validation",
    )

    collator = DataCollatorForSeq2Seq(
        tokenizer=tokenizer,
        model=model,
    )

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

        prediction_text = tokenizer.batch_decode(
            predictions,
            skip_special_tokens=True,
        )
        reference_text = tokenizer.batch_decode(
            labels,
            skip_special_tokens=True,
        )

        return {
            "chrf": chrf.corpus_score(
                prediction_text,
                [reference_text],
            ).score,
            "bleu": bleu.corpus_score(
                prediction_text,
                [reference_text],
            ).score,
        }

    training_args = Seq2SeqTrainingArguments(
        output_dir=args.output_dir,
        evaluation_strategy="epoch",
        save_strategy="epoch",
        learning_rate=args.learning_rate,
        per_device_train_batch_size=args.train_batch_size,
        per_device_eval_batch_size=args.eval_batch_size,
        gradient_accumulation_steps=args.gradient_accumulation_steps,
        weight_decay=0.01,
        num_train_epochs=args.epochs,
        predict_with_generate=True,
        generation_max_length=MAX_LENGTH,
        generation_num_beams=4,
        load_best_model_at_end=True,
        metric_for_best_model="chrf",
        greater_is_better=True,
        save_total_limit=2,
        logging_steps=50,
        report_to="none",
        fp16=torch.cuda.is_available(),
    )

    trainer = Seq2SeqTrainer(
        model=model,
        args=training_args,
        train_dataset=train_tokenized,
        eval_dataset=validation_tokenized,
        tokenizer=tokenizer,
        data_collator=collator,
        compute_metrics=compute_metrics,
        callbacks=[
            EarlyStoppingCallback(
                early_stopping_patience=2
            )
        ],
    )

    trainer.train()

    final_dir = (
        Path(args.output_dir)
        / "best_model"
    )
    trainer.save_model(final_dir)
    tokenizer.save_pretrained(final_dir)

    print(
        f"Saved best model to {final_dir}"
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--train-file",
        default=(
            "ml/data/processed/"
            "yua_es_train.csv"
        ),
    )
    parser.add_argument(
        "--validation-file",
        default=(
            "ml/data/processed/"
            "yua_es_validation.csv"
        ),
    )
    parser.add_argument(
        "--model-name",
        default=DEFAULT_MODEL,
    )
    parser.add_argument(
        "--output-dir",
        default=(
            "ml/artifacts/"
            "m2m100-yua-es"
        ),
    )
    parser.add_argument(
        "--epochs",
        type=float,
        default=3.0,
    )
    parser.add_argument(
        "--learning-rate",
        type=float,
        default=2e-5,
    )
    parser.add_argument(
        "--train-batch-size",
        type=int,
        default=2,
    )
    parser.add_argument(
        "--eval-batch-size",
        type=int,
        default=4,
    )
    parser.add_argument(
        "--gradient-accumulation-steps",
        type=int,
        default=8,
    )
    parser.add_argument(
        "--max-train-samples",
        type=int,
        default=None,
    )
    parser.add_argument(
        "--max-eval-samples",
        type=int,
        default=None,
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=42,
    )
    parser.add_argument(
        "--allow-cpu",
        action="store_true",
    )
    parser.add_argument(
        "--no-gradient-checkpointing",
        dest="gradient_checkpointing",
        action="store_false",
    )
    parser.set_defaults(
        gradient_checkpointing=True
    )

    main(parser.parse_args())
