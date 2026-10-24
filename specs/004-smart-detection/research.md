# Research: Smart document detection

## R1 — Where the model runs

- **Decision**: inside the C++ scan engine, with **our own inference code** on OpenCV core
  (`src/model.cpp`, ~300 lines): direct 1×1 convolutions (vectorised axpy, threaded by output
  channel), direct 3×3 convolutions (stride-aware), bilinear ×2 upsampling (`cv::resize`,
  half-pixel like PyTorch), concat, global average pool, linear. Weights come in a small
  project format, `.ppdm` (BatchNorm folded, tensors by name), written by
  `tools/detector-training/detector/ppdm.py`.
- **Spike results (T001, 2026-10-24)**:

  | Option | arm64 `.so` (stripped) | Download (gzip) | Model | Pass (M-series, 4 threads) |
  |--------|------------------------|-----------------|-------|----------------------------|
  | Before | 5.33 MB | 2.07 MB | — | — |
  | OpenCV `dnn` (ONNX) | 9.58 MB (+4.25) | 3.57 MB (+1.5) | 2.03 MB ONNX | 5.6 ms |
  | Own inference | 5.43 MB (+0.10) | — | 2.01 MB `.ppdm` | 12.5 ms |

  `dnn` registers every layer and the protobuf ONNX parser statically, so `--gc-sections`
  cannot drop them; with the model it exceeds the 5 MB gate. Our inference matches PyTorch to
  9e-8 (`native/tests/model_parity.cpp`), adds 0.1 MB and needs no extra OpenCV module on any
  platform (host build unchanged). Expected phone time ≈ 25–35 ms per pass.
- **Alternatives considered**: OpenCV `dnn` (measured above, over budget); TFLite/LiteRT and
  ONNX Runtime Mobile (a second runtime per platform, 1.5–3 MB each, separate bridge wiring).

## R2 — Model design

- **Decision**: a small encoder–decoder that predicts a **document mask** (probability per
  pixel) at 1/2 of a 256×256 input (output 128×128), plus a "document present" score from the
  pooled encoder features. Encoder: MobileNetV2-style inverted residual blocks with ReLU6
  (width multiplier ≈ 0.5, ≈ 0.4–0.6 M parameters); decoder: three upsampling stages with skip
  connections. Exported to ONNX (opset 13) with fixed input size; float32 weights (~2 MB).
- **From mask to quad**: threshold 0.5 → largest component → hull → the existing
  `quadFromContour` + `maxAreaQuad` fit, scaled to working resolution, then the existing edge
  snapping (`refineQuad`) when edges support it. A mask completes hidden corners naturally (a
  finger over a corner still leaves the page shape), which corner regression handles worse
  when a corner is out of frame.
- **Confidence**: High when present-score ≥ 0.8, mask/quad IoU ≥ 0.9 and area ≥ 5%; Low when
  present-score ≥ 0.5; otherwise NotFound. The classic detector is not consulted when the model
  says "no document" with score < 0.2 (prevents clutter outlines).
- **Alternatives considered**: corner heatmaps (precise but brittle with hidden/out-of-frame
  corners); direct 8-value regression (DocCornerNet-style; weaker with occlusion); larger
  segmentation models (DeepLabV3-MobileNet ≈ 10+ MB, too big and slow).
- **Third-party weights**: DocAligner (Apache-2.0 code) and scanic's DocCornerNet LEAN publish no
  licence for their weights or training data, so they cannot ship in a paid app. We train our
  own.

## R3 — Training data (synthetic composites)

- **Decision**: a deterministic generator in `tools/detector-training/` (Python) that composes
  each sample from:
  - **documents**: pages rendered by the scansuite synthetic generator (Latin and Arabic text,
    drawings, colour), ID-card-like and receipt-like layouts, book spreads with a spine, dark
    covers; random aspect, scale 5–98% of the frame, perspective (≤ 35° tilt), slight curl;
  - **backgrounds**: procedural textures (wood grain, tiles, fabric, floor patterns, noise,
    gradients, desks of paper-like colour) and the owner's own background photos (rooms, desks,
    floors without documents) kept locally under `~/.cache/plainpaper/detector/backgrounds/`;
  - **occluders**: procedural hands (skin-tone palm + finger capsules, over an edge or a corner),
    cables (dark Bézier strokes across the page), pens, phones, other papers;
  - **photometry**: directional lighting, cast shadows, glare spots, white balance shifts, blur,
    noise, JPEG artefacts, low light.
  - **negatives**: 15% of samples contain no document (backgrounds and clutter only).
- **Labels**: the document's visible-plus-occluded quad rasterised as the mask (the mask is the
  whole document, including the parts under fingers), plus presence.
- **Rationale**: no licensing questions on data; unlimited variety; matches the failure cases
  from the phone tests (hand, cable, light floor, cloth).
- **Alternatives considered**: public datasets (SmartDoc 2015, MIDV-500/2020, DocCornerDataset):
  licences are research-only or inconsistent; usable only for private evaluation, not training.

