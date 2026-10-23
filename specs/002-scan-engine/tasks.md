# Tasks: Scan Engine

**Input**: Design documents from `/specs/002-scan-engine/`

**Prerequisites**: plan.md, spec.md, research.md (R1–R17), data-model.md, contracts/ (c-api, kotlin-api, reference-manifest, ci), quickstart.md

**Tests**: REQUIRED (constitution Principle VI). Every story lists its tests first; implementation tasks make them pass. Native stages are verified by the reference quality suite (`:tools:scansuite`), pure Kotlin decisions by `commonTest`.

**Organization**: tasks grouped by user story so each is an independently testable increment. Commit each finished artifact separately with a short `type: subject` message (no co-author trailers).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1…US5 from spec.md
- Paths are repository-relative

## Path Conventions

- Engine module: `core/scanengine/` (KMP: `src/commonMain`, `src/commonTest`, `src/jvmCommonMain`, `src/androidMain`, `src/jvmMain`, `src/iosMain`, `src/androidDeviceTest`, `src/nativeInterop/cinterop`), native C++ in `core/scanengine/native/`
- Android NDK packaging: `core/scanengine-android/`
- Quality suite: `tools/scansuite/`; reference photos: `reference-set/`; scripts: `scripts/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: modules, toolchain, dependency acquisition and conventions needed by every story.

- [ ] T001 Add versions to `gradle/libs.versions.toml`: `opencv = "4.13.0"`, `opencvAndroidSdkSha256`, `opencvIosXcframeworkSha256` (compute from the downloaded assets), `exifinterface = "1.4.2"` (latest stable on maven.google.com, 2026-10-23), library `androidx-exifinterface`, and plugin alias `kotlinJvm` (`org.jetbrains.kotlin.jvm`, version.ref kotlin)
- [ ] T002 Register modules in `settings.gradle.kts`: `include(":core:scanengine")`, `include(":core:scanengine-android")`, `include(":tools:scansuite")`; remove the reserved comment for `:core:scanengine`
- [ ] T003 [P] Create `build-logic/convention/src/main/kotlin/DownloadArchiveTask.kt` (cached download to `~/.cache/plainpaper/downloads/<name>`, SHA-256 verification, unzip into an output directory; `@CacheableTask`, inputs = url + sha, output = directory) and register it in `build-logic/convention/build.gradle.kts` sources
- [ ] T004 [P] Create `build-logic/convention/src/main/kotlin/CMakeBuildTask.kt` (runs `cmake -S <native> -B <buildDir> -G Ninja <defines>` then `cmake --build`, finds `cmake` on PATH then `$ANDROID_HOME/cmake/*/bin`, inputs = native sources + defines, output = build dir)
- [ ] T005 [P] Create `scripts/build-opencv-host.sh` (research R1): downloads `opencv-4.13.0.tar.gz` (sha256 pinned), configures with `-DBUILD_LIST=core,imgproc -DBUILD_SHARED_LIBS=OFF -DWITH_*` all off except `WITH_PTHREADS_PF`, `-DCMAKE_INSTALL_PREFIX=~/.cache/plainpaper/opencv-4.13.0-<os>-<arch>`; idempotent (skips when `OpenCVConfig.cmake` exists); `set -euo pipefail`
- [ ] T006 Create `core/scanengine-android/build.gradle.kts` (`com.android.library`, namespace `app.plainpaper.core.scanengine.native`, compileSdk/minSdk from catalog, `externalNativeBuild.cmake.path = file("../scanengine/native/CMakeLists.txt")`, `ndk.abiFilters = arm64-v8a, armeabi-v7a, x86_64`, `cmake.arguments += "-DPP_PLATFORM=android", "-DOpenCV_DIR=<download task output>/OpenCV-android-sdk/sdk/native/jni", "-DANDROID_STL=c++_static"`, `ndkVersion = "30.0.16248370"`; depends on the Android SDK `DownloadArchiveTask`) and `core/scanengine-android/src/main/AndroidManifest.xml` (empty `<manifest/>`)
- [ ] T007 Create `core/scanengine/build.gradle.kts`: `plainpaper.kmp.library`, add `jvm()` target, source set hierarchy `jvmCommonMain` ← `androidMain`, `jvmMain`; `androidMain` depends on `projects.core.scanengineAndroid` + `libs.androidx.exifinterface`; `commonMain` depends on `projects.core.domain`, `libs.kotlinx.coroutines.core`, `libs.koin.core`; `commonTest` on `projects.core.testing`; cinterop `plainpaper_scan` on both iOS targets with `defFile`, `includeDirs(native/include)`, and `compilations.getByName("main").cinterops` depending on `buildNativeIos<Target>` CMake tasks; `buildNativeHost` task wired into `jvmTest`/`jvmMain` resources (`-DPP_PLATFORM=host -DOpenCV_DIR=~/.cache/plainpaper/opencv-4.13.0-<os>-<arch>/lib/cmake/opencv4`); iOS test binaries `linkerOpts` per research R3; `withDeviceTest { instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }`
- [ ] T008 [P] Create `core/scanengine/native/CMakeLists.txt` (C++17, `-fexceptions -frtti -fvisibility=hidden`, `PP_PLATFORM` ∈ android|ios|host selects: shared lib + JNI sources for android/host, static lib without JNI for ios; `find_package(OpenCV REQUIRED COMPONENTS core imgproc)` with `OpenCV_STATIC ON`; `target_link_libraries(opencv_core opencv_imgproc)` plus `jnigraphics log` on android, `JNI::JNI` on host; `-Wall -Wextra -Werror`) and `core/scanengine/native/cmake/FindPlainpaperOpenCV.cmake` (per-platform include/lib resolution from `OpenCV_DIR` or `PP_OPENCV_XCFRAMEWORK` slice for iOS)
- [ ] T009 [P] Create `core/scanengine/native/.clang-format` (LLVM base, 4-space indent, 100 cols) and `scripts/check-native-format.sh` (`clang-format --dry-run -Werror` over `core/scanengine/native/**/*.{h,hpp,cpp}`), executable bit set
- [ ] T010 [P] Create `tools/scansuite/build.gradle.kts` (`kotlinJvm`, `kotlinSerialization`, `application { mainClass = "app.plainpaper.scansuite.MainKt" }`, `plainpaper.spotless`, `plainpaper.detekt`; deps `projects.core.scanengine`, `projects.core.domain`, `libs.kotlinx.serialization.json`, `libs.kotlinx.coroutines.core`; tests `kotlin("test")`, `kotlinx-coroutines-test`; `tasks.test` and `run` set `workingDir = rootDir`, `systemProperty("pp.native.path", …)`, and depend on `:core:scanengine:buildNativeHost`)
- [ ] T011 [P] Update `build-logic/convention/src/main/kotlin/KoverConventionPlugin.kt`: add `:core:scanengine` to the merged report; exclude classes `*.bridge.*`, `*PhotoDecoder*`, `*CapabilityProvider*`, `*Jni*`, `*Cinterop*`, `*ScanPhoto*`, `*ScanOutput*`, `*Module*`; keep minBound 80
- [ ] T012 [P] Update `build-logic/convention/src/main/kotlin/SpotlessConventionPlugin.kt` and `DetektConventionPlugin.kt` so `tools/scansuite` (plain JVM, `src/main`, `src/test`) is covered; update `config/detekt/detekt.yml` only if the suite needs a baseline exemption (prefer none)
- [ ] T013 [P] Create `scripts/scanref/requirements.txt` (`Pillow`, `matplotlib`, `numpy`) and `scripts/scanref/README.md` (owner workflow: ingest → annotate → transcripts/clean reference → competitor exports at 150 DPI; privacy rules from research R13)
- [ ] T014 Install local prerequisites and verify: `brew install cmake ninja tesseract tesseract-lang clang-format`, run `scripts/build-opencv-host.sh`, confirm `~/.cache/plainpaper/opencv-4.13.0-darwin-arm64/lib/cmake/opencv4/OpenCVConfig.cmake` exists and `tesseract --list-langs` shows `ara eng fra`; record versions in `specs/002-scan-engine/research.md` R17

**Checkpoint**: `./gradlew :core:scanengine:tasks` lists `buildNativeHost`, `buildNativeIosArm64`, `buildNativeIosSimulatorArm64`, `downloadOpencvAndroidSdk`, `downloadOpencvIosXcframework`; `./gradlew spotlessCheck detekt` passes with the empty modules.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: domain models, the C API, the Kotlin façade with its fakes, platform bridges, decoders, capability providers, Koin wiring, reference-set skeleton and the suite runner. No story can be verified without these.

### Domain models (tests first)

- [ ] T015 [P] Write `core/domain/src/commonTest/kotlin/app/plainpaper/core/domain/model/PageSizeTest.kt`: A4 = 2480×3508, Letter = 2550×3300, landscape swap, `Natural` long side 3508 with aspect clamp 0.2…5.0 and even rounding, `defaultFor("us"|"CA"|"MX"|"PH") == Letter`, `defaultFor(null|"EG"|"xx") == A4`
- [ ] T016 [P] Write `core/domain/src/commonTest/kotlin/app/plainpaper/core/domain/model/DeviceCapabilityTest.kt`: `classifyCapability` boundaries (exactly 4 GiB → Full; 4 GiB − 1 → Simplified; 32-bit → Simplified; low-RAM flag → Simplified)
- [ ] T017 [P] Create `core/domain/src/commonMain/kotlin/app/plainpaper/core/domain/model/PageSize.kt` (`enum class PageSize { A4, Letter, Natural }`, `PixelSize`, `outputPixels(detectedAspect, landscape)`, `companion defaultFor(regionCode)`; constants from data-model.md)
- [ ] T018 [P] Create `core/domain/src/commonMain/kotlin/app/plainpaper/core/domain/model/OutputStyle.kt` (`Scan, Grayscale, ColorDocument, Original` with `channels`) and `DeviceCapability.kt` (`Full, Simplified`, `classifyCapability(totalMemoryBytes, is64Bit, isLowRamDevice)`)

### Native core skeleton

- [ ] T019 Create `core/scanengine/native/include/plainpaper_scan.h` exactly as `contracts/c-api.md` (`PP_API_VERSION 1`, enums, structs, functions, `extern "C"` guards, `PP_EXPORT` visibility macro)
- [ ] T020 Create `core/scanengine/native/src/cancel.hpp` (`check_cancel(flag)` throwing `pp::Cancelled`), `timing.hpp` (`StepTimer` RAII writing `pp_step_timing`), `errors.hpp` (`pp::Error{status, detail}`, thread-local last error), `session.hpp` (`struct pp_session { cv::Mat normalizedPage; pp_analysis_info info; cv::Mat workingGray; std::vector<LineFit> lines; }`)
- [ ] T021 Create `core/scanengine/native/src/engine.cpp`: `pp_api_version`, `pp_engine_create/destroy` (sets `cv::setNumThreads` per capability at analyze), `pp_analyze` orchestrating stages behind one `try` mapping `pp::Cancelled → PP_CANCELLED`, `std::bad_alloc → PP_ERR_OUT_OF_MEMORY`, `cv::Exception → PP_ERR_INTERNAL` (detail in last error), input validation (`pixels != null`, 640 ≤ long edge ≤ 6000, stride ≥ width×4 → `PP_ERR_INVALID_INPUT`), `pp_session_info`, `pp_session_destroy`, `pp_output_free`, `pp_last_error`; stage functions declared in `stages.hpp` with stub bodies that pass the image through (replaced in the stories)
- [ ] T022 Create `core/scanengine/native/jni/plainpaper_jni.cpp`: `JNI_OnLoad` registering natives on `app/plainpaper/core/scanengine/bridge/JniScanBridge` (method table from contracts/c-api.md), Android path reads `Bitmap` via `AndroidBitmap_lockPixels` (ARGB_8888 only), host path reads an `int[]`/`byte[]` raster from `BufferedImage`; cancel flag = `jlong` to a heap `std::atomic<int32_t>`; `nativeRender` writes into a caller-provided `Bitmap`/`byte[]`; all JNI entry points catch everything and return negative statuses
- [ ] T023 Build and smoke-test the host library: `./gradlew :core:scanengine:buildNativeHost` produces `core/scanengine/build/native/host/libplainpaper_scan.{dylib|so}`; `nm` shows `pp_api_version` exported; commit `native: engine skeleton`

### Kotlin façade (tests first)

- [ ] T024 [P] Create `core/scanengine/src/commonMain/kotlin/app/plainpaper/core/scanengine/model/Models.kt`: `Point`, `Quad` (+ `isConvex`, `area`, `fullFrame(width,height)`), `AnalyzeOptions` (defaults and ranges from data-model.md: `workingLongEdge` 800…3000), `Confidence`, `CornerSource`, `OrientationConfidence`, `ProcessingStep`, `StepStatus` (`Applied`, `Skipped(reason)`, `Failed(reason)`), `SkipReason`, `StepTiming`, `AnalysisFlag`, `Outcome` sealed (`Success, Cancelled, InvalidInput(detail), TooDark, OutOfMemory, Internal(code)`), `RenderOptions`, `FitMode`, `ScanReport`, `CapabilityDetails`
- [ ] T025 [P] Create `core/scanengine/src/commonMain/kotlin/app/plainpaper/core/scanengine/bridge/NativeBridge.kt`: `interface NativeBridge` (`apiVersion()`, `engineCreate()`, `engineDestroy`, `analyze(photo, options, cancelFlag): NativeAnalyzeResult`, `sessionInfo`, `render(session, style, fit, w, h, cancelFlag, target): NativeRenderResult`, `sessionDestroy`, `CancelFlag` create/set/destroy) with `NativeStatus` enum mirroring `pp_status`; `expect fun platformNativeBridge(): NativeBridge`
- [ ] T026 [P] Create `core/scanengine/src/commonMain/kotlin/app/plainpaper/core/scanengine/ScanPhoto.kt` and `ScanOutput.kt` as `expect class` with `width`, `height` (+ `channels`, `styleUsed`, `fallbackReason`, `steps` on output) per data-model.md
- [ ] T027 [P] Create `core/testing/src/commonMain/kotlin/app/plainpaper/core/testing/FakeNativeBridge.kt` (scripted queue of statuses/results, records calls, exposes the dispatcher name it was called on, simulates cancel-flag observation) and `FakeScanEngine.kt` (scripted `AnalyzeResult`/`RenderResult`, records calls)
- [ ] T028 Write `core/scanengine/src/commonTest/kotlin/app/plainpaper/core/scanengine/DefaultScanEngineTest.kt` covering contracts/kotlin-api.md K1–K6 and K10 (dispatcher, cancellation sets flag and no result, OOM retry then failure, TooDark → Ready, user-corner validation, render after close, api-version mismatch) using `TestDispatcherProvider` + `FakeNativeBridge` + Turbine where flows are involved
- [ ] T029 [P] Write `core/scanengine/src/commonTest/kotlin/app/plainpaper/core/scanengine/decision/PageFramingTest.kt` (K7: output pixels for every page size/orientation, Natural aspect edge cases, centred margins offset calculation) and `RetryPolicyTest.kt` (halving of working edge, downgrade to Simplified, max one retry)
- [ ] T030 Create `core/scanengine/src/commonMain/kotlin/app/plainpaper/core/scanengine/decision/PageFraming.kt`, `RetryPolicy.kt`, `WorkingResolution.kt` (2000 Full / 1500 Simplified / override) to satisfy T029
- [ ] T031 Create `core/scanengine/src/commonMain/kotlin/app/plainpaper/core/scanengine/ScanEngine.kt` (interface, `AnalyzeResult`, `RenderResult`, `ScanAnalysis` as `Closeable` holding the native session with `closed` guard) and `DefaultScanEngine.kt` (`withContext(dispatchers.default)` + `suspendCancellableCoroutine`/`invokeOnCancellation` → `CancelFlag.set`, retry per `RetryPolicy`, status → `Outcome` mapping, API version check on first use) to satisfy T028
- [ ] T032 [P] Create `core/scanengine/src/commonMain/kotlin/app/plainpaper/core/scanengine/PhotoDecoder.kt` (`interface PhotoDecoder`, `DecodeResult`, `DecodeFailure`) and `CapabilityProvider.kt`
- [ ] T033 [P] Create `core/scanengine/src/commonMain/kotlin/app/plainpaper/core/scanengine/di/ScanEngineModule.kt` (`scanEngineModule`: `NativeBridge` via `platformNativeBridge()`, `CapabilityProvider`, `PhotoDecoder`, `ScanEngine` = `DefaultScanEngine`; platform `expect val scanEnginePlatformModule: Module`)
- [ ] T034 Run `./gradlew :core:scanengine:testAndroidHostTest :core:domain:testAndroidHostTest` → green; commit `scanengine: kotlin façade with fakes`

### Platform bridges, decoders, capability

- [ ] T035 [P] Create `core/scanengine/src/jvmCommonMain/kotlin/app/plainpaper/core/scanengine/bridge/JniScanBridge.kt` (`external fun`s per contracts/c-api.md, `expect fun loadNativeLibrary()`, `JniNativeBridge : NativeBridge` marshalling `pp_analysis_info` through `LongArray`/`FloatArray`)
- [ ] T036 [P] Create `core/scanengine/src/androidMain/kotlin/app/plainpaper/core/scanengine/` : `NativeLoader.android.kt` (`System.loadLibrary("plainpaper_scan")`), `ScanPhoto.android.kt` (wraps `Bitmap` ARGB_8888, converts other configs), `ScanOutput.android.kt` (`ALPHA_8` for 1 channel, `ARGB_8888` for 4), `AndroidPhotoDecoder.kt` (`ImageDecoder` on API 28+ with `setTargetSampleSize`, `BitmapFactory` + `ExifInterface` rotation on 26–27, HEIC → `UnsupportedFormat` below 28, cap 12 MP), `AndroidCapabilityProvider.kt` (`ActivityManager.MemoryInfo.totalMem`, `isLowRamDevice`, `Build.SUPPORTED_64_BIT_ABIS`), `ScanEngineModule.android.kt`
- [ ] T037 [P] Create `core/scanengine/src/jvmMain/kotlin/app/plainpaper/core/scanengine/` : `NativeLoader.jvm.kt` (`System.load(System.getProperty("pp.native.path"))`), `ScanPhoto.jvm.kt` / `ScanOutput.jvm.kt` (`BufferedImage` TYPE_INT_ARGB / TYPE_BYTE_GRAY), `ImageIoPhotoDecoder.kt` (JPEG/PNG with `setSourceSubsampling` to cap 12 MP), `HostCapabilityProvider.kt` (`Full`, overridable by `-Dpp.capability`), `ScanEngineModule.jvm.kt`
- [ ] T038 [P] Create `core/scanengine/src/nativeInterop/cinterop/plainpaper_scan.def` (`headers = plainpaper_scan.h`, `headerFilter = plainpaper_scan.h`, `package = app.plainpaper.scanengine.cinterop`, `staticLibraries = libplainpaper_scan.a`, `libraryPaths.ios_arm64 = <build dir>/native/ios-arm64`, `libraryPaths.ios_simulator_arm64 = <build dir>/native/ios-simulator-arm64`, `linkerOpts = -lc++`) and `core/scanengine/src/iosMain/kotlin/app/plainpaper/core/scanengine/` : `CinteropNativeBridge.kt` (pinned `ByteArray`/`CGDataProvider` pixels, `memScoped` structs, `AtomicInt`-backed cancel flag), `ScanPhoto.ios.kt` / `ScanOutput.ios.kt` (`CGImage` with `CGDataProviderCreateWithData` release callback calling `pp_output_free`), `IosPhotoDecoder.kt` (`CGImageSourceCreateThumbnailAtIndex` + `kCGImageSourceCreateThumbnailWithTransform`, max pixel size for 12 MP), `IosCapabilityProvider.kt` (`NSProcessInfo.processInfo.physicalMemory`, low-RAM < 3 GiB), `ScanEngineModule.ios.kt`
- [ ] T039 Wire `scanEngineModule` + platform module into `composeApp/src/commonMain/kotlin/app/plainpaper/di/AppModules.kt` and the Android/iOS platform module lists; update `iosApp/iosApp.xcodeproj/project.pbxproj` to link `$(SRCROOT)/../core/scanengine/build/opencv/ios/opencv2.xcframework`, add `OTHER_LDFLAGS = -lc++`, and frameworks `Accelerate CoreImage CoreMedia CoreVideo AVFoundation`; ensure the "Compile Kotlin Framework" phase runs `:core:scanengine:downloadOpencvIosXcframework` first
- [ ] T040 Build Android: `./gradlew :androidApp:assembleDebug` succeeds for three ABIs; `unzip -l` the APK shows `lib/<abi>/libplainpaper_scan.so`; `objdump -p` (or `llvm-readelf -l`) shows LOAD align 0x4000; commit `scanengine: android native packaging`
- [ ] T041 Push and verify CI iOS jobs (test-ios, build-ios) pass with the cinterop + xcframework link; if `staticLibraries` fails to link, apply the research R3 fallback (C++ core as an Xcode static-library target) and record the outcome in research.md R3

### Reference set and suite runner

- [ ] T042 [P] Create `reference-set/manifest.json` (`version: 1`, empty `photos` array), `reference-set/README.md` (categories, privacy rules, size budget, how CI uses it), directories `photos/`, `clean/`, `transcripts/`, `competitors/{adobe-scan,microsoft-lens,camscanner}/`, `benchmarks/` with `.gitkeep`
- [ ] T043 [P] Create `scripts/scanref/ingest.py` (args: source photo(s), `--id`, `--tags`, `--language`, `--rotate` for deliberately rotated cases; applies EXIF transpose, strips all metadata, downsizes to ≤ 12 MP, saves JPEG q85 under `reference-set/photos/<id>.jpg`, appends a manifest stub with `expectedCorners: null`) and `scripts/scanref/annotate.py` (opens a photo, four clicks TL→TR→BR→BL, writes `expectedCorners`; `--all-missing` loops over un-annotated entries)
- [ ] T044 [P] Write `tools/scansuite/src/test/kotlin/app/plainpaper/scansuite/ManifestTest.kt` (parses the example from contracts/reference-manifest.md; validation errors: duplicate id, bad id pattern, missing file, corners order/inside bounds, `transcript` without `cleanReference`, unknown competitor key, `null` corners without `no-page` tag)
- [ ] T045 Create `tools/scansuite/src/main/kotlin/app/plainpaper/scansuite/Manifest.kt` (`@Serializable` `ReferencePhoto`, `Category` enum with the 19 values, `Manifest`, `validate(root): List<String>`) to satisfy T044
- [ ] T046 [P] Write `tools/scansuite/src/test/kotlin/app/plainpaper/scansuite/metrics/GeometryMetricsTest.kt` (corner error on synthetic quads, whiteness/darkness on synthetic images, straightness on synthetic rotated lines) and create `tools/scansuite/src/main/kotlin/app/plainpaper/scansuite/metrics/{CornerError,Whiteness,TextDarkness,Straightness,Orientation}.kt`
- [ ] T047 Create `tools/scansuite/src/main/kotlin/app/plainpaper/scansuite/SuiteRunner.kt` (loads manifest, decodes each photo with `ImageIoPhotoDecoder`, runs `DefaultScanEngine` analyze + render Scan (and other styles on request), writes `build/scansuite/outputs/<id>-<style>.png`, computes `MetricRow`), `Thresholds.kt` (+ `src/main/resources/thresholds.json` from contracts), `Report.kt` (`QualityReport`, `report.json`, `report.md` table with ✅/❌), `Main.kt` (`run`: never fails)
- [ ] T048 Create `tools/scansuite/src/test/kotlin/app/plainpaper/scansuite/QualityGateTest.kt` skeleton: loads the report produced by `SuiteRunner`, asserts every non-informational threshold; when the manifest has fewer than 3 photos in a category prints a warning instead of failing that category (per contracts); `cancelLatency` test placeholder (`@Ignore` until US4)
- [ ] T048a [P] Write `tools/scansuite/src/test/kotlin/app/plainpaper/scansuite/SyntheticSetTest.kt` (generator is deterministic for a seed: same manifest and byte-identical photos on two runs; every entry has corners inside bounds, a transcript, a clean reference and tags containing `synthetic`; ≥ 24 entries covering en/fr/ar, rotations 0/90/180/270, shadow and no-shadow)
- [ ] T048b Create `tools/scansuite/src/main/kotlin/app/plainpaper/scansuite/synthetic/SyntheticSetGenerator.kt` (research R13b: `java.awt` rendering with the repository fonts, fixed text resources under `tools/scansuite/src/main/resources/synthetic/{en,fr,ar}.txt`, homography warp with known corners, shadow gradients, rotation, noise, JPEG q80; writes `build/scansuite/synthetic/` + manifest) and load it in `SuiteRunner` alongside the real manifest; extend `thresholds.json` with the `synthetic*` keys from contracts/reference-manifest.md and `QualityGateTest` to enforce them
- [ ] T049 Add three seed photos the owner provides now (one English page, one Arabic receipt, one no-page) via `scripts/scanref/ingest.py`, annotate corners, and verify `./gradlew :tools:scansuite:run` produces `build/scansuite/report.md` with three rows (metrics will be failing until the stories land); commit `reference-set: seed photos`
- [ ] T050 Add the `scan-quality` job and caches to `.github/workflows/pr.yml` exactly as `contracts/ci.md`; add `scan-quality` to the required status checks via `gh api -X PATCH repos/m04az/Plainpaper/branches/main/protection/required_status_checks`; open the PR early (draft) so every push runs the suite

**Checkpoint**: the host runs a pass-through engine end to end; CI builds Android + iOS native code and runs the (failing-threshold) suite in a draft PR.

---

## Phase 3: User Story 1 - A photo becomes a clean scanned page (Priority: P1) 🎯 MVP

**Goal**: detection, perspective, deskew, upright orientation, illumination normalisation, Scan-style rendering at true page size.

**Independent Test**: `./gradlew :tools:scansuite:test` passes SC-001 (corners), SC-002 (whiteness/darkness), FR-007 orientation and output-size checks on the reference set; outputs in `build/scansuite/outputs/` look like scans.

### Tests for User Story 1

- [ ] T051 [P] [US1] Write `core/scanengine/src/commonTest/kotlin/app/plainpaper/core/scanengine/decision/OrientationVoteTest.kt` (K8: ≥ 60% weight and ≥ 3 lines flips; fewer lines keeps with `OrientationConfidence.None`; axis ratio < 1.3 keeps portrait decision)
- [ ] T052 [P] [US1] Write `tools/scansuite/src/test/kotlin/app/plainpaper/scansuite/OutputSizeTest.kt` (every rendered output has exactly `PageSize.outputPixels` dimensions for A4, Letter, Natural; 1 channel for Scan)
- [ ] T053 [US1] Extend `reference-set/` with the owner's photos for categories `english`, `arabic`, `french`, `mixed-script`, `hard-shadow`, `rotated` (90/180/270 cases via `--rotate`), `pencil`, `stamp-signature`, `page-fills-frame`, each with corners annotated; add transcripts + clean references for ≥ 6 pages (2 per language); commit per batch

### Implementation for User Story 1

- [ ] T054 [P] [US1] Create `core/scanengine/src/commonMain/kotlin/app/plainpaper/core/scanengine/decision/OrientationVote.kt` (pure Kotlin mirror of the native vote used for reporting and tests) to satisfy T051
- [ ] T055 [US1] Implement `core/scanengine/native/src/detect.cpp` (research R5 step 2: smoothing, auto-Canny + adaptive-threshold edge map, contours → convex quads ≥ 20% area scored by area × edge support × rectangularity, Hough-pair and bright-hull fallbacks, confidence High/Low/NotFound, page-fills-frame rule → full frame with Low/Detected, NotFound full-frame fallback, user-corner bypass)
- [ ] T056 [US1] Implement `core/scanengine/native/src/geometry.cpp` (corner ordering TL/TR/BR/BL, physical aspect estimate from vanishing points with side-length fallback, `warpPerspective` from the full-resolution photo in 8 horizontal bands with cancel checks, `INTER_CUBIC`/`INTER_AREA` selection)
- [ ] T057 [US1] Implement `core/scanengine/native/src/deskew.cpp` (projection-profile variance over −5…+5° in 0.25° steps on the working grayscale; rotate when |angle| ≥ 0.3°)
- [ ] T058 [US1] Implement `core/scanengine/native/src/orient.cpp` (research R6: axis choice by profile variance ratio ≥ 1.3, line segmentation, per-line ink-mass centre vote weighted by line length, flip at ≥ 60% with ≥ 3 lines, `orientation_confidence`, apply 0/90/180/270 rotation to the page and remap corners)
- [ ] T059 [US1] Implement `core/scanengine/native/src/illumination.cpp` (morphological closing with ellipse ≈ page width / 25 + median blur background per channel, divide normalisation, white point at the 97th percentile, specular-highlight flag when > 2% of pixels are clipped in the input)
- [ ] T060 [US1] Implement `core/scanengine/native/src/render.cpp` for the Scan style and page framing (research R8/R9: Sauvola window 31, k = 0.2, R = 128, ink floor on local std-dev < 4, 3×3 median, anti-aliased composite; centred on white margins for A4/Letter, Natural sizing; `INTER_AREA` resample to output size; 1-channel output) and wire `pp_render` with `PP_STEP_RENDER`/`PP_STEP_RESAMPLE` timings
- [ ] T061 [US1] Tune on the synthetic + reference sets until `:tools:scansuite:test` passes SC-001, SC-002, SC-010 (orientation ≥ 95%, synthetic 100%) and OutputSizeTest; record parameter values as named constants in `core/scanengine/native/src/params.hpp` with a comment per constant; commit `native: detection, geometry, orientation, illumination, scan style`
- [ ] T062 [US1] Verify on the Android emulator (API 36) via a temporary `androidDeviceTest` that decodes one bundled photo and asserts a `Ready` result with `Confidence.High` (keep this test as `SmokeDeviceTest`); push and confirm CI green including `scan-quality`

**Checkpoint**: US1 delivers flatbed-looking Scan pages at true size, verified by metrics.

---

## Phase 4: User Story 2 - Choose an output style (Priority: P2)

**Goal**: Grayscale, Color document and Original styles rendered from the same analysis with identical geometry.

**Independent Test**: suite renders every style for a stamped page; outputs differ as specified, same pixel dimensions; the colour-retention check passes.

### Tests for User Story 2

- [ ] T063 [P] [US2] Write `tools/scansuite/src/test/kotlin/app/plainpaper/scansuite/StylesTest.kt` (all four styles share dimensions and page boundary; Grayscale has 1 channel with > 16 distinct levels on an ID photo; Color document keeps the hue of stamp pixels within ±15° of the input stamp hue and paper ≥ 0.97 white; Original keeps the background within 15% of the input luminance)
- [ ] T064 [P] [US2] Write `core/scanengine/src/commonTest/kotlin/app/plainpaper/core/scanengine/decision/StyleFallbackTest.kt` (inverted-document flag with Scan requested → `styleUsed = Grayscale`, `fallbackReason = InvertedDocument`; other styles unchanged)
- [ ] T065 [US2] Add `stamp-signature`, `id-card` (owner's own, fields covered) and an `inverted` reference photo to `reference-set/` with annotations

### Implementation for User Story 2

- [ ] T066 [P] [US2] Create `core/scanengine/src/commonMain/kotlin/app/plainpaper/core/scanengine/decision/StyleFallback.kt` to satisfy T064 and apply it in `DefaultScanEngine.render`
- [ ] T067 [US2] Extend `core/scanengine/native/src/render.cpp` with Grayscale (tone curve, gamma 0.9), Color document (chroma < 12 & luminance > 0.85 white point → white, saturation ×1.15, luminance from the Grayscale curve), Original (1.08× lift, 0.5-strength grey-world white balance), inverted-document detection (median L < 90 and ink detection fails) with `fallback_reason = 1`; 4-channel RGBA output for Color/Original
- [ ] T068 [US2] Make `pp_render` reuse the session's normalised page and corners (no re-detection) and add a suite assertion that rendering twice yields identical `corners`/dimensions; tune until `StylesTest` passes; commit `native: grayscale, color document, original styles`

**Checkpoint**: four styles from one analysis.

---

## Phase 5: User Story 3 - It works when the photo is hard (Priority: P3)

**Goal**: graceful detection failure with default rectangle, user corners, curl flattening on capable devices (skipped and reported otherwise), too-dark reporting, open-book and edge cases.

**Independent Test**: hard subsets (dark-room, low-contrast-background, curled, folded, no-page, open-book, flash-highlight) return results with the expected confidence/flags and no crash; curled pages straight within 0.5° on Full.

### Tests for User Story 3

- [ ] T069 [P] [US3] Write `tools/scansuite/src/test/kotlin/app/plainpaper/scansuite/HardCasesTest.kt` (no-page → `NotFound` + full-frame corners; low-contrast → corners within threshold or `Low`; dark-room → `TooDark` outcome or whiteness threshold met; curled on Full → straightness ≤ 0.5°; curled on Simplified → `Flatten` = `Skipped(Capability)`; folded → `Skipped(InsufficientLines|NoImprovement)` and legible; open-book → `Low` confidence; user corners override detection and set `CornerSource.User`)
- [ ] T070 [P] [US3] Write `core/scanengine/src/commonTest/kotlin/app/plainpaper/core/scanengine/decision/UserCornersValidationTest.kt` (non-convex, outside bounds beyond 2%, area < 5% → `InvalidInput("corners")`; valid → passed through unchanged)
- [ ] T071 [US3] Add reference photos for `curled`, `folded`, `dark-room`, `low-contrast-background`, `no-page` (≥ 3), `open-book`, `flash-highlight`, `receipt` with annotations and transcripts where text exists

### Implementation for User Story 3

- [ ] T072 [P] [US3] Create `core/scanengine/src/commonMain/kotlin/app/plainpaper/core/scanengine/decision/UserCornersValidation.kt` to satisfy T070 and call it in `DefaultScanEngine.analyze` before any native call
- [ ] T073 [US3] Implement `core/scanengine/native/src/flatten.cpp` (research R7: text-line extraction by wide closing + components, quadratic fit of ≥ 6 consistent lines, displacement field, `cv::remap`, accept only on ≥ 50% curvature reduction, skip reasons) and gate it on `capability == FULL && allow_flatten`
- [ ] T074 [US3] Implement too-dark detection in `engine.cpp` (median luminance < 0.12 after contrast stretch → continue the pipeline, return `PP_ERR_TOO_DARK` with a session), open-book handling in `detect.cpp` (two large quads → larger one, confidence Low), specular handling in `illumination.cpp` (clipped regions treated as paper white), full-frame fallback cleanup path
- [ ] T075 [US3] Tune on the hard subsets until `HardCasesTest` passes without regressing US1/US2 thresholds; commit `native: flattening and hard-photo fallbacks`

**Checkpoint**: every reference photo returns a usable result with honest confidence.

---

## Phase 6: User Story 4 - Fast, cancellable and safe on every phone (Priority: P4)

**Goal**: timing targets on reference devices, cancellation ≤ 200 ms, memory discipline, Simplified pipeline, OOM retry, zero crashes.

**Independent Test**: benchmark JSON for the mid-range phone and the low-end emulator meet SC-005/SC-006; cancel-latency test passes 50/50; memory returns to baseline.

### Tests for User Story 4

- [ ] T076 [P] [US4] Un-`@Ignore` and complete `QualityGateTest.cancelLatency` in `tools/scansuite/src/test/kotlin/app/plainpaper/scansuite/QualityGateTest.kt` (50 analyses cancelled at random delays 0…1500 ms; assert `Outcome.Cancelled` within 200 ms of cancel and no output file written)
- [ ] T077 [P] [US4] Write `tools/scansuite/src/test/kotlin/app/plainpaper/scansuite/SimplifiedPipelineTest.kt` (run the set with `capability = Simplified`: every photo still succeeds, `Flatten` skipped with `Capability`, working edge 1500, peak bytes < 70 MB; Full peak < 120 MB)
- [ ] T078 [P] [US4] Write `core/scanengine/src/androidDeviceTest/kotlin/app/plainpaper/core/scanengine/BenchmarkTest.kt` (reads photos from `context.getExternalFilesDir("bench")`, loops the set twice in Scan style, records per-photo `ScanReport` timings, native heap delta 1 s after each run via `Debug.getNativeHeapAllocatedSize`, writes `bench.json` to the same directory; asserts zero `Failed` outcomes)

### Implementation for User Story 4

- [ ] T079 [US4] Add cancel checks between every stage and inside band loops in `geometry.cpp`, `render.cpp`, `flatten.cpp`, `illumination.cpp` (`check_cancel` every band / every row chunk of 256); make all intermediates RAII `cv::Mat` so unwinding frees them; cap `cv::setNumThreads` (2 Simplified, min(4, cores) Full)
- [ ] T080 [US4] Implement the native allocator high-water mark (`peak_bytes`) via a `cv::MatAllocator` wrapper or `mallinfo`-free counting in `engine.cpp`; verify `OutOfMemory` mapping by injecting `std::bad_alloc` behind a test hook (`PP_TEST_FAIL_ALLOC_AT_STAGE` env var in host builds only)
- [ ] T081 [US4] Create `scripts/bench-android.sh` (pushes `reference-set/photos` subset of 10 via `adb push` to the app's external files dir, runs `./gradlew :core:scanengine:connectedAndroidDeviceTest --tests '*BenchmarkTest*'` (confirm the exact task name from `./gradlew :core:scanengine:tasks --all` and record it in the script), pulls `bench.json` into `reference-set/benchmarks/<model>-<date>.json`; `--low-end` creates/starts an AVD `plainpaper-lowend` with 2048 MB RAM, 2 cores, API 30 x86_64)
- [ ] T082 [US4] Run the benchmark on the owner's Android phone and on the low-end emulator; if medians exceed 2.0 s / 6.0 s, profile with the per-step timings and optimise the dominant stages (typical levers: working-edge, `INTER_LINEAR` for the analysis warp, integral-image Sauvola, single-channel paths on Simplified) until SC-005 holds; commit the JSON files
- [ ] T083 [US4] Add `iosApp/iosAppTests/ScanBenchmarkTests.swift` + test target in `iosApp/iosApp.xcodeproj/project.pbxproj` (bundles the same 10 photos, calls the engine through the ComposeApp framework, prints JSON); CI `build-ios` builds the test target (`build-for-testing`) but does not run it; note in `reference-set/benchmarks/README.md` that the iPhone numbers are captured manually
- [ ] T084 [US4] Run SC-006 soak: 200 consecutive runs on the low-end emulator via `BenchmarkTest` with `-Dpp.bench.iterations=200`; zero crashes/unhandled failures; record in `reference-set/benchmarks/`

**Checkpoint**: timing, cancellation and memory numbers are on record and gated where measurable on the host.

---

## Phase 7: User Story 5 - Quality is measured, not guessed (Priority: P5)

**Goal**: legibility scoring with Tesseract, competitor comparison, per-category reporting, CI gate demonstrated with a deliberate regression.

**Independent Test**: suite reports legibility per language, competitor "matches or beats" table, fails the PR on a deliberate regression (SC-009).

### Tests for User Story 5

- [ ] T085 [P] [US5] Write `tools/scansuite/src/test/kotlin/app/plainpaper/scansuite/TesseractTest.kt` (text normalisation: NFC, Arabic diacritics stripped, whitespace collapsed; CER on known pairs; CLI absence → `skipped` unless `PP_REQUIRE_OCR=1` → failure)
- [ ] T086 [P] [US5] Write `tools/scansuite/src/test/kotlin/app/plainpaper/scansuite/CompetitorComparisonTest.kt` (matches-or-beats rule on synthetic rows; worst relative delta; legibility compared only when both scored)

### Implementation for User Story 5

- [ ] T087 [P] [US5] Create `tools/scansuite/src/main/kotlin/app/plainpaper/scansuite/Tesseract.kt` (`ProcessBuilder("tesseract", image, "stdout", "-l", "ara+eng+fra", "--psm", "6")`, timeout 60 s, normalisation, Levenshtein CER, `LegibilityMetric` relative to the clean reference)
- [ ] T088 [P] [US5] Create `tools/scansuite/src/main/kotlin/app/plainpaper/scansuite/CompetitorComparison.kt` (downscale Plainpaper output to 150 DPI long edge for comparison; compute whiteness + legibility on competitor images; per-app summary) and extend `Report.kt` with per-category legibility and the competitor table
- [ ] T089 [US5] Owner captures competitor outputs for ≥ 15 reference photos per app (Adobe Scan, Microsoft Lens, CamScanner; gallery import, default document mode, best-quality export, resized to 150 DPI by `scripts/scanref/ingest.py --competitor <app>`) and adds them to the manifest; commit per app
- [ ] T090 [US5] Enable SC-003 and SC-008 assertions in `QualityGateTest`, run the suite, tune Scan parameters if any language subset is < 0.90 relative legibility or the competitor rate < 60%; commit
- [ ] T091 [US5] Demonstrate SC-009: push a throwaway commit disabling the white-point rescale in `illumination.cpp`; confirm `scan-quality` fails and the PR is blocked; revert; record the run URL in `specs/002-scan-engine/quickstart.md`

**Checkpoint**: metrics, competitor comparison and the CI gate are live.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T092 [P] Update `CLAUDE.md` (module map: `:core:scanengine`, `:core:scanengine-android`, `:tools:scansuite`; commands for the suite, native builds, benchmarks; prerequisites) and `README.md`
- [ ] T093 [P] Add `scripts/check-native-format.sh` to the `static-analysis` CI job and run `clang-format -i` over `core/scanengine/native/`
- [ ] T094 [P] Review Kover: `./gradlew koverXmlReport koverVerify` ≥ 80% with `:core:scanengine` included; add tests for any uncovered decision code
- [ ] T095 [P] Add `reference-set/benchmarks/README.md` and a `specs/002-scan-engine/results.md` summarising final metrics, device timings and competitor comparison (numbers only, from the generated reports)
- [ ] T096 Run the full quickstart.md checklist (sections 1–6), fix anything that fails, mark PR ready for review, ensure all six required checks are green, merge PR into `main` (squash disabled; merge commit as in 001); update memory `plainpaper-progress`

---

## Dependencies & Execution Order

- **Phase 1 → Phase 2 → stories**: Phases 1–2 are strictly sequential prerequisites (T001–T050). T041 (CI iOS link) may be in flight while stories start on the host.
- **US1 (P1)** is the MVP and depends only on Phase 2.
- **US2** depends on US1's `illumination.cpp`/`render.cpp` (T059, T060).
- **US3** depends on US1's `detect.cpp`/`geometry.cpp` (T055, T056); `flatten.cpp` is independent of US2.
- **US4** depends on US1–US3 stage code existing (cancel checks touch all stages); the benchmark harness (T078, T081) can be written in parallel with US2/US3.
- **US5** depends on US1 outputs; Tesseract/competitor code (T087, T088) can be written in parallel with US2–US4; T089 needs the owner.
- **Owner-blocked inputs**: T049, T053, T065, T071, T089 (photos, transcripts, competitor exports), T082/T084 (physical phone). Everything else proceeds without them; the synthetic set (T048a/T048b) keeps the gate meaningful meanwhile, and real-photo thresholds are enforced per category only once a category has ≥ 3 photos.

## Parallel Execution Examples

- Phase 1: T003, T004, T005, T008, T009, T010, T011, T012, T013 in parallel after T001–T002.
- Phase 2: T015–T018 together; T024–T027 together; T035–T038 together; T042–T046 together.
- US1: T051, T052 (tests) with T054; native stages T055–T060 are sequential within `native/src` but T054 and the reference-set work (T053) run alongside.
- US4: T076–T078 together; T081 alongside T079–T080.
- US5: T085–T088 together.

## Implementation Strategy

1. **MVP = Phases 1–3**: ship a Scan-style, true-size, upright page with measured corners and whiteness. Everything after is additive.
2. Keep the draft PR open from T050 so the quality suite runs on every push; thresholds for categories without photos warn instead of fail, so the gate tightens as the owner adds photos.
3. Native work is sequential per file; use the pure-Kotlin decision tests and the host suite as the inner loop (seconds), the emulator for smoke, the phone for timing.
4. Small commits: one per task or per tuned stage, message `type: subject` (`native:`, `scanengine:`, `scansuite:`, `reference-set:`, `ci:`, `specs:`).
