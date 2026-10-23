#!/usr/bin/env python3
"""Click the four page corners (TL, TR, BR, BL, as the page reads upright) for reference photos.

annotate.py --id receipt-ar-001      one photo
annotate.py --all-missing            every photo without expectedCorners (skips no-page entries)

Keys: u = undo last click, r = reset, s = skip this photo, q = quit.
"""
import argparse
import json
from pathlib import Path

import matplotlib
import matplotlib.pyplot as plt
from PIL import Image

ROOT = Path(__file__).resolve().parents[2] / "reference-set"
MANIFEST = ROOT / "manifest.json"
LABELS = ["TL", "TR", "BR", "BL"]


def annotate(entry):
    image = Image.open(ROOT / entry["file"])
    clicks = []
    skipped = {"value": False}
    fig, ax = plt.subplots(figsize=(10, 8))
    ax.imshow(image)
    ax.set_title(f"{entry['id']}: click {LABELS[0]} (u undo, r reset, s skip, q quit)")
    markers = []

    def redraw():
        for m in markers:
            m.remove()
        markers.clear()
        for (x, y), label in zip(clicks, LABELS):
            markers.append(ax.plot(x, y, "r+", markersize=14)[0])
            markers.append(ax.annotate(label, (x, y), color="red", fontsize=12))
        if len(clicks) == 4:
            xs = [c[0] for c in clicks] + [clicks[0][0]]
            ys = [c[1] for c in clicks] + [clicks[0][1]]
            markers.append(ax.plot(xs, ys, "r-")[0])
            ax.set_title(f"{entry['id']}: close the window to save")
        else:
            ax.set_title(f"{entry['id']}: click {LABELS[len(clicks)]} (u undo, r reset, s skip, q quit)")
        fig.canvas.draw_idle()

    def on_click(event):
        if event.inaxes != ax or len(clicks) >= 4 or event.xdata is None:
            return
        clicks.append((round(float(event.xdata), 1), round(float(event.ydata), 1)))
        redraw()

    def on_key(event):
        if event.key == "u" and clicks:
            clicks.pop()
        elif event.key == "r":
            clicks.clear()
        elif event.key == "s":
            skipped["value"] = True
            plt.close(fig)
            return
        elif event.key == "q":
            raise SystemExit(0)
        redraw()

    fig.canvas.mpl_connect("button_press_event", on_click)
    fig.canvas.mpl_connect("key_press_event", on_key)
    plt.show()
    if skipped["value"] or len(clicks) != 4:
        return False
    entry["expectedCorners"] = [[x, y] for x, y in clicks]
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--id")
    parser.add_argument("--all-missing", action="store_true")
    args = parser.parse_args()
    matplotlib.use("MacOSX" if matplotlib.get_backend().lower().startswith("agg") else matplotlib.get_backend())
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    targets = [
        p for p in manifest["photos"]
        if (args.id and p["id"] == args.id)
        or (args.all_missing and p.get("expectedCorners") is None and "no-page" not in p["tags"])
    ]
    if not targets:
        print("nothing to annotate")
        return
    for entry in targets:
        if annotate(entry):
            MANIFEST.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
            print(f"saved corners for {entry['id']}")


if __name__ == "__main__":
    main()
