# Implementation Plan: App Foundation

**Branch**: `001-app-foundation` | **Date**: 2026-10-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-app-foundation/spec.md`

## Summary

Replace the Android-only template with a Kotlin Multiplatform + Compose Multiplatform project
that produces the Plainpaper app shell on Android and iOS: Home ("Documents") with the empty
state, a floating tab bar (Documents / Folders / Settings) and Scan FAB, placeholder screens for
Scan, Import and Folders, a Settings screen with Language (Follow system / English / Arabic)
and Appearance (System / Light / Dark) persisted in DataStore, English + Arabic resources with
common-code RTL, the Plainpaper name and icon, and a GitHub Actions pipeline that gates every
pull request on formatting, static analysis, unit tests on both platforms, an 80% Kover gate
and debug builds of both apps. Architecture is Clean + MVVM/UDF with Koin, using Gradle
convention plugins and the AGP 9 module split (`:composeApp` shared, `:androidApp` host).

## Technical Context

**Language/Version**: Kotlin 2.4.21 (Compose compiler plugin 2.4.21), JDK 21, Swift 5 host
only in `:iosApp`

**Primary Dependencies**: Compose Multiplatform 1.12.2 (foundation, material3, resources,
ui-tooling-preview), org.jetbrains.androidx.lifecycle 2.11.0 (viewmodel-compose,
runtime-compose), org.jetbrains.androidx.navigation navigation-compose 2.10.0-rc01,
kotlinx-serialization-json 1.12.0, kotlinx-coroutines 1.11.0, Koin 4.2.2 (core, compose,
compose-viewmodel), androidx.datastore datastore-preferences-core 1.3.0, androidx.activity
activity-compose 1.13.0 (Android host only). Build: AGP 9.3.1, Gradle 9.6.0, Spotless 8.10.4 +
ktlint 1.8.0, detekt 1.23.8, Kover 0.9.11. See [research.md](research.md) R1–R5, R9.

**Storage**: Preferences DataStore file `plainpaper.preferences_pb` in app files directory;
no database in this feature.

**Testing**: kotlin.test, kotlinx-coroutines-test 1.11.0, Turbine 1.2.1, fakes in
`:core:testing`; Kover gate ≥ 80% line coverage over `:core:domain`, `:feature:home`,
`:feature:settings`. Android target tests run on the JVM (`testDebugUnitTest` /
`androidHostTest`); iOS tests run on `iosSimulatorArm64Test`.

**Target Platform**: Android 8.0+ (minSdk 26, compileSdk/targetSdk 36); iOS 15.0+ (arm64 device,
arm64 simulator; x64 simulator omitted). Development machine: macOS, JDK 21, Android SDK 36,
Xcode not yet installed (research R12).

**Project Type**: mobile-app (Kotlin Multiplatform, single codebase)

**Performance Goals**: cold start to interactive Home < 3 s on a mid-range Android device
(SC-001); language and theme switches re-render in a single frame cycle without activity or
app restart.

**Constraints**: offline, no network permissions, no account, no analytics/ad SDKs; no strings
outside `composeResources`; 48dp touch targets; text scales with system font size; explicit
API mode in `:core:*`; every new dependency justified in research.md.

**Scale/Scope**: 4 screens (Home, Settings, 3 placeholders sharing one composable), 2
ViewModels, 2 use-case groups, 1 repository, ~8 design-system components, 2 locales, 1 CI
workflow with 5 jobs, 9 Gradle modules + build-logic.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Evidence / Plan |
|-----------|--------|-----------------|
| I. Privacy & Offline First | PASS | No network dependency, no INTERNET permission, no analytics, no crash reporter in this feature. Preferences stay in app sandbox. |
| II. Honest Product | PASS | No Pro surface in this feature; Settings omits Pro banner and App lock rows (spec assumption). |
| III. KMP + Clean Architecture | PASS | `:core:domain` is pure Kotlin (no Compose, no platform deps); `:core:data` depends on domain + DataStore; features depend on domain + designsystem only; platform specifics behind `expect`/`actual` (`LocalAppLocale`, DataStore path) and Koin-provided platform modules. |
| IV. MVVM + UDF | PASS | `HomeViewModel` and `SettingsViewModel` each expose one `StateFlow<UiState>`, accept a sealed `Action`, emit `Effect` via `Channel`; screens are stateless composables fed state + callbacks. |
| V. Coroutines & Structured Concurrency | PASS | `DispatcherProvider` interface in domain, injected by Koin; ViewModels use `viewModelScope`; no `GlobalScope`. |
| VI. Test-First (NON-NEGOTIABLE) | PASS | Tasks will order tests before implementation for every use case, repository, mapper and ViewModel; Kover verify at 80% over domain + presentation; fakes in `:core:testing`. |
| VII. Continuous Integration | PASS | `pr.yml` with static-analysis, test-android, build-android, test-ios, build-ios jobs (research R11). |
| VIII. User-Friendly & Accessible | PASS | RTL computed in common code; all strings in `values`/`values-ar`; `contentDescription` on icon buttons; 48dp minimum sizes in design-system components; dynamic type via `sp`; light and dark color schemes from design tokens. |
| IX. Simplicity | PASS with notes | No `:core:database`, `:core:scanengine`, Room, camera or ML dependencies. Navigation 2.10.0-rc01 is a release candidate (justified in R3). `:androidApp` is an extra module forced by AGP 9 (R2). |

**Post-design re-check (after Phase 1)**: unchanged, all PASS. The two notes under IX are
recorded in Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/001-app-foundation/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   ├── ui-contracts.md  # Routes, UiState/Action/Effect shapes, design-system component API
│   ├── preferences.md   # DataStore schema and defaults
│   ├── strings.md       # String resource keys (en + ar)
│   └── ci.md            # Workflow jobs, required checks, Gradle tasks per job
├── checklists/requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
build-logic/
└── convention/
    ├── build.gradle.kts
    └── src/main/kotlin/
        ├── KmpLibraryConventionPlugin.kt        # kotlin.multiplatform + android kmp library, targets, explicitApi opt-in
        ├── KmpComposeConventionPlugin.kt        # + compose multiplatform + compose compiler + resources
        ├── KmpFeatureConventionPlugin.kt        # compose + lifecycle + koin + domain + designsystem deps
        ├── DetektConventionPlugin.kt
        ├── SpotlessConventionPlugin.kt
        └── KoverConventionPlugin.kt

gradle/libs.versions.toml
settings.gradle.kts                               # includes below; comments reserve :core:database, :core:scanengine
build.gradle.kts                                  # root: plugins apply false, kover merge, spotless root

androidApp/                                       # com.android.application host (AGP 9 split)
├── build.gradle.kts
└── src/main/
    ├── AndroidManifest.xml                       # label @string/app_name, icon, MainActivity
    ├── kotlin/app/plainpaper/android/
    │   ├── PlainpaperApplication.kt              # startKoin(androidModule + appModules)
    │   └── MainActivity.kt                       # setContent { PlainpaperApp() }
    └── res/
        ├── mipmap-anydpi-v26/ic_launcher.xml
        ├── drawable/ic_launcher_foreground.xml, ic_launcher_monochrome.xml
        ├── values/ic_launcher_background.xml, strings.xml (app_name only)
        └── values-ar/strings.xml (app_name)

iosApp/
├── iosApp.xcodeproj/
├── iosApp/
│   ├── iOSApp.swift                              # @main, calls KoinKt.doInitKoin()
│   ├── ContentView.swift                         # UIViewControllerRepresentable → MainViewControllerKt
│   ├── Info.plist                                # CFBundleDisplayName Plainpaper, UIRequiresFullScreen etc.
│   ├── InfoPlist.strings (ar.lproj)
│   └── Assets.xcassets/AppIcon.appiconset/
└── Configuration/Config.xcconfig                 # TEAM_ID, BUNDLE_ID=app.plainpaper, APP_NAME=Plainpaper

composeApp/                                       # shared app: navigation, DI wiring, root composable
├── build.gradle.kts                              # kmp library + compose; iOS framework "ComposeApp"
└── src/
    ├── commonMain/kotlin/app/plainpaper/
    │   ├── PlainpaperApp.kt                      # AppEnvironment + PlainpaperTheme + AppNavHost
    │   ├── navigation/AppRoutes.kt               # @Serializable routes
    │   ├── navigation/AppNavHost.kt
    │   ├── navigation/AppScaffold.kt             # PpFloatingTabBar + PpScanFab host
    │   ├── placeholder/PlaceholderScreen.kt      # Scan / Import / Folders placeholders
    │   ├── di/AppModules.kt                      # koin modules aggregation
    │   └── locale/AppEnvironment.kt              # LocalAppLocale expect + layout direction
    ├── androidMain/kotlin/app/plainpaper/
    │   ├── locale/LocalAppLocale.android.kt
    │   └── di/PlatformModule.android.kt
    ├── iosMain/kotlin/app/plainpaper/
    │   ├── MainViewController.kt                 # ComposeUIViewController { PlainpaperApp() }
    │   ├── Koin.kt                               # initKoin()
    │   ├── locale/LocalAppLocale.ios.kt
    │   └── di/PlatformModule.ios.kt
    └── commonTest/kotlin/app/plainpaper/
        └── locale/LayoutDirectionResolverTest.kt

core/
├── domain/                                       # pure Kotlin, explicitApi
│   └── src/commonMain/kotlin/app/plainpaper/core/domain/
│       ├── model/AppLanguage.kt, ThemeMode.kt, AppPreferences.kt
│       ├── repository/PreferencesRepository.kt
│       ├── usecase/ObservePreferencesUseCase.kt, SetLanguageUseCase.kt, SetThemeModeUseCase.kt
│       └── coroutines/DispatcherProvider.kt
│   └── src/commonTest/...                        # use case tests
├── data/
│   └── src/commonMain/kotlin/app/plainpaper/core/data/
│       ├── preferences/PreferencesKeys.kt, PreferencesMapper.kt
│       ├── preferences/DataStorePreferencesRepository.kt
│       ├── preferences/createDataStore.kt       # expect fun dataStorePath(): String
│       └── di/DataModule.kt
│   ├── src/androidMain/... createDataStore.android.kt
│   ├── src/iosMain/...     createDataStore.ios.kt
│   └── src/commonTest/...                        # mapper + repository tests (in-memory DataStore via tmp path)
├── designsystem/
│   └── src/commonMain/kotlin/app/plainpaper/core/designsystem/
│       ├── theme/PlainpaperTheme.kt, Color.kt, Type.kt, Shape.kt, Spacing.kt
│       ├── component/PpPrimaryButton.kt, PpGlassButton.kt, PpFloatingTabBar.kt, PpScanFab.kt,
│       │             PpEmptyState.kt, PpSettingsGroup.kt, PpSettingsRow.kt, PpTopTitle.kt
│       └── icon/PpIcons.kt                       # ImageVector icons from the design
│   └── src/commonMain/composeResources/
│       ├── font/inter_*.ttf, ibm_plex_sans_arabic_*.ttf
│       └── files/licenses/OFL-Inter.txt, OFL-IBMPlexSansArabic.txt
└── testing/
    └── src/commonMain/kotlin/app/plainpaper/core/testing/
        ├── TestDispatcherProvider.kt
        ├── FakePreferencesRepository.kt
        └── MainDispatcherRule.kt (helper for runTest + Dispatchers.setMain)

feature/
├── home/
│   ├── src/commonMain/kotlin/app/plainpaper/feature/home/
│   │   ├── HomeViewModel.kt, HomeUiState.kt, HomeAction.kt, HomeEffect.kt
│   │   ├── HomeScreen.kt                         # stateless; HomeRoute() wires koinViewModel
│   │   └── di/HomeModule.kt
│   ├── src/commonMain/composeResources/values/strings.xml, values-ar/strings.xml
│   └── src/commonTest/... HomeViewModelTest.kt
└── settings/
    ├── src/commonMain/kotlin/app/plainpaper/feature/settings/
    │   ├── SettingsViewModel.kt, SettingsUiState.kt, SettingsAction.kt, SettingsEffect.kt
    │   ├── SettingsScreen.kt, LanguagePickerSheet.kt, ThemePickerSheet.kt
    │   └── di/SettingsModule.kt
    ├── src/commonMain/composeResources/values/strings.xml, values-ar/strings.xml
    └── src/commonTest/... SettingsViewModelTest.kt

.github/workflows/pr.yml
config/detekt/detekt.yml
.editorconfig                                     # ktlint_official, max_line_length=120
CLAUDE.md                                         # runtime agent guidance (constitution pointer, commands)
```

