# Tasks: Capture & Import

**Input**: Design documents from `/specs/003-capture-import/`

**Prerequisites**: plan.md, spec.md, research.md (R1–R14), data-model.md, contracts/ (kotlin-api, c-api-detect, storage, ui), quickstart.md

**Tests**: REQUIRED (constitution Principle VI). Every story lists its tests first; implementation tasks make them pass. Decisions and ViewModels are verified in `commonTest` with the fakes in `:core:testing`; `pp_detect` by `:tools:scansuite`; camera plumbing by the emulator smoke test; accessibility by Compose semantics tests.

**Organization**: tasks grouped by user story so each is an independently testable increment. Commit each finished artifact separately with a short `type: subject` message (no co-author trailers).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1…US5 from spec.md
- Paths are repository-relative

## Path Conventions

- Domain: `core/domain/src/{commonMain,commonTest}/kotlin/app/plainpaper/core/domain/`
- Data: `core/data/src/{commonMain,commonTest}/kotlin/app/plainpaper/core/data/`
- Platform capture services: `core/capture/src/{commonMain,androidMain,iosMain,commonTest,androidDeviceTest}/kotlin/app/plainpaper/core/capture/`
- Feature: `feature/capture/src/{commonMain,commonTest,androidDeviceTest}/kotlin/app/plainpaper/feature/capture/`, strings in `feature/capture/src/commonMain/composeResources/values{,-ar}/strings.xml`
- Engine addition: `core/scanengine/native/`, `core/scanengine/src/*`
- Fakes: `core/testing/src/commonMain/kotlin/app/plainpaper/core/testing/`
- App wiring: `composeApp/src/*`, `androidApp/src/main/AndroidManifest.xml`, `iosApp/iosApp/Info.plist`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: modules, dependencies, permissions and conventions needed by every story.

- [X] T001 Add to `gradle/libs.versions.toml`: `camerax = "1.6.2"` and libraries `androidx-camera-core`, `androidx-camera-camera2`, `androidx-camera-lifecycle`, `androidx-camera-compose` (`androidx.camera:camera-*`), bundle `camerax`; confirm 1.6.2 is the latest stable on maven.google.com (research R1)
- [X] T002 Register modules in `settings.gradle.kts`: `include(":core:capture")`, `include(":feature:capture")`
- [X] T003 [P] Create `core/capture/build.gradle.kts` (`plainpaper.kmp.compose`; commonMain deps `projects.core.domain`, `projects.core.scanengine`, `libs.kotlinx.coroutines.core`, `libs.koin.core`, `libs.okio`; androidMain deps `libs.bundles.camerax`, `libs.androidx.activity.compose`; commonTest `projects.core.testing`; `withDeviceTestBuilder { sourceSetTreeName = "test" }` + `instrumentationRunner`; namespace `app.plainpaper.core.capture`)
- [X] T004 [P] Create `feature/capture/build.gradle.kts` (`plainpaper.kmp.feature`; extra commonMain deps `projects.core.capture`, `projects.core.scanengine`, `libs.kotlinx.serialization.json` for routes; `kotlinSerialization` plugin; device test builder for the semantics tests) and `feature/capture/src/commonMain/composeResources/values/strings.xml` + `values-ar/strings.xml` with the keys from contracts/ui.md (English + Arabic)
- [X] T005 [P] Add `<uses-permission android:name="android.permission.CAMERA"/>`, `<uses-feature android:name="android.hardware.camera.any" android:required="false"/>` and the photo-picker backport `<service android:name="com.google.android.gms.metadata.ModuleDependencies" …>` meta-data (`photopicker_activity:0:required`) to `androidApp/src/main/AndroidManifest.xml` (research R6)
- [X] T006 [P] Add `NSCameraUsageDescription` to `iosApp/iosApp/Info.plist` with localized values in `iosApp/iosApp/en.lproj/InfoPlist.strings` and `ar.lproj/InfoPlist.strings` (register in `project.pbxproj`); text: "Plainpaper needs the camera to scan pages. Photos stay on this phone."
- [X] T007 [P] Update `build-logic/convention/src/main/kotlin/KoverConventionPlugin.kt`: include `:core:capture` and `:feature:capture` in the merged report; exclude platform actuals (`*.camera.CameraX*`, `*.camera.Av*`, `*Preview*`, `*Picker*`, `*ImageFiles*`, `*Permission*Android*`, `*Screen*`, `*Module*`); keep minBound 80
- [X] T008 [P] Update `scripts/check-hardcoded-strings.sh` scope (if path-filtered) so `feature/capture` and `core/capture` are scanned; in `.github/workflows/pr.yml` make `build-android` also assemble the device-test APKs of `:core:capture` and `:feature:capture` (confirm task names with `./gradlew :core:capture:tasks --all`); `scan-quality` keeps its `core/scanengine/**` filter (covers `pp_detect`)

