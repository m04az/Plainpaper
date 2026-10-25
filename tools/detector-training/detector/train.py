"""Train DocumentNet on generated scenes (specs/004 research R4).

    uv run python -m detector.train --name v1 --epochs 30 [--steps 1000 --batch 32 --workers 8]

Writes runs/<name>/{best.pt,last.pt,log.csv} and, at the end, detector.ppdm + vectors.vec
(the engine's weights and parity vectors) from the best checkpoint.
"""

from __future__ import annotations

import argparse
import csv
import math
import time
from pathlib import Path

import numpy as np
import torch
from torch import nn
from torch.nn import functional as F
from torch.utils.data import DataLoader, IterableDataset, get_worker_info

from detector.generate import sample_for
from detector.model import DocumentNet, parameter_count
from detector.ppdm import write, write_vectors

VALIDATION_SEED = 999_001


class Scenes(IterableDataset):
    """Endless stream of generated scenes; each worker draws its own index range."""

    def __init__(self, seed: int) -> None:
        self.seed = seed

    def __iter__(self):
        info = get_worker_info()
        worker = info.id if info else 0
        workers = info.num_workers if info else 1
        index = worker
        while True:
            s = sample_for(self.seed, index)
            index += workers
            yield to_tensors(s)


def to_tensors(s):
    image = torch.from_numpy(s.image).permute(2, 0, 1).float() / 255.0
    mask = torch.from_numpy(s.mask)[None]
    presence = torch.tensor([s.presence], dtype=torch.float32)
    return image, mask, presence


def validation_set(count: int) -> tuple[torch.Tensor, torch.Tensor, torch.Tensor]:
    items = [to_tensors(sample_for(VALIDATION_SEED, i)) for i in range(count)]
    return tuple(torch.stack(t) for t in zip(*items))  # type: ignore[return-value]


def loss_fn(mask_logits, presence_logits, mask, presence):
    bce = F.binary_cross_entropy_with_logits(mask_logits, mask)
    prob = torch.sigmoid(mask_logits)
    inter = (prob * mask).sum((1, 2, 3))
    union = prob.sum((1, 2, 3)) + mask.sum((1, 2, 3))
    positive = presence[:, 0] > 0.5
    dice = 1 - (2 * inter + 1) / (union + 1)
    dice = dice[positive].mean() if positive.any() else torch.zeros((), device=mask.device)
    pres = F.binary_cross_entropy_with_logits(presence_logits, presence)
    return bce + dice + 0.5 * pres, (bce.item(), dice.item(), pres.item())


@torch.no_grad()
def evaluate(model, val, device, batch=64):
    model.eval()
    images, masks, presences = val
    ious, correct = [], 0
    for i in range(0, len(images), batch):
        x = images[i : i + batch].to(device)
        m, p = model(x)
        pred = (torch.sigmoid(m) > 0.5).float().cpu()
        target = masks[i : i + batch]
        for k in range(len(x)):
            if presences[i + k, 0] > 0.5:
                inter = (pred[k] * target[k]).sum().item()
                union = ((pred[k] + target[k]) > 0).sum().item()
                ious.append(inter / max(union, 1))
        correct += ((torch.sigmoid(p).cpu() > 0.5) == (presences[i : i + batch] > 0.5)).sum().item()
    model.train()
    return float(np.mean(ious)), correct / len(images)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--name", default="v1")
    ap.add_argument("--epochs", type=int, default=30)
    ap.add_argument("--steps", type=int, default=1000)
    ap.add_argument("--batch", type=int, default=32)
    ap.add_argument("--workers", type=int, default=8)
    ap.add_argument("--lr", type=float, default=2e-3)
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--val", type=int, default=512)
    ap.add_argument("--resume", type=Path)
    args = ap.parse_args()

    torch.manual_seed(args.seed)
    device = torch.device("mps" if torch.backends.mps.is_available() else "cpu")
    out = Path("runs") / args.name
    out.mkdir(parents=True, exist_ok=True)
    model = DocumentNet().to(device)
    if args.resume:
        model.load_state_dict(torch.load(args.resume, map_location=device)["model"])
    print(f"params={parameter_count(model)} device={device}", flush=True)
    opt = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=1e-4)
    total = args.epochs * args.steps
    sched = torch.optim.lr_scheduler.LambdaLR(opt, lambda step: 0.5 * (1 + math.cos(math.pi * min(step, total) / total)))
    loader = DataLoader(Scenes(args.seed), batch_size=args.batch, num_workers=args.workers, persistent_workers=args.workers > 0, prefetch_factor=4 if args.workers else None)
    val = validation_set(args.val)
    best = -1.0
    log = (out / "log.csv").open("a", newline="")
    writer = csv.writer(log)
    batches = iter(loader)
    for epoch in range(args.epochs):
        start = time.time()
        totals = np.zeros(3)
        for _ in range(args.steps):
            x, m, p = (t.to(device) for t in next(batches))
            mask_logits, presence_logits = model(x)
            loss, parts = loss_fn(mask_logits, presence_logits, m, p)
            opt.zero_grad(set_to_none=True)
            loss.backward()
            opt.step()
            sched.step()
            totals += parts
        iou, acc = evaluate(model, val, device)
        totals /= args.steps
        print(f"epoch {epoch + 1}/{args.epochs} bce={totals[0]:.4f} dice={totals[1]:.4f} pres={totals[2]:.4f} val_iou={iou:.4f} val_presence_acc={acc:.4f} {time.time() - start:.0f}s", flush=True)
        writer.writerow([epoch + 1, *totals, iou, acc])
        log.flush()
        state = {"model": model.state_dict(), "epoch": epoch + 1, "val_iou": iou, "val_presence_acc": acc}
        torch.save(state, out / "last.pt")
        if iou > best:
            best = iou
            torch.save(state, out / "best.pt")
    model.load_state_dict(torch.load(out / "best.pt", map_location=device)["model"])
    model = model.cpu().eval()
    digest = write(model, out / "detector.ppdm")
    write_vectors(model, out / "vectors.vec")
    print(f"best val_iou={best:.4f} ppdm sha256={digest}")


if __name__ == "__main__":
    main()
