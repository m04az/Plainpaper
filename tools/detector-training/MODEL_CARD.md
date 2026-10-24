# Model card: Plainpaper document detector

- **File**: `composeApp/src/commonMain/composeResources/files/detector.ppdm` (2.0 MB, `.ppdm` v1, arch v1)
- **SHA-256**: `68b448e89ee524b2a55d52da71764673656784a95e386d4b3fd7cc515f0d6e98`
- **Owner / licence**: project-owned weights, trained only on procedurally generated scenes
  (no third-party images or datasets). Ships under the app's licence.
- **Architecture**: `detector.model.DocumentNet` — MobileNetV2-style encoder (×0.5, ReLU6),
  4-stage U-Net decoder; 509 k parameters. Input 256×256 RGB (frame resized, aspect not kept);
  outputs a 128×128 document mask (whole document, including occluded parts) and a presence logit.
- **Training (run `v1`)**: generator v1 (`detector.generate`, seed 1), 8 epochs × 1000 steps ×
  batch 32 (stopped early; validation plateaued), AdamW lr 2e-3 cosine, BCE + Dice + presence BCE,
  Apple MPS. Validation (512 generated scenes, seed 999001): mask IoU 0.958, presence accuracy 0.99.
- **Engine parity**: `native/tests/model_parity.cpp` vs PyTorch, max abs diff < 1e-5.
- **Speed**: 12.5 ms per pass on an M-series Mac (4 threads); expected 25–35 ms on a mid-range phone.
- **Known limits (owner's real photos)**: finds notebook pages and cards reliably (presence ≈ 1.0),
  but the mask spills onto large dark regions touching the document (cloth, knee, phone body).
  The engine therefore trusts the classic detector's edges when they are confident or agree,
  snaps to an edge quad inside the model region when one exists, and never reports High
  confidence from the mask alone.
- **Run `v2`** (generator v2 with objects under documents and arms; fine-tuned from v1, 25 epochs,
  synthetic IoU 0.980) was **not shipped**: on real photos it was more confident on spilled
  outlines. Next step: train with real background photos (owner's rooms, kept locally under
  `~/.cache/plainpaper/detector/backgrounds/`) and an annotated real validation set.

Reproduce: `uv run python -m detector.train --name v1 --epochs 40 --steps 1000` (stop after epoch 8)
then `uv run python -m detector.ppdm runs/v1/best.pt <out.ppdm> --vectors <out.vec>`.