**Structure Decision**: Mobile KMP monorepo. `:composeApp` is the shared application module
(navigation, DI wiring, root composable) and is consumed by `:androidApp` (AGP 9 split, R2)
and `:iosApp` (framework `ComposeApp`). Core modules hold cross-feature code; each feature
module owns its ViewModel, UI state, screens, string resources and Koin module. Shared
app-shell strings (tab labels, placeholder copy, app name) live in `:composeApp`
`composeResources`. The `:app` template module is deleted (R14).

## Phase 0: Research

Complete: see [research.md](research.md). No NEEDS CLARIFICATION remain. Open risk: iOS
in-process string refresh (R6), verified in quickstart step 6.

## Phase 1: Design

- [data-model.md](data-model.md): `AppLanguage`, `ThemeMode`, `AppPreferences`, UI states.
- [contracts/ui-contracts.md](contracts/ui-contracts.md): routes, ViewModel contracts,
  design-system component signatures.
- [contracts/preferences.md](contracts/preferences.md): DataStore keys, encoding, defaults,
  migration rule.
- [contracts/strings.md](contracts/strings.md): every string key with en and ar values.
- [contracts/ci.md](contracts/ci.md): workflow jobs, Gradle tasks, required status checks.
- [quickstart.md](quickstart.md): prerequisites, build/test/run commands, acceptance walk-through.

## Deferred (explicitly out of this feature)

- Screenshot tests in light/dark × LTR/RTL (needs a desktop/JVM target or Roborazzi; R10).
- Compose rule set for ktlint/detekt (`io.nlopez.compose.rules`) — add with the first
  non-trivial UI feature.
- Crash reporting (constitution allows a privacy-safe reporter later).
- Per-app language integration with Android 13 system settings.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Extra `:androidApp` module (10 modules instead of 9) | AGP 9 forbids `com.android.application` in a KMP module | Legacy variant API flag is removed in AGP 10 (H2 2026); migrating later costs more than splitting now |
| Navigation 2.10.0-rc01 (release candidate) | Only JetBrains navigation build aligned with lifecycle 2.11.0 and Compose Multiplatform 1.12.2 | Stable 2.9.2 targets lifecycle 2.9.x and risks runtime binary mismatch with the stable lifecycle 2.11.0 the ViewModels need |
