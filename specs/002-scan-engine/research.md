# Research: Scan Engine (002)

**Date**: 2026-10-23 | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Each entry: Decision, Rationale, Alternatives considered. Versions were checked against the
upstream release pages on 2026-10-23.

## R1. OpenCV version and packaging per platform

**Decision**: OpenCV **4.13.0** on every platform, consumed as prebuilt static libraries:

| Platform | Source | How it is obtained |
|----------|--------|--------------------|
| Android | Official `opencv-4.13.0-android-sdk.zip` (GitHub release asset, 318 MB) | Gradle task downloads once, verifies SHA-256, unzips to `core/scanengine/build/opencv/android/`; CMake uses `sdk/native/jni` with `OpenCV_STATIC=ON` and links only `opencv_core`, `opencv_imgproc` (+ 3rdparty `tegra_hal`/`kleidicv`/`cpufeatures`/`ittnotify` as the config requires) |
| iOS | `opencv2.xcframework.zip` from the `yeatse/opencv-spm` 4.13.0 release (built from the official sources with `build_xcframework.py`; contains `ios-arm64` and `ios-arm64_x86_64-simulator` slices) | Same download task into `core/scanengine/build/opencv/ios/`; CMake (`CMAKE_SYSTEM_NAME=iOS`) compiles the C++ core against its headers; the Xcode app links the xcframework |
| Host (macOS / Ubuntu for the quality suite) | Built from the 4.13.0 source tarball by `scripts/build-opencv-host.sh` with `BUILD_LIST=core,imgproc`, static, no GUI/video/codecs | Output cached under `~/.cache/plainpaper/opencv-4.13.0-<os>-<arch>/`; CI caches the directory with `actions/cache` keyed on version + script hash |

**Rationale**: the official 4.14.0 iOS asset is a fat `opencv2.framework` without an
arm64-simulator slice, which cannot link on the Apple-silicon simulator the CI and the
project already standardise on (`EXCLUDED_ARCHS[sdk=iphonesimulator*] = x86_64`).
`opencv-spm` publishes an xcframework built from the unmodified OpenCV sources (exceptions
and RTTI on) for 4.12.0, 4.13.0 and 5.0.0 but not yet 4.14.0. One version everywhere keeps
the host quality suite representative of the phones. 4.13.0 (2025-12-31) is recent, and the
feature only needs `core` + `imgproc`, whose APIs are stable across 4.x.

**Alternatives considered**:
- *OpenCV 5.0.0* (2026-06-06): new major with renamed modules and a C++17 requirement;
  its Android SDK needed a separate "16kb-page-fix" asset. Too fresh for the core of the
  product; revisit when 5.x has a patch release.
- *opencv-mobile* (nihui, 4.14.0 packages, 21 MB Android / 90 MB Apple): attractive size,
  but it disables C++ exceptions via patches. The engine relies on catching `cv::Exception`
  and `std::bad_alloc` to honour FR-013/FR-014 ("never crash"), so it is unsuitable.
- *Maven Central `org.opencv:opencv` AAR*: ships the Java bindings plus a shared
  `libopencv_java4.so` (~20 MB per ABI) and no static libs; forum reports show CMake
  `find_package` problems with its prefab layout. Rejected on size and reliability.
- *Build OpenCV for Android/iOS from source in CI*: 30-60 minutes per platform, only
  partly mitigated by caching. Kept as the fallback if a prebuilt is ever unavailable.
- *Ubuntu `libopencv-dev` for the host*: Ubuntu 24.04 ships 4.6.0, a different version from
  the phones. Rejected to keep metrics comparable.

## R2. Where native code is built on Android (AGP 9 KMP library plugin)

**Decision**: a separate `com.android.library` module `:core:scanengine-android`
(`core/scanengine-android/`) owns `externalNativeBuild { cmake { path = "../scanengine/native/CMakeLists.txt" } }`
and produces `libplainpaper_scan.so` for `arm64-v8a`, `armeabi-v7a` and `x86_64`.
`:core:scanengine` depends on it from `androidMain`. The Kotlin JNI bindings
(`external fun`) live in `:core:scanengine` and are registered from `JNI_OnLoad` by class
name, so the native module contains no Kotlin.

