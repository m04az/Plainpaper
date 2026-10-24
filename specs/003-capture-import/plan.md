# Implementation Plan: Capture & Import

**Branch**: `003-capture-import` | **Date**: 2026-10-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/003-capture-import/spec.md`

## Summary

Deliver the two ways pages enter Plainpaper: a camera screen (live page outline from a new
detect-only engine entry, steadiness-gated auto-capture, manual shutter, flash and Auto
toggles, Document / ID card / Receipt modes, page counter, Done) and a gallery import flow
(system pickers, free limit of 10 with a Pro explanation, progress screen with per-photo
tiles, cancel with keep/discard). Every photo is processed in the background by the scan
engine (Feature 2) into a Scan-style page at the region's page size (Natural for receipts
and ID cards) and stored, with its original, in a file-backed capture session that survives
backgrounding and process death and is handed to review (Feature 4; a thumbnail hand-off
screen stands in until then). Camera permission is requested only on first camera use with
an explanation; denial leads to import. Platform camera, picker, permission, file and region
services live in a new `:core:capture` module behind commonMain interfaces; all decisions
(auto-capture gate, free limit, batch progress, session ordering and recovery, permission
state, mode tagging) are pure Kotlin with commonTest.

## Technical Context

**Language/Version**: Kotlin 2.4.21 (KMP: Android, iosArm64, iosSimulatorArm64); C++17 for
the `pp_detect` addition to the engine core; Compose Multiplatform 1.12.2.

**Primary Dependencies**: CameraX 1.6.2 (`camera-core`, `camera-camera2`,
`camera-lifecycle`, `camera-compose`; R1); AVFoundation + PhotosUI through Kotlin/Native
platform libraries (no artifact; R2, R6); `androidx.activity:activity-compose` 1.13.0
(already present; photo picker + permission contracts); Okio 3.18.2 and kotlinx
serialization 1.12.0 (already present; session files, R7); Koin 4.2.2; existing
`:core:scanengine`. No new SDK that leaves the device.

**Storage**: app-private files per session (`capture/<sessionId>/…` + `session.json`, R7);
capture settings (flash, Auto, mode) in the existing Preferences DataStore. No database.

**Testing**: kotlin.test + kotlinx-coroutines-test + Turbine in commonTest for every
decision and ViewModel (`FakeCameraController`, `FakePhotoPicker`, `FakeCameraPermission`,
`FakeCaptureSessionRepository`, `FakeScanEngine` in `:core:testing`); `:tools:scansuite`
test for `pp_detect` accuracy/timing on the synthetic set; `androidDeviceTest`: CameraX
smoke test on the emulator's virtual-scene camera (R13) and Compose semantics tests for
SC-010 (R12); iOS compiled by CI (`test-ios`, `build-ios`).

**Target Platform**: Android 8.0+ (API 26), iOS 15+; portrait camera.

**Project Type**: mobile feature (two screens + flows) + platform service module + a small
native engine addition.

**Performance Goals**: outline ≥ 15 updates/s with < 150 ms lag (mid-range) and ≥ 6/s
(low-end) → `pp_detect` < 40 ms / < 120 ms on a 640 px frame; preview visible < 1.5 s after
tapping Scan; auto-capture within 1 s of steadiness; 10-photo import < 25 s mid-range /
< 60 s low-end; cancel < 1 s (SC-001…SC-006).

**Constraints**: on-device only, no network, only the CAMERA permission added; no page lost
across background/foreground or process death; low-end devices: one page processed at a
time, longer steady window, never a crash; Xcode absent locally (iOS verified by CI).

**Scale/Scope**: 2 screens + 2 overlays (permission explanation/denial, Pro limit sheet) +
hand-off placeholder; sessions of up to ~50 pages; imports of up to 100 photos (Pro).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Evidence |
|-----------|--------|----------|
| I Privacy & Offline First | PASS | Camera frames and photos stay in process/app-private files; system pickers run out of process; only CAMERA is added (`NSCameraUsageDescription`, `android.permission.CAMERA`); no network, no analytics (FR-022). |
| II Honest Product | PASS | The free limit is explained before conversion and never drops photos silently; the Pro sheet appears only when the person exceeds the limit (FR-013, SC-009). Free scans stay unlimited. |
| III KMP + Clean Architecture | PASS | Session models + repository interface in `:core:domain`; file implementation in `:core:data`; platform services behind interfaces in `:core:capture` (androidMain/iosMain actuals); `:feature:capture` depends on domain, designsystem, capture, scanengine only; no feature-to-feature dependency (Home navigates by route). |
| IV MVVM/UDF | PASS | `CameraViewModel`, `ImportViewModel`, `HandoffViewModel`: one `UiState` `StateFlow` each, sealed `Action`, effects via `Channel`; composables stateless; `CameraPreview` is a platform surface receiving a controller, no logic. |
| V Coroutines | PASS | `PageProcessor` in a supervised app scope with injected dispatchers; camera frames as a `Flow<PreviewFrame>`; cancellation per page/session; no `GlobalScope`. |
| VI Test-First | PASS | commonTest for the auto-capture gate, free-limit rule, batch progress/cancel, session ordering/recovery, permission state machine, mode tagging, page-size resolution, every ViewModel; `pp_detect` gated in `:tools:scansuite`; Kover gate extended to `:feature:capture` and `:core:capture` common code. |
| VII CI | PASS | Existing six jobs cover the new modules (unit tests both platforms, Kover, Android APK, iOS framework/app build); `scan-quality` gains a detect check; device tests build in CI, run locally (R13). |
| VIII Accessible / low-end | PASS | Every control labelled, 48 dp targets, RTL mirroring, EN+AR strings (FR-021); Simplified devices get a longer steady window, lower analysis rate and single-page processing; permission denial never blocks import. |
| IX Simplicity | PASS with justification | New dependency: CameraX (replaces hand-written Camera2 handling; on-device; ≈ 1 MB). No Room, no WorkManager, no image-loading or permission library (R7, R8, R9, R11). New native entry `pp_detect` reuses `detectPage`. Screenshot tooling deferred again (R12). |

Post-design re-check (after Phase 1): unchanged, PASS. The only structural additions are the
two new modules (`:core:capture`, `:feature:capture`) and one C function.

## Project Structure

### Documentation (this feature)

```text
specs/003-capture-import/
├── plan.md              # This file
├── research.md          # Phase 0 (R1–R14)
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── kotlin-api.md    # :core:capture interfaces, domain models/repository, feature routes, Koin
│   ├── c-api-detect.md  # pp_detect addition to plainpaper_scan.h
│   ├── storage.md       # session folder layout and session.json schema
│   └── ui.md            # screen states, strings, accessibility and RTL rules
├── checklists/requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
core/domain/src/commonMain/kotlin/app/plainpaper/core/domain/
├── model/capture/                  # CaptureSession, CapturedPage, PageState, CaptureMode, PageTag, SessionSource
├── model/ProStatus.kt
├── repository/CaptureSessionRepository.kt
├── repository/ProStatusRepository.kt
└── usecase/capture/                # ObserveActiveSessionUseCase, StartSessionUseCase, AddPagesUseCase, FinishSessionUseCase, DiscardSessionUseCase, RecoverSessionUseCase, ImportLimitUseCase

