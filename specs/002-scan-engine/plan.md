# Implementation Plan: Scan Engine

**Branch**: `002-scan-engine` | **Date**: 2026-10-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-scan-engine/spec.md`

## Summary

Deliver `:core:scanengine`: a shared C++ (OpenCV 4.13.0 `core` + `imgproc`) pipeline that
turns one photo into a flatbed-quality page (detect corners → perspective → deskew →
orient → flatten on capable devices → normalise illumination → render Scan / Grayscale /
Color document / Original at 300 DPI A4 / Letter / Natural), exposed through a plain C API,
bridged with JNI on Android (separate `com.android.library` module for the NDK build) and
cinterop on iOS, behind a `ScanEngine` Kotlin interface with cancellable `suspend` calls,
capability detection and explicit failure outcomes. The same C++ core is built for the host
JVM so a reference-photo quality suite (`:tools:scansuite`, Tesseract-scored legibility,
competitor comparison, thresholds from the success criteria) gates every pull request in
CI. No UI, no camera, no OCR SDK, no database, no ML model in this feature.

## Technical Context

**Language/Version**: Kotlin 2.4.21 (KMP: Android, iosArm64, iosSimulatorArm64, **jvm** for
the suite); C++17 for the engine core; C for the bridge header; Python 3 (Pillow,
matplotlib) for the reference-set ingest/annotation scripts only.

**Primary Dependencies**: OpenCV 4.13.0 (`opencv_core`, `opencv_imgproc`; prebuilt
Android SDK static libs, `opencv-spm` xcframework for iOS, source build for the host:
research R1); kotlinx-coroutines 1.11.0; Koin 4.2.2 (module wiring); kotlinx-serialization
1.12.0 (manifest / report JSON); `androidx.exifinterface` (API 26-27 EXIF only, R12);
Tesseract 5.x **CLI on the host only** (R14). No new Compose or UI dependency.

**Storage**: none in the engine (callers own persistence, Feature 6). Reference set and
benchmark results are files in git (`reference-set/`, R13).

**Testing**: kotlin.test + kotlinx-coroutines-test + Turbine in `commonTest` for every
pure decision (capability, page size, orientation vote, style/fallback selection, retry
logic, result mapping) using a `FakeNativeBridge`; `:tools:scansuite` JVM tests run the real
native core on the reference set and assert thresholds; `androidDeviceTest` benchmark on a
phone; Kover ≥ 80% on `:core:scanengine` common code (bridges and decoders excluded).

**Target Platform**: Android 8.0+ (API 26; ABIs arm64-v8a, armeabi-v7a, x86_64), iOS 15+
(arm64 device, arm64 simulator); host macOS arm64 and Ubuntu 24.04 x86_64 for the suite.

**Project Type**: mobile library module + native core + developer tooling.

**Performance Goals**: 12 MP photo, Scan style: median < 2.0 s / p95 < 3.0 s on the
mid-range device, median < 6.0 s / p95 < 9.0 s on the low-end profile; cancellation
≤ 200 ms; memory back to baseline within 1 s (SC-005, SC-007).

**Constraints**: fully on-device, no network; never crash (catch native failures, retry
once at lower resolution); working resolution 2000 px (Full) / 1500 px (Simplified)
long edge; peak extra memory < 120 MB Full / < 70 MB Simplified; reference set ≤ 150 MB of
photos + ≤ 110 MB of competitor outputs in plain git; Xcode absent locally (iOS native
build and tests run in CI only).

**Scale/Scope**: one engine, 9 stages, 4 styles, 3 page sizes, ~60 reference photos in
13 categories, 3 competitor apps, 2 reference device profiles.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Evidence |
|-----------|--------|----------|
| I Privacy & Offline First | PASS | Engine has no network code; decoders and the native core read local pixels only; reference photos are metadata-stripped (R13). No new permission. |
| II Honest Product | PASS | No monetisation surface in this feature. |
| III KMP + Clean Architecture | PASS | `ScanEngine` interface, models and pure decisions in `commonMain`; platform bridges in `androidMain`/`iosMain`/`jvmMain`; `:core:domain` gains only pure models (`PageSize`, `OutputStyle`, `DeviceCapability`) so features can use them without depending on the engine. No feature module depends on another. |
| IV MVVM/UDF | N/A | No screens in this feature. |
| V Coroutines | PASS | `suspend` API on injected `DispatcherProvider.default`; cooperative cancellation to native via an atomic flag; no `GlobalScope`. |
| VI Test-First | PASS | commonTest for every pure decision before implementation; reference suite with measurable metrics gating PRs (FR-016…018); Kover gate extended to the module. |
| VII CI | PASS | New `scan-quality` job plus native builds inside the existing five jobs; required checks updated (R15). |
| VIII Accessible / low-end | PASS | Capability detection and Simplified pipeline; retry on memory pressure; graceful outcomes instead of crashes. |
| IX Simplicity | PASS with justification | New dependencies: OpenCV (what it replaces: hand-written image ops; privacy: none, offline; size: ~3-4 MB per ABI after dead-stripping core+imgproc), `androidx.exifinterface` (R12), Tesseract **host-only**. Separate `:core:scanengine-android` module is forced by the AGP 9 KMP plugin (R2). No ML runtime. Complexity Tracking below. |

Post-design re-check (after Phase 1): unchanged, PASS. The only structural additions are
the native Android module (mandated by tooling), the host `jvm()` target (needed for the
constitution's own reference-set requirement) and the `tools/scansuite` developer module.

## Project Structure

### Documentation (this feature)

```text
specs/002-scan-engine/
├── plan.md              # This file
├── research.md          # Phase 0 (R1–R17)
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── c-api.md         # plainpaper_scan.h: the C façade shared by JNI and cinterop
│   ├── kotlin-api.md    # ScanEngine interface, models, Koin module, DI qualifiers
│   ├── reference-manifest.md  # reference-set/manifest.json schema and thresholds.json
│   └── ci.md            # scan-quality job, caches, required checks, path filter
├── checklists/requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
core/domain/src/commonMain/kotlin/app/plainpaper/core/domain/model/
├── PageSize.kt                # A4 / Letter / Natural + pixel sizes + defaultFor(region)
├── OutputStyle.kt             # Scan / Grayscale / ColorDocument / Original
└── DeviceCapability.kt        # Full / Simplified + classifyCapability(...)