**Checkpoint**: `./gradlew spotlessCheck detekt :core:capture:tasks :feature:capture:tasks` passes with the empty modules.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: domain session model and repositories, the detect entry in the engine, the platform service interfaces with fakes, the page processor, design-system pieces and app wiring. No story can be verified without these.

### Domain (tests first)

- [X] T009 [P] Write `core/domain/src/commonTest/kotlin/app/plainpaper/core/domain/model/capture/CaptureSessionTest.kt` (page positions equal indices; `withPage` appends; `PageState` transitions `Pending→Processing→Ready|NeedsAttention|Failed`; `PageTag` equality for `IdFront(cardId)`) and `core/domain/src/commonTest/.../usecase/capture/CaptureUseCasesTest.kt` (Observe/Start/AddCapturedPage/Finish/Discard/Recover delegate to a fake repository with the right arguments and results)
- [X] T010 [P] Write `core/domain/src/commonTest/kotlin/app/plainpaper/core/domain/usecase/capture/ImportLimitTest.kt` (free: 10 → `Proceed`, 11 → `ExceedsFreeLimit(limit 10, picked 11)`; Pro: 250 → `Proceed`; 0 → `Proceed(empty)`)
- [X] T011 [P] Write `core/domain/src/commonTest/kotlin/app/plainpaper/core/domain/usecase/capture/ResolveDefaultPageSizeTest.kt` (`Document` + region "US" → Letter, "EG" → A4, null → A4; `Receipt`/`IdCard` → Natural regardless of region)
- [X] T012 [P] Create `core/domain/src/commonMain/kotlin/app/plainpaper/core/domain/model/capture/` : `CaptureSession.kt`, `CapturedPage.kt`, `PageState.kt`, `CaptureMode.kt` (+ `PageTag`), `SessionSource.kt`, `SessionStatus.kt`, `CaptureSettings.kt`, `CameraPermissionState.kt` and `core/domain/.../model/ProStatus.kt` exactly as data-model.md (explicit API)
- [X] T013 [P] Create `core/domain/src/commonMain/kotlin/app/plainpaper/core/domain/repository/CaptureSessionRepository.kt`, `CaptureSettingsRepository.kt`, `ProStatusRepository.kt` (contracts/kotlin-api.md)
- [X] T014 Create use cases in `core/domain/src/commonMain/kotlin/app/plainpaper/core/domain/usecase/capture/`: `ObserveActiveSessionUseCase`, `StartSessionUseCase`, `AddCapturedPageUseCase`, `FinishSessionUseCase`, `DiscardSessionUseCase`, `RecoverSessionUseCase`, `EvaluateImportLimitUseCase` (+ `ImportLimit` rule object), `ResolveDefaultPageSizeUseCase` to satisfy T010–T011

### Data (tests first)

- [X] T015 [P] Write `core/data/src/commonTest/kotlin/app/plainpaper/core/data/capture/FileCaptureSessionRepositoryTest.kt` using Okio `FakeFileSystem`: start creates the folder + `session.json`; `addPage` assigns positions in order and relative paths; `updatePage` rewrites atomically (no `.tmp` left); `recoverable()` returns the session with pages and maps `Processing` → `Pending`; `start()` discards an empty Active session but keeps one with pages; `discard` deletes the folder; unknown JSON keys ignored
- [X] T016 [P] Write `core/data/src/commonTest/kotlin/app/plainpaper/core/data/capture/CaptureSettingsStoreTest.kt` (defaults flash=false, auto=true, mode=Document; round-trip through the preferences DataStore test harness used by `DataStorePreferencesRepositoryTest`)
- [X] T017 [P] Create `core/data/src/commonMain/kotlin/app/plainpaper/core/data/capture/SessionIndex.kt` (`@Serializable` schema v1 from contracts/storage.md + mappers to/from domain) and `FileCaptureSessionRepository.kt` (Okio `FileSystem` injected, root path from `named(CAPTURE_ROOT_QUALIFIER)`, `MutableStateFlow` for `activeSession`, atomic writes, `sessionDirectory`) to satisfy T015
- [X] T018 [P] Create `core/data/src/commonMain/kotlin/app/plainpaper/core/data/capture/DataStoreCaptureSettingsRepository.kt` (keys `capture.flash`, `capture.auto`, `capture.mode`) to satisfy T016 and `core/data/.../pro/FreeOnlyProStatusRepository.kt`
- [X] T019 Register in `core/data/src/commonMain/kotlin/app/plainpaper/core/data/di/DataModule.kt`: `CAPTURE_ROOT_QUALIFIER`, `FileSystem.SYSTEM`, the three repositories; provide the root path in `composeApp/src/androidMain/kotlin/app/plainpaper/di/AndroidPlatformModule.kt` (`filesDir/capture`) and `composeApp/src/iosMain/kotlin/app/plainpaper/di/IosPlatformModule.kt` (`Documents/capture`, excluded from backup)

