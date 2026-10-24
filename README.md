# Plainpaper

Photos in. Plain paper out. Plainpaper turns photos of paper into documents that look like they
came from a real flatbed scanner. Everything happens on the phone: no account, no uploads, no
ads, no watermark.

One Kotlin Multiplatform codebase with Compose Multiplatform UI for Android (8.0+) and iOS (15+).

## Prerequisites

| Tool | Version |
|------|---------|
| JDK | 21 |
| Android SDK | platform 37, build-tools 36 |
| Xcode | 26.4 (iOS only) |

## Build, test, run

```sh
./gradlew spotlessCheck detekt testAndroidHostTest koverVerify   # quality + tests + coverage gate
./gradlew :androidApp:installDebug                               # install on a connected device/emulator
open iosApp/iosApp.xcodeproj                                     # run on an iPhone simulator
```

Full validation steps per feature live in `specs/<feature>/quickstart.md`, starting with
[specs/001-app-foundation/quickstart.md](specs/001-app-foundation/quickstart.md). Project rules
are in `.specify/memory/constitution.md`; agent guidance in `CLAUDE.md`.

## Structure

`build-logic/` (Gradle conventions) · `androidApp/` · `iosApp/` · `composeApp/` (shared app) ·
`core/{domain,data,designsystem,testing}` · `feature/{home,settings}` · `specs/` · `.github/workflows/`

## Scan engine (feature 002)

A shared C++ pipeline (OpenCV 4.13) turns a photo into a flatbed-quality page: page detection
(with low-contrast and Hough fallbacks), perspective correction, deskew, upright orientation for
Arabic and Latin text without OCR, curl flattening on capable devices, illumination
normalisation and four output styles at 300 DPI. It runs on Android (JNI), iOS (cinterop) and
the host JVM, where `tools/scansuite` gates every pull request against a synthetic reference
set and the real photos in `reference-set/`. See `specs/002-scan-engine/`.

## Capture & import (feature 003)

Camera screen with a live page outline (`pp_detect`), steadiness-gated auto-capture, manual
shutter, flash/Auto toggles and Document / ID card / Receipt modes; gallery import through the
system photo picker (free users: 10 photos per import) with a progress screen; a file-backed
capture session that survives backgrounding and process death and is handed to review.
Camera permission is requested only on first use; import never needs one.

```sh
scripts/camera-smoke-android.sh      # CameraX plumbing on a device or a virtual-scene emulator
./gradlew :feature:capture:testAndroidHostTest   # gate, modes, import, processor, ViewModels
```
