# Local LoRA Training

This workflow is intended for a Windows laptop with a low-VRAM NVIDIA GPU.

## Why LoRA

Full M2M100-418M fine-tuning stores gradients and optimizer states for the entire model. On a 6 GB RTX 4050 Laptop GPU this is too tight.

LoRA freezes the base model and trains only small attention adapters.

Current LoRA targets:

- q_proj
- v_proj

Default adapter settings:

- rank: 8
- alpha: 16
- dropout: 0.05
- batch size: 1
- gradient accumulation: 16
- gradient checkpointing: enabled

## Install

Use the existing ML environment and add PEFT:

```powershell
python -m pip install -r ml\requirements-lora.txt
```

## Rebuild the two human datasets

```powershell
python ml\scripts\download_gatitos.py
python ml\scripts\build_gatitos_human_corpus.py
python ml\scripts\download_yua_es_ccc.py
python ml\scripts\build_yua_es_corpus.py
```

## First local smoke test

```powershell
python ml\train_joint_lora.py ^
  --max-english-train-samples 100 ^
  --max-spanish-train-samples 200 ^
  --max-english-eval-samples 30 ^
  --max-spanish-eval-samples 50 ^
  --english-repeat 1 ^
  --validation-english-repeat 1 ^
  --epochs 1 ^
  --output-dir ml\artifacts\m2m100-yua-joint-lora-smoke
```

PowerShell also accepts the command on one line.

## Monitor temperature

Open a second PowerShell window:

```powershell
nvidia-smi -l 2
```

For the first run, stop and investigate if temperature climbs abnormally or the system becomes unstable.

## Evaluate

```powershell
python ml\evaluate_joint_lora.py --adapter-dir ml\artifacts\m2m100-yua-joint-lora-smoke\best_adapter
```

## Manual check

```powershell
python ml\manual_joint_lora_check.py --adapter-dir ml\artifacts\m2m100-yua-joint-lora-smoke\best_adapter
```

The LoRA adapter should be much smaller than a complete M2M100 checkpoint.