### Engine: `pp_detect` (tests first)

- [X] T020 [P] Write `tools/scansuite/src/test/kotlin/app/plainpaper/scansuite/DetectTest.kt` (downscale each synthetic photo to 640 px long edge as a `PreviewFrame`, call `ScanEngine.detect`: plain/shadow/rotated scenes → corners within 0.02 of the diagonal of the scaled expected corners and `High`; `nopage` scenes → `NotFound`; `verydark` → `meanLuma < 40`; BGRA input gives the same corners as RGBA; median micros printed, informational)
- [X] T021 [P] Write `core/scanengine/src/commonTest/kotlin/app/plainpaper/core/scanengine/DefaultScanEngineDetectTest.kt` with `FakeNativeBridge`: maps status/quad/confidence, returns `corners = null` on `NotFound`, never throws on `Internal` (returns `NotFound` with `micros`), honours cancellation
- [X] T022 Add `pp_detect`, `pp_detect_info`, `PP_FRAME_BGRA` to `core/scanengine/native/include/plainpaper_scan.h` and implement in `core/scanengine/native/src/engine.cpp` (build a BGR working mat from the frame honouring the flag, downscale to ≤ 800 px, reuse `detectPage` on an `AnalyzeContext`, fill `mean_luma`, `fill_fraction`, `micros`; try/catch → status); clang-format clean (contracts/c-api-detect.md)
- [X] T023 Add `PreviewFrame` (`core/scanengine/src/commonMain/.../PreviewFrame.kt`: width, height, strideBytes, `PixelFormat { RGBA, BGRA }`, timestampMillis, platform pixel holder) and `DetectResult`, extend `NativeBridge` with `detect(frame, cancel)` and `ScanEngine.detect(frame)` in `DefaultScanEngine` (default dispatcher, cancel flag, status mapping) to satisfy T021
- [X] T024 Implement the bridge actuals: `core/scanengine/native/jni/plainpaper_jni.cpp` `nativeDetect` (direct `ByteBuffer` address or `byte[]` pinned, out `double[11]`), `JniScanBridge`/`JniNativeBridge` in `src/jvmCommonMain`, `AndroidNativeBridge` (frame from `ImageProxy` RGBA plane, `src/androidMain`), `jvmMain` (`ByteArray` frame for the suite), `CinteropNativeBridge` (pinned `ByteArray`/`CVPixelBuffer` base address, `src/iosMain`); update `core/testing/.../FakeNativeBridge.kt` and `FakeScanEngine.kt` with `detect` scripting; run T020–T021 green; commit `scanengine: pp_detect`

### Platform service interfaces, fakes, processor (tests first)