**Rationale**: the Android KMP library plugin documentation states that native build
support via `externalNativeBuild` is not available and recommends exactly this split.
NDK r30 (installed locally; CI uses the SDK manager) links shared objects 16 KB page-aligned
by default, which satisfies Google Play's requirement for targetSdk 35+.

**Alternatives considered**: Kotlin/Native `androidNativeArm64` targets with cinterop
(the other documented option): it would compile the engine wrapper with Kotlin/Native on
Android, adding a second runtime and a much slower build, for no benefit over JNI.

## R3. iOS bridging: C API + cinterop, static library inside the klib

**Decision**: the C++ core exposes a plain C header `plainpaper_scan.h` (`extern "C"`,
opaque handles, POD structs). `core/scanengine/src/nativeInterop/cinterop/plainpaper_scan.def`
lists that header and includes the CMake-built `libplainpaper_scan.a` through
`staticLibraries` / `libraryPaths` (per target: `ios_arm64`, `ios_simulator_arm64`).
Gradle tasks `buildNativeIosArm64` / `buildNativeIosSimulatorArm64` run CMake with
`-DCMAKE_SYSTEM_NAME=iOS -DCMAKE_OSX_SYSROOT=<iphoneos|iphonesimulator> -DCMAKE_OSX_ARCHITECTURES=arm64`
before the cinterop task. Test binaries (`iosSimulatorArm64Test`) get
`linkerOpts("-F<xcframework slice>", "-framework", "opencv2", "-framework", "Accelerate",
"-framework", "CoreImage", "-framework", "CoreMedia", "-framework", "CoreVideo",
"-framework", "AVFoundation", "-lc++")` from the build script. The Xcode app target links
`opencv2.xcframework` (path under `core/scanengine/build/opencv/ios/`, produced by the
download task that the existing "Compile Kotlin Framework" run-script phase triggers) and
adds `-lc++` plus the same system frameworks to `OTHER_LDFLAGS`.

**Rationale**: Kotlin/Native interops with C, not C++, so a C façade is required anyway;
it is also what JNI calls. Including the static library in the klib means the ComposeApp
static framework carries the engine's object code, and only OpenCV itself has to be linked
by Xcode. The framework list is the one `opencv-spm` declares as required by the
xcframework. `staticLibraries`/`libraryPaths` are marked Experimental in the Kotlin docs
but have been stable in practice since 1.3; the fallback (if a link problem appears in CI)
is to compile the C++ core as a static-library target inside `iosApp.xcodeproj` and keep
cinterop header-only, which changes no Kotlin code.

**Alternatives considered**: Objective-C++ wrapper consumed through cinterop (adds an
Objective-C layer with no benefit since the API is plain C); dynamic framework output for
ComposeApp (001 decided static).

## R4. Host (JVM) build of the same engine for the quality suite

**Decision**: `:core:scanengine` adds a `jvm()` target. The JNI bridge source
(`native/jni/plainpaper_jni.cpp`) is shared by Android and the host; the Kotlin `external`
declarations live in an intermediate source set `jvmCommonMain` (parents of `androidMain`
and `jvmMain`), with an `expect fun loadNativeLibrary()` whose Android actual calls
`System.loadLibrary("plainpaper_scan")` and whose JVM actual calls `System.load(path)`
on the CMake output under `core/scanengine/build/native/host/`. Gradle task
`buildNativeHost` runs CMake with the host preset (needs `cmake` + `ninja` from Homebrew
locally, preinstalled on the Ubuntu runner) against the host OpenCV from R1.

**Rationale**: the quality suite must execute the exact C++ pipeline that ships, on a
Linux runner, on every pull request. A JVM target makes the suite ordinary Kotlin code
using the same `ScanEngine` interface as the app.

**Alternatives considered**: a Python harness calling the C library via ctypes (second
language, duplicated models); OpenCV Java (`org.openpnp:opencv`) with a Kotlin
re-implementation of the pipeline (two pipelines to keep in sync, defeats the purpose).

