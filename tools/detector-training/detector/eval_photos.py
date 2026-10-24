"""Run a checkpoint on real photos (kept locally) and write an overlay grid.

    uv run python -m detector.eval_photos <checkpoint.pt> <photo dir> <out.jpg>
"""

from __future__ import annotations

import sys
from pathlib import Path

import cv2
import numpy as np
import torch

from detector.model import INPUT_SIZE, DocumentNet


def predict(model, bgr):
    rgb = cv2.cvtColor(cv2.resize(bgr, (INPUT_SIZE, INPUT_SIZE), interpolation=cv2.INTER_AREA), cv2.COLOR_BGR2RGB)
    x = torch.from_numpy(rgb).permute(2, 0, 1).float()[None] / 255.0
    with torch.no_grad():
        m, p = model(x)
    return torch.sigmoid(m)[0, 0].numpy(), float(torch.sigmoid(p)[0, 0])


def main(argv):
    model = DocumentNet()
    state = torch.load(argv[0], map_location="cpu")
    model.load_state_dict(state["model"] if "model" in state else state)
    model.eval()
    tiles = []
    for path in sorted(Path(argv[1]).glob("*.jpg")):
        bgr = cv2.imread(str(path))
        if bgr is None:
            continue
        h, w = bgr.shape[:2]
        scale = 480 / max(h, w)
        small = cv2.resize(bgr, (int(w * scale), int(h * scale)), interpolation=cv2.INTER_AREA)
        prob, presence = predict(model, small)
        mask = cv2.resize(prob, (small.shape[1], small.shape[0])) > 0.5
        overlay = small.copy()
        overlay[mask] = (0.5 * overlay[mask] + 0.5 * np.array([255, 120, 0])).astype(np.uint8)
        contours, _ = cv2.findContours(mask.astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        if contours:
            c = max(contours, key=cv2.contourArea)
            approx = cv2.approxPolyDP(cv2.convexHull(c), 0.02 * cv2.arcLength(c, True), True)
            cv2.polylines(overlay, [approx], True, (0, 255, 0), 3)
        cv2.putText(overlay, f"{path.stem[:18]} p={presence:.2f}", (8, 24), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 0, 255), 2)
        canvas = np.zeros((480, 480, 3), np.uint8)
        canvas[: overlay.shape[0], : overlay.shape[1]] = overlay
        tiles.append(canvas)
    while len(tiles) % 4:
        tiles.append(np.zeros((480, 480, 3), np.uint8))
    rows = [np.concatenate(tiles[i : i + 4], axis=1) for i in range(0, len(tiles), 4)]
    cv2.imwrite(argv[2], np.concatenate(rows, axis=0))


if __name__ == "__main__":
    main(sys.argv[1:])