- [ ] T025 [P] Create `core/capture/src/commonMain/kotlin/app/plainpaper/core/capture/`: `camera/CameraController.kt` (+ `CameraState`, `CaptureResult`), `camera/CameraPreview.kt` (`expect @Composable`), `camera/PreviewGeometry.kt` (`frameToView` for aspect-fill, rotation-aware), `permission/CameraPermission.kt` (+ `expect @Composable rememberCameraPermission()`), `picker/PhotoPicker.kt` (+ `PickedPhoto`, `expect @Composable rememberPhotoPicker()`), `files/ImageFiles.kt`, `background/BackgroundTask.kt`, `region/RegionProvider.kt`, `di/CaptureModule.kt` (+ `expect val capturePlatformModule`) per contracts/kotlin-api.md
- [ ] T026 [P] Write `core/capture/src/commonTest/kotlin/app/plainpaper/core/capture/camera/PreviewGeometryTest.kt` (640×480 frame into 390×844 view: aspect-fill scale and centring; corners outside the visible crop clamp; 90° sensor rotation maps TL→TR) and implement `PreviewGeometry` to pass
- [ ] T027 [P] Create fakes in `core/testing/src/commonMain/kotlin/app/plainpaper/core/testing/`: `FakeCameraController.kt` (scripted state + `MutableSharedFlow<PreviewFrame>` + capture results), `FakeCameraPermission.kt`, `FakePhotoPicker.kt`, `FakeImageFiles.kt` (in-memory paths, `freeBytes` scripted), `FakeBackgroundTask.kt`, `FakeRegionProvider.kt`, `FakeCaptureSessionRepository.kt` (in-memory, `MutableStateFlow`), `FakeProStatusRepository.kt`, `FakeCaptureSettingsRepository.kt`; `:core:testing` build adds `projects.core.capture`
- [ ] T028 [P] Write `feature/capture/src/commonTest/kotlin/app/plainpaper/feature/capture/processing/PageProcessorTest.kt` (enqueue → `Processing` → `Ready` with page + thumbnail paths and corners; `NotFound` confidence → `NeedsAttention(NoPageFound)`; `TooDark` → `NeedsAttention(TooDark)`; `Low` → `NeedsAttention(LowConfidence)`; decode failure → `Failed(Unreadable)`; `OutOfMemory` → `Failed`; cancel(page) → `Failed(Cancelled)`; cancel(session) stops the queue; `resume()` re-queues `Pending` pages; concurrency 1 on Simplified, 2 on Full; `keepAlive` wraps the batch)
- [ ] T029 Create `feature/capture/src/commonMain/kotlin/app/plainpaper/feature/capture/processing/PageProcessor.kt` (research R8) to satisfy T028; page size per page from `CapturedPage.pageSize`; Scan style; thumbnail 400 px
- [ ] T030 [P] Create design-system components in `core/designsystem/src/commonMain/kotlin/app/plainpaper/core/designsystem/component/`: `PpSegmentedControl.kt`, `PpHintPill.kt`, `PpShutterButton.kt`, `PpPageCounter.kt`, `PpProgressTile.kt`, dark-glass variant in `PpButtons.kt`, `PpOutline.kt` (quad overlay) with tokens from the design; add `PpIcons.Flash`, `PpIcons.Close`, `PpIcons.Warning`, `PpIcons.Lock`
- [ ] T031 Create `feature/capture/src/commonMain/kotlin/app/plainpaper/feature/capture/CaptureRoutes.kt` (`CameraRoute`, `ImportRoute`, `HandoffRoute(sessionId)`), `di/CaptureFeatureModule.kt` (ViewModels, `PageProcessor` single, use cases), and wire in `composeApp`: `AppModules.kt` (`captureModule`, `captureFeatureModule`), `AppRoutes.kt`/`AppNavHost.kt`/`AppScaffold.kt` (replace `ScanPlaceholder`/`ImportPlaceholder` with the capture routes; keep a `ProPlaceholder` route for "See Pro"), delete the obsolete placeholder strings from `composeApp/.../strings.xml` (both languages)
- [ ] T032 Push the branch, open draft PR #3 "Capture & import (003)" so CI runs on every push; confirm `build-ios` compiles the empty `expect`/`actual` scaffolding

**Checkpoint**: host tests green for domain/data/feature processor; `:tools:scansuite:test` green including `DetectTest`; app builds on Android with the camera route showing an empty screen.

---

## Phase 3: User Story 1 - Scan a page with the camera (Priority: P1) 🎯 MVP

**Goal**: live outline, auto-capture, shutter, flash/Auto toggles, background processing, counter + thumbnail, Done → hand-off.

**Independent Test**: quickstart §3 steps 1–3 on the emulator's virtual-scene camera; `CameraViewModelTest` + `AutoCaptureGateTest` on the host.

### Tests for User Story 1