## R5. Pipeline stages (classical, no ML)

**Decision** (all at a working resolution with long edge 2000 px for Full capability and
1500 px for Simplified; the final warp samples the full-resolution photo):

1. **Normalise input**: RGBA → BGR `cv::Mat`; reject if smaller than 640 px on the long
   edge (`INVALID_INPUT`). Measure median luminance; below 0.12 of full scale after
   contrast stretch → `TOO_DARK` outcome (still returns the cleaned frame).
2. **Page detection**: grayscale → bilateral/Gaussian smoothing → Canny with automatic
   thresholds (median-based) plus an adaptive-threshold edge map (handles low contrast
   desks) → dilate → `findContours` → `approxPolyDP` → keep convex quadrilaterals with area
   ≥ 20% of the frame, score by area × edge support × rectangularity; two more candidate
   sources when no quad passes: Hough lines clustered into two near-orthogonal pairs, and
   the convex hull of the bright connected region (`GrabCut`-free). Confidence: **high**
   when the best quad has edge support ≥ 0.8 on all four sides and area ≥ 30%; **low**
   when a quad was found below that; **not found** otherwise → full frame rectangle.
   Page-fills-frame rule: when no quad passes but a bright, low-texture region (paper
   statistics: luminance > 0.6, local std-dev < 8) covers ≥ 95% of the frame, the corners are
   the full frame with confidence **low** and source *Detected*, not *NotFound*.
   User-supplied corners bypass detection.
3. **Perspective correction**: order corners, estimate the physical aspect ratio of the
   quadrilateral (Zhang-style from the vanishing points; fallback to the ratio of the
   averaged side lengths), choose the target page frame (see R8), `getPerspectiveTransform`
   + `warpPerspective` from the full-resolution photo with `INTER_CUBIC` for downscale ≤ 2×
   and `INTER_AREA` otherwise. The warp runs in horizontal bands (8 bands) so the cancel
   flag is honoured between bands.
4. **Residual deskew**: on the warped grayscale, text-line angle from the dominant
   orientation of the horizontal projection profile over −5…+5° (0.25° steps, maximise
   profile variance); rotate about the centre if |angle| ≥ 0.3°.
5. **Orientation (0/90/180/270)** – see R6.
6. **Flattening (Full capability only)** – see R7.
7. **Illumination normalisation**: background estimate = morphological closing with a
   large ellipse (kernel ≈ 1/25 of the page width) followed by median blur, on each
   channel; reflectance = page / background (divide normalisation), then a white-point
   rescale so the 97th-percentile background luminance maps to 255. This removes shadows
   and casts while keeping faint strokes because the division is local.
8. **Style rendering** (R9).
9. **Resample to output size** (R8) and return.

Each stage records `std::chrono::steady_clock` micro-timings and whether it ran, was
skipped (capability) or failed (FR-019). Everything runs inside one `try` that maps
`std::bad_alloc` → `OUT_OF_MEMORY` and `cv::Exception` → `INTERNAL`; Kotlin retries once
at a smaller working resolution on `OUT_OF_MEMORY` (FR-013).

**Rationale**: these are the standard document-scanner building blocks; each is
available in `opencv_core`/`opencv_imgproc` only. The shadow approach follows the
reflectance/illumination model (I = R × L) recommended in OpenCV's shadow-correction
material and the document-shading literature, using a morphological background estimate
instead of Retinex because it is faster and does not blur text.

**Alternatives considered**: `cv::ximgproc::niBlackThreshold` (Sauvola) for binarisation
(needs `opencv_contrib`; a hand-written Sauvola on the normalised image is ~40 lines and
avoids contrib); Retinex (too slow at 2000 px on low-end); `GrabCut` segmentation for
page detection (slow, unpredictable).

## R6. Upright orientation without OCR (Arabic and Latin)

**Decision**: a two-step heuristic on the binarised, deskewed page:

