"""Export a trained DocumentNet to ONNX for the scan engine (OpenCV dnn).

    uv run python -m detector.export <checkpoint.pt | --untrained> <out.onnx>
"""

from __future__ import annotations

import hashlib
import sys
from pathlib import Path

import torch

from detector.model import INPUT_SIZE, DocumentNet, parameter_count


def export(model: DocumentNet, out: Path) -> str:
    model.eval()
    dummy = torch.zeros(1, 3, INPUT_SIZE, INPUT_SIZE)
    out.parent.mkdir(parents=True, exist_ok=True)
    torch.onnx.export(
        model,
        (dummy,),
        str(out),
        input_names=["image"],
        output_names=["mask", "presence"],
        opset_version=13,
        dynamo=False,
        do_constant_folding=True,
    )
    return hashlib.sha256(out.read_bytes()).hexdigest()


def main(argv: list[str]) -> None:
    source, target = argv[0], Path(argv[1])
    model = DocumentNet()
    if source != "--untrained":
        state = torch.load(source, map_location="cpu")
        model.load_state_dict(state["model"] if "model" in state else state)
    digest = export(model, target)
    print(f"params={parameter_count(model)} bytes={target.stat().st_size} sha256={digest}")


if __name__ == "__main__":
    main(sys.argv[1:])