- [ ] T033 [P] [US1] Write `feature/capture/src/commonTest/kotlin/app/plainpaper/feature/capture/camera/AutoCaptureGateTest.kt` (scripted `DetectionSample`s: steady high-confidence page for 700 ms → `Capture` exactly once; drift 2 % resets progress; fill 0.15 → no capture; low confidence → `LowConfidence`, no capture; luma 30 → `TooDark`; after `Capture` stays `Cooldown` until drift > 10 % or page lost + found; 1000 ms window on Simplified; Auto off → never `Capture`; no page for 3 s with Auto on → `NoPage(prolonged = true)` so the hint becomes `MoveCloser`)
- [ ] T034 [P] [US1] Write `feature/capture/src/commonTest/kotlin/app/plainpaper/feature/capture/camera/CameraViewModelTest.kt` with Turbine (permission granted → camera started with the session directory; frames → `outline` in view coordinates + `hint`; gate `Capture` → `CameraController.capture` → page added `Pending` → processor enqueued → `pageCount` 1 + thumbnail when `Ready`; shutter action captures regardless of gate; flash/auto toggles persisted through `CaptureSettingsRepository`; `Done` → effect `NavigateToHandoff(sessionId)` and session `HandedOff`; capture failure → page `Failed` and a hint; `storageFull` when `freeBytes < 50 MB`)
- [ ] T035 [P] [US1] Write `feature/capture/src/commonTest/kotlin/app/plainpaper/feature/capture/handoff/HandoffViewModelTest.kt` (lists pages in order with states; updates as pages become `Ready`; `processingRemaining` count)

### Implementation for User Story 1

- [ ] T036 [P] [US1] Create `feature/capture/.../camera/AutoCaptureGate.kt` (research R5) to satisfy T033
- [ ] T037 [P] [US1] Create `feature/capture/.../camera/CameraUiState.kt`, `CameraAction.kt`, `CameraEffect.kt`, `CameraViewModel.kt` to satisfy T034 (frames collected on `dispatchers.default`, detect throttled to one in flight, hint derived from gate/mode)
- [ ] T038 [P] [US1] Create `feature/capture/.../handoff/HandoffUiState.kt`, `HandoffViewModel.kt`, `HandoffScreen.kt` (contracts/ui.md placeholder; marks session `HandedOff`) to satisfy T035
- [ ] T039 [US1] Implement `core/capture/src/androidMain/.../camera/CameraXController.kt` (Preview + ImageAnalysis RGBA 640 px keep-latest + ImageCapture JPEG ≤ 12 MP to `outputDirectory/original-<n>.jpg`, flash mode, `ProcessCameraProvider.awaitInstance`, lifecycle from the host activity via `LocalLifecycleOwner`) and `CameraXPreview.kt` (`CameraXViewfinder` from the `SurfaceRequest`; fallback `PreviewView` in `AndroidView` if the Compose versions clash — note which in research R1)
- [ ] T040 [US1] Implement `core/capture/src/iosMain/.../camera/AvCameraController.kt` (`AVCaptureSession`, video data output BGRA 640 px with a Kotlin delegate, `AVCapturePhotoOutput` JPEG with flash, start off-main) and `AvCameraPreview.kt` (`UIKitView` hosting a `UIView` with `AVCaptureVideoPreviewLayer`, `layoutSubviews` resize, `resizeAspectFill`) (research R2)
- [ ] T041 [US1] Create `feature/capture/.../camera/CameraScreen.kt` (layers per contracts/ui.md: preview, `PpOutline`, top bar with close/flash/Auto, hint pill, counter + thumbnail, shutter, Done; capture flash animation; always dark) and `CameraRoute` composable collecting effects → navigation
- [ ] T042 [US1] Write `core/capture/src/androidDeviceTest/kotlin/app/plainpaper/core/capture/CameraSmokeTest.kt` (binds the controller on the emulator virtual-scene camera, receives ≥ 5 frames, captures one JPEG that decodes with `PhotoDecoder`) and `scripts/camera-smoke-android.sh` (creates/starts AVD `plainpaper-camera` with `hw.camera.back=virtualscene`, runs the test); run it green locally
- [ ] T043 [US1] Install on the emulator, walk through quickstart §3 steps 1–3 (preview < 1.5 s, outline tracks, auto-capture, counter, thumbnail, Done → hand-off grid); fix; push and confirm CI green; commit `capture: camera screen`

**Checkpoint**: a page scanned from the camera reaches the hand-off screen as a clean Scan-style page.

---

## Phase 4: User Story 2 - Import photos from the gallery (Priority: P2)

**Goal**: system picker, free limit with Pro sheet, progress screen with tiles/estimate, cancel with keep/discard, skipped items, hand-off.

**Independent Test**: quickstart §3 step 7 on the emulator (push 12 photos to the gallery first); `ImportViewModelTest` on the host.

### Tests for User Story 2