1. **Portrait vs rotated 90°**: compute horizontal and vertical projection profiles of
   ink; text lines produce a strongly periodic, high-variance profile along the axis
   perpendicular to the lines. Choose the axis whose profile has higher normalised
   variance (ratio ≥ 1.3 required, otherwise keep the current orientation).
2. **Up vs down (180°)**: segment text lines from the chosen profile (valleys), and for
   each line compute the vertical centre of ink mass relative to the line box. In upright
   Latin text the baseline band is dense and ascenders outnumber descenders, so mass sits
   in the lower-middle of the box with a thin tail above; in upright Arabic the baseline
   stroke and the majority of letter bodies also sit on the lower part of the box, with dots
   and tails above and below in smaller amounts. In both scripts the row of maximum ink
   density lies in the lower half of the line box; upside-down text flips it to the upper
   half. Vote across lines (weighted by line length); flip when ≥ 60% of the weight says
   "upside down" and at least 3 lines were found; otherwise keep.

   Script-agnostic extra cue for 180°: the right/left margin asymmetry of ragged line ends
   is not used, because Arabic is right-aligned and Latin left-aligned and the engine must
   not know the script.

Output: rotation applied (0/90/180/270) and an `orientationConfidence` (high / low / none)
so the review screen can offer a rotate action. The quality suite scores orientation
accuracy (≥ 95% on the oriented subset, with every "no text" photo left unrotated).

**Rationale**: patents US 8,712,188 (HP) and US 10,115,036 describe exactly this family
(strip projection profiles for portrait/landscape, text asymmetry ratio for up/down)
without character recognition; the `docorient` package implements the projection part in
the open. The baseline-band asymmetry holds for both target scripts, which the reference
set verifies. No OCR dependency is introduced (Principle IX; OCR arrives in Feature 8 and
can refine orientation later).

**Alternatives considered**: Tesseract OSD (needs Tesseract on-device, ~15 MB of data,
contradicts the no-OCR scope); a tiny CNN orientation classifier (would need TFLite and a
training set; deferred with the ML work).

## R7. Classical flattening of single-axis curl

**Decision**: text-line based cylinder correction on capable devices:

1. Detect text lines on the perspective-corrected binarised page (horizontal smearing via
   morphological closing with a wide kernel, connected components, keep long components).
2. Fit a quadratic `y = a x² + b x + c` to each line's centreline (sampled at 32 points);
   require ≥ 6 lines with consistent sign of `a`; otherwise report `flatten = skipped
   (insufficient lines)`.
3. Build a smooth displacement field `dy(x, y)` by interpolating the fitted curves across
   the page height (piecewise-linear between neighbouring lines, extrapolated flat at the
   margins), then `cv::remap` the page so each curve becomes a straight horizontal line.
4. Accept the result only if the mean residual curvature drops by ≥ 50%; otherwise keep
   the un-flattened page and report `skipped (no improvement)`.

**Rationale**: polynomial text-line fitting handles smooth page curl but not folds or
arbitrary warps (ICDAR 2011 FlowLines paper, model-based dewarping patents); that is the
scope the clarification fixed. The method costs ~150 ms at 2000 px and needs only
`imgproc`.

**Alternatives considered**: full cylinder model with camera parameters (Fu et al.,
CBDAR 2007: more accurate for book spines, needs the camera-parallel assumption to be
removed with extra estimation, overkill for loose pages); learned dewarping (deferred).

## R8. Page size, aspect handling and output resolution

**Decision**:
- Output pixel sizes at 300 DPI: A4 2480 × 3508, Letter 2550 × 3300, both in the
  orientation (portrait/landscape) that matches the detected page; the engine never
  stretches: the corrected page is scaled to fit inside the page frame preserving its
  physical aspect estimate and centred on pure white margins (FR-008, receipts edge case).
- `Natural`: output keeps the detected aspect ratio; the longer side is 3508 px, i.e. the
  physical long side of an A4 sheet at 300 DPI, so a receipt becomes a tall narrow image
  without white bands (Feature 7 depends on this).
