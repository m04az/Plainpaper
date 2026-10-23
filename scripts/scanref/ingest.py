#!/usr/bin/env python3
"""Imports a phone photo (or a competitor export) into reference-set/ (spec 002, research R13).

Photo:      ingest.py IMG.jpg --id receipt-ar-001 --tags receipt,arabic --language ar [--rotate 90]
Competitor: ingest.py export.jpg --id receipt-ar-001 --competitor adobe-scan

Applies the EXIF rotation, strips every piece of metadata, caps the size, writes JPEG q85 and
updates manifest.json (a stub entry with expectedCorners = null for photos; annotate.py fills it).
"""
import argparse
import json
import math
import sys
from pathlib import Path

from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[2] / "reference-set"
MANIFEST = ROOT / "manifest.json"
MAX_PIXELS = 12_000_000
COMPETITOR_LONG_EDGE = 1754  # A4 long side at 150 DPI
COMPETITORS = {"adobe-scan", "microsoft-lens", "camscanner"}
LANGUAGES = {"ar", "en", "fr", "mixed", "none"}


def load_manifest():
    if MANIFEST.exists():
        return json.loads(MANIFEST.read_text(encoding="utf-8"))
    return {"version": 1, "photos": []}


def save_manifest(manifest):
    MANIFEST.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def normalise(image, max_pixels, rotate):
    image = ImageOps.exif_transpose(image)  # bake EXIF orientation into pixels
    if rotate:
        image = image.rotate(rotate, expand=True)  # counter-clockwise, so the engine must rotate clockwise by `rotate`
    pixels = image.width * image.height
    if pixels > max_pixels:
        scale = math.sqrt(max_pixels / pixels)
        image = image.resize((int(image.width * scale), int(image.height * scale)), Image.LANCZOS)
    return image.convert("RGB")  # drops alpha and all metadata


def write_jpeg(image, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, "JPEG", quality=85, optimize=True)  # no exif= argument: metadata stripped


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("source")
    parser.add_argument("--id", required=True, help="kebab-case id, e.g. receipt-ar-001")
    parser.add_argument("--tags", help="comma-separated categories")
    parser.add_argument("--language", choices=sorted(LANGUAGES))
    parser.add_argument("--rotate", type=int, default=0, choices=[0, 90, 180, 270],
                        help="turn the photo so the engine must apply this rotation to make it upright")
    parser.add_argument("--competitor", choices=sorted(COMPETITORS))
    parser.add_argument("--device")
    parser.add_argument("--notes")
    args = parser.parse_args()

    if not args.id.replace("-", "").isalnum() or args.id != args.id.lower():
        sys.exit("id must be lowercase kebab-case")
    manifest = load_manifest()
    entry = next((p for p in manifest["photos"] if p["id"] == args.id), None)

    with Image.open(args.source) as source:
        if args.competitor:
            if entry is None:
                sys.exit(f"ingest the photo {args.id} first")
            image = normalise(source, max_pixels=COMPETITOR_LONG_EDGE * COMPETITOR_LONG_EDGE, rotate=0)
            long_edge = max(image.width, image.height)
            if long_edge > COMPETITOR_LONG_EDGE:
                scale = COMPETITOR_LONG_EDGE / long_edge
                image = image.resize((int(image.width * scale), int(image.height * scale)), Image.LANCZOS)
            rel = f"competitors/{args.competitor}/{args.id}.jpg"
            write_jpeg(image, ROOT / rel)
            entry.setdefault("competitors", {})[args.competitor] = rel
            save_manifest(manifest)
            print(f"competitor output stored at {rel}")
            return

        if not args.tags or not args.language:
            sys.exit("--tags and --language are required for photos")
        image = normalise(source, MAX_PIXELS, args.rotate)
        rel = f"photos/{args.id}.jpg"
        write_jpeg(image, ROOT / rel)
        tags = sorted({t.strip() for t in args.tags.split(",") if t.strip()})
        if args.rotate and "rotated" not in tags:
            tags.append("rotated")
        new_entry = {
            "id": args.id,
            "file": rel,
            "tags": tags,
            "language": args.language,
            "expectedCorners": None if "no-page" in tags else (entry or {}).get("expectedCorners"),
            "expectedRotation": args.rotate,
        }
        if args.device:
            new_entry["device"] = args.device
        if args.notes:
            new_entry["notes"] = args.notes
        if entry:
            entry.update(new_entry)
        else:
            manifest["photos"].append(new_entry)
        save_manifest(manifest)
        print(f"{rel}: {image.width}x{image.height}, tags={tags}; now run annotate.py --all-missing")


if __name__ == "__main__":
    main()