- [ ] T044 [P] [US2] Write `feature/capture/src/commonTest/kotlin/app/plainpaper/feature/capture/import/ImportViewModelTest.kt` (picker returns 12 as free → `LimitSheet(12, 10)`; `ImportFirst` → session of 10 in picked order, tiles `Queued`; conversions advance `doneCount`, active tile, estimate = avg of last 3 × remaining; unreadable item → `Skipped` and batch continues; no-page → page `NeedsAttention` but counts as done; `Cancel` → processor cancelled within the test clock, `CancelChoice`; `Keep` → `NavigateToHandoff`, `Discard` → session discarded + `NavigateHome`; all done → `Finished` heading counts; Pro → no sheet; picker cancelled → `NavigateBack`; `SeePro` → `NavigateToPro`)
- [ ] T045 [P] [US2] Write `feature/capture/src/commonTest/kotlin/app/plainpaper/feature/capture/import/ImportEstimateTest.kt` (moving average of last three durations × remaining, rounded up, min 1 s)

### Implementation for User Story 2

- [ ] T046 [P] [US2] Create `feature/capture/.../import/ImportUiState.kt`, `ImportAction.kt`, `ImportEffect.kt`, `ImportEstimate.kt`, `ImportViewModel.kt` to satisfy T044–T045 (copies ALL originals via `ImageFiles.copyOriginal` into the session before any conversion starts, enqueues pages with tag `Document`, observes the session for tile states)
- [ ] T047 [P] [US2] Implement `core/capture/src/androidMain/.../picker/AndroidPhotoPicker.kt` (`rememberLauncherForActivityResult(PickMultipleVisualMedia(maxItems))`, `ImageOnly`, suspend bridge through a `CompletableDeferred`) and `core/capture/src/iosMain/.../picker/PhPhotoPicker.kt` (`PHPickerViewController` presented from the root view controller, `selectionLimit`, `loadFileRepresentation` copies into a temp dir) plus `ImageFiles.copyOriginal` actuals (Android `ContentResolver.openInputStream`, iOS file move)
- [ ] T048 [US2] Create `feature/capture/.../import/ImportScreen.kt` (contracts/ui.md: title "From Photos", heading/sub, progress bar with range info, 4-column `PpProgressTile` grid, privacy card, limit sheet, cancel-choice sheet, "Review N pages") and the `ImportRoute` composable (launches the picker on entry)
- [ ] T049 [US2] Add the `ProPlaceholder` route screen in `composeApp` ("Plainpaper Pro is coming" + body, back) and strings (EN/AR); wire `SeePro`
- [ ] T050 [US2] Emulator walkthrough of quickstart §3 step 7 with 12 pushed photos (one deliberately corrupt): limit sheet, progress, skip, cancel keep/discard, finish → hand-off; push, CI green; commit `capture: import flow`

**Checkpoint**: 10 gallery photos become 10 ordered pages with progress, and a free user is never silently truncated.

---

## Phase 5: User Story 3 - Capture many pages in one session (Priority: P3)

**Goal**: fast re-arm after capture, discard confirmation, survive background/foreground and process death, Done while processing, 30-page sessions on low-end.

**Independent Test**: quickstart §3 steps 5–6 and §4; repository/processor tests on the host.

### Tests for User Story 3

- [ ] T051 [P] [US3] Extend `CameraViewModelTest.kt` (close with pages → `showDiscardConfirm`; `ConfirmDiscard` → session discarded + `NavigateBack`; `KeepScanning` hides; close with 0 pages → `NavigateBack` directly; `Done` while 2 pages `Processing` → navigates and processing continues; lifecycle `Stopped`/`Started` → camera stopped/restarted, session untouched; 30 captures keep order and count)
- [ ] T052 [P] [US3] Write `feature/capture/src/commonTest/kotlin/app/plainpaper/feature/capture/recovery/RecoverSessionTest.kt` (recoverable session with pages → `RecoverSession` returns it once; after `Discard` it is gone; `Continue` keeps status; `Processing` pages come back `Pending` and `PageProcessor.resume()` re-queues exactly those)
- [ ] T053 [P] [US3] Write `feature/home/src/commonTest/kotlin/app/plainpaper/feature/home/HomeViewModelTest.kt` additions (recovery card shown when a session is recoverable; `ContinueRecovery` → `NavigateToHandoff(id)`; `DiscardRecovery` → discarded and card hidden)

### Implementation for User Story 3