core/data/src/commonMain/kotlin/app/plainpaper/core/data/
├── capture/FileCaptureSessionRepository.kt   # Okio files + session.json (R7)
├── capture/SessionIndex.kt                   # serialisable schema (contracts/storage.md)
├── capture/CaptureSettingsStore.kt           # flash/auto/mode in DataStore
├── pro/FreeOnlyProStatusRepository.kt
└── di/dataModule.kt                          # + CAPTURE_ROOT_QUALIFIER (platform provides the path)

core/capture/                                  # NEW: plainpaper.kmp.compose + library (expect/actual)
├── build.gradle.kts
└── src/
    ├── commonMain/kotlin/app/plainpaper/core/capture/
    │   ├── camera/CameraController.kt        # start/stop, frames Flow, capture(), flash, state
    │   ├── camera/CameraPreview.kt           # expect @Composable
    │   ├── camera/PreviewFrame.kt            # width/height/stride/format + pinned pixel access
    │   ├── permission/CameraPermission.kt    # states + expect rememberCameraPermission()
    │   ├── picker/PhotoPicker.kt             # expect rememberPhotoPicker(maxItems)
    │   ├── files/ImageFiles.kt               # writeJpeg(ScanOutput), loadThumbnail, copyOriginal
    │   ├── background/BackgroundTask.kt      # keepAlive { }
    │   ├── region/RegionProvider.kt
    │   └── di/CaptureModule.kt
    ├── androidMain/kotlin/...                # CameraXController, CameraXPreview (CameraXViewfinder), AndroidCameraPermission, AndroidPhotoPicker, AndroidImageFiles, AndroidRegionProvider
    ├── iosMain/kotlin/...                    # AvCameraController, AvCameraPreview (UIKitView), IosCameraPermission, PhPhotoPicker, IosImageFiles, IosBackgroundTask, IosRegionProvider
    ├── commonTest/kotlin/...                 # PreviewFrame geometry, region → page size
    └── androidDeviceTest/kotlin/...          # CameraSmokeTest (R13)

