#!/usr/bin/env python3
"""Fine-tune a direct bidirectional Yucatec Maya ↔ English NMT model."""

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


def prompt(
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


def read_pairs(
    path: str,
    max_pairs: int | None = None,
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
        english = row["english"].strip()
        yucatec = row["yucatec"].strip()

        examples.append(
            {
                "input_text": prompt(
                    "en_to_yua",
                    english,
                ),
                "target_text": yucatec,
                "direction": "en_to_yua",
                "quality": row.get(
                    "quality",
                    "",
                ),
            }
        )

        examples.append(
            {
                "input_text": prompt(
                    "yua_to_en",
                    yucatec,
                ),
                "target_text": english,
                "direction": "yua_to_en",
                "quality": row.get(
                    "quality",
                    "",
                ),
            }
        )

    return examples


def print_runtime_info(
    allow_cpu: bool,
) -> None:
    cuda_available = torch.cuda.is_available()

    print(
        "PyTorch CUDA available:",
        cuda_available,
    )

    if cuda_available:
        print(
            "GPU:",
            torch.cuda.get_device_name(0),
        )
        return

    print("Training device: CPU")

    if not allow_cpu:
        raise SystemExit(
            "No CUDA GPU is available to PyTorch. "
            "M2M100-418M training on CPU is impractically slow. "
            "In Google Colab choose Runtime → Change runtime type → "
            "T4 GPU, reconnect the runtime, then run the notebook again. "
            "Use --allow-cpu only for debugging."
        )


def main(args) -> None:
    print_runtime_info(args.allow_cpu)
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
        read_pairs(
            args.train_file,
            args.max_train_samples,
        )
    )

    validation_dataset = Dataset.from_list(
        read_pairs(
            args.validation_file,
            args.max_eval_samples,
        )
    )

    def preprocess(batch):
        model_inputs = tokenizer(
            batch["input_text"],
            max_length=MAX_LENGTH,
            truncation=True,
        )

        # Important for M2M100 in this experiment:
        # do not use tokenizer(text_target=...). The stock M2M100 tokenizer
        # expects one of its built-in language codes when it switches to
        # target mode, but Yucatec Maya is not one of those built-in codes.
        #
        # We train M2M100 as a prompt-conditioned seq2seq model, following the
        # prompt-based treatment used in the AmericasNLP Yucatec Maya work.
        # Tokenizing target strings normally avoids an invalid tgt_lang lookup
        # while still producing correct label token IDs.
        labels = tokenizer(
            batch["target_text"],
            max_length=MAX_LENGTH,
            truncation=True,
        )

        model_inputs["labels"] = labels[
            "input_ids"
        ]
        return model_inputs

    remove_columns = train_dataset.column_names

    train_tokenized = train_dataset.map(
        preprocess,
        batched=True,
        remove_columns=remove_columns,
        desc="Tokenizing train",
    )

    validation_tokenized = (
        validation_dataset.map(
            preprocess,
            batched=True,
            remove_columns=remove_columns,
            desc="Tokenizing validation",
        )
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

        decoded_predictions = tokenizer.batch_decode(
            predictions,
            skip_special_tokens=True,
        )
        decoded_labels = tokenizer.batch_decode(
            labels,
            skip_special_tokens=True,
        )

        return {
            "chrf": chrf.corpus_score(
                decoded_predictions,
                [decoded_labels],
            ).score,
            "bleu": bleu.corpus_score(
                decoded_predictions,
                [decoded_labels],
            ).score,
        }

    training_args = Seq2SeqTrainingArguments(
        output_dir=args.output_dir,
        evaluation_strategy="epoch",
        save_strategy="epoch",
        learning_rate=args.learning_rate,
        per_device_train_batch_size=(
            args.train_batch_size
        ),
        per_device_eval_batch_size=(
            args.eval_batch_size
        ),
        gradient_accumulation_steps=(
            args.gradient_accumulation_steps
        ),
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

    callbacks = [
        EarlyStoppingCallback(
            early_stopping_patience=2
        )
    ]

    trainer = Seq2SeqTrainer(
        model=model,
        args=training_args,
        train_dataset=train_tokenized,
        eval_dataset=validation_tokenized,
        tokenizer=tokenizer,
        data_collator=collator,
        compute_metrics=compute_metrics,
        callbacks=callbacks,
    )

    trainer.train()

    final_dir = Path(args.output_dir) / "best_model"
    trainer.save_model(final_dir)
    tokenizer.save_pretrained(final_dir)

    print(f"Saved best model to {final_dir}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()

    parser.add_argument(
        "--train-file",
        default="ml/data/processed/train.csv",
    )
    parser.add_argument(
        "--validation-file",
        default="ml/data/processed/validation.csv",
    )
    parser.add_argument(
        "--model-name",
        default=DEFAULT_MODEL,
    )
    parser.add_argument(
        "--output-dir",
        default="ml/artifacts/m2m100-yua-en",
    )
    parser.add_argument(
        "--epochs",
        type=float,
        default=5.0,
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
        help=(
            "Allow CPU-only training for debugging. "
            "Not recommended for the 418M model."
        ),
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