- [ ] T054 [P] [US3] Extend `CameraViewModel`/`CameraScreen` with the discard confirmation dialog, lifecycle-aware start/stop (`Lifecycle.repeatOnLifecycle` from the route), re-arm within 1 s (gate `Cooldown` → preview active immediately; flash animation 300 ms), storage-full guard, Done-while-processing
- [ ] T055 [P] [US3] Implement `core/capture/src/iosMain/.../background/IosBackgroundTask.kt` (`beginBackgroundTaskWithExpirationHandler` → cancels the block) and `AndroidBackgroundTask.kt` (pass-through); call `PageProcessor.resume()` from `PlainpaperApp` on foreground (`LifecycleEventObserver` ON_START) and at launch
- [ ] T056 [US3] Add the recovery card to `feature/home` (`HomeUiState.recoverable`, actions `ContinueRecovery`/`DiscardRecovery`, strings EN/AR "You have an unfinished scan of %1$d pages", "Continue", "Discard"; `feature/home` gains `projects.core.domain` use cases only) and route `NavigateToHandoff` in `AppNavHost`
- [ ] T057 [US3] Emulator: capture 6 pages, background/return, force-stop (`adb shell am force-stop`), relaunch → recovery card → Continue → 6 pages; low-end AVD: 30 pages without a crash (quickstart §4); push, CI green; commit `capture: session persistence and recovery`

**Checkpoint**: no page is lost across interruptions; a 30-page session works on the low-end profile.

---

## Phase 6: User Story 4 - Choose a capture mode (Priority: P4)

**Goal**: Document / ID card / Receipt modes with hints, guides, tags, Natural proportions, mode remembered.

**Independent Test**: quickstart §3 step 4; `ModeSequencerTest` on the host.

### Tests for User Story 4

- [ ] T058 [P] [US4] Write `feature/capture/src/commonTest/kotlin/app/plainpaper/feature/capture/camera/ModeSequencerTest.kt` (IdCard: captures → `IdFront(c1)`, `IdBack(c1)`, `IdFront(c2)`; switching to Document mid-card → `Document`, back to IdCard → new card; Receipt → `Receipt`; hints `FrontOfCard`/`BackOfCard`/`FitReceipt`; page size `Natural` for IdCard/Receipt, region default for Document)
- [ ] T059 [P] [US4] Extend `CameraViewModelTest.kt` (`SelectMode` persists via settings and changes hint/guide; earlier pages keep their tags; mode restored on next start)

### Implementation for User Story 4

- [ ] T060 [P] [US4] Create `feature/capture/.../camera/ModeSequencer.kt` to satisfy T058 and use it in `CameraViewModel` (`AddCapturedPage` with tag + page size)
- [ ] T061 [US4] Add the `PpSegmentedControl` modes row, card-shaped guide overlay for IdCard (aspect 1.586, centred, 70 % width) and the mode hints to `CameraScreen.kt`; strings EN/AR
- [ ] T062 [US4] Emulator walkthrough of quickstart §3 step 4; push, CI green; commit `capture: modes`

**Checkpoint**: ID front/back pairs and receipts carry the tags and proportions Feature 7 needs.

---

## Phase 7: User Story 5 - Permissions asked only when needed (Priority: P5)

**Goal**: explanation before the first system prompt, denial panel with import and settings, no prompt on launch/Home/import, recovery after granting in settings, no-camera devices go to import.

**Independent Test**: quickstart §3 step 8; `CameraPermissionFlowTest` on the host.

### Tests for User Story 5

- [ ] T063 [P] [US5] Write `feature/capture/src/commonTest/kotlin/app/plainpaper/feature/capture/permission/CameraPermissionFlowTest.kt` (ViewModel: `NotAsked` → explanation panel, camera not started; `AllowCamera` → `request()`; `Granted` → camera starts; `Denied` → denial panel with retry; `DeniedPermanently` → "Open settings" action calls `openSettings()`, no further `request()`; foreground resume re-queries and starts when `Granted`; `Unavailable` → effect `NavigateToImport` with notice; `ImportInstead` → `NavigateToImport`)

### Implementation for User Story 5

- [ ] T064 [P] [US5] Implement `core/capture/src/androidMain/.../permission/AndroidCameraPermission.kt` (`RequestPermission` launcher, `shouldShowRequestPermissionRationale` + "asked before" flag in preferences to classify `DeniedPermanently`, `FEATURE_CAMERA_ANY`, settings intent) and `core/capture/src/iosMain/.../permission/IosCameraPermission.kt` (`AVCaptureDevice.authorizationStatus`, `requestAccess`, `UIApplicationOpenSettingsURLString`, device discovery for `Unavailable`)
- [ ] T065 [US5] Create `feature/capture/.../permission/PermissionPanels.kt` (explanation + denial panels per contracts/ui.md, strings EN/AR) and integrate in `CameraScreen`/`CameraViewModel` to satisfy T063; verify Home and Import never call `request()` (test in T063 + `HomeViewModelTest`)
- [ ] T066 [US5] Emulator: `adb shell pm revoke app.plainpaper android.permission.CAMERA` paths from quickstart §3 step 8 (deny → import works → grant in settings → camera works on return); push, CI green; commit `capture: permission flow`

