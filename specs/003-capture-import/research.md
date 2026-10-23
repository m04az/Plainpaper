# Research: Capture & Import (003)

**Date**: 2026-10-23 | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Each entry: Decision, Rationale, Alternatives considered. Versions checked against the
upstream release pages on 2026-10-23.

## R1. Android camera stack: CameraX 1.6.2 with `camera-compose`

**Decision**: `androidx.camera:camera-core`, `camera-camera2`, `camera-lifecycle` and
`camera-compose` at **1.6.2** (stable, 2026-08-26; 1.7.0-rc01 exists but is not stable).
The preview is drawn with `CameraXViewfinder` from `camera-compose`, fed by the
`SurfaceRequest` of the `Preview` use case. Three use cases are bound to the activity
lifecycle: `Preview`, `ImageAnalysis` (640 px long edge, `STRATEGY_KEEP_ONLY_LATEST`,
`OUTPUT_IMAGE_FORMAT_RGBA_8888`) for the live outline, and `ImageCapture`
(`CAPTURE_MODE_MINIMIZE_LATENCY`, JPEG, target 12 MP cap via `ResolutionSelector`) for the
full-resolution photo. Flash is `ImageCapture.flashMode` (ON/OFF) plus torch off.

**Rationale**: CameraX hides Camera2 device quirks (the low-end target audience), runs on
API 26, supports the emulator's virtual-scene camera for the smoke test, and `camera-compose`
removes the `AndroidView`/`PreviewView` interop and its sizing bugs. The RGBA analysis
output lets the frame go straight to `pp_detect` without a YUV conversion in Kotlin.

**Alternatives considered**: Camera2 directly (hundreds of lines of device-specific
handling); `PreviewView` in `AndroidView` (kept as the fallback if `camera-compose` and the
Compose Multiplatform 1.12.2 runtime disagree on a Jetpack Compose version — it is one
file to swap); CameraX `camera-view` `CameraController` (bundles use cases but hides the
analysis resolution control we need).

## R2. iOS camera stack: AVFoundation through Kotlin/Native, preview in `UIKitView`