- Default page size by region is resolved by the caller (`PageSize.defaultFor(regionCode)`
  helper in `:core:domain`: `US`, `CA`, `MX`, `PH` → Letter, else A4), as the spec says the
  engine itself takes an explicit size.

**Alternatives considered**: stretching the page to fill the frame (distorts text; what
several competitors do); 200 DPI output (smaller files but below the spec).

## R9. Output styles

**Decision** (on the illumination-normalised page `N`, with luminance `L`):
- **Scan**: local Sauvola-style threshold (window 31 px at 2000 px working width,
  k = 0.2, R = 128) on `L`, followed by a 3×3 median to remove salt noise and a soft
  anti-aliased edge (binary mask blurred by 0.7 px and composited black-on-white) so
  text is dark and crisp at 300 DPI rather than jagged. Faint pencil and light stamps
  survive because the threshold adapts to local contrast; a global "ink floor" prevents
  blank paper from turning into noise (regions whose local std-dev < 4 levels are white).
  Inverted pages (dark paper, light text: median `L` of the page < 90 and ink detection
  fails) fall back to Grayscale rendering and set `fallbackReason = INVERTED_DOCUMENT`.
- **Grayscale**: `L` with a gentle tone curve (paper mapped to 255 at the 97th
  percentile, gamma 0.9 for mid-tones), preserving photos on IDs.
- **Color document**: paper forced to white (pixels whose chroma < 12 and luminance
  > 0.85 of the white point → 255); remaining pixels keep their hue with saturation
  boosted by 1.15 and luminance from the Grayscale curve, so stamps, signatures and logos
  stay coloured while text is dark.
- **Original**: perspective-corrected page with only a 1.08× brightness lift and 0.5
  strength white balance (grey-world on the background estimate); background, texture and
  cast are kept.

Scan and Grayscale return 1-channel images; Color document and Original return 4-channel
RGBA. Re-rendering another style reuses the session's geometry and normalised page
(FR-009), which is why analysis and rendering are separate calls (see contracts).

## R10. Device capability detection

**Decision**: `DeviceCapability` is derived in `commonMain` by a pure function
`classifyCapability(totalMemoryBytes, is64Bit, isLowRamDevice)` → `Full` when
`totalMemory ≥ 4 GiB && is64Bit && !isLowRam`, else `Simplified`. Platform inputs:
Android `ActivityManager.getMemoryInfo().totalMem`, `ActivityManager.isLowRamDevice`,
`Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()`; iOS `NSProcessInfo.processInfo.physicalMemory`,
always 64-bit, low-RAM = physicalMemory < 3 GiB. The caller can override (for the
benchmark and for a future developer setting). Simplified pipeline: working long edge
1500 px, no flattening, no denoise, 1-channel intermediates where possible.

**Rationale**: `totalMem` is the documented "RAM size of the device"; `isLowRamDevice`
is the platform's own low-end flag. Both are cheap and need no permissions.

## R11. Cancellation and memory discipline

**Decision**:
- Kotlin runs native calls under `withContext(dispatchers.default)` inside
  `suspendCancellableCoroutine`; `invokeOnCancellation` sets a native atomic flag passed
  to the call. Native checks the flag before every stage and between warp bands; on
  cancel it unwinds (RAII frees all `cv::Mat`s) and returns `CANCELLED`. The slowest
  unsplittable stage at working resolution is under 150 ms on the mid-range profile, so
  the 200 ms budget (FR-011) holds; the suite measures cancel latency by cancelling at
  random points 50 times.
- Peak memory budget: input 12 MP RGBA (48 MB, owned by the platform bitmap, read in
  place via `AndroidBitmap_lockPixels` / `CGDataProvider` / `BufferedImage` raster, never
  copied into the JVM heap) + working BGR 2000×1500×3 (9 MB) + a few same-size temporaries
  + output (A4 1-channel 8.7 MB or RGBA 35 MB). Target < 120 MB extra on Full, < 70 MB on
  Simplified. A native allocation failure maps to `OUT_OF_MEMORY`; Kotlin retries once
  with `workingLongEdge / 2` and the Simplified pipeline.
