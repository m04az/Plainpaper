# Quickstart: validating capture & import (003)

## Prerequisites

- Everything from 002 (JDK 21, NDK r30, host OpenCV, tesseract) plus an emulator or phone
  with a camera. For the emulator: `scripts/camera-smoke-android.sh` creates
  `plainpaper-camera` with `hw.camera.back=virtualscene` (the virtual scene contains a
  printed page you can walk up to with the emulator's camera controls).
- iOS is built and unit-tested by CI (`test-ios`, `build-ios`); camera behaviour on an
  iPhone is verified manually by the owner from the CI-built app.

## 1. Pure logic (host JVM)

```sh
./gradlew :feature:capture:testAndroidHostTest :core:capture:testAndroidHostTest \
          :core:domain:testAndroidHostTest :core:data:testAndroidHostTest koverXmlReport koverVerify
```

Expected: auto-capture gate (steady window, drift, fill, dark, cooldown), mode sequencer
(ID front/back), import limit (10 free / unlimited Pro), batch progress + cancel keep/discard,
session repository (ordering, recovery, `Processing → Pending`), permission state machine,
page-size resolution per mode/region, and all three ViewModels pass; Kover ≥ 80 %.

## 2. Detection entry

```sh
./gradlew :tools:scansuite:test --tests '*Detect*'
```

Expected: `pp_detect` on the synthetic set's 640 px previews finds corners within 0.02 of
the diagonal for every plain photo and reports `NotFound` for the no-page scenes; host median
under 15 ms (informational, mid-range budget is 40 ms).

## 3. Android device checks

```sh
scripts/camera-smoke-android.sh                   # CameraX smoke: frames flow, one capture decodes
./gradlew :feature:capture:connectedAndroidDeviceTest   # semantics: labels, 48 dp, RTL mirroring
./gradlew :androidApp:installDebug && adb shell am start -n app.plainpaper/.android.MainActivity
```

Manual on the emulator or a phone (spec acceptance scenarios):
1. Home → Scan: explanation panel, system prompt, preview with outline within 1.5 s.
2. Hold the page steady: capture fires without touching the shutter, counter 1, thumbnail.
3. Auto off → no capture until the shutter; flash toggle remembered after relaunch.
4. ID card mode: hints "Front side" then "Back side"; Receipt mode: tall page.
5. Background the app and return: counter unchanged; force-stop and relaunch: Home offers
   "You have an unfinished scan of N pages".
6. Close with pages → discard confirmation.
7. Home → Import photos: pick 12 → limit sheet → "Import the first 10" → progress tiles,
   estimate, cancel → keep/discard; finish → "Review 10 pages" → hand-off grid.
8. Deny the camera permission: denial panel with "Import photos instead" (import works
   with no prompt) and "Open settings"; grant in settings → camera works on return.

## 4. Low-end profile

Run step 3 on the `plainpaper-lowend` AVD from 002 (`scripts/bench-android.sh --low-end`
creates it): outline still updates (≥ 6/s), steady window 1 s, pages process one at a time,
50 captures and 2 imports of 10 without a crash (SC-008 sample; full soak is manual).

## 5. CI

All six required checks green; the `scan-quality` job now includes the detect test;
`build-android` assembles the device-test APKs; `build-ios` compiles the AVFoundation code.

## 5a. Verified 2026-10-24

Sections 1–3 ran on the Pixel 10a AVD (API 37, virtual-scene camera); results and the SC
table are in `results.md`. The emulator ignores `-virtualscene-poster`, so step 2
(auto-capture on a steady page) is covered by `AutoCaptureGateTest` until the owner runs it
on a phone. Section 4 (low-end AVD) and the iPhone checks are pending the owner.

## 6. Done criteria

Spec SC-001…SC-010 recorded in `specs/003-capture-import/results.md` with the device and
build for each timing; owner-dependent numbers (iPhone timings) marked pending.
