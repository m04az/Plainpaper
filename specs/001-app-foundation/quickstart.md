# Quickstart: App Foundation

How to build, test and validate feature 001 end-to-end. Commands run from the repository root.

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK | 21 (Temurin or Zulu) | `java -version` |
| Android SDK | platform 37 (compileSdk), build-tools 36.0.0 | Android Studio or `sdkmanager` |
| Xcode | 26.4 | **Not installed on the dev Mac yet** (research R12). Required for `test-ios`/`build-ios` locally; CI covers iOS until then. |
| Android device/emulator | API 26+ | Pixel-class emulator image or a physical phone |
| iPhone simulator | iOS 17+ runtime | installed with Xcode |

## 1. Build everything (JVM side)

```sh
./gradlew spotlessCheck detekt
./gradlew :androidApp:assembleDebug
```

Expected: both succeed; APK at `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

## 2. Run unit tests and the coverage gate

```sh
./gradlew testAndroidHostTest koverXmlReport koverVerify
```

Expected: all tests green; `koverVerify` passes with ≥ 80% line coverage over domain +
presentation. The HTML report is at `build/reports/kover/html/index.html`.

## 3. Install and launch on Android (US1)

```sh
./gradlew :androidApp:installDebug
adb shell am start -n app.plainpaper/.MainActivity
```

Measure cold start (SC-001, after a force-stop):

```sh
adb shell am force-stop app.plainpaper
adb shell am start -W -n app.plainpaper/.MainActivity | grep TotalTime
```

Expected: `TotalTime` under 3000 ms on a mid-range device.

Check:
- Launcher shows "Plainpaper" with the paper icon.
- Home title "Documents", empty state "No documents yet", primary "Scan a page", secondary
  "Import photos", floating tab bar (Documents / Folders / Settings) and round Scan button.
- "Scan a page", the Scan button and "Import photos" each open a placeholder with a back arrow.
- Folders tab opens its placeholder inside the tab bar.

## 4. Settings and persistence (US3)

- Settings tab → Appearance → Dark: whole app re-themes immediately.
- Set Appearance → System, toggle device dark mode in quick settings: app follows.
- Appearance → Light, force-stop and relaunch: still light.

## 5. Arabic and RTL (US2)

- Settings → Language → العربية: all text switches to Arabic; tab bar order, back arrows,
  chevrons and alignment mirror immediately with no restart.
- Force-stop and relaunch: still Arabic/RTL.
- Language → Follow system, set the device language to Arabic, relaunch: Arabic/RTL.
- Set device language to French with "Follow system": English/LTR fallback.

## 6. iOS simulator (US1, US2) — requires Xcode

```sh
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
open iosApp/iosApp.xcodeproj   # Run on an iPhone simulator
```

Repeat steps 3–5 on the simulator. **Specifically verify** (research R6 risk) that choosing
العربية in Settings updates the strings in-process. If the strings stay English while the
layout mirrors, apply the R6 fallback (iOS-only "restart to apply" notice) and record it in
the spec's Assumptions before closing the feature.

iOS unit tests:

```sh
./gradlew iosSimulatorArm64Test
```

## 7. Accessibility and dynamic type

- Android: Settings → Display → Font size → Largest. Home and Settings remain untruncated.
- TalkBack / VoiceOver: every button, tab, row and the back arrow announces a label.

## 8. CI (US4)

1. Push the branch and open a pull request to `main`.
2. Confirm the five checks run and pass: `static-analysis`, `test-android`, `build-android`,
   `test-ios`, `build-ios`.
3. Negative check: push a commit that breaks formatting (for example, remove a newline at end
   of a Kotlin file); `static-analysis` must fail and the PR must show "not mergeable".
   Revert it.
4. One-time branch protection (manual, repository setting):

```sh
gh api -X PUT repos/m04az/Plainpaper/branches/main/protection \
  --input - <<'EOF'
{
  "required_status_checks": {
    "strict": true,
    "contexts": ["static-analysis", "test-android", "build-android", "test-ios", "build-ios"]
  },
  "enforce_admins": true,
  "required_pull_request_reviews": null,
  "restrictions": null
}
EOF
```

## Success criteria mapping

| Criterion | Verified by |
|-----------|-------------|
| SC-001 launch on both platforms | steps 3, 6 |
| SC-002 Arabic flips all screens | step 5 (and 6 on iOS) |
| SC-003 persistence | steps 4, 5 |
| SC-004 dynamic type | step 7 |
| SC-005 pipeline runs and fails on formatting | step 8 |
| SC-006 ≥ 80% coverage | step 2 and `test-android` job |
| SC-007 clone-to-build documented | this file + `CLAUDE.md` |
