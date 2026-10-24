# Implementation Plan: Smart document detection

**Branch**: `004-smart-detection` | **Date**: 2026-10-24 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/004-smart-detection/spec.md`

## Summary

Make page finding reliable in cluttered scenes in two ways. (1) A small document-segmentation
model, trained by the project on synthetic composites, runs inside the C++ scan engine with its
own small inference code on OpenCV core (research R1) for both live preview frames and the full photo; its mask is fitted to a
quad by the existing geometry code, and the classic detector remains the fallback. (2) Quick scan
opens the phone's built-in scanner (ML Kit document scanner on Android, VisionKit on iOS) and adds
the returned, already-cropped pages to the capture session without re-cropping.

## Technical Context

**Language/Version**: Kotlin 2.4.21 (KMP), C++17 (scan engine), Python 3.12 (training tool only)

**Primary Dependencies**: existing (Compose MP, Koin, CameraX, OpenCV 4.13 core/imgproc); new:
`play-services-mlkit-document-scanner` 16.0.0 (Android), VisionKit (iOS system framework);
training only: PyTorch, numpy, opencv-python, onnx (not shipped)

**Storage**: model file as an app resource; session files as in Feature 003 (`session.json` gains
`source`)

**Testing**: kotlin.test + coroutines-test + Turbine (commonTest); scansuite host tests for
detection quality (new cluttered-scene set + metrics); Python unit tests for the generator;
Android device test for Quick scan wiring is manual (system UI)

**Target Platform**: Android 8+ (minSdk 26), iOS 15+

**Project Type**: mobile app (KMP) + native engine + offline training tool

**Performance Goals**: model forward pass ≤ 25 ms on the reference phone; outline ≥ 10 Hz
(mid-range), ≥ 5 Hz (low-end); full-photo detection adds ≤ 60 ms to `pp_analyze`

**Constraints**: no network permission; engine growth + model ≤ 5 MB per platform; deterministic
results on host (scansuite gate); classic fallback always available

**Scale/Scope**: 1 model, 1 new C API call + 1 option, 1 new platform service (DocumentScanner),
camera/home UI entry points, training tool

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Privacy & Offline First | **Pass with accepted deviation** | Model runs on device; no data leaves. Quick scan runs the phone maker's scanner in its own process (Google Play services / iOS); Plainpaper gets only page images and declares no network permission (library logging permissions removed, CI-checked). Owner accepted, spec Clarifications 2026-10-24. |
| II. Honest Product | Pass | Free for everyone; no upsell. |
| III. KMP + Clean Architecture | Pass | `DocumentScanner` interface in `:core:capture` commonMain, actuals per platform; model source interface in `:core:scanengine`; features depend on interfaces. |
| IV. MVVM + UDF | Pass | Quick scan actions/effects in Home and Camera ViewModels. |
| V. Coroutines | Pass | Model load and inference off main; cancellable via the existing cancel flag. |
| VI. Test-First | Pass | Generator, gate/session/ViewModel changes tested first; detection quality measured by the scansuite on a new cluttered set; no regression on existing sets. |
| VII. CI | Pass | New CI step: merged-manifest permission check; scansuite runs with the model. |
| VIII. Accessible, low-end | Pass | Fallback on slow devices; EN + AR strings; Quick scan labelled. |
| IX. Simplicity | Pass with justification | Two new dependencies justified below (Complexity Tracking). |

## Project Structure

### Documentation (this feature)

```text
specs/004-smart-detection/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── c-api-detector.md
│   ├── kotlin-api.md
│   └── ui.md
└── tasks.md            # /speckit-tasks
```

### Source Code (repository root)

```text
core/scanengine/native/
├── include/plainpaper_scan.h        # + pp_engine_load_detector, pp_analyze_options.keep_orientation
├── src/model.cpp, model.hpp         # .ppdm loader, inference, mask → quad
├── tests/model_parity.cpp           # host parity vs PyTorch vectors
├── src/detect.cpp                   # model path first, classic fallback
└── CMakeLists.txt                   # model.cpp, PP_BUILD_TESTS
core/scanengine/src/commonMain/.../  # DetectorModelSource, AnalyzeOptions.keepOrientation, engine wiring
core/scanengine/src/{jvmCommonMain,androidMain,iosMain}/.../bridge/  # new calls
core/capture/src/commonMain/.../scanner/DocumentScanner.kt          # interface + factory
core/capture/src/androidMain/.../scanner/MlKitDocumentScanner.kt
core/capture/src/iosMain/.../scanner/VisionKitDocumentScanner.kt
core/domain/.../model/capture/CapturedPage.kt                       # + source
core/data/.../SessionIndex                                          # persist source
feature/capture/.../quickscan/                                      # QuickScan flow (ViewModel + route)
feature/home/...                                                    # Quick scan entry
composeApp/src/commonMain/composeResources/files/detector.ppdm
composeApp/src/commonMain/.../DetectorModelSource impl + Koin
androidApp/src/main/AndroidManifest.xml                             # tools:node="remove" network perms
scripts/check-no-network-permission.sh                              # CI
tools/detector-training/                                            # generator, train, export, eval (Python)
tools/scansuite/.../cluttered/                                      # cluttered-scene set + metrics + gate
```

**Structure Decision**: extend existing modules; the training tool is a standalone Python project
under `tools/` (not part of the Gradle build). No new Gradle module.

## Phases

1. **Spike (size gate)**: link `opencv_dnn`, load a stub ONNX model from memory on host and
   Android, measure per-ABI growth. Stop and switch to ONNX Runtime minimal (research R1) if
   growth + 2 MB model > 5 MB.
2. **Training tool**: generator (documents, backgrounds, hands, cables, photometry, negatives),
   training, export, OpenCV-Python parity check, model card.
3. **Engine**: model path in `pp_detect`/`pp_analyze`, fallback rules, `keep_orientation`,
   `pp_engine_load_detector`; scansuite cluttered set and gate.
4. **App wiring**: model source, engine load at startup, capability fallback.
5. **Quick scan**: interface + Android/iOS actuals, session `source`, Home/Camera entry points,
   manifest permission removal + CI check.
6. **Phone verification** against SC-001..SC-006.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Own CNN inference in the engine (~300 lines) | Runs the model on both platforms and the host suite with +0.1 MB | OpenCV `dnn` measured +4.25 MB (over the 5 MB gate with the model); TFLite/ONNX Runtime add a runtime per platform |
| ML Kit document scanner (Play services) | Owner asked for Quick scan with the phone's own scanner | Building our own full scanner UI is this feature's part (1); Quick scan is the platform option by definition |
| Python training tool in the repo | The model must be ours and reproducible | Third-party weights have no clear licence (research R2) |
