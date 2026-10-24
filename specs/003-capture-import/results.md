# Results: Capture & Import (003)

Numbers from the development machine (Apple M5 Pro host) and the Pixel 10a AVD
(`emu64a16k`, API 37, arm64, 4 GB class → Simplified pipeline, virtual-scene camera at
640 × 480 / 1280 × 960). Phone numbers (mid-range Android, iPhone) are pending the owner.

## Automated checks

| Check | Result |
|---|---|
| Host unit tests (`testAndroidHostTest`) | all green: domain 11 files, data 4, scanengine 13, capture 1, feature:capture 9 (55 tests), feature:home 5 |
| `pp_detect` on the synthetic set (`DetectTest`) | 26 plain scenes found with high confidence within 0.02 of the diagonal; no-page scenes NotFound; BGRA = RGBA; host median 1.8 ms per 640 px frame |
| `QualityGateTest` (scan-quality) | unchanged, green |
| Kover merged line coverage | 90.2 % (gate ≥ 80 %) |
| `CameraSmokeTest` (emulator virtual-scene camera) | frames flow, capture decodes, stop clean |
| `CaptureSemanticsTest` (SC-010) | labels + 48 dp targets on both screens, live-region hint, RTL mirroring: 3/3 green (it caught a 44 dp close button, now fixed) |
| `CaptureSoakDeviceTest` (SC-008 sample) | see below |

## Spec success criteria

| SC | Target | Measured | Where |
|---|---|---|---|
| SC-001 preview visible | ≤ 1.5 s mid-range | 1.08 s cold / 0.13 s warm from `start()` to `Running` on the emulator | `CameraSmoke` logcat |
| SC-002 outline rate / lag | ≥ 15/s, < 150 ms | detect median see smoke log (host 1.8 ms); analysis stream 30 fps on the emulator | `CameraSmokeTest` |
| SC-003 auto-capture within 1 s of steadiness | ≥ 90 % of 20 trials | gate fires at exactly the 700 ms window in `AutoCaptureGateTest`; no real page can be shown to the emulator camera (poster flag ignored) → phone trial pending | unit test |
| SC-004 preview live again < 1 s; 10 pages processed ≤ 5 s after the last capture | | capture flash 300 ms; pages process concurrently (2 workers on Full, 1 on Simplified); emulator: 2 pages ready within ~3 s of the second shutter | emulator walkthrough |
| SC-005 10-photo import | ≤ 25 s mid-range / 60 s low-end | see soak import timings below (emulator, Simplified) | `CaptureSoakDeviceTest` |
| SC-006 cancel ≤ 1 s | 100 % | `PageProcessor.cancel` cancels the in-flight job immediately; `ImportViewModelTest.cancelOffersKeepOrDiscard` | unit test |
| SC-007 denied → import in ≤ 2 taps | 100 % | denial panel → "Import photos instead" (1 tap) → picker | emulator walkthrough |
| SC-008 zero crashes / lost pages | 50 sessions × 5, 20 imports × 10, 10 cycles | see soak below | `CaptureSoakDeviceTest` |
| SC-009 free limit explained before conversion | 100 % | `ImportViewModelTest.freeUsersSeeTheLimitSheetAndCanImportTheFirstTen`; limit applied in Kotlin regardless of picker cap | unit test |
| SC-010 labels, 48 dp, RTL, themes | all controls | `CaptureSemanticsTest` green; screenshots attached to PR #3 | device test |

## Soak (SC-008 sample)

SOAK_PLACEHOLDER

## Screenshots (Pixel 10a AVD, light theme)

`screenshots/camera.png`, `camera-id-card.png`, `home-recovery.png`, `import-picker.png`,
`import-finished.png`, `permission-explanation.png`, `handoff.png`.

## Emulator walkthrough (quickstart §3)

1. Home → Scan: explanation panel on first use, system prompt, live preview with outline.
2. Shutter: capture, counter 1, thumbnail of the processed page (NeedsAttention: no page in the
   virtual scene), Done → hand-off grid.
3. Flash/Auto toggles persist across relaunch; Auto off shows "Tap the shutter when ready".
4. ID card mode: dashed card guide, "Front side of the card" → "Back side of the card".
5. Background + return: camera resumes with the counter intact; force-stop + relaunch: Home
   shows "You have an unfinished scan of 2 pages" → Continue → hand-off with both pages.
6. Close with pages: discard confirmation.
7. Import: picker with 13 pushed photos (one corrupt) → 4 picked → "3 pages ready", one
   skipped with a warning tile, "Review 3 pages" → hand-off.
8. Permission revoked: explanation → system prompt → "Don't allow" → denial panel with
   "Import photos instead" / second refusal → "Open settings"; granted in settings → camera
   works on return.

## Pending on the owner's devices

- Auto-capture on a real page (SC-003 trials) and SC-001/SC-002/SC-005 timings on the
  mid-range phone and iPhone; the low-end AVD run of the soak.
- iOS: the AVFoundation camera, PHPicker and permission paths compile in CI only (no Xcode
  locally) and need a manual run on the iPhone.
