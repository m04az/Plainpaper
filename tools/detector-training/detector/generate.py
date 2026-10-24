"""Synthetic training scenes for the document detector (specs/004 research R3).

Each sample is a phone-like photo: a procedural background (wood, tiles, fabric, paper-coloured
desk, clutter, or a local background photo), one document (text page, drawing page, ID card,
receipt, book spread, dark cover) placed with perspective, then shadows, glare, occluders (hands
over edges and corners, cables across, pens) and camera effects (blur, noise, JPEG). The label is
the whole document region — including the parts under fingers and cables — so the model learns
to complete hidden corners. About 15% of samples have no document.

    uv run python -m detector.generate --preview 32 --out /tmp/det-preview [--seed 1]
"""

from __future__ import annotations

import argparse
import math
import os
from dataclasses import dataclass
from pathlib import Path

import cv2
import numpy as np

from detector.model import INPUT_SIZE, MASK_SIZE

SCENE_LONG = 512
ASPECTS = [(3, 4), (4, 3), (9, 16), (1, 1)]  # width:height of the camera frame
NEGATIVE_RATE = 0.15
BACKGROUND_DIR = Path(os.environ.get("PP_DETECTOR_BACKGROUNDS", Path.home() / ".cache/plainpaper/detector/backgrounds"))


@dataclass
class Sample:
    image: np.ndarray  # INPUT_SIZE x INPUT_SIZE x 3, uint8 RGB
    mask: np.ndarray  # MASK_SIZE x MASK_SIZE, float32 0/1
    presence: float
    quad: np.ndarray | None  # 4x2 scene corners normalised to 0..1 (TL, TR, BR, BL), None if absent
    area: float  # document area fraction of the frame (in-frame part)


# ---------------------------------------------------------------------------------------------
# helpers


def rand_color(rng: np.random.Generator, lo: int = 0, hi: int = 255) -> tuple[int, int, int]:
    return tuple(int(v) for v in rng.integers(lo, hi + 1, 3))


def smooth_noise(rng: np.random.Generator, h: int, w: int, scale: float) -> np.ndarray:
    """Value noise in 0..1, features about `scale` pixels wide."""
    sh, sw = max(2, int(h / scale) + 2), max(2, int(w / scale) + 2)
    small = rng.random((sh, sw)).astype(np.float32)
    return cv2.resize(small, (w, h), interpolation=cv2.INTER_CUBIC).clip(0, 1)


def paper_color(rng: np.random.Generator) -> np.ndarray:
    base = rng.choice(
        np.array([[250, 250, 248], [245, 240, 225], [235, 235, 235], [230, 238, 245], [250, 245, 235]], np.float32)
    )
    return np.clip(base + rng.normal(0, 6, 3), 0, 255)


# ---------------------------------------------------------------------------------------------
# backgrounds


def bg_wood(rng, h, w):
    base = np.array(rand_color(rng, 60, 200), np.float32) * np.array([1.0, 0.75, 0.5])
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    warp = smooth_noise(rng, h, w, 60) * 30
    grain = np.sin((yy + warp) / rng.uniform(3, 9)) * 0.5 + 0.5
    img = base[None, None, :] * (0.75 + 0.35 * grain[..., None])
    if rng.random() < 0.5:  # planks
        plank = int(rng.uniform(40, 120))
        img[(yy.astype(int) % plank) < 2] *= 0.6
    return img


