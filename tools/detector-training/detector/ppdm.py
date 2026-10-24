"""Plainpaper detector model format (.ppdm) — the weights the scan engine loads.

Layout (little endian):
    magic  b"PPDM"
    u32    format version (1)
    u32    architecture version (ARCH_VERSION, must match the engine's DocumentNet)
    u32    tensor count
    per tensor: u16 name length, name (ASCII), u8 ndim, u32 dims[ndim], float32 data
Every convolution is stored with its BatchNorm folded in: ``<name>.w`` and ``<name>.b``.
The engine builds the same network as ``detector.model.DocumentNet`` and reads tensors by name.

    uv run python -m detector.ppdm <checkpoint.pt | --untrained> <out.ppdm> [--vectors <out.vec>]
"""

from __future__ import annotations

import hashlib
import struct
import sys
from pathlib import Path

import numpy as np
import torch
from torch import nn

from detector.model import INPUT_SIZE, DocumentNet, InvertedResidual, Up

FORMAT_VERSION = 1
ARCH_VERSION = 1


def fold(seq: nn.Sequential) -> tuple[np.ndarray, np.ndarray]:
    """Conv2d (no bias) + BatchNorm2d → folded weight and bias."""
    conv, bn = seq[0], seq[1]
    assert isinstance(conv, nn.Conv2d) and isinstance(bn, nn.BatchNorm2d)
    scale = bn.weight / torch.sqrt(bn.running_var + bn.eps)
    w = conv.weight * scale[:, None, None, None]
    b = bn.bias - bn.running_mean * scale
    return w.detach().numpy().astype(np.float32), b.detach().numpy().astype(np.float32)


def tensors(model: DocumentNet) -> list[tuple[str, np.ndarray]]:
    out: list[tuple[str, np.ndarray]] = []

    def conv(name: str, seq: nn.Sequential) -> None:
        w, b = fold(seq)
        out.append((f"{name}.w", w))
        out.append((f"{name}.b", b))

    conv("stem", model.stem)
    for s_index, stage in enumerate([model.s1, model.s2, model.s3, model.s4, model.s5, model.s6], start=1):
        for b_index, block in enumerate(stage):
            assert isinstance(block, InvertedResidual)
            layers = list(block.block)
            prefix = f"s{s_index}.{b_index}"
            if len(layers) == 3:
                conv(f"{prefix}.expand", layers[0])
            conv(f"{prefix}.dw", layers[-2])
            conv(f"{prefix}.project", layers[-1])
    for name in ["up16", "up32", "up64", "up128"]:
        up: Up = getattr(model, name)
        conv(f"{name}.fuse", up.fuse)
        conv(f"{name}.dw", up.refine[0])
        conv(f"{name}.pw", up.refine[1])
    out.append(("mask.w", model.mask_head.weight.detach().numpy().astype(np.float32)))
    out.append(("mask.b", model.mask_head.bias.detach().numpy().astype(np.float32)))
    out.append(("presence.w", model.presence_head.weight.detach().numpy().astype(np.float32)))
    out.append(("presence.b", model.presence_head.bias.detach().numpy().astype(np.float32)))
    return out


def write(model: DocumentNet, path: Path) -> str:
    model.eval()
    blobs = [b"PPDM", struct.pack("<III", FORMAT_VERSION, ARCH_VERSION, 0)]
    count = 0
    for name, array in tensors(model):
        encoded = name.encode("ascii")
        blobs.append(struct.pack("<H", len(encoded)) + encoded)
        blobs.append(struct.pack("<B", array.ndim) + struct.pack(f"<{array.ndim}I", *array.shape))
        blobs.append(np.ascontiguousarray(array, dtype="<f4").tobytes())
        count += 1
    blobs[1] = struct.pack("<III", FORMAT_VERSION, ARCH_VERSION, count)
    data = b"".join(blobs)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    return hashlib.sha256(data).hexdigest()


def write_vectors(model: DocumentNet, path: Path, count: int = 3) -> None:
    """Test vectors for the engine's parity test: u32 count, then per case the input
    (3x256x256 float RGB 0..1), the mask probabilities (128x128) and the presence probability."""
    model.eval()
    parts = [struct.pack("<I", count)]
    for seed in range(count):
        x = np.random.default_rng(seed).random((1, 3, INPUT_SIZE, INPUT_SIZE), dtype=np.float32)
        with torch.no_grad():
            mask, presence = model(torch.from_numpy(x))
        parts.append(x.astype("<f4").tobytes())
        parts.append(torch.sigmoid(mask).numpy().astype("<f4").tobytes())
        parts.append(torch.sigmoid(presence).numpy().astype("<f4").tobytes())
    path.write_bytes(b"".join(parts))


def load_model(source: str) -> DocumentNet:
    model = DocumentNet()
    if source != "--untrained":
        state = torch.load(source, map_location="cpu")
        model.load_state_dict(state["model"] if "model" in state else state)
    else:
        torch.manual_seed(0)
        model = DocumentNet()
        # Non-trivial BatchNorm statistics so the folding is exercised by the parity test.
        for module in model.modules():
            if isinstance(module, nn.BatchNorm2d):
                module.running_mean.uniform_(-0.2, 0.2)
                module.running_var.uniform_(0.5, 1.5)
                module.weight.data.uniform_(0.5, 1.5)
                module.bias.data.uniform_(-0.1, 0.1)
    return model.eval()


def main(argv: list[str]) -> None:
    model = load_model(argv[0])
    target = Path(argv[1])
    digest = write(model, target)
    print(f"bytes={target.stat().st_size} sha256={digest}")
    if "--vectors" in argv:
        write_vectors(model, Path(argv[argv.index("--vectors") + 1]))


if __name__ == "__main__":
    main(sys.argv[1:])
