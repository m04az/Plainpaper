# Quickstart: validate smart detection

## Prerequisites
- Feature 003 prerequisites (host OpenCV rebuilt with `dnn`: `scripts/build-opencv-host.sh`).
- Training only: `cd tools/detector-training && uv sync`.

## Model
```sh
cd tools/detector-training
uv run python -m detector.generate --preview 32 --out /tmp/det-preview   # look at samples
uv run python -m detector.train --config configs/v1.yaml                 # trains, writes runs/<id>/
uv run python -m detector.export runs/<id>/best.pt ../../composeApp/src/commonMain/composeResources/files/detector.onnx
uv run python -m detector.check ../../composeApp/src/commonMain/composeResources/files/detector.onnx  # cv2.dnn parity
```
Expected: parity max abs diff < 1e-3; MODEL_CARD.md updated with seed, metrics, SHA-256.

## Quality gate
```sh
./gradlew :tools:scansuite:test                                   # existing gate + cluttered gate
./gradlew :tools:scansuite:run --args="--cluttered"               # report: found rate, FP rate
./gradlew :tools:scansuite:run --args="--photos <local dir>"      # owner photos, prints detector + corners
```
Expected: cluttered found rate ≥ 90% at ≤ 2% error (SC-001), FP ≤ 5% (SC-002), existing sets unchanged (SC-003).

## Size
```sh
./gradlew :androidApp:assembleRelease && unzip -l androidApp/build/outputs/apk/release/*.apk | grep -E "libplainpaper|detector.onnx"
```
Expected: growth vs main ≤ 5 MB per ABI including the model (SC-005).

## Privacy
```sh
scripts/check-no-network-permission.sh   # fails if the merged manifest declares INTERNET / ACCESS_NETWORK_STATE
```

## Phone
1. Hand-held ID card, cable across a page, white page on a light floor: outline within 1 s, auto-capture within 3 s of holding still (SC-004).
2. Quick scan: 3 pages → session → change look, rotate, flip (SC-006).
