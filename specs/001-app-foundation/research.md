# Research: App Foundation

**Feature**: 001-app-foundation | **Date**: 2026-10-23

All versions below were checked against Maven Central, Google Maven, the Kotlin Multiplatform
compatibility guide and the Compose Multiplatform 1.12.2 release notes on 2026-10-23.

## R1. Toolchain versions

**Decision**: Kotlin 2.4.21, Compose Multiplatform 1.12.2, AGP 9.3.1, Gradle 9.6.0 (wrapper
already present), JDK 21 (installed locally; CI uses Temurin 21), compileSdk/targetSdk 36,
minSdk 26, iOS deployment target 15.0, Xcode 26.4 on CI.

**Rationale**: The KMP compatibility guide row for Kotlin 2.4.20–2.4.21 is Gradle 7.6.3–9.7.0,
AGP 8.5.2–9.3.1, Xcode 26.4. The template ships AGP 9.4.1, which is outside the tested range,
so the plan steps it down to 9.3.1. Compose Multiplatform 1.12.2 is the current stable (Oct
15, 2026) and is always compatible with the latest Kotlin; the Compose compiler plugin version
must equal the Kotlin version. compileSdk 36 is the 2026 Play target requirement and avoids
depending on API 37 support in AGP 9.3.

**Alternatives considered**: Kotlin 2.5.0-Beta2 (rejected, pre-release). AGP 9.4.1 (rejected,
untested with Kotlin 2.4.x per JetBrains). Compose Multiplatform 1.13.0-alpha02 (rejected,
pre-release).

## R2. AGP 9 module topology

**Decision**: Shared code lives in `:composeApp` using `org.jetbrains.kotlin.multiplatform` +
`com.android.kotlin.multiplatform.library`. The Android entry point (`MainActivity`,
manifest, launcher icon, app id) lives in a separate `:androidApp` module using
`com.android.application`. `:iosApp` is the Xcode host. Every core and feature module uses
the KMP library plugin via a convention plugin.

**Rationale**: AGP 9 makes `com.android.application` and `com.android.library` incompatible with
the KMP plugin in the same module; JetBrains' AGP 9 migration guide prescribes exactly this
split. `androidResources { enable = true }` must be set in `kotlin.androidLibrary {}` so
Compose resources are packaged. The KMP library plugin has no build variants, so tooling
dependencies use `androidRuntimeClasspath(...)`.

**Alternatives considered**: `android.enableLegacyVariantApi=true` to keep one module (rejected:
removed in AGP 10, second half of 2026). Putting `MainActivity` in `:composeApp` (not possible
with AGP 9).

## R3. Lifecycle, ViewModel and navigation

**Decision**: `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0` and
`lifecycle-runtime-compose:2.11.0`; `org.jetbrains.androidx.navigation:navigation-compose:2.10.0-rc01`
with `@Serializable` type-safe routes (kotlinx-serialization 1.12.0).

**Rationale**: Lifecycle 2.11.0 is the first stable with full KMP ViewModel-Compose support and
is the version Compose Multiplatform 1.12.2 lists. Navigation 2.10.0-rc01 is the navigation
build JetBrains pairs with 1.12.2 and lifecycle 2.11.0; the latest stable JetBrains navigation
(2.9.2) targets lifecycle 2.9.x. The playbook asks for Navigation Compose with type-safe routes,
which is the Nav2 API.

**Alternatives considered**: Navigation 3 (`navigation3-ui:1.2.0-rc01`), also rc and supported
in CMP 1.12; rejected for this feature because the playbook names Navigation Compose and
Nav3 adds back-stack ownership concepts the shell does not need yet. Can be revisited when
the document library needs nested graphs. Navigation 2.9.2 stable: rejected for the lifecycle
mismatch.

## R4. Dependency injection

**Decision**: Koin 4.2.2 (`koin-core`, `koin-compose`, `koin-compose-viewmodel`).

**Rationale**: Playbook choice; 4.2.2 is the current stable (June 2026), supports all targets,
and `koinViewModel()` integrates with lifecycle 2.11.

