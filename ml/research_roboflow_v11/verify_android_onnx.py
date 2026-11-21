#!/usr/bin/env python3
"""Fail fast if the trained v11 segmentation ONNX is incompatible with Android.

Static inspection only; model's real-photo accuracy is NOT tested by this.
"""
import argparse
import hashlib
import json
from pathlib import Path


def shape(value_info):
    return [
        dim.dim_value if dim.HasField("dim_value") else
        (dim.dim_param if dim.HasField("dim_param") else None)
        for dim in value_info.type.tensor_type.shape.dim
    ]


def verify(path: Path):
    if not path.is_file():
        raise FileNotFoundError(path)
    try:
        import onnx
    except ImportError as exc:
        raise RuntimeError("Install ONNX: python -m pip install onnx") from exc
    model = onnx.load_model(str(path), load_external_data=False)
    onnx.checker.check_model(model)
    inputs = {x.name: shape(x) for x in model.graph.input}
    outputs = {x.name: shape(x) for x in model.graph.output}
    expected = {
        "input": [1, 3, 512, 512],
        "output0": [1, 37, 5376],
        "output1": [1, 32, 128, 128],
    }
    # Names of model input might be 'images' or 'input', so only insist on one.
    if len(inputs) != 1 or list(inputs.values())[0] != expected["input"]:
        raise ValueError(f"Expected fixed RGB 512 input, got {inputs}")
    for name in ("output0", "output1"):
        if outputs.get(name) != expected[name]:
            raise ValueError(f"Expected {name}={expected[name]}, got {outputs}")
    info = {
        "file": str(path.resolve()),
        "bytes": path.stat().st_size,
        "sha256": hashlib.file_digest(path.open("rb"), "sha256").hexdigest(),
        "input": inputs,
        "outputs": outputs,
        "compatible_android_raw_yolo11n_seg": True,
        "limitation": "Shape compatibility only; independently test Android inference on pictures."
    }
    print(json.dumps(info, indent=2))
    return info


if __name__ == "__main__":
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--onnx", type=Path, required=True)
    args = p.parse_args()
    try:
        verify(args.onnx)
    except (ValueError, FileNotFoundError, RuntimeError) as exc:
        p.exit(1, f"NOT COMPATIBLE: {exc}\n")