core/scanengine/
├── native/include/plainpaper_scan.h          # + pp_detect, pp_detect_info, PP_FRAME_BGRA
├── native/src/engine.cpp                     # pp_detect implementation (reuses detectPage)
├── native/jni/plainpaper_jni.cpp             # nativeDetect
├── src/commonMain/.../ScanEngine.kt          # + detect(frame): DetectResult
├── src/commonMain/.../bridge/NativeBridge.kt # + detect
├── src/{jvmCommonMain,androidMain,jvmMain,iosMain}/...   # bridge actuals
└── src/commonTest/...                        # DefaultScanEngine.detect tests

core/testing/src/commonMain/kotlin/app/plainpaper/core/testing/
├── FakeCameraController.kt, FakeCameraPermission.kt, FakePhotoPicker.kt
├── FakeCaptureSessionRepository.kt, FakeProStatusRepository.kt, FakeImageFiles.kt
└── FakeScanEngine.kt                         # + detect scripting

core/designsystem/src/commonMain/kotlin/app/plainpaper/core/designsystem/component/
├── PpSegmentedControl.kt, PpHintPill.kt, PpShutterButton.kt, PpPageCounter.kt, PpProgressTile.kt
└── PpButtons.kt                              # + dark glass variant

feature/capture/                               # NEW: plainpaper.kmp.feature (+ :core:capture, :core:scanengine)
├── build.gradle.kts
└── src/
    ├── commonMain/kotlin/app/plainpaper/feature/capture/
    │   ├── camera/{CameraViewModel,CameraUiState,CameraAction,CameraEffect,CameraScreen}.kt
    │   ├── camera/AutoCaptureGate.kt         # R5
    │   ├── camera/ModeSequencer.kt           # ID front/back tagging
    │   ├── permission/PermissionScreens.kt   # explanation + denial
    │   ├── import/{ImportViewModel,ImportUiState,ImportAction,ImportEffect,ImportScreen}.kt
    │   ├── import/ImportLimit.kt             # free-limit rule + Pro sheet state
    │   ├── handoff/{HandoffViewModel,HandoffScreen}.kt   # placeholder until Feature 4
    │   ├── processing/PageProcessor.kt       # R8
    │   ├── CaptureRoutes.kt                  # Camera, Import(uris), Handoff(sessionId) as @Serializable routes
    │   └── di/CaptureFeatureModule.kt
    ├── commonMain/composeResources/values{,-ar}/strings.xml
    ├── commonTest/kotlin/...                 # gate, sequencer, limit, processor, ViewModels (Turbine)
    └── androidDeviceTest/kotlin/...          # semantics/RTL tests (R12)

composeApp/src/commonMain/kotlin/app/plainpaper/navigation/
├── AppRoutes.kt                              # ScanPlaceholder/ImportPlaceholder removed; capture routes
└── AppNavHost.kt                             # Home → Camera / Import; recovery prompt on Home
composeApp/src/{androidMain,iosMain}/.../di/  # CAPTURE_ROOT_QUALIFIER path
androidApp/src/main/AndroidManifest.xml       # CAMERA permission, camera feature not required, photo-picker backport meta-data
iosApp/iosApp/Info.plist (+ InfoPlist.strings en/ar)   # NSCameraUsageDescription
scripts/camera-smoke-android.sh               # AVD with virtual-scene camera + device test
.github/workflows/pr.yml                      # scan-quality path filter + assemble device test APKs
settings.gradle.kts                           # include :core:capture, :feature:capture
gradle/libs.versions.toml                     # camerax = "1.6.2"
```

**Structure Decision**: platform capture services are isolated in `:core:capture` so the
feature module stays commonMain-only and host-testable; session models sit in `:core:domain`
and their file store in `:core:data` because Feature 4 (review) and Feature 6 (library)
consume the same session. The processor is feature-internal but app-scoped (Koin `single`)
so processing continues across screens and the hand-off.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Two new modules (`:core:capture`, `:feature:capture`) | Constitution III requires platform code behind commonMain interfaces and feature modules per feature | Putting camera actuals into `:feature:capture` would make the feature non-host-testable and let Feature 4 depend on camera code |
| New C entry `pp_detect` in the engine | 15 fps outline needs detection without warp/render | Running `pp_analyze` per frame is 5–10× too slow; a Kotlin port would duplicate detection logic |
| App-scoped `PageProcessor` instead of ViewModel-scoped work | Pages must keep processing across the camera → hand-off navigation and when the app is backgrounded | ViewModel-scoped jobs die with the screen and would lose in-flight pages |
