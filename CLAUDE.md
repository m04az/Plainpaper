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
| `:core:testing` | `TestDispatcherProvider`, `FakePreferencesRepository` |
| `:feature:home`, `:feature:settings` | ViewModel + UiState + Action + Effect + screens + Koin module per feature |

Reserved (not yet created): `:core:database`, `:core:scanengine`.

## Commands

```sh
./gradlew spotlessApply detekt                 # format + static analysis
./gradlew testAndroidHostTest                  # all commonTest on the JVM
./gradlew koverXmlReport koverVerify           # coverage report + 80% gate
./gradlew :androidApp:assembleDebug            # Android APK
./gradlew :androidApp:installDebug && adb shell am start -n app.plainpaper/.android.MainActivity
./gradlew iosSimulatorArm64Test                # iOS unit tests (needs Xcode)
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64   # iOS framework (needs Xcode)
```

## Environment notes

- JDK 21. The Gradle daemon JVM is pinned to 21 in `gradle/gradle-daemon-jvm.properties`
  (detekt 1.23 cannot run on JDK 25).
- compileSdk 37 is required by Compose 1.12; targetSdk 36, minSdk 26.
- Xcode 26.4 is required for any iOS task. If it is not installed, iOS is verified by CI.
- Feature specs, plans and tasks live in `specs/<nnn-name>/`; run the Spec Kit cycle
  (`/speckit-specify` → clarify → plan → tasks → analyze → implement) per feature.
- The approved UI design is the Claude artifact linked in `specs/001-app-foundation/spec.md`.