def bg_tiles(rng, h, w):
    c1 = np.array(rand_color(rng, 120, 250), np.float32)
    c2 = np.array(rand_color(rng, 60, 250), np.float32) if rng.random() < 0.5 else c1 * rng.uniform(0.8, 1.0)
    size = int(rng.uniform(30, 110))
    yy, xx = np.mgrid[0:h, 0:w]
    checker = ((yy // size + xx // size) % 2).astype(np.float32)[..., None]
    img = c1 * (1 - checker) + c2 * checker
    if rng.random() < 0.5:  # accent squares (like a patterned floor)
        accent = np.array(rand_color(rng, 80, 230), np.float32)
        m = (((yy // size) % 3 == 1) & ((xx // size) % 3 == 1))[..., None]
        inner = ((yy % size) > size * 0.25) & ((yy % size) < size * 0.75) & ((xx % size) > size * 0.25) & ((xx % size) < size * 0.75)
        img = np.where(m & inner[..., None], accent, img)
    grout = ((yy % size) < 2) | ((xx % size) < 2)
    img[grout] *= rng.uniform(0.6, 0.95)
    return img * (0.9 + 0.2 * smooth_noise(rng, h, w, 80)[..., None])


def bg_fabric(rng, h, w):
    base = np.array(rand_color(rng, 30, 200), np.float32)
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    period = rng.uniform(2, 5)
    weave = (np.sin(xx / period) * np.sin(yy / period)) * 0.08
    folds = smooth_noise(rng, h, w, rng.uniform(40, 150))
    return base * (0.7 + 0.5 * folds[..., None] + weave[..., None])


def bg_desk(rng, h, w):
    """Plain surfaces, often close to paper colour (the hardest case for the classic detector)."""
    base = paper_color(rng) * rng.uniform(0.75, 1.0) if rng.random() < 0.6 else np.array(rand_color(rng, 40, 240), np.float32)
    grad = smooth_noise(rng, h, w, rng.uniform(150, 400))
    return base * (0.85 + 0.25 * grad[..., None]) + rng.normal(0, 3, (h, w, 3))


def bg_clutter(rng, h, w):
    img = bg_desk(rng, h, w) if rng.random() < 0.5 else bg_wood(rng, h, w)
    for _ in range(int(rng.integers(3, 12))):
        color = rand_color(rng)
        kind = rng.integers(0, 3)
        if kind == 0:
            p = rng.integers(0, [w, h])
            q = p + rng.integers(20, 150, 2)
            cv2.rectangle(img, tuple(int(v) for v in p), tuple(int(v) for v in q), color, -1)
        elif kind == 1:
            cv2.circle(img, tuple(int(v) for v in rng.integers(0, [w, h])), int(rng.integers(10, 70)), color, -1)
        else:
            pts = rng.integers(0, [w, h], (int(rng.integers(3, 6)), 2)).astype(np.int32)
            cv2.polylines(img, [pts], False, color, int(rng.integers(2, 10)))
    return cv2.GaussianBlur(img, (0, 0), rng.uniform(0.5, 2.0))


_local_backgrounds: list[Path] | None = None


def bg_photo(rng, h, w):
    global _local_backgrounds
    if _local_backgrounds is None:
        _local_backgrounds = sorted(p for p in BACKGROUND_DIR.glob("*") if p.suffix.lower() in {".jpg", ".jpeg", ".png"}) if BACKGROUND_DIR.exists() else []
    if not _local_backgrounds:
        return bg_clutter(rng, h, w)
    img = cv2.imread(str(_local_backgrounds[int(rng.integers(len(_local_backgrounds)))]))
    if img is None:
        return bg_clutter(rng, h, w)
    img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
    ih, iw = img.shape[:2]
    scale = max(h / ih, w / iw) * rng.uniform(1.0, 1.6)
    img = cv2.resize(img, (int(iw * scale) + 1, int(ih * scale) + 1), interpolation=cv2.INTER_AREA)
    y = int(rng.integers(0, img.shape[0] - h + 1))
    x = int(rng.integers(0, img.shape[1] - w + 1))
    return img[y : y + h, x : x + w].astype(np.float32)


BACKGROUNDS = [(bg_wood, 2), (bg_tiles, 2), (bg_fabric, 2), (bg_desk, 3), (bg_clutter, 2), (bg_photo, 2)]


def background(rng, h, w) -> np.ndarray:
    fns, weights = zip(*BACKGROUNDS)
    fn = fns[int(rng.choice(len(fns), p=np.array(weights) / sum(weights)))]
    return np.clip(fn(rng, h, w), 0, 255).astype(np.float32)


# ---------------------------------------------------------------------------------------------
# documents (textures in their own frame; aspect = width / height)


def ink(rng) -> tuple[int, int, int]:
    return rng.choice([(20, 20, 25), (30, 40, 120), (20, 60, 160), (150, 20, 20), (20, 110, 40)]).tolist()


def text_lines(rng, img, x0, y0, x1, y1, size_px):
    """Word-like strokes and Latin glyphs (cv2 has no Arabic shaping; strokes stand in for it)."""
    color = ink(rng)
    y = y0 + size_px
    while y < y1:
        x = x0 + (int(rng.integers(0, size_px * 3)) if rng.random() < 0.2 else 0)
        while x < x1 - size_px:
            wlen = int(rng.integers(2, 9)) * size_px // 2
            wlen = min(wlen, x1 - x)
            if rng.random() < 0.5:
                cv2.putText(img, "".join(rng.choice(list("abcdefghijklmnopqrstuvwxyz0123456789"), max(1, wlen // max(1, size_px // 2)))),
                            (x, y), cv2.FONT_HERSHEY_SIMPLEX, size_px / 30, color, max(1, size_px // 12), cv2.LINE_AA)
            else:  # cursive-like stroke with dots (Arabic-ish)
                pts = np.stack([np.linspace(x, x + wlen, 8), y - size_px * 0.3 + rng.normal(0, size_px * 0.15, 8)], 1).astype(np.int32)
                cv2.polylines(img, [pts], False, color, max(1, size_px // 8), cv2.LINE_AA)
                for _ in range(int(rng.integers(0, 3))):
                    cv2.circle(img, (int(rng.integers(x, x + wlen + 1)), int(y + rng.choice([-1, 1]) * size_px * 0.6)), max(1, size_px // 10), color, -1)
            x += wlen + size_px // 2
        y += int(size_px * rng.uniform(1.4, 2.0))


def doc_page(rng, scale=1.0):
    aspect = rng.choice([1 / 1.414, 1 / 1.294, 0.75, 1 / 1.6])
    h = int(600 * scale)
    w = int(h * aspect)
    img = np.ones((h, w, 3), np.float32) * paper_color(rng)
    m = int(w * rng.uniform(0.05, 0.12))
    if rng.random() < 0.6:
        text_lines(rng, img, m, m, w - m, h - m, int(rng.integers(8, 18)))
    else:  # drawing page with a text header (colouring books, worksheets)
        text_lines(rng, img, m, m, w - m, int(h * 0.25), int(rng.integers(8, 14)))
        for _ in range(int(rng.integers(4, 12))):
            pts = rng.integers([m, int(h * 0.3)], [w - m, h - m], (int(rng.integers(3, 8)), 2)).astype(np.int32)
            if rng.random() < 0.5:
                cv2.fillPoly(img, [cv2.convexHull(pts)], rand_color(rng, 60, 240))
            cv2.polylines(img, [pts], True, (20, 20, 20), int(rng.integers(1, 4)), cv2.LINE_AA)
    if rng.random() < 0.3:  # ruled lines
        for y in range(m, h - m, int(rng.integers(18, 30))):
            cv2.line(img, (0, y), (w, y), (170, 190, 220), 1)
    return img


def doc_card(rng):
    w, h = 428, 270  # ID-1
    img = np.ones((h, w, 3), np.float32) * (paper_color(rng) if rng.random() < 0.6 else np.array(rand_color(rng, 150, 250), np.float32))
    band = int(h * rng.uniform(0.15, 0.3))
    if rng.random() < 0.8:
        img[:band] = rand_color(rng, 20, 200)
        text_lines(rng, img, 20, 5, w - 20, band - 5, int(band * 0.45))
    px = int(rng.choice([15, w - 120]))
    img[band + 15 : h - 30, px : px + 100] = np.array(rand_color(rng, 60, 220), np.float32)
    tx0 = 130 if px == 15 else 15
    text_lines(rng, img, tx0, band + 10, tx0 + w - 150, h - 10, int(rng.integers(9, 14)))
    if rng.random() < 0.5:
        cv2.circle(img, (int(rng.integers(100, w - 50)), int(rng.integers(band + 40, h - 30))), int(rng.integers(20, 40)), ink(rng), 2, cv2.LINE_AA)
    return img, 0.06  # corner radius as a fraction of the short side


def doc_receipt(rng):
    w = 260
    h = int(w * rng.uniform(1.8, 3.5))
    img = np.ones((h, w, 3), np.float32) * np.array([245, 244, 238], np.float32)
    text_lines(rng, img, 15, 10, w - 15, h - 10, int(rng.integers(7, 11)))
    return img


def doc_spread(rng):
    left = doc_page(rng)
    right = doc_page(rng)
    h = min(left.shape[0], right.shape[0])
    img = np.concatenate([left[:h], right[:h, : left.shape[1]]], axis=1)
    w = img.shape[1]
    x = np.arange(w, dtype=np.float32)
    shade = 1 - 0.35 * np.exp(-((x - w / 2) ** 2) / (2 * (w * 0.03) ** 2))
    return img * shade[None, :, None]


def doc_cover(rng):
    h = 600
    w = int(h * rng.uniform(0.65, 0.8))
    img = np.ones((h, w, 3), np.float32) * np.array(rand_color(rng, 10, 90), np.float32)
    cv2.putText(img, "TITLE", (int(w * 0.15), int(h * 0.3)), cv2.FONT_HERSHEY_DUPLEX, w / 250, rand_color(rng, 150, 255), 3, cv2.LINE_AA)
    return img


def document(rng) -> tuple[np.ndarray, float]:
    """Texture (RGB float) and corner radius fraction."""
    kind = rng.choice(["page", "card", "receipt", "spread", "cover"], p=[0.45, 0.2, 0.12, 0.13, 0.1])
    if kind == "card":
        return doc_card(rng)
    if kind == "receipt":
        return doc_receipt(rng), 0.0
    if kind == "spread":
        return doc_spread(rng), 0.0
    if kind == "cover":
        return doc_cover(rng), 0.02
    return doc_page(rng), 0.0


# ---------------------------------------------------------------------------------------------
# placement


def place_quad(rng, doc_w, doc_h, frame_w, frame_h) -> np.ndarray:
    """Scene corners (TL, TR, BR, BL) for a document of the given size."""
    area = math.exp(rng.uniform(math.log(0.05), math.log(0.95)))
    aspect = doc_w / doc_h
    # rotate the document a lot sometimes (cards and pages photographed sideways)
    angle = rng.normal(0, 12) + (rng.choice([0, 90, 180, 270]) if rng.random() < 0.25 else 0)
    target = area * frame_w * frame_h
    h = math.sqrt(target / aspect)
    w = h * aspect
    rect = np.array([[-w / 2, -h / 2], [w / 2, -h / 2], [w / 2, h / 2], [-w / 2, h / 2]], np.float32)
    # perspective: shrink the far edge
    tilt = rng.uniform(0, 0.3)
    side = rng.integers(0, 4)
    far = [(0, 1), (1, 2), (2, 3), (3, 0)][side]
    centre_edge = (rect[far[0]] + rect[far[1]]) / 2
    for idx in far:
        rect[idx] = centre_edge + (rect[idx] - centre_edge) * (1 - tilt)
    rect += rng.normal(0, 0.02 * min(w, h), rect.shape)
    t = math.radians(angle)
    rot = np.array([[math.cos(t), -math.sin(t)], [math.sin(t), math.cos(t)]], np.float32)
    rect = rect @ rot.T
    # centre: mostly inside, sometimes partly out of frame
    margin = 0.5 if rng.random() < 0.8 else 0.75
    ext = rect.max(0) - rect.min(0)
    cx = rng.uniform(min(frame_w / 2, ext[0] * margin), max(frame_w / 2, frame_w - ext[0] * margin)) if ext[0] * margin < frame_w - ext[0] * margin else frame_w / 2
    cy = rng.uniform(min(frame_h / 2, ext[1] * margin), max(frame_h / 2, frame_h - ext[1] * margin)) if ext[1] * margin < frame_h - ext[1] * margin else frame_h / 2
    return (rect + np.array([cx, cy], np.float32)).astype(np.float32)


def rounded_alpha(h, w, radius_fraction) -> np.ndarray:
    alpha = np.ones((h, w), np.float32)
    r = int(min(h, w) * radius_fraction)
    if r > 1:
        alpha[:] = 0
        cv2.rectangle(alpha, (r, 0), (w - 1 - r, h - 1), 1, -1)
        cv2.rectangle(alpha, (0, r), (w - 1, h - 1 - r), 1, -1)
        for cx, cy in [(r, r), (w - 1 - r, r), (w - 1 - r, h - 1 - r), (r, h - 1 - r)]:
            cv2.circle(alpha, (cx, cy), r, 1, -1)
    return alpha


# ---------------------------------------------------------------------------------------------
# effects


def add_shadow(rng, img):
    h, w = img.shape[:2]
    pts = rng.integers([-w // 2, -h // 2], [w + w // 2, h + h // 2], (int(rng.integers(3, 6)), 2)).astype(np.int32)
    m = np.zeros((h, w), np.float32)
    cv2.fillPoly(m, [cv2.convexHull(pts)], 1.0)
    m = cv2.GaussianBlur(m, (0, 0), rng.uniform(5, 40))
    return img * (1 - rng.uniform(0.2, 0.55) * m[..., None])


def add_lighting(rng, img):
    h, w = img.shape[:2]
    g = smooth_noise(rng, h, w, rng.uniform(200, 600))
    return img * (rng.uniform(0.55, 1.0) + rng.uniform(0.0, 0.5) * g[..., None])


def add_glare(rng, img):
    h, w = img.shape[:2]
    m = np.zeros((h, w), np.float32)
    cv2.ellipse(m, (int(rng.integers(0, w)), int(rng.integers(0, h))), (int(rng.integers(15, 90)), int(rng.integers(10, 60))), float(rng.uniform(0, 180)), 0, 360, 1.0, -1)
    m = cv2.GaussianBlur(m, (0, 0), rng.uniform(4, 20))
    return img + rng.uniform(80, 220) * m[..., None]


SKIN = [(255, 219, 172), (241, 194, 125), (224, 172, 105), (198, 134, 66), (141, 85, 36), (92, 60, 40)]


def add_hand(rng, img, quad):
    """Palm + fingers over a document edge or corner (or anywhere when there is no document)."""
    h, w = img.shape[:2]
    color = np.array(SKIN[int(rng.integers(len(SKIN)))], np.float32) * rng.uniform(0.8, 1.1)
    if quad is not None:
        i = int(rng.integers(0, 4))
        t = rng.uniform(0, 1) if rng.random() < 0.6 else 0.0
        anchor = quad[i] + (quad[(i + 1) % 4] - quad[i]) * t
    else:
        anchor = rng.integers(0, [w, h]).astype(np.float32)
    centre = quad.mean(0) if quad is not None else np.array([w / 2, h / 2], np.float32)
    out = anchor - centre
    out /= np.linalg.norm(out) + 1e-6
    scale = rng.uniform(0.12, 0.3) * min(h, w)
    palm = anchor + out * scale * 0.9
    layer = np.zeros((h, w), np.float32)
    cv2.ellipse(layer, (int(palm[0]), int(palm[1])), (int(scale * 0.7), int(scale * 0.55)), float(np.degrees(np.arctan2(out[1], out[0]))), 0, 360, 1.0, -1)
    perp = np.array([-out[1], out[0]])
    for f in range(int(rng.integers(1, 5))):
        base = palm - out * scale * 0.4 + perp * (f - 1.5) * scale * 0.3
        tip = base - out * scale * rng.uniform(0.6, 1.0)
        cv2.line(layer, (int(base[0]), int(base[1])), (int(tip[0]), int(tip[1])), 1.0, int(scale * 0.22))
    layer = cv2.GaussianBlur(layer, (0, 0), 1.5)
    shade = (0.75 + 0.35 * smooth_noise(rng, h, w, 40))[..., None]
    return img * (1 - layer[..., None]) + (color * shade) * layer[..., None]


def organic_mask(rng, h, w, centre, size) -> np.ndarray:
    """A soft, irregular region (cloth fold, knee, bag, laptop) around `centre`."""
    m = np.zeros((h, w), np.float32)
    for _ in range(int(rng.integers(2, 6))):
        c = centre + rng.normal(0, size * 0.35, 2)
        axes = (int(size * rng.uniform(0.3, 0.8)), int(size * rng.uniform(0.2, 0.6)))
        cv2.ellipse(m, (int(c[0]), int(c[1])), axes, float(rng.uniform(0, 180)), 0, 360, 1.0, -1)
    if rng.random() < 0.3:  # straight-edged object (laptop, box, book)
        r = cv2.boxPoints(((float(centre[0]), float(centre[1])), (size * rng.uniform(0.8, 1.6), size * rng.uniform(0.5, 1.2)), float(rng.uniform(0, 90))))
        cv2.fillPoly(m, [r.astype(np.int32)], 1.0)
    noise = smooth_noise(rng, h, w, max(8.0, size / 6))
    m = np.clip(m * (0.75 + 0.5 * noise) - 0.25, 0, 1) * 1.6
    return cv2.GaussianBlur(np.clip(m, 0, 1), (0, 0), rng.uniform(1.5, 6))


def object_texture(rng, h, w) -> np.ndarray:
    kind = rng.integers(0, 4)
    if kind == 0:
        return bg_fabric(rng, h, w)
    if kind == 1:  # skin (knee, leg, arm)
        return np.array(SKIN[int(rng.integers(len(SKIN)))], np.float32) * (0.7 + 0.4 * smooth_noise(rng, h, w, 60)[..., None])
    if kind == 2:  # dark plastic / laptop / phone
        return np.array(rand_color(rng, 10, 70), np.float32) * (0.8 + 0.4 * smooth_noise(rng, h, w, 120)[..., None])
    return bg_wood(rng, h, w) if rng.random() < 0.5 else bg_tiles(rng, h, w)


def add_object(rng, img, quad):
    """A large object next to or under the document (the outline must not spill onto it)."""
    h, w = img.shape[:2]
    if quad is not None and rng.random() < 0.7:
        centre = quad.mean(0) + rng.normal(0, 0.35, 2) * np.array([w, h]) * 0.5
    else:
        centre = rng.uniform([0, 0], [w, h])
    m = organic_mask(rng, h, w, centre, rng.uniform(0.25, 0.7) * min(h, w))
    tex = object_texture(rng, h, w)
    shade = 1 - 0.3 * cv2.GaussianBlur(m, (0, 0), 12)[..., None]  # contact shadow
    return img * shade * (1 - m[..., None]) + tex * m[..., None]


def add_arm(rng, img, quad):
    """A hand holding the document from one side, with the forearm reaching the frame edge."""
    h, w = img.shape[:2]
    if quad is None:
        return add_hand(rng, img, None)
    i = int(rng.integers(0, 4))
    grip = quad[i] + (quad[(i + 1) % 4] - quad[i]) * rng.uniform(0.2, 0.8)
    centre = quad.mean(0)
    out = grip - centre
    out /= np.linalg.norm(out) + 1e-6
    # arm runs from the grip to the frame edge in the outward direction
    t = 0.0
    end = grip.copy()
    while 0 <= end[0] < w and 0 <= end[1] < h and t < 3 * max(h, w):
        t += 4
        end = grip + out * t
    width = rng.uniform(0.12, 0.25) * min(h, w)
    layer = np.zeros((h, w), np.float32)
    cv2.line(layer, (int(grip[0]), int(grip[1])), (int(end[0]), int(end[1])), 1.0, int(width))
    perp = np.array([-out[1], out[0]])
    thumb = grip - out * width * 0.6 + perp * width * 0.3
    cv2.line(layer, (int(grip[0]), int(grip[1])), (int(thumb[0]), int(thumb[1])), 1.0, int(width * 0.35))
    layer = cv2.GaussianBlur(layer, (0, 0), 2)
    color = np.array(SKIN[int(rng.integers(len(SKIN)))], np.float32)
    tex = color * (0.65 + 0.45 * smooth_noise(rng, h, w, 50)[..., None])
    if rng.random() < 0.4:  # sleeve
        sleeve = np.array(rand_color(rng, 20, 200), np.float32)
        far = np.zeros((h, w), np.float32)
        cv2.line(far, (int((grip + out * width * 2)[0]), int((grip + out * width * 2)[1])), (int(end[0]), int(end[1])), 1.0, int(width * 1.2))
        tex = tex * (1 - far[..., None]) + sleeve * far[..., None]
    return img * (1 - layer[..., None]) + tex * layer[..., None]


def add_cable(rng, img):
    h, w = img.shape[:2]
    p = rng.uniform([-0.1, -0.1], [1.1, 1.1], (4, 2)) * [w, h]
    t = np.linspace(0, 1, 60)[:, None]
    curve = ((1 - t) ** 3 * p[0] + 3 * (1 - t) ** 2 * t * p[1] + 3 * (1 - t) * t**2 * p[2] + t**3 * p[3]).astype(np.int32)
    color = (20, 20, 20) if rng.random() < 0.7 else rand_color(rng, 150, 255)
    thick = int(rng.integers(3, 12))
    cv2.polylines(img, [curve], False, color, thick, cv2.LINE_AA)
    if rng.random() < 0.5:
        cv2.polylines(img, [curve + np.array([1, -1])], False, tuple(min(255, c + 60) for c in color), max(1, thick // 4), cv2.LINE_AA)
    return img


def add_pen(rng, img):
    h, w = img.shape[:2]
    c = rng.integers(0, [w, h])
    a = rng.uniform(0, math.pi)
    length = rng.uniform(0.15, 0.4) * min(h, w)
    d = np.array([math.cos(a), math.sin(a)]) * length / 2
    cv2.line(img, tuple(int(v) for v in c - d), tuple(int(v) for v in c + d), rand_color(rng), int(rng.integers(5, 12)), cv2.LINE_AA)
    return img


def camera(rng, img):
    if rng.random() < 0.4:
        img = cv2.GaussianBlur(img, (0, 0), rng.uniform(0.5, 2.5))
    if rng.random() < 0.2:  # motion blur
        k = int(rng.integers(3, 12))
        kernel = np.zeros((k, k), np.float32)
        kernel[k // 2, :] = 1 / k
        rot = cv2.getRotationMatrix2D((k / 2, k / 2), float(rng.uniform(0, 180)), 1)
        img = cv2.filter2D(img, -1, cv2.warpAffine(kernel, rot, (k, k)))
    img = img * rng.uniform(0.85, 1.15, 3)[None, None, :]  # white balance
    img = img + rng.normal(0, rng.uniform(1, 8), img.shape)
    img = np.clip(img, 0, 255).astype(np.uint8)
    if rng.random() < 0.6:
        ok, enc = cv2.imencode(".jpg", img, [cv2.IMWRITE_JPEG_QUALITY, int(rng.integers(40, 95))])
        img = cv2.imdecode(enc, cv2.IMREAD_UNCHANGED)
    return img


# ---------------------------------------------------------------------------------------------


def sample(rng: np.random.Generator) -> Sample:
    aw, ah = ASPECTS[int(rng.integers(len(ASPECTS)))]
    if aw >= ah:
        fw, fh = SCENE_LONG, int(SCENE_LONG * ah / aw)
    else:
        fh, fw = SCENE_LONG, int(SCENE_LONG * aw / ah)
    img = background(rng, fh, fw)
    mask = np.zeros((fh, fw), np.float32)
    quad = None
    present = rng.random() >= NEGATIVE_RATE
    for _ in range(int(rng.choice([0, 1, 1, 2]))):  # objects lying under/near the document
        img = add_object(rng, img, None)
    if present:
        tex, radius = document(rng)
        th, tw = tex.shape[:2]
        quad = place_quad(rng, tw, th, fw, fh)
        if rng.random() < 0.4:  # something big under the document, touching its sides
            img = add_object(rng, img, quad)
        src = np.array([[0, 0], [tw, 0], [tw, th], [0, th]], np.float32)
        H = cv2.getPerspectiveTransform(src, quad)
        warped = cv2.warpPerspective(tex, H, (fw, fh), flags=cv2.INTER_AREA, borderMode=cv2.BORDER_CONSTANT)
        alpha = cv2.warpPerspective(rounded_alpha(th, tw, radius), H, (fw, fh), flags=cv2.INTER_LINEAR)
        if rng.random() < 0.5:  # drop shadow under the document
            sh = cv2.GaussianBlur(alpha, (0, 0), rng.uniform(2, 8))
            sh = np.roll(sh, (int(rng.integers(-6, 7)), int(rng.integers(-6, 7))), (0, 1))
            img *= 1 - 0.35 * sh[..., None]
        img = img * (1 - alpha[..., None]) + warped * alpha[..., None]
        mask = alpha
    img = add_lighting(rng, img)
    if rng.random() < 0.4:
        img = add_shadow(rng, img)
    if rng.random() < 0.25:
        img = add_glare(rng, img)
    if rng.random() < 0.3:
        img = add_arm(rng, img, quad)
    for _ in range(int(rng.choice([0, 0, 1, 1, 2]))):
        img = add_hand(rng, img, quad)
    for _ in range(int(rng.choice([0, 0, 0, 1, 2]))):
        img = add_cable(rng, img)
    if rng.random() < 0.2:
        img = add_pen(rng, img)
    img = camera(rng, img)
    area = float((mask > 0.5).mean()) if quad is not None else 0.0
    if quad is not None and area < 0.01:  # pushed almost entirely out of frame: treat as absent
        quad, mask, area = None, np.zeros_like(mask), 0.0
    image = cv2.resize(img, (INPUT_SIZE, INPUT_SIZE), interpolation=cv2.INTER_AREA)
    label = (cv2.resize(mask, (MASK_SIZE, MASK_SIZE), interpolation=cv2.INTER_AREA) > 0.5).astype(np.float32)
    norm_quad = None if quad is None else quad / np.array([fw, fh], np.float32)
    return Sample(image=image, mask=label, presence=1.0 if quad is not None else 0.0, quad=norm_quad, area=area)


def sample_for(seed: int, index: int) -> Sample:
    """Deterministic sample: the same (seed, index) always gives the same scene."""
    return sample(np.random.default_rng([seed, index]))


def preview(count: int, out: Path, seed: int) -> None:
    out.mkdir(parents=True, exist_ok=True)
    tiles = []
    for i in range(count):
        s = sample_for(seed, i)
        img = cv2.cvtColor(s.image, cv2.COLOR_RGB2BGR)
        overlay = img.copy()
        m = cv2.resize(s.mask, (INPUT_SIZE, INPUT_SIZE), interpolation=cv2.INTER_NEAREST)
        overlay[m > 0.5] = (0.5 * overlay[m > 0.5] + 0.5 * np.array([255, 120, 0])).astype(np.uint8)
        tiles.append(np.concatenate([img, overlay], axis=1))
    rows = [np.concatenate(tiles[i : i + 4], axis=1) for i in range(0, len(tiles) - len(tiles) % 4, 4)]
    if rows:
        cv2.imwrite(str(out / "preview.jpg"), np.concatenate(rows, axis=0))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--preview", type=int, default=16)
    parser.add_argument("--out", type=Path, default=Path("cache/preview"))
    parser.add_argument("--seed", type=int, default=1)
    args = parser.parse_args()
    preview(args.preview, args.out, args.seed)


if __name__ == "__main__":
    main()