- Sessions (analysis results) are explicit `Closeable` handles so the review screen can
  release the native page as soon as the user leaves.
- Measured (2026-10-23, host build, synthetic 3 MP photos, OpenCV buffers counted by a
  `cv::MatAllocator` wrapper): peak 83-96 MB on Full before the illumination division and the
  Sauvola threshold were banded, 55 MB after; the paper-level estimate now runs at a 500 px
  long edge (242 ms → 9 ms per call). Cancellation latency p100 was 9 ms over 50 random
  cut-offs once detection, deskew and orientation checked the flag between their passes.

## R12. Photo decoding per platform

**Decision**: decoding lives outside the C++ core, in a platform `PhotoDecoder`
(`core/scanengine` platform source sets): Android `ImageDecoder` (API 28+, applies EXIF
orientation, supports HEIC on devices with HEVC) with `BitmapFactory` + `androidx.exifinterface`
on API 26-27 (JPEG only; HEIC → `UnsupportedFormat`); iOS `CGImageSourceCreateThumbnailAtIndex`
with `kCGImageSourceCreateThumbnailWithTransform` (EXIF applied, HEIC native); JVM
`javax.imageio` (JPEG/PNG; the reference set is stored EXIF-baked by the ingest script).
All decoders cap the decoded size at 12 MP by sub-sampling (`setTargetSampleSize` /
`kCGImageSourceThumbnailMaxPixelSize` / `ImageReadParam.setSourceSubsampling`).

**New dependency justification (Principle IX)**: `androidx.exifinterface:exifinterface`
(~100 KB, no network) replaces hand-parsing EXIF on API 26-27; removed when minSdk ≥ 28.

## R13. Reference set format, size and privacy

**Decision**: `reference-set/` at the repository root:

```
reference-set/
├── manifest.json            # list of ReferencePhoto entries (contracts/reference-manifest.md)
├── photos/<id>.jpg          # ≤ 12 MP, JPEG q85, EXIF baked in and metadata stripped
├── clean/<id>.png           # optional clean reference (flatbed scan or flat well-lit photo)
├── transcripts/<id>.txt     # optional UTF-8 ground truth (one line per text line)
├── competitors/adobe-scan/<id>.jpg
├── competitors/microsoft-lens/<id>.jpg
└── competitors/camscanner/<id>.jpg     # 150 DPI JPEG q85 exports (see below)
```

- `scripts/scanref/ingest.py` (Python 3 + Pillow) imports a phone photo: applies EXIF
  rotation, strips all metadata (GPS included), downsizes to ≤ 12 MP, writes q85 JPEG,
  appends a manifest stub. `scripts/scanref/annotate.py` (matplotlib) lets the owner click
  the four corners and records them.
- Size budget: ≤ 60 photos × ≤ 2.5 MB = ≤ 150 MB of photos; competitor outputs are
  stored at 150 DPI (≈ 0.6 MB each, ≤ 110 MB for three apps) and Plainpaper's output is
  downscaled to 150 DPI for the comparison so both sides are measured identically. Plain
  git, no LFS (LFS bandwidth would be consumed by every CI clone). Shallow clones in CI
  (`fetch-depth: 1`) keep checkout time bounded.
- Privacy: only the owner's documents, sensitive fields physically covered before
  photographing, metadata stripped on ingest, reviewed in the pull request like code.

## R13b. Synthetic reference set (deterministic gate from day one)

**Decision**: `:tools:scansuite` generates, at test time, a synthetic set under
`build/scansuite/synthetic/` from a fixed seed: pages rendered with `java.awt` using the
repository fonts (Inter, IBM Plex Sans Arabic from `core/designsystem/src/commonMain/composeResources/font/`)
with English, French and Arabic paragraphs (fixed text resources, so transcripts are
exact), placed on a textured "desk" background, warped by known homographies (random
corner jitter 5-20% of the frame), multiplied by shadow gradients, rotated by 0/90/180/270,
with Gaussian noise and JPEG q80 round-trip. The generator writes photos, expected corners
(from the homography), transcripts, clean references (the un-warped render) and a manifest
with the same schema as the real set (`category = synthetic` plus the matching real
categories). ~24 pages, generated in < 10 s, never committed.

