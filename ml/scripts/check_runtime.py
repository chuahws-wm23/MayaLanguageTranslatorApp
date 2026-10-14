#!/usr/bin/env python3
"""Print the PyTorch accelerator visible to the current runtime."""

import torch


print("PyTorch version:", torch.__version__)
print("CUDA available:", torch.cuda.is_available())

if torch.cuda.is_available():
    print("CUDA version:", torch.version.cuda)
    print("GPU:", torch.cuda.get_device_name(0))
    print(
        "GPU count:",
        torch.cuda.device_count(),
    )
else:
    print(
        "No CUDA GPU is visible to PyTorch. "
        "In Colab choose Runtime → Change runtime type → T4 GPU "
        "and reconnect the runtime."
    )