## R4 — Training setup

- **Decision**: PyTorch on the Mac (MPS backend), environment managed by `uv` with a pinned
  `pyproject.toml` in `tools/detector-training/`; ~200 k generated samples (generated on the fly,
  seeded), loss = BCE + Dice on the mask + BCE on presence; AdamW, cosine schedule, ~30 epochs.
  Export with `detector.ppdm` (BatchNorm folded) plus test vectors; the engine's parity test
  (`model_parity`) must match PyTorch within 1e-4. ONNX export stays available for inspection.
- **Reproducibility**: the generator seed, the training config and the exported model's SHA-256
  are recorded in `tools/detector-training/MODEL_CARD.md`; the model file lives in the repo
  (≈ 2 MB, via Git LFS if the repo policy prefers).
- **Evaluation**: a fixed held-out synthetic hard set (generated with a separate seed, committed
  as a manifest + seed, rendered on demand by the scansuite) and the owner's local phone photos
  (corners marked with the existing `scripts/scanref/annotate.py`, kept locally).

## R5 — Shipping and loading the model

- **Decision**: the ONNX file is a Compose Multiplatform resource of `:composeApp`
  (`composeResources/files/detector.ppdm`; `:core:scanengine` has no Compose resources). A
  `DetectorModelSource` interface in `:core:scanengine` commonMain (`suspend fun bytes(): ByteArray?`)
  is implemented in `:composeApp` with `Res.readBytes` and bound in Koin; the scansuite reads the
  same file from the repository. The engine receives the bytes once through a new C API call
  `pp_engine_load_detector(engine, bytes, len)`, keeps one `cv::dnn::Net` per engine and
  serialises forward passes with a mutex (preview detection and full-photo analysis may run on
  different threads).
- **Rationale**: one resource path for both platforms; no file-system paths in the C API.
- **Alternatives considered**: Android assets + iOS bundle path (two code paths); embedding the
  weights in the native library (rebuild needed for every model update).

## R6 — Performance and fallback

- **Decision**: preview frames are already ≤ 800 px; the model input is the frame resized to 256×256 (aspect not kept; training renders scenes at 3:4, 4:3, 9:16 and 1:1 and resizes the same way).
  Target ≤ 25 ms per forward pass on the reference phone (≈ 60–80 MFLOPs). The ViewModel already
  drops frames while detection runs, so the outline rate is bounded by detection time.
- **Fallback**: classic detector when the model failed to load, when the capability is
  Simplified **and** a measured model pass exceeds 120 ms (measured on the first 5 frames), or
  when the model says "document present" but no valid quad can be fitted.
- **Full photo** (`pp_analyze`): the model runs on a 256×256 downscale; its quad (scaled up) seeds
  the final crop and the existing edge snapping refines it at working resolution.

## R7 — Quick scan, Android

- **Decision**: `com.google.android.gms:play-services-mlkit-document-scanner:16.0.0`,
  `GmsDocumentScannerOptions` with `SCANNER_MODE_FULL`, `RESULT_FORMAT_JPEG`, gallery import off
  (Plainpaper has its own import), page limit off. Launched with
  `ActivityResultContracts.StartIntentSenderForResult` from a `@Composable` factory (same pattern
  as `PhotoPickerFactory`). Availability: `GmsDocumentScanning.getClient(...)` +
  `ModuleInstall.getClient(context).areModulesAvailable(scanner)`; hidden when Play services is
  missing or the call fails; when the module is not yet installed, Quick scan stays visible and the
  first tap shows "Your phone is preparing its scanner" while Play services installs it.
- **Privacy**: the library's transitive `transport-backend-cct` (Google usage logging) declares
  `INTERNET` and `ACCESS_NETWORK_STATE`. The app manifest removes both with
  `tools:node="remove"`; a CI script checks the merged release manifest has no network
  permission. The scanner UI runs in the Play services process (accepted deviation, spec
  Clarifications 2026-10-24).
- **Size**: ≈ 300 KB download (the models and UI are delivered by Play services).

## R8 — Quick scan, iOS

- **Decision**: VisionKit `VNDocumentCameraViewController` presented from the key window's root
  view controller; availability `VNDocumentCameraViewController.isSupported`; the delegate
  receives `VNDocumentCameraScan`, each page via `imageOfPage(at:)` (already cropped and
  perspective-corrected), written as JPEG into the session folder. Requires
  `NSCameraUsageDescription` (already present). Fully on-device, part of the OS.

## R9 — Pages from Quick scan

- **Decision**: `CapturedPage.source` (Camera, Import, QuickScan). QuickScan pages are analysed
  with the full frame as user corners and **no automatic rotation** (new
  `pp_analyze_options.keep_orientation = 1`: corners used in the given order, axis and up/down
  votes skipped, deskew skipped), so they are never re-cropped or turned; looks, user rotation
  and mirror apply as for other pages.
- **Rationale**: the built-in scanners already crop and orient; a second pass would only risk a
  wrong crop or flip (FR-012).