**Alternatives considered**: Manual DI (rejected: nine modules already; feature modules need a
composition root). kotlin-inject (rejected: playbook names Koin).

## R5. Preferences storage

**Decision**: `androidx.datastore:datastore-preferences-core:1.3.0` with an `expect`/`actual`
path provider (Android: `context.filesDir`, iOS: `NSDocumentDirectory`). Keys: `language`
(`system|en|ar`) and `theme` (`system|light|dark`).

**Rationale**: Playbook choice; 1.3.0 stable (Oct 21, 2026); only Preferences DataStore is
supported on KMP. No database is needed for this feature.

**Alternatives considered**: Room (rejected: nothing relational yet, Principle IX).
multiplatform-settings (rejected: extra dependency when DataStore is already planned).

## R6. Runtime language switching

**Decision**: Common `LocalAppLocale` `expect object` per the JetBrains "resource environment"
guide: common code holds the chosen language tag (`null` = follow system) in a
`mutableStateOf`; `AppEnvironment` wraps content in
`CompositionLocalProvider(LocalAppLocale provides tag, LocalLayoutDirection provides dir) { key(tag) { content() } }`.
Android actual: `Locale.setDefault(Locale.forLanguageTag(tag))` plus an updated
`Configuration` provided through `LocalConfiguration`. iOS actual: write `AppleLanguages` to
`NSUserDefaults.standardUserDefaults` and rely on `key(tag)` recomposition. Layout direction
is derived in common code from the effective language (`ar` → RTL), never from the platform.

**Rationale**: Compose resources resolve the locale through an internal `getSystemLocales()`;
`LocalComposeEnvironment` and the `ResourceEnvironment` constructor are internal, so the only
documented route is the platform override, which JetBrains publishes as the official
workaround. Deriving layout direction in common code makes RTL deterministic and unit-testable.

**Risk**: Reports (CMP-8376, Apple DTS) say iOS may not re-read `AppleLanguages` in-process.
Mitigation: the quickstart includes an explicit iOS simulator check (T-verify). If strings do
not refresh on iOS, fallback is an iOS-only "Restart to apply" notice on the Language sheet,
recorded as a known limitation in the spec; RTL mirroring still applies immediately because it
is computed in common code.

**Alternatives considered**: AppCompat `setApplicationLocales` on Android (rejected: Android-only,
needs AppCompatActivity, recreates the activity). Hand-rolled string tables in Kotlin
(rejected: duplicates Compose resources, six languages coming). moko-resources (rejected: a
second resource system).

## R7. Theme switching

**Decision**: `ThemeMode { SYSTEM, LIGHT, DARK }` in domain; `PlainpaperTheme(darkTheme = resolve(mode, isSystemInDarkTheme()))`
with Material 3 color schemes built from the design tokens. Light/dark color schemes are
plain `ColorScheme` objects; no dynamic color (brand consistency and API 26 floor).

**Rationale**: `isSystemInDarkTheme()` is already multiplatform in Compose; `System` follows
live changes without extra platform code.

## R8. Fonts

**Decision**: Bundle Inter (Regular, Medium, SemiBold, Bold, ExtraBold) and IBM Plex Sans Arabic
(Regular, Medium, SemiBold, Bold) as Compose resource fonts (`composeResources/font/`),
loaded with `Font(Res.font.inter_regular)` in `:core:designsystem`. Both are OFL-licensed;
licenses copied to `composeResources/files/licenses/`.

**Rationale**: The design specifies these families; Compose Multiplatform resources load fonts
identically on both platforms. Variable fonts are avoided because Compose Multiplatform
resource fonts expose weights per file.

**Alternatives considered**: System fonts only (rejected: design fidelity, Arabic rendering
differs by OEM). Google Fonts provider (rejected: network, Principle I).

## R9. Static analysis, formatting and coverage

**Decision**: Spotless 8.10.4 running ktlint 1.8.0 (`ktlint_official` style, Compose rule set
`io.nlopez.compose.rules:ktlint` is deferred); detekt 1.23.8 with `detekt-formatting` off (ktlint
owns formatting) and a project `detekt.yml`; Kover 0.9.11 with a merged report and a verify
rule of 80% line coverage over `:core:domain`, `:feature:home` and `:feature:settings`
(presentation). Core modules enable `explicitApi()`.