**Rationale**: the gate must be meaningful before the owner's photos exist and must stay
deterministic; synthetic pages give exact ground truth for corners, rotation and text.
They complement, never replace, the real set (FR-015).

## R14. Legibility and other metrics on the host

**Decision**: the suite shells out to the `tesseract` CLI (`-l ara+eng+fra --psm 6`,
`tessdata` from the distro / Homebrew `tesseract-lang`), computes character accuracy
`1 − CER` (Levenshtein distance over the normalised transcript: Unicode NFC, collapsed
whitespace, Arabic diacritics removed). For SC-003 the manifest requires a clean
reference image for every transcribed photo; the score is
`accuracy(output) / accuracy(clean)`. Other metrics: corner error (max corner distance /
image diagonal), paper whiteness (median luminance of background pixels, from the
expected-corner mask eroded 3%), text darkness (median luminance of ink pixels), line
straightness (mean |slope| of fitted baselines in degrees), orientation correctness,
host processing time (informational), cancel latency. Thresholds live in
`tools/scansuite/src/main/resources/thresholds.json` and mirror SC-001…SC-008.

CI installs `tesseract-ocr tesseract-ocr-ara tesseract-ocr-fra` via apt on
`ubuntu-24.04` (Tesseract 5.3.x); locally `brew install tesseract tesseract-lang`.
When the CLI is missing the suite marks legibility rows "skipped" and fails only if
`PP_REQUIRE_OCR=1` (set in CI).

**Alternatives considered**: Tess4J/JavaCPP Tesseract bindings (adds 60+ MB of native
jars to the build); ML Kit on an emulator in CI (slow, Google Play services dependency).

## R15. CI integration

**Decision**: new job `scan-quality` (ubuntu-24.04) added to `.github/workflows/pr.yml`
and to the required checks on `main`: installs tesseract, restores the host OpenCV
cache (or builds it), runs `./gradlew :tools:scansuite:test` (which builds the host
native library) and uploads `build/scansuite/report.md` + `report.json`. A
`dorny/paths-filter` step skips the heavy steps when neither `core/scanengine*`,
`tools/scansuite`, `reference-set` nor the workflow changed, so the check still reports
success on unrelated PRs (a path-filtered *job* would leave the required check pending).
`build-android`, `test-ios` and `build-ios` gain an `actions/cache` entry for
`~/.cache/plainpaper/downloads` (OpenCV archives) and the macOS jobs run the CMake
builds through Gradle. SC-009 is demonstrated once by a deliberate threshold regression in
a throwaway commit, as 001 did for formatting.

## R16. Device benchmarks

**Decision**: Android: an instrumented test in `:core:scanengine`
(`withDeviceTest {}` → `src/androidDeviceTest`) that processes the photos found in the
app's external files directory and prints a JSON timing table; `scripts/bench-android.sh`
pushes a 10-photo subset with `adb`, runs the device test on the connected phone and
stores the result under `reference-set/benchmarks/<device>-<date>.json`. iOS: an XCTest in
`iosApp` with the same photos bundled as test resources, run manually from Xcode on the
owner's iPhone (Xcode is not installed on the development Mac; CI builds but does not
time it). The low-end profile is an Android emulator with 2 GB RAM / 2 cores created by
`scripts/bench-android.sh --low-end`. Results are committed with the device name and
build, as the clarification requires.

## R17. Reference devices and emulator notes

Local machine: Apple M5 Pro, macOS 26.5, Command Line Tools only (no Xcode), NDK r28/r30
and SDK CMake 3.22.1 installed, no Homebrew `cmake`/`ninja`/`tesseract` yet (install in
Setup). Ubuntu 24.04 runner: CMake 3.31, Ninja 1.13, GCC 13/14, Clang 18, Python 3.12;
no OpenCV, no Tesseract preinstalled.