**Decision**: `AVCaptureSession` (`.photo` preset) with `AVCaptureDeviceInput` (back wide
camera), `AVCaptureVideoDataOutput` (`kCVPixelFormatType_32BGRA`, 640 px long edge via
`sessionPreset` + downscale, `alwaysDiscardsLateVideoFrames = true`) delivering frames to a
Kotlin `NSObject` implementing `AVCaptureVideoDataOutputSampleBufferDelegateProtocol`, and
`AVCapturePhotoOutput` for the 12 MP JPEG (`AVCapturePhotoSettings` with `flashMode`). The
preview is an `AVCaptureVideoPreviewLayer` inside a `UIView` subclass that resizes the layer
in `layoutSubviews`, hosted with Compose Multiplatform's `UIKitView(factory, modifier,
update, onRelease)`; `videoGravity = resizeAspectFill`. `startRunning()` is called off the
main thread. BGRA frames are passed to `pp_detect` with the BGRA flag (R4).

**Rationale**: no third-party dependency; the JetBrains UIKit-integration guide documents
exactly this preview-layer pattern. iOS 15 is the floor, all APIs exist there.

**Alternatives considered**: a Swift camera module in `iosApp` exposed to Kotlin (splits the
camera logic across two languages and cannot be unit tested from Kotlin); a KMP camera
library (none is stable and privacy-reviewed; each adds an SDK the constitution would need
to justify).

## R3. Module layout: `:core:capture` (platform services) + `:feature:capture` (screens)

**Decision**: a new KMP module `:core:capture` holds every platform-specific capture
service behind commonMain interfaces: `CameraController` (+ `CameraPreview` composable),
`CameraPermission`, `PhotoPicker`, `ImageFiles` (encode a `ScanOutput` to a JPEG file,
decode a thumbnail to an `ImageBitmap`), `BackgroundTask`, `RegionProvider`. A new
`:feature:capture` module holds the ViewModels, UI states, screens (camera, permission
denial, import progress, hand-off placeholder) and the app-scoped `PageProcessor`. Pure
session models and repository interface live in `:core:domain`; the file-backed
`CaptureSessionRepository` implementation lives in `:core:data`.

**Rationale**: constitution III (platform code behind commonMain interfaces; features
depend only on domain and core modules). `:core:camera` was the working name; `capture`
covers picker, files and permission as well. Feature 4 reuses the session models and
repository without touching camera code.

**Alternatives considered**: camera actuals inside `:feature:capture` (features would
grow platform source sets and lose host-testability); one `:core:platform` grab-bag module
(would later swallow PDF, purchases, biometrics and become the dependency everyone needs).

## R4. Live outline: new `pp_detect` C entry, detection only

**Decision**: add to `plainpaper_scan.h` (API version stays 1; additive):

```c
typedef struct { pp_quad corners; pp_confidence confidence; float mean_luma; float fill_fraction; int64_t micros; } pp_detect_info;
PP_EXPORT pp_status pp_detect(pp_engine* engine, const pp_image_rgba* frame, uint32_t flags /* PP_FRAME_BGRA */, volatile int32_t* cancel_flag, pp_detect_info* out);
```

It runs `detectPage` on the frame as given (no further downscale when the long edge is
≤ 800 px), returns corners in frame pixels, the same confidence classes as `pp_analyze`,
mean luminance (for the low-light hint) and the page's fraction of the frame. Budget: under
40 ms on a 640 × 480 frame on the mid-range device, under 120 ms on low-end (the analysis
stream drops frames it cannot keep up with). Exposed in Kotlin as
`ScanEngine.detect(frame: PreviewFrame): DetectResult` through the existing JNI/cinterop
bridges (frame pixels pinned, never copied).

**Rationale**: `pp_analyze` builds a session, warps and normalises — far too heavy at
15 fps. Reusing `detectPage` keeps the live outline consistent with the final detection.

**Alternatives considered**: a Kotlin re-implementation of detection for the preview
(duplicate logic, slower); running `pp_analyze` on preview frames (≈ 150–300 ms, not
real time); ML Kit document scanner (off-device SDK policy, constitution I).

## R5. Steadiness and auto-capture: a pure Kotlin gate

**Decision**: `AutoCaptureGate` in `:feature:capture` commonMain is a state machine fed
with `DetectionSample(timestampMillis, quad?, confidence, fillFraction, meanLuma)` and
emits `GateState` (`NoPage`, `LowConfidence`, `TooDark`, `Tracking(progress 0..1)`,
`Capture`, `Cooldown`). Rules from FR-003 and the clarifications: high confidence, fill
≥ 0.20, corner movement < 1.5 % of the frame diagonal over the steady window (700 ms
mid-range, 1000 ms low-end), mean luma above the dark threshold; after `Capture` the gate
stays in `Cooldown` until the quad moves > 10 % of the diagonal or the page disappears and
returns. Thresholds are constructor parameters so tests pin them.

**Rationale**: everything the spec measures (SC-003) is a decision over samples; it is
testable on the JVM with scripted samples and independent of either camera stack.

**Alternatives considered**: CameraX/AVFoundation focus/stability callbacks (not
exposed uniformly, and they measure the device, not the page).

## R6. Photo picking: system pickers, no permission

**Decision**: Android `ActivityResultContracts.PickMultipleVisualMedia(maxItems)` with
`ImageOnly`; `maxItems` = 10 for free, `getPickImagesMaxLimit()` (or 100 when unknown) for
Pro; the contract falls back to the documents UI on devices without the picker, and the
manifest declares the Play-services backport (`photopicker_activity:0:required`
`ModuleDependencies` meta-data) so Android 8–10 phones get the real picker where Play
services exist. iOS `PHPickerViewController` with `selectionLimit` (10 or 0 = unlimited),
`filter = images`, items copied through `loadFileRepresentation` into the session folder.
Because free users may still pick more than the limit on fallback pickers, the free-limit
rule (FR-013) is also enforced in Kotlin on the returned list, which is where the Pro sheet
is triggered.

**Rationale**: both pickers run out of process and need no storage or photo permission,
satisfying FR-018/FR-022. Enforcing the limit in Kotlin keeps SC-009 true on every picker.

**Alternatives considered**: `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` with an in-app
grid (a permission, a lot of UI, and worse privacy optics); third-party pickers (an SDK).

## R7. Session storage: files + JSON index in app-private storage, no Room yet

**Decision**: `CaptureSessionRepository` (domain interface) implemented in `:core:data`
with Okio 3.18.2 (`FileSystem.SYSTEM`) under `<appFiles>/capture/<sessionId>/`:
`original-<n>.jpg`, `page-<n>.jpg`, `thumb-<n>.jpg` and `session.json` (kotlinx
serialization, schema in contracts/storage.md). Writes are atomic (temp file + rename).
The repository exposes `Flow<CaptureSession?>` for the active session, so the camera
screen, the import screen and the hand-off screen observe the same state, and the
recovery prompt on Home reads the index at launch. Processed pages are JPEG quality 90
(Scan style is 1-channel grayscale JPEG; a 2480 × 3508 page is ≈ 300–600 KB).

**Rationale**: one session at a time, a handful of files, and Feature 4 will move the
pages into a document; a database brings migrations and a module for nothing yet
(constitution IX). Okio is already in the catalog.

**Alternatives considered**: Room KMP now (`:core:database` reserved for Feature 4/6);
DataStore proto for the index (schema tooling for one small JSON); PNG for pages (3–5×
larger and slower to encode on low-end devices; export quality is Feature 5's job).

## R8. Background processing: in-process, app-scoped, no foreground service

**Decision**: `PageProcessor` is a Koin `single` with its own `CoroutineScope(SupervisorJob()
+ dispatchers.default)`, consuming a queue of page ids: decode original → `ScanEngine.analyze`
→ `render(Scan, pageSize)` → encode page + thumbnail → repository update. Concurrency 1 on
Simplified devices, 2 on Full. Cancellation per page (import cancel) and per session
(discard). Android: processing simply continues while the process lives; no foreground
service (it would need `FOREGROUND_SERVICE*` permissions, excluded by FR-022). iOS:
`BackgroundTask.keepAlive { }` wraps the running batch in
`beginBackgroundTaskWithExpirationHandler` (≈ 30 s). On resume the processor re-reads the
index and continues with pages still `Pending`; a page that was `Processing` when the
process died is restarted, so no page is converted twice in the stored result.

**Rationale**: satisfies the clarified behaviour (continue while the system allows, then
pause and resume) with no new permission and no WorkManager (which cannot run on iOS).

**Alternatives considered**: WorkManager on Android (persistence we already have via the
index; adds a dependency and platform divergence); a foreground service with notification
(permission + notification UX the spec does not ask for).

## R9. Camera permission flow

**Decision**: `CameraPermission` interface in `:core:capture` with `status()`,
`suspend request()`, `openSettings()`, and the states `NotAsked`, `Granted`, `Denied`,
`DeniedPermanently`, `Unavailable`. Android: `ActivityResultContracts.RequestPermission`
through a `rememberCameraPermission()` composable bridge in the feature (needs the
Activity's result registry), `shouldShowRequestPermissionRationale` to tell `Denied` from
`DeniedPermanently`, `PackageManager.FEATURE_CAMERA_ANY` for `Unavailable`. iOS:
`AVCaptureDevice.authorizationStatus(for: .video)` / `requestAccess`, `UIApplication.open(
UIApplicationOpenSettingsURLString)`. The in-app explanation screen precedes the first
system prompt (FR-017). Returning to the foreground re-queries the status (FR-018).

**Rationale**: constitution "permissions only when needed, flow usable when denied".

**Alternatives considered**: Accompanist permissions (Android-only, deprecated path);
`moko-permissions` (an SDK for one permission).

## R10. Default page size without a setting: `RegionProvider`

**Decision**: `RegionProvider.regionCode(): String?` in `:core:capture` (Android
`Resources.getSystem().configuration.locales[0].country`, iOS
`NSLocale.currentLocale.countryCode`), fed to the existing `PageSize.defaultFor(region)`.
Receipt and ID card modes use `PageSize.Natural`.

**Alternatives considered**: hard-coding A4 (wrong for US users from day one).

## R11. Thumbnails and page images in Compose

**Decision**: `ImageFiles.loadThumbnail(path, maxEdge): ImageBitmap?` in `:core:capture`
(Android `BitmapFactory` with `inSampleSize` → `asImageBitmap()`; iOS file bytes →
`org.jetbrains.skia.Image.makeFromEncoded` → `toComposeImageBitmap()`), and
`ImageFiles.writeJpeg(output: ScanOutput, path, quality)` (Android `Bitmap.compress`; iOS
`UIImage(cgImage).jpegData`). Thumbnails are written once by the processor at 400 px long
edge so lists never decode full pages.

**Alternatives considered**: Coil 3 multiplatform (an image-loading SDK for a dozen local
thumbnails).

## R12. Screenshot tests: still deferred; SC-010 verified with semantics tests

**Decision**: do not introduce Roborazzi in this feature. SC-010 is verified by Compose UI
tests (`androidDeviceTest` in `:feature:capture`) asserting content descriptions, 48 dp
minimum sizes and RTL mirroring on the camera and import screens with a fake camera, plus
reviewed light/dark/RTL screenshots attached to the PR. Screenshot tooling gets its own
small feature once Review (Feature 4) adds the screens that benefit most. The spec's SC-010
wording is adjusted accordingly.

**Rationale**: the camera screen is mostly a live surface that cannot be screenshotted
deterministically; the Roborazzi + AGP 9 KMP library plugin combination is unverified
(constitution IX: no speculative tooling).

**Alternatives considered**: Roborazzi 1.63 via Robolectric host tests (needs
`withHostTest { isIncludeAndroidResources = true }` and a Robolectric SDK download in CI;
revisit in the tooling feature).

## R13. Emulator smoke test for the camera plumbing

**Decision**: `androidDeviceTest` `CameraSmokeTest` in `:core:capture` binds the three use
cases on the emulator's virtual-scene camera, receives ≥ 5 analysis frames, captures one
JPEG and checks the file decodes. Runs locally (`scripts/camera-smoke-android.sh` creates
the AVD with `hw.camera.back=virtualscene` when needed); CI builds the test APK only
(same policy as the 002 benchmark).

## R14. Strings and design

**Decision**: all new strings in `feature/capture/src/commonMain/composeResources/values{,-ar}/
strings.xml`; copy from the Camera and Import artboards (hints, "From Photos", "Converting
%1$d photos", "%1$d of %2$d done · about %3$d s left", "Review %1$d pages", "Processed on
this phone. Your photos never leave it."), plurals via `%1$d` placeholders with Arabic
plural forms. New design-system pieces: `PpSegmentedControl` (modes), `PpHintPill`,
`PpShutterButton`, `PpPageCounter`, `PpProgressTile`; dark glass variant of
`PpGlassButton` for the camera overlay (tokens from `Tokens.dc.html`).