core/scanengine/
├── build.gradle.kts           # plainpaper.kmp.library + jvm() + cinterop + native build/download tasks
├── native/                    # shared C++ core (one CMake project for Android, iOS, host)
│   ├── CMakeLists.txt
│   ├── cmake/FindPlainpaperOpenCV.cmake   # resolves OpenCV per platform (R1)
│   ├── include/plainpaper_scan.h          # C API (contracts/c-api.md)
│   ├── src/
│   │   ├── engine.cpp             # pp_engine / pp_session lifecycle, try/catch → status
│   │   ├── detect.cpp             # page detection + confidence
│   │   ├── geometry.cpp           # corner ordering, aspect estimate, perspective warp in bands
│   │   ├── deskew.cpp             # residual tilt
│   │   ├── orient.cpp             # 0/90/180/270 vote (R6)
│   │   ├── flatten.cpp            # text-line curl correction (R7)
│   │   ├── illumination.cpp       # background estimate + divide normalisation
│   │   ├── render.cpp             # four styles, page framing, resampling (R8, R9)
│   │   ├── cancel.hpp             # cancel-flag checks, PP_CANCELLED unwinding
│   │   └── timing.hpp             # per-step timers
│   └── jni/plainpaper_jni.cpp     # JNI bridge (Android + host JVM), RegisterNatives in JNI_OnLoad
├── src/commonMain/kotlin/app/plainpaper/core/scanengine/
│   ├── ScanEngine.kt              # interface + ScanAnalysis (Closeable) + ScanOutput
│   ├── model/                     # ScanRequest, AnalyzeOptions, Confidence, Outcome, ProcessingStep, StepTiming, PipelineReport
│   ├── decision/                  # pure: OrientationVote, PageFraming, RetryPolicy, StyleFallback, WorkingResolution
│   ├── bridge/NativeBridge.kt     # expect interface the platform actuals implement (handles + raw calls)
│   ├── DefaultScanEngine.kt       # coroutine wrapper: dispatcher, cancel flag, retry, mapping
│   ├── PhotoDecoder.kt            # expect: decode(path, maxPixels) → ScanPhoto
│   └── di/ScanEngineModule.kt     # Koin module (engine, capability provider, decoder)
├── src/jvmCommonMain/kotlin/.../bridge/JniScanBridge.kt   # external funs shared by Android + JVM
├── src/androidMain/kotlin/...     # loadNativeLibrary(), AndroidPhotoDecoder, AndroidCapabilityProvider, ScanPhoto(Bitmap)
├── src/jvmMain/kotlin/...         # loadNativeLibrary(path), ImageIoPhotoDecoder, HostCapabilityProvider, ScanPhoto(BufferedImage)
├── src/iosMain/kotlin/...         # CinteropScanBridge, IosPhotoDecoder (CGImageSource), IosCapabilityProvider, ScanPhoto(CGImage)
├── src/nativeInterop/cinterop/plainpaper_scan.def
├── src/commonTest/kotlin/...      # decision tests, DefaultScanEngine tests with FakeNativeBridge
└── src/androidDeviceTest/kotlin/...  # BenchmarkTest (R16)

