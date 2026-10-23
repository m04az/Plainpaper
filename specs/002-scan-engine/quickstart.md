# Quickstart: validating the scan engine (002)

## Prerequisites

- JDK 21, Android SDK with NDK r30 and platform 37 (already used by 001).
- `brew install cmake ninja tesseract tesseract-lang clang-format` (macOS); on Ubuntu
  `apt install cmake ninja-build tesseract-ocr tesseract-ocr-ara tesseract-ocr-fra`.
- Host OpenCV once: `scripts/build-opencv-host.sh` (≈ 5-8 min, cached under
  `~/.cache/plainpaper/`).
- Reference photos in `reference-set/` (see contracts/reference-manifest.md); at least
  the owner's seed set with corners annotated.

## 1. Pure logic (no native code)

```sh
./gradlew :core:domain:testAndroidHostTest :core:scanengine:testAndroidHostTest koverXmlReport koverVerify
```

Expected: all decision tests pass (page sizes, capability, orientation vote, retry,
outcome mapping, user-corner validation); Kover ≥ 80% including `:core:scanengine`.

## 2. Reference quality suite on the host

```sh
./gradlew :tools:scansuite:run            # full report, never fails
./gradlew :tools:scansuite:test           # the gate: asserts thresholds.json
open build/scansuite/report.md            # aggregates, per-category legibility, competitor table
open build/scansuite/outputs/             # <id>-<style>.png for eyeballing
```

Expected: every manifest photo yields a row; aggregates meet SC-001…SC-004, SC-007,
SC-008 (FR-007 orientation ≥ 95%); legibility rows are scored (not "skipped") when
`tesseract` is on `PATH`.

## 3. Android build and device benchmark

```sh
./gradlew :androidApp:assembleDebug                      # builds libplainpaper_scan.so for 3 ABIs
scripts/bench-android.sh                                 # connected phone: pushes 10 photos, runs androidDeviceTest, prints timings
scripts/bench-android.sh --low-end                       # creates/starts the 2 GB / 2-core emulator profile first
```

Expected: JSON under `reference-set/benchmarks/<device>-<date>.json`; mid-range median
< 2.0 s (Scan, 12 MP), low-end < 6.0 s, zero crashes over 20 consecutive runs (the
benchmark loops the set twice), memory back to baseline (`Debug.getNativeHeapAllocatedSize`
delta < 5 MB) within 1 s after each run.

## 4. iOS (CI, or a Mac with Xcode 26)

```sh
./gradlew :core:scanengine:iosSimulatorArm64Test         # links the static lib + opencv2.xcframework
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' ARCHS=arm64 CODE_SIGNING_ALLOWED=NO build
```

Expected: no undefined symbols; the benchmark XCTest runs on the owner's iPhone from
Xcode and its JSON is committed like the Android one.

## 5. CI gate demonstration (SC-009)

Push a throwaway commit that breaks a pipeline stage → `scan-quality` fails and the PR is
blocked; revert; `scan-quality` is green.

Demonstrated 2026-10-23 with commit `2caaab9` (`kFlipThreshold` raised to 1.10 so the
upside-down vote can never win): synthetic orientation accuracy fell to 0.636 (gate ≥ 1.000),
`QualityGateTest.everyEnforcedCheckPasses` failed and the PR reported `BLOCKED`:
<https://github.com/m04az/Plainpaper/actions/runs/40009914478/job/120607752803>. Reverted in
`a64079e`.

Two rehearsals that did **not** trip the gate, and why: weakening the white-point rescale
(`kWhitePoint × 0.55`) and inverting the Sauvola `k` both left the Scan output white, because the
ink floor (`kInkFloorStdDev`), the ink luminance cap (`kInkMaxLuminance`) and the local threshold
hold paper white whatever the global level. A tone regression has to defeat those guards before
the style check sees it; corner and orientation regressions are caught directly.

## 6. Cancellation check

`QualityGateTest.cancelLatency` cancels 50 analyses at random delays and asserts every
`PP_CANCELLED` returned within 200 ms of the flag being set and that no output was produced.
