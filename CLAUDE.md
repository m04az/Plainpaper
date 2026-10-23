# Plainpaper — agent guide

Plainpaper turns photos of paper into documents that look like they came from a flatbed scanner.
Kotlin Multiplatform + Compose Multiplatform, Android and iOS from one codebase.

## Rules that override everything else

- The constitution at `.specify/memory/constitution.md` governs. Read it before changing code.
- Tests first: every use case, repository, mapper and ViewModel has a `commonTest` written with
  or before the implementation. Kover gates domain + presentation at 80%.
- No user-visible string literal in Kotlin. Every string lives in `composeResources/values*/strings.xml`
  (English + Arabic). `scripts/check-hardcoded-strings.sh` enforces this in CI.
- Nothing leaves the device. No network permission, no analytics, no ad SDKs.
- Core modules (`:core:*`) use explicit API mode: public declarations need `public`.
- Commit each finished artifact separately with a short message. No co-author trailers.

## Module map

| Module | Role |
|--------|------|
| `build-logic/` | Gradle convention plugins (`plainpaper.kmp.library`, `.compose`, `.feature`, `.spotless`, `.detekt`, `.kover`) |
| `:androidApp` | Android host (`com.android.application`, AGP 9 split): `MainActivity`, manifest, launcher icon |
| `iosApp/` | Xcode host; links the `ComposeApp` framework |
| `:composeApp` | Shared app: `PlainpaperApp`, navigation, locale environment, Koin wiring, platform modules |
| `:core:domain` | Pure Kotlin models, repository interfaces, use cases, `DispatcherProvider` |
| `:core:data` | Repository implementations (Preferences DataStore), `dataModule` |
| `:core:designsystem` | `PlainpaperTheme`, tokens, fonts, `Pp*` components, `PpIcons` |
| `:core:scanengine` | `ScanEngine` façade (commonMain), native C++ core in `native/` (OpenCV 4.13, C API `plainpaper_scan.h`), JNI bridge (Android + host JVM), cinterop bridge (iOS), photo decoders, capability detection, `scanEngineModule` |
| `:core:scanengine-android` | `com.android.library` that runs the NDK/CMake build of the C++ core (the KMP plugin cannot) |
| `:core:testing` | `TestDispatcherProvider`, `FakePreferencesRepository`, `FakeScanEngine`, `FakeNativeBridge` |
| `:feature:home`, `:feature:settings` | ViewModel + UiState + Action + Effect + screens + Koin module per feature |
| `:tools:scansuite` | Host JVM quality suite: `reference-set/` + generated synthetic set, metrics, Tesseract legibility, `QualityGateTest` (CI gate) |

Reserved (not yet created): `:core:database`.

## Scan engine rules

- The C++ pipeline lives in `core/scanengine/native/src` (one stage per file, constants in
  `params.hpp`); the C API in `native/include/plainpaper_scan.h` is the contract for both bridges.
- Never change a pipeline stage without running `./gradlew :tools:scansuite:test`; the synthetic
  set (`tools/scansuite/.../synthetic/`) gates corners, orientation, Scan tone and legibility, and
  real photos in `reference-set/` are added with `scripts/scanref/ingest.py` + `annotate.py`.
- Debugging a photo: `PP_DEBUG_DIR=/tmp/dbg ./gradlew :tools:scansuite:run --args="--only <id>"`
  dumps PGM stages and prints `[pp]` logs from the native code.
- Native formatting: `clang-format` (`scripts/check-native-format.sh`), enforced in CI.

## Commands

```sh
./gradlew spotlessApply detekt                 # format + static analysis
./gradlew testAndroidHostTest                  # all commonTest on the JVM
./gradlew koverXmlReport koverVerify           # coverage report + 80% gate
./gradlew :androidApp:assembleDebug            # Android APK
./gradlew :androidApp:installDebug && adb shell am start -n app.plainpaper/.android.MainActivity
./gradlew iosSimulatorArm64Test                # iOS unit tests (needs Xcode)
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64   # iOS framework (needs Xcode)
./gradlew :tools:scansuite:test                # scan-engine quality gate (host OpenCV + tesseract)
./gradlew :tools:scansuite:run --args="--styles scan,colordocument"   # full report in build/scansuite/
scripts/bench-android.sh [--low-end]           # device benchmark -> reference-set/benchmarks/
```

## Environment notes

- JDK 21. The Gradle daemon JVM is pinned to 21 in `gradle/gradle-daemon-jvm.properties`
  (detekt 1.23 cannot run on JDK 25).
- compileSdk 37 is required by Compose 1.12; targetSdk 36, minSdk 26.
- Xcode 26.4 is required for any iOS task. If it is not installed, iOS is verified by CI.
- Scan engine prerequisites: `brew install cmake ninja tesseract tesseract-lang clang-format`, NDK
  r30 + CMake 3.22.1 from the SDK Manager; `scripts/build-opencv-host.sh` builds the host OpenCV
  once into `~/.cache/plainpaper/`. The Gradle tasks download the OpenCV Android SDK and iOS
  xcframework (checksums in `gradle/libs.versions.toml`).
- Feature specs, plans and tasks live in `specs/<nnn-name>/`; run the Spec Kit cycle
  (`/speckit-specify` → clarify → plan → tasks → analyze → implement) per feature.
- The approved UI design is the Claude artifact linked in `specs/001-app-foundation/spec.md`.