**Rationale**: All current stable releases. detekt 2.0 is still alpha.6. Kover's merged
report lets the gate target the modules the constitution names rather than UI-only code.

**Alternatives considered**: detekt 2.0.0-alpha.6 (rejected: pre-release). Jacoco (rejected:
no KMP support).

## R10. Testing stack

**Decision**: kotlin.test, kotlinx-coroutines-test 1.11.0, Turbine 1.2.1, fakes and a
`TestDispatcherProvider` in `:core:testing`. Compose UI tests (`compose.uiTest`) for Home,
Settings and RTL on the JVM (`desktop` target is not added; UI tests run on Android unit test
via Robolectric is also rejected). Instead, screen-level logic is covered through ViewModel
tests, and screenshot tests are deferred to a later feature (noted in plan Deferred items).

**Rationale**: Principle VI demands ViewModel/use case/repository coverage in commonTest, which
the above delivers without adding a desktop target or Robolectric. Compose Multiplatform UI
tests need either a desktop/JVM target or device tests; adding a desktop target solely for
tests violates Principle IX for this feature.

**Alternatives considered**: Add `jvm("desktop")` target for `compose.uiTest` (deferred:
reconsider when screenshot tests are introduced). Roborazzi (deferred).

## R11. Continuous integration

**Decision**: One workflow `.github/workflows/pr.yml` on `pull_request` to `main` and `push`
to `main`, with jobs:

| Job | Runner | Steps |
|-----|--------|-------|
| `static-analysis` | ubuntu-latest | `spotlessCheck`, `detekt` |
| `test-android` | ubuntu-latest | `allTests` for JVM/Android targets, `koverXmlReport`, `koverVerify`, upload report |
| `build-android` | ubuntu-latest | `:androidApp:assembleDebug`, upload APK |
| `test-ios` | macos-26 | `iosSimulatorArm64Test` |
| `build-ios` | macos-26 | `linkDebugFrameworkIosSimulatorArm64`, then `xcodebuild -scheme iosApp -sdk iphonesimulator -configuration Debug build` |

Actions: `actions/checkout@v7`, `actions/setup-java@v6` (Temurin 21), `gradle/actions/setup-gradle@v6`
(Gradle cache), Kotlin/Native cache via `actions/cache` on `~/.konan`. Concurrency group per
ref cancels superseded runs. Branch protection on `main` requires all five jobs.

**Rationale**: Constitution VII lists exactly these checks; macOS runners are expensive, so iOS
tests and build are separate from the fast ubuntu jobs. `macos-26` has Xcode 26.x and iOS
simulators preinstalled.

**Alternatives considered**: Single macOS job (rejected: slower, costlier). Matrix per module
(rejected: Gradle already parallelises).

## R12. Local iOS verification

**Finding**: The development Mac has only Xcode Command Line Tools; `/Applications/Xcode.app`
is absent. iOS unit tests and the simulator build cannot run locally until Xcode 26.4 is
installed.

**Decision**: The plan does not block on this. Implementation verifies Android locally and iOS
on CI (`test-ios`, `build-ios`). Installing Xcode is listed in quickstart prerequisites.

## R13. App icon

**Decision**: Provisional icon generated from the design mark (white sheet with folded corner
and two ink-blue lines on the paper background): Android adaptive icon (foreground vector,
background color `#F2F1EE`, monochrome layer) in `:androidApp`; iOS `AppIcon.appiconset` with
a single 1024×1024 PNG in `iosApp/Assets.xcassets`. The PNG is rendered from the vector
with `rsvg-convert` or `sips` during implementation and committed.

**Rationale**: Spec FR-002; final brand artwork may replace it without a spec change.

## R14. Removed template

**Decision**: Delete the `:app` module entirely (its only content is a generated empty
Activity and JUnit/Espresso test stubs). Keep the Gradle wrapper, `gradle.properties`
(extended), and `.gitignore` (extended with `*.xcuserstate`, `iosApp/build`, `.kotlin/`).
