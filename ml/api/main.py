"""Minimal local inference API for the trained FYP model."""

import os
from typing import Literal

import torch
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from transformers import (
    AutoModelForSeq2SeqLM,
    AutoTokenizer,
)


MODEL_DIR = os.environ.get(
    "MAYA_MODEL_DIR",
    "ml/artifacts/m2m100-yua-en/best_model",
)

app = FastAPI(
    title="Yucatec Maya-English NMT API",
    version="0.1.0",
)

tokenizer = None
model = None
device = torch.device(
    "cuda" if torch.cuda.is_available() else "cpu"
)


class TranslationRequest(BaseModel):
    text: str = Field(min_length=1, max_length=1000)
    direction: Literal[
        "en_to_yua",
        "yua_to_en",
    ]


class TranslationResponse(BaseModel):
    translation: str
    direction: str
    model: str


def build_prompt(
    direction: str,
    text: str,
) -> str:
    if direction == "en_to_yua":
        return (
            "translate English to Yucatec Maya: "
            + text.strip()
        )

    return (
        "translate Yucatec Maya to English: "
        + text.strip()
    )


@app.on_event("startup")
def load_model() -> None:
    global tokenizer, model

    if not os.path.isdir(MODEL_DIR):
        return

    tokenizer = AutoTokenizer.from_pretrained(
        MODEL_DIR
    )
    model = AutoModelForSeq2SeqLM.from_pretrained(
        MODEL_DIR
    )
    model.to(device)
    model.eval()


@app.get("/health")
def health() -> dict:
    return {
        "ready": model is not None,
        "model_dir": MODEL_DIR,
        "device": str(device),
    }


@app.post(
    "/translate",
    response_model=TranslationResponse,
)
def translate(
    request: TranslationRequest,
) -> TranslationResponse:
    if tokenizer is None or model is None:
        raise HTTPException(
            status_code=503,
            detail=(
                "Model is not loaded. Train the model first "
                "or set MAYA_MODEL_DIR."
            ),
        )

    prompt = build_prompt(
        request.direction,
        request.text,
    )

    encoded = tokenizer(
        prompt,
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

    output = tokenizer.decode(
        output_ids[0],
        skip_special_tokens=True,
    ).strip()

    return TranslationResponse(
        translation=output,
        direction=request.direction,
        model=MODEL_DIR,
    )
