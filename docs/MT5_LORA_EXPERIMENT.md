# mT5-small + LoRA experiment

This experiment replaces the M2M100 LoRA path for the direct app translator.

## Why

The M2M100 LoRA smoke test trained successfully on the local RTX 4050, but its outputs frequently switched to French, Vietnamese, or copied the prompt. Yucatec Maya is not one of M2M100's built-in language codes.

mT5 is a multilingual text-to-text model, so Yucatec Maya can be learned directly as target text.

## Tasks

The same adapter learns:

- English → Yucatec Maya
- Yucatec Maya → English
- Spanish → Yucatec Maya
- Yucatec Maya → Spanish

Spanish is auxiliary training supervision only. It is not used as a runtime pivot.

## Smoke test

Run:

```powershell
python ml\train_joint_mt5_lora.py --max-english-train-samples 100 --max-spanish-train-samples 200 --max-english-eval-samples 30 --max-spanish-eval-samples 50 --english-repeat 1 --validation-english-repeat 1 --epochs 1 --output-dir ml\artifacts\mt5-yua-joint-lora-smoke
```

Evaluate:

```powershell
python ml\evaluate_joint_mt5_lora.py --adapter-dir ml\artifacts\mt5-yua-joint-lora-smoke\best_adapter
```

Manual check:

```powershell
python ml\manual_joint_mt5_lora_check.py --adapter-dir ml\artifacts\mt5-yua-joint-lora-smoke\best_adapter
```

The smoke test is for architecture/GPU validation only. Translation quality should be assessed after a larger training run.