core/scanengine-android/           # com.android.library, externalNativeBuild → libplainpaper_scan.so
├── build.gradle.kts
└── src/main/AndroidManifest.xml

core/testing/src/commonMain/kotlin/app/plainpaper/core/testing/
├── FakeScanEngine.kt              # for Features 3/4 and this feature's tests
└── FakeNativeBridge.kt

tools/scansuite/                   # kotlin("jvm"); depends on :core:scanengine (jvm)
├── build.gradle.kts
├── src/main/kotlin/app/plainpaper/scansuite/
│   ├── Manifest.kt                # kotlinx.serialization models (contracts/reference-manifest.md)
│   ├── metrics/                   # CornerError, Whiteness, TextContrast, Legibility (tesseract), Straightness, Orientation, Timing, CancelLatency
│   ├── Tesseract.kt               # CLI runner + text normalisation + CER
│   ├── CompetitorComparison.kt
│   ├── Report.kt                  # report.md + report.json
│   └── Main.kt                    # `run` entry: full report without asserting
├── src/main/resources/thresholds.json
└── src/test/kotlin/.../QualityGateTest.kt   # asserts thresholds (the CI gate)

reference-set/                     # R13 layout; manifest.json; photos/; clean/; transcripts/; competitors/; benchmarks/
scripts/
├── build-opencv-host.sh           # source build + cache (R1)
├── scanref/ingest.py, annotate.py, requirements.txt
└── bench-android.sh               # push subset, run androidDeviceTest, save JSON (R16)

iosApp/                            # pbxproj: link opencv2.xcframework, -lc++ and system frameworks; XCTest benchmark target
.github/workflows/pr.yml           # scan-quality job + caches (contracts/ci.md)
settings.gradle.kts                # include :core:scanengine, :core:scanengine-android, :tools:scansuite
gradle/libs.versions.toml          # opencv = "4.13.0", exifinterface, download checksums
```

**Structure Decision**: the engine stays one KMP module with a nested native CMake project
so Android, iOS and host builds compile the identical sources; the Android NDK packaging is
split into `:core:scanengine-android` only because the AGP 9 KMP library plugin cannot run
`externalNativeBuild` (R2). The quality suite is a plain JVM module under `tools/` because
it is developer tooling, not app code, and must never ship.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Extra Gradle module `:core:scanengine-android` | AGP 9 KMP library plugin has no NDK/CMake support; Google's documented workaround is a separate `com.android.library` module | Kotlin/Native `androidNative*` targets would add a second native runtime on Android and much slower builds |
| `jvm()` target + `:tools:scansuite` | Constitution VI requires scan-engine changes to be verified against reference photos with metrics on every PR; only a host build can run on the Ubuntu runner | Emulator-based Android instrumented run in CI: 10+ minutes per PR, flaky, and still not the Linux-native path |
| Three OpenCV acquisition paths (Android SDK zip, iOS xcframework, host source build) | No single upstream artifact covers Android static libs, an arm64-simulator slice and a Linux host at the same version | Building OpenCV from source for every platform in CI: 30-60 min per platform per cache miss |
| Python scripts for ingest/annotation | One-off owner tooling for photos (EXIF bake, metadata strip, corner clicks); Pillow/matplotlib make this ~150 lines | Writing a Compose desktop annotator would add a desktop target to the project for a developer-only tool |
