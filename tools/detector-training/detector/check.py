"""Check an exported model with OpenCV's dnn importer (the same one the engine uses).

    uv run python -m detector.check <model.onnx> [checkpoint.pt]

Prints load success, output shapes, CPU time per pass and, with a checkpoint, the largest
difference between OpenCV and PyTorch outputs on random inputs (must be < 1e-3).
"""

from __future__ import annotations

import hashlib
import sys
import time
from pathlib import Path

import cv2
import numpy as np

from detector.model import INPUT_SIZE


def run_cv(net: cv2.dnn.Net, image: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    blob = image[None].astype(np.float32)  # 1x3xHxW
    net.setInput(blob, "image")
    mask, presence = net.forward(["mask", "presence"])
    return mask, presence


def main(argv: list[str]) -> None:
    path = Path(argv[0])
    net = cv2.dnn.readNetFromONNX(str(path))
    cv2.setNumThreads(4)
    rng = np.random.default_rng(0)
    image = rng.random((3, INPUT_SIZE, INPUT_SIZE), dtype=np.float32)
    mask, presence = run_cv(net, image)
    times = []
    for _ in range(20):
        start = time.perf_counter()
        run_cv(net, image)
        times.append((time.perf_counter() - start) * 1000)
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    print(f"mask={mask.shape} presence={presence.shape} median_ms={np.median(times):.1f} sha256={digest}")
    if len(argv) > 1:
        import torch

        from detector.model import DocumentNet

        model = DocumentNet()
        state = torch.load(argv[1], map_location="cpu")
        model.load_state_dict(state["model"] if "model" in state else state)
        model.eval()
        worst = 0.0
        for seed in range(5):
            x = np.random.default_rng(seed).random((3, INPUT_SIZE, INPUT_SIZE), dtype=np.float32)
            cv_mask, cv_presence = run_cv(net, x)
            with torch.no_grad():
                t_mask, t_presence = model(torch.from_numpy(x[None]))
            worst = max(worst, float(np.abs(cv_mask - t_mask.numpy()).max()))
            worst = max(worst, float(np.abs(cv_presence - t_presence.numpy()).max()))
        print(f"parity_max_abs_diff={worst:.2e}")
        if worst >= 1e-3:
            raise SystemExit("parity check failed")


if __name__ == "__main__":
    main(sys.argv[1:])
