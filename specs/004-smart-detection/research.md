# Research: Smart document detection

## R1 — Where the model runs

- **Decision**: inside the C++ scan engine with OpenCV's `dnn` module (`cv::dnn::readNetFromONNX`
  from a memory buffer), CPU backend, one forward pass per detection call.
- **Rationale**: one implementation for Android, iOS and the host JVM test suite; no new runtime
  dependency on either platform. The OpenCV Android SDK 4.13 already contains
  `libopencv_dnn.a` (all ABIs) and the iOS `opencv2.xcframework` already contains the dnn
  headers and code; only the host build (`scripts/build-opencv-host.sh`, `BUILD_LIST=core,imgproc`)
  needs `dnn` added. Static linking with `--gc-sections`/`-dead_strip` keeps only what the ONNX
  importer and the used layers need.
- **Size risk**: the linked size of `dnn` (ONNX importer + protobuf + layers) is not known; the
  current `libplainpaper_scan.so` is 5.6 MB (arm64-v8a). **First task is a spike** that links
  dnn, loads a stub model and measures the per-ABI growth. Gate: engine growth + model ≤ 5 MB per
  platform (spec FR-006/SC-005).
- **Alternatives considered**: TensorFlow Lite / LiteRT (separate Android and iOS integrations,
  CocoaPods in a KMP build, two code paths); ONNX Runtime Mobile (≈ 1.5–3 MB per ABI per platform,
  separate C API wiring on both bridges); a hand-written CNN in C++ (smallest, but slow without
  per-arch SIMD work and a maintenance burden). Fallback if the spike fails the size gate:
  ONNX Runtime minimal build with only the operators the model uses.

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
  Export with `torch.onnx.export` (opset 13, static 1×3×256×256), then verify the ONNX file loads
  and matches PyTorch outputs in OpenCV's Python `cv2.dnn` (same importer as the engine).
- **Reproducibility**: the generator seed, the training config and the exported model's SHA-256
  are recorded in `tools/detector-training/MODEL_CARD.md`; the model file lives in the repo
  (≈ 2 MB, via Git LFS if the repo policy prefers).
- **Evaluation**: a fixed held-out synthetic hard set (generated with a separate seed, committed
  as a manifest + seed, rendered on demand by the scansuite) and the owner's local phone photos
  (corners marked with the existing `scripts/scanref/annotate.py`, kept locally).

## R5 — Shipping and loading the model

- **Decision**: the ONNX file is a Compose Multiplatform resource of `:composeApp`
  (`composeResources/files/detector.onnx`; `:core:scanengine` has no Compose resources). A
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

- **Decision**: preview frames are already ≤ 800 px; the model input is 256×256 (letterboxed).
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