**Checkpoint**: every spec permission scenario passes; no flow is a dead end.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T067 [P] Write `feature/capture/src/androidDeviceTest/kotlin/app/plainpaper/feature/capture/CaptureSemanticsTest.kt` (Compose UI test with fakes: every control on the camera and import screens has a content description/role, `assertTouchHeightIsEqualTo`/width ≥ 48 dp, hint pill is a live region, RTL: close at the end and Done at the start, Arabic strings resolve) and run on the emulator (SC-010); attach light/dark/RTL screenshots of both screens to the PR description
- [ ] T068 [P] Measure SC-001…SC-006 on the emulator (and the owner's phone when available): preview start time, detect micros per frame (`ScanReport`-style log), auto-capture latency over 20 trials, 10-photo import duration, cancel latency; record in `specs/003-capture-import/results.md` with device/build
- [ ] T069 [P] Update `CLAUDE.md` (module map: `:core:capture`, `:feature:capture`; commands: camera smoke script, device tests; rule: camera code only in `:core:capture`) and `README.md`
- [ ] T070 [P] Review Kover: `./gradlew koverXmlReport koverVerify` ≥ 80 % with the new modules; add tests for uncovered decisions
- [ ] T071 [P] Detekt/Spotless clean; `scripts/check-hardcoded-strings.sh` clean; remove the obsolete placeholder routes/strings; confirm no new permission beyond CAMERA in the merged manifest (`./gradlew :androidApp:processDebugManifest` + inspect)
- [ ] T073 [P] Write `feature/capture/src/androidDeviceTest/kotlin/app/plainpaper/feature/capture/CaptureSoakDeviceTest.kt` (real repository + processor + engine with bundled photos and a fake camera: 50 sessions × 5 pages, 20 imports × 10 photos, 10 simulated background/foreground cycles via `PageProcessor.resume()`; asserts zero `Failed`, zero lost pages, no crash) and run it on the low-end AVD (SC-008); record in `results.md`
- [ ] T072 Run the full quickstart.md checklist (sections 1–6), fix anything that fails, mark PR #3 ready for review, ensure all six required checks are green, merge into `main` (merge commit); update memory `plainpaper-progress`

---

## Dependencies & Execution Order

- **Phase 1 → Phase 2 → stories**: T001–T008 then T009–T032 are prerequisites. Within Phase 2 the four tracks (domain, data, engine detect, services/processor) run in parallel after T012–T013.
- **US1 (P1)** is the MVP and depends only on Phase 2 (hand-off placeholder T038 is part of US1 so the flow is demonstrable).
- **US2** depends on Phase 2 + T038 (hand-off); independent of US1's camera code.
- **US3** depends on US1 (camera screen) and the repository (T017); the recovery card touches `feature/home`.
- **US4** depends on US1; **US5** depends on US1's screen but its permission actuals (T064) can be written during US2/US3.
- **Owner-dependent**: iPhone timings in T068 (CI builds iOS; the owner runs it). Everything else runs on the emulator.

## Parallel Execution Examples

- Phase 1: T003–T008 in parallel after T001–T002.
- Phase 2: T009–T011 together, then T012–T014; T015–T016 with T017–T018; T020–T021 with T022; T025–T028 together after T012/T023.
- US1: T033–T035 together; T036–T038 together; T039 and T040 together (Android vs iOS), then T041.
- US2: T044–T045 with T046–T047.
- US3: T051–T053 together; T054–T055 together.
- Polish: T067–T071 together.

## Implementation Strategy

1. **MVP = Phases 1–3**: camera → clean page → hand-off. Import, persistence, modes and permission panels are additive.
2. Keep the draft PR open from T032 so both platforms compile on every push (iOS only compiles in CI).
3. Inner loop = host tests with fakes (seconds); emulator for camera plumbing and walkthroughs; the low-end AVD for SC-008 samples.
4. Small commits: one per task or per screen, message `type: subject` (`capture:`, `scanengine:`, `domain:`, `data:`, `designsystem:`, `app:`, `ci:`, `specs:`).
