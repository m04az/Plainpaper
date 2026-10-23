# Tasks: App Foundation

**Input**: Design documents from `/specs/001-app-foundation/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ (ui-contracts,
preferences, strings, ci), quickstart.md

**Tests**: REQUIRED. Constitution Principle VI (Test-First) is non-negotiable: every use case,
repository, mapper and ViewModel gets a `commonTest` written before its implementation. Test
tasks are listed before the code they cover and must fail first.

**Organization**: Grouped by user story. US1 (launchable shell) is the MVP; US2 (Arabic/RTL)
and US3 (theme) both build on the Settings screen introduced in US2; US4 (CI) is independent
of the UI stories once the build compiles.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on an incomplete task)
- **[Story]**: US1–US4 from spec.md
- Package root for all Kotlin is `app.plainpaper`; module paths are relative to the repo root

## Path Conventions

Kotlin Multiplatform monorepo (plan.md "Project Structure"): `build-logic/`, `androidApp/`,
`iosApp/`, `composeApp/`, `core/{domain,data,designsystem,testing}/`,
`feature/{home,settings}/`, `.github/workflows/`. Source sets: `src/commonMain`,
`src/androidMain`, `src/iosMain`, `src/commonTest`.

---

## Phase 1: Setup (Gradle, build-logic, quality tooling)

**Purpose**: Replace the Android-only template with the KMP build skeleton so every module
configures and `./gradlew projects` succeeds.

- [X] T001 Delete the template module: remove `app/` entirely and drop `include(":app")` from `settings.gradle.kts`; keep `gradlew`, `gradlew.bat`, `gradle/wrapper/` (Gradle 9.6.0).
- [X] T002 Rewrite `gradle/libs.versions.toml` with the pinned versions from research.md R1–R5, R9–R10: kotlin 2.4.21, agp 9.3.1, composeMultiplatform 1.12.2, jetbrainsLifecycle 2.11.0, jetbrainsNavigation 2.10.0-rc01, kotlinxSerialization 1.12.0, kotlinxCoroutines 1.11.0, koin 4.2.2, datastore 1.3.0, activityCompose 1.13.0, androidxCore 1.19.1, turbine 1.2.1, okio 3.x (latest stable, needed for DataStore `Path`), kover 0.9.11, detekt 1.23.8, spotless 8.10.4, ktlint 1.8.0; android compileSdk 36, targetSdk 36, minSdk 26. Define `[libraries]` and `[plugins]` entries (kotlinMultiplatform, androidKmpLibrary `com.android.kotlin.multiplatform.library`, androidApplication, composeMultiplatform, composeCompiler, kotlinSerialization, kover, detekt, spotless) plus bundles `koin`, `lifecycle`.
- [X] T003 Rewrite `settings.gradle.kts`: `pluginManagement { includeBuild("build-logic"); repositories { google(); mavenCentral(); gradlePluginPortal() } }`, `dependencyResolutionManagement` with google + mavenCentral, `rootProject.name = "Plainpaper"`, `enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")`, includes `:androidApp`, `:composeApp`, `:core:domain`, `:core:data`, `:core:designsystem`, `:core:testing`, `:feature:home`, `:feature:settings`, and comment lines reserving `:core:database` and `:core:scanengine` for later features.
- [X] T004 Create `build-logic/settings.gradle.kts` (repositories, `versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }`, `include(":convention")`) and `build-logic/convention/build.gradle.kts` (`kotlin-dsl`, deps on kotlin/android/compose/detekt/spotless/kover gradle plugin artifacts, `gradlePlugin { plugins { register(...) } }` ids `plainpaper.kmp.library`, `plainpaper.kmp.compose`, `plainpaper.kmp.feature`, `plainpaper.spotless`, `plainpaper.detekt`, `plainpaper.kover`).
- [X] T005 [P] Implement `build-logic/convention/src/main/kotlin/KmpLibraryConventionPlugin.kt`: applies `org.jetbrains.kotlin.multiplatform` + `com.android.kotlin.multiplatform.library`; targets `androidLibrary { namespace = "app.plainpaper.<module>"; compileSdk/minSdk from catalog; androidResources.enable = true; withHostTestBuilder {}; compilerOptions.jvmTarget = JVM_17 }` (host tests are mandatory: without `withHostTestBuilder {}` commonTest never runs on the JVM and Kover measures nothing), `iosArm64()`, `iosSimulatorArm64()`; `explicitApi()` when project path starts with `:core:`; `commonTest` deps kotlin-test + coroutines-test + turbine; `kotlin.compilerOptions.allWarningsAsErrors = true` on CI (`providers.gradleProperty("ci")`).
- [X] T006 [P] Implement `build-logic/convention/src/main/kotlin/KmpComposeConventionPlugin.kt`: applies `plainpaper.kmp.library`, `org.jetbrains.compose`, `org.jetbrains.kotlin.plugin.compose`; adds `compose.runtime`, `compose.foundation`, `compose.material3`, `compose.ui`, `compose.components.resources`, `compose.components.uiToolingPreview` to commonMain; `androidRuntimeClasspath(compose.uiTooling)`; sets `compose.resources { publicResClass = false; packageOfResClass = "app.plainpaper.<module>.resources" }`.
- [X] T007 [P] Implement `build-logic/convention/src/main/kotlin/KmpFeatureConventionPlugin.kt`: applies `plainpaper.kmp.compose`; adds `projects.core.domain`, `projects.core.designsystem`, lifecycle bundle (`lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`), koin bundle (`koin-core`, `koin-compose`, `koin-compose-viewmodel`) to commonMain; `projects.core.testing` to commonTest.
- [X] T008 [P] Implement `build-logic/convention/src/main/kotlin/SpotlessConventionPlugin.kt` (kotlin + kotlinGradle targets, `ktlint("1.8.0")`, exclude `**/build/**`) and create `.editorconfig` (`[*.{kt,kts}] ktlint_code_style = ktlint_official, max_line_length = 120, ktlint_function_naming_ignore_when_annotated_with = Composable`).
- [X] T009 [P] Implement `build-logic/convention/src/main/kotlin/DetektConventionPlugin.kt` (config `config/detekt/detekt.yml`, `buildUponDefaultConfig = true`, `source` = all `src/*/kotlin`, SARIF + HTML reports) and create `config/detekt/detekt.yml` (disable `style.MaxLineLength` in favour of ktlint, `naming.FunctionNaming.ignoreAnnotated = [Composable]`, `complexity.LongParameterList.ignoreAnnotated = [Composable]`, `style.MagicNumber.ignoreAnnotated = [Composable, Preview]`).
- [X] T010 [P] Implement `build-logic/convention/src/main/kotlin/KoverConventionPlugin.kt`: applies `org.jetbrains.kotlinx.kover`; in root, `dependencies { kover(projects.core.domain); kover(projects.feature.home); kover(projects.feature.settings) }`; `kover.reports { filters.excludes { classes("*Screen*", "*Route*", "*Preview*", "*ComposableSingletons*", "*Module*Kt", "*.resources.*", "*Res*") } ; verify.rule { minBound(80, CoverageUnit.LINE) } }` (contracts/ci.md "Coverage gate").
- [X] T011 Rewrite root `build.gradle.kts` (declare all catalog plugins with `apply false`; apply `plainpaper.spotless`, `plainpaper.detekt`, `plainpaper.kover` at root; register `detektAll`/`check` aggregation), extend `gradle.properties` (`org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8`, `org.gradle.parallel=true`, `org.gradle.caching=true`, `android.useAndroidX=true`, `kotlin.mpp.enableCInteropCommonization=true`, `kotlin.native.ignoreDisabledTargets=true`, `org.jetbrains.compose.experimental.uikit.enabled=true`), and extend `.gitignore` (`.kotlin/`, `**/build/`, `iosApp/build/`, `*.xcuserstate`, `iosApp/iosApp.xcodeproj/xcuserdata/`, `iosApp/Configuration/Config.xcconfig.local`).
- [X] T012 Create minimal `build.gradle.kts` for each module: `core/domain` (`plainpaper.kmp.library` + kotlinx-coroutines-core), `core/testing` (`plainpaper.kmp.library` + domain + coroutines-test, explicitApi off), `core/data` (`plainpaper.kmp.library` + domain + datastore-preferences-core + koin-core), `core/designsystem` (`plainpaper.kmp.compose`), `feature/home` and `feature/settings` (`plainpaper.kmp.feature`), `composeApp` (`plainpaper.kmp.compose` + kotlinSerialization plugin, all project deps, navigation-compose, koin bundle, `listOf(iosArm64(), iosSimulatorArm64()).forEach { it.binaries.framework { baseName = "ComposeApp"; isStatic = true } }`). Each with an empty `src/commonMain/kotlin/app/plainpaper/...` directory. Run `./gradlew projects` and `./gradlew :core:domain:compileKotlinIosSimulatorArm64 --dry-run` to confirm configuration, and `./gradlew :core:domain:tasks --all | grep -i test` to confirm the Android host-test task name (expected `testAndroidHostTest`); if it differs, update contracts/ci.md, quickstart.md and T064 to the real name.

**Checkpoint**: `./gradlew projects spotlessCheck detekt` succeeds on an empty skeleton. Commit.

---

## Phase 2: Foundational (domain, data, design system, shell wiring, hosts)

**Purpose**: Everything every story needs: domain model + preferences repository (US2, US3),
design system (all), shell wiring + Android/iOS hosts (all).

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

### Domain (`core/domain`)

- [X] T013 [P] Create domain models in `core/domain/src/commonMain/kotlin/app/plainpaper/core/domain/model/`: `AppLanguage.kt` (`enum class AppLanguage(val tag: String?) { SYSTEM(null), ENGLISH("en"), ARABIC("ar") }`), `ThemeMode.kt` (`SYSTEM, LIGHT, DARK`), `AppPreferences.kt` (`data class AppPreferences(language = SYSTEM, themeMode = SYSTEM)`), `AppLayoutDirection.kt` (`enum LTR, RTL`). All `public`, no platform imports.
- [X] T014 [P] Create `core/domain/src/commonMain/kotlin/app/plainpaper/core/domain/coroutines/DispatcherProvider.kt` (`interface DispatcherProvider { val main; val io; val default }`).
- [X] T015 [P] Create `core/domain/src/commonMain/kotlin/app/plainpaper/core/domain/repository/PreferencesRepository.kt` per data-model.md (`val preferences: Flow<AppPreferences>`, `suspend fun setLanguage`, `suspend fun setThemeMode`).
- [X] T016 [P] Create test helpers in `core/testing/src/commonMain/kotlin/app/plainpaper/core/testing/`: `TestDispatcherProvider.kt` (all dispatchers = given `TestDispatcher`), `FakePreferencesRepository.kt` (`MutableStateFlow<AppPreferences>`, records `setLanguage`/`setThemeMode` calls in lists), (`DefaultDispatcherProvider` lives in `:core:data`, T025).
- [X] T017 Write failing tests `core/domain/src/commonTest/kotlin/app/plainpaper/core/domain/usecase/PreferencesUseCasesTest.kt`: `ObservePreferencesUseCase` emits repository values and suppresses consecutive duplicates (Turbine); `SetLanguageUseCase` calls repository once when value differs and not at all when equal to current; same for `SetThemeModeUseCase`.
- [X] T018 Implement `core/domain/src/commonMain/kotlin/app/plainpaper/core/domain/usecase/ObservePreferencesUseCase.kt`, `SetLanguageUseCase.kt`, `SetThemeModeUseCase.kt` (classes with `operator fun invoke`) until T017 passes.
- [X] T019 Write failing tests `core/domain/src/commonTest/kotlin/app/plainpaper/core/domain/model/LayoutDirectionResolverTest.kt`: `(ARABIC, any) → RTL`, `(ENGLISH, "ar") → LTR`, `(SYSTEM, "ar-EG") → RTL`, `(SYSTEM, "AR") → RTL`, `(SYSTEM, "fr") → LTR`, `(SYSTEM, null) → LTR`; and `ThemeModeTest.kt`: `resolveIsDark(SYSTEM, systemDark=true) → true`, `(LIGHT, true) → false`, `(DARK, false) → true`.
- [X] T020 Implement `core/domain/src/commonMain/kotlin/app/plainpaper/core/domain/model/LayoutDirectionResolver.kt` (`fun resolveLayoutDirection(language: AppLanguage, systemLanguageTag: String?): AppLayoutDirection`, rule: effective tag = `language.tag ?: systemLanguageTag`; RTL iff primary subtag equals `ar` case-insensitively) and `ThemeMode.resolveIsDark(systemDark: Boolean): Boolean` extension in `ThemeMode.kt` until T019 passes.

### Data (`core/data`)

- [X] T021 [P] Write failing tests `core/data/src/commonTest/kotlin/app/plainpaper/core/data/preferences/PreferencesMapperTest.kt`: every `AppLanguage`/`ThemeMode` round-trips through its token (`system|en|ar`, `system|light|dark`); unknown token `"zz"` maps to `SYSTEM`; missing keys map to `AppPreferences()`.
- [X] T022 Implement `core/data/src/commonMain/kotlin/app/plainpaper/core/data/preferences/PreferencesKeys.kt` (`stringPreferencesKey("language")`, `stringPreferencesKey("theme")`) and `PreferencesMapper.kt` (`Preferences.toAppPreferences()`, `AppLanguage.token`, `ThemeMode.token`, `String?.toAppLanguage()`, `String?.toThemeMode()`) until T021 passes.
- [X] T023 [P] Create DataStore factory: `core/data/src/commonMain/kotlin/app/plainpaper/core/data/preferences/CreateDataStore.kt` (`expect fun preferencesDataStorePath(): String`; `fun createPreferencesDataStore(path: String): DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath { path.toPath() }` using `okio.Path.Companion.toPath`), `core/data/src/androidMain/.../CreateDataStore.android.kt` (needs `Context` via Koin: `actual` takes context from a `PlatformContext` holder; path `context.filesDir/datastore/plainpaper.preferences_pb`), `core/data/src/iosMain/.../CreateDataStore.ios.kt` (`NSFileManager.defaultManager.URLForDirectory(NSDocumentDirectory, ...)` + `/datastore/plainpaper.preferences_pb`).
- [X] T024 Write failing tests `core/data/src/commonTest/kotlin/app/plainpaper/core/data/preferences/DataStorePreferencesRepositoryTest.kt` using a temp file path (`expect fun tempDirectoryPath(): String` in commonTest with android/ios actuals) and `TestScope`: initial emission is defaults; `setLanguage(ARABIC)` then `preferences.first().language == ARABIC`; `setThemeMode(DARK)` persists; writing language then theme keeps both; corrupt/unknown token yields defaults.
- [X] T025 Implement `core/data/src/commonMain/kotlin/app/plainpaper/core/data/preferences/DataStorePreferencesRepository.kt` (`preferences = dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { it.toAppPreferences() }`; `edit { it[KEY] = token }`), `core/data/src/commonMain/kotlin/app/plainpaper/core/data/coroutines/DefaultDispatcherProvider.kt`, and `core/data/src/commonMain/kotlin/app/plainpaper/core/data/di/DataModule.kt` (Koin: `single<DataStore<Preferences>>`, `single<PreferencesRepository>`, `single<DispatcherProvider>`, use-case factories) until T024 passes.

### Design system (`core/designsystem`)

- [X] T026 [P] Add fonts to `core/designsystem/src/commonMain/composeResources/font/`: `inter_regular.ttf`, `inter_medium.ttf`, `inter_semibold.ttf`, `inter_bold.ttf`, `inter_extrabold.ttf`, `ibm_plex_sans_arabic_regular.ttf`, `ibm_plex_sans_arabic_medium.ttf`, `ibm_plex_sans_arabic_semibold.ttf`, `ibm_plex_sans_arabic_bold.ttf` (static TTFs from the official GitHub releases of Inter and IBM Plex), plus `composeResources/files/licenses/OFL-Inter.txt` and `OFL-IBMPlexSansArabic.txt`.
- [X] T027 [P] Create `core/designsystem/src/commonMain/kotlin/app/plainpaper/core/designsystem/theme/Color.kt`: `PpColors` object with every light/dark hex from contracts/ui-contracts.md §4 and `lightColorScheme()` / `darkColorScheme()` builders mapping background, surface, onSurface, onSurfaceVariant, primary, onPrimary, primaryContainer, outline, error; plus `PpExtendedColors(success, glassSurface, glassStroke)` as a `staticCompositionLocalOf`.
- [X] T028 [P] Create `core/designsystem/src/commonMain/kotlin/app/plainpaper/core/designsystem/theme/Spacing.kt` (`Spacing(xs=4, s=8, m=12, l=16, xl=20, xxl=24, xxxl=32)` dp, `LocalSpacing`) and `Shape.kt` (`PpShapes(search=12, card=20, toolbar=30, sheet=38, capsule=CircleShape)` + Material `Shapes` mapping).
- [X] T029 Create `core/designsystem/src/commonMain/kotlin/app/plainpaper/core/designsystem/theme/Type.kt`: `plainpaperFontFamily()` composable building `FontFamily` from Inter + IBM Plex Sans Arabic resource fonts (weights 400/500/600/700/800), `plainpaperTypography()` with displaySmall 34sp/800, headlineMedium 28sp/800, titleLarge 22sp/700, titleMedium 17sp/700, bodyLarge 16sp/400, bodySmall 13sp/500 (depends on T026).
- [X] T030 Create `core/designsystem/src/commonMain/kotlin/app/plainpaper/core/designsystem/theme/PlainpaperTheme.kt`: `@Composable fun PlainpaperTheme(darkTheme: Boolean = isSystemInDarkTheme(), content)` wiring `MaterialTheme(colorScheme, typography, shapes)` + `CompositionLocalProvider(LocalSpacing, LocalPpExtendedColors)`; `object PlainpaperTheme { colors, typography, spacing, shapes, extended }` accessors (depends on T027–T029).
- [X] T031 [P] Create `core/designsystem/src/commonMain/kotlin/app/plainpaper/core/designsystem/icon/PpIcons.kt`: `ImageVector`s drawn from the design SVG paths: `ScanFrame`, `ImportPhoto`, `Document`, `Folder`, `Settings`, `Globe`, `Moon`, `Shield`, `Info`, `ChevronEnd` (auto-mirrored), `ArrowBack` (auto-mirrored), `Check`.
- [X] T032 [P] Create `core/designsystem/src/commonMain/kotlin/app/plainpaper/core/designsystem/component/PpPrimaryButton.kt` and `PpGlassButton.kt` per contracts/ui-contracts.md §4 (52dp capsule, 17sp/600, leading icon slot, `Modifier.semantics { role = Role.Button }`, min width 48dp).
- [X] T033 [P] Create `core/designsystem/src/commonMain/kotlin/app/plainpaper/core/designsystem/component/PpFloatingTabBar.kt` (`PpTabItem(id, label, icon)`, 62dp capsule, glass surface, selected pill `primaryContainer` + primary tint, `Role.Tab`, `selected` semantics) and `PpScanFab.kt` (62dp circle, primary, `contentDescription` param).
- [X] T034 [P] Create `core/designsystem/src/commonMain/kotlin/app/plainpaper/core/designsystem/component/PpEmptyState.kt` (illustration slot, title 22sp/700, body 16sp onSurfaceVariant, primary + secondary slots, vertical 10dp gap) and `PpTopTitle.kt` (34sp/800 large title, start-aligned).
- [X] T035 [P] Create `core/designsystem/src/commonMain/kotlin/app/plainpaper/core/designsystem/component/PpSettingsGroup.kt` (uppercase 13sp/600 caption, surface card radius 20, dividers `outline` between rows) and `PpSettingsRow.kt` (30dp tinted icon box, title 16sp, trailing value 15sp onSurfaceVariant + `ChevronEnd`, `minHeight 50dp`, clickable with `Role.Button` only when `onClick != null`).

### Shell wiring (`composeApp`)

- [X] T036 [P] Create shell strings `composeApp/src/commonMain/composeResources/values/strings.xml` and `values-ar/strings.xml` with every `:composeApp` key/value from contracts/strings.md.
- [X] T037 [P] Create `composeApp/src/commonMain/kotlin/app/plainpaper/navigation/AppRoutes.kt` (`@Serializable` `Home`, `Folders`, `Settings`, `ScanPlaceholder`, `ImportPlaceholder` objects implementing `AppRoute`) and `composeApp/src/commonMain/kotlin/app/plainpaper/placeholder/PlaceholderScreen.kt` (`PlaceholderScreen(title, body, onBack)`: top bar with `ArrowBack` icon button `contentDescription = back_cd`, title, body).
- [X] T038 [P] Create locale environment: `composeApp/src/commonMain/kotlin/app/plainpaper/locale/LocalAppLocale.kt` (`expect object LocalAppLocale { val current: String @Composable get; @Composable infix fun provides(value: String?): ProvidedValue<*> }`, `expect fun systemLanguageTag(): String?`) with `composeApp/src/androidMain/kotlin/app/plainpaper/locale/LocalAppLocale.android.kt` (capture the device locale once in a top-level `lazy` on first access; for a non-null tag call `Locale.setDefault(Locale.forLanguageTag(tag))`, build a new `Configuration` with `setLocale` and `setLayoutDirection`, `resources.updateConfiguration`, return `LocalConfiguration provides configuration`; for `null` restore the captured device locale the same way; `systemLanguageTag()` = `Resources.getSystem().configuration.locales[0].toLanguageTag()`) and `composeApp/src/iosMain/kotlin/app/plainpaper/locale/LocalAppLocale.ios.kt` (`NSUserDefaults.standardUserDefaults.setObject(listOf(tag), "AppleLanguages")` or `removeObjectForKey` when null; `systemLanguageTag()` = `NSLocale.preferredLanguages.firstOrNull()`), and `composeApp/src/commonMain/kotlin/app/plainpaper/locale/AppEnvironment.kt` (`AppEnvironment(language, layoutDirection, content)` = `CompositionLocalProvider(LocalAppLocale provides language.tag, LocalLayoutDirection provides dir.toCompose()) { key(language) { content() } }`).
- [X] T039 Create `composeApp/src/commonMain/kotlin/app/plainpaper/navigation/AppScaffold.kt`: `Scaffold`-like layout that draws the route content, and when the current route is a tab destination overlays `PpFloatingTabBar(items = Documents/Folders/Settings with icons + localized labels)` and `PpScanFab` at the bottom (16dp side padding, 30dp bottom), hiding both on placeholder routes (depends on T033, T036, T037).
- [X] T040 Create DI and platform entry points: `composeApp/src/commonMain/kotlin/app/plainpaper/di/AppModules.kt` (`fun appModules(platform: Module) = listOf(platform, dataModule, homeModule, settingsModule)`), `composeApp/src/androidMain/kotlin/app/plainpaper/di/PlatformModule.android.kt` (`androidContext` based, provides `preferencesDataStorePath`), `composeApp/src/iosMain/kotlin/app/plainpaper/di/PlatformModule.ios.kt`, `composeApp/src/iosMain/kotlin/app/plainpaper/Koin.kt` (`fun initKoin()`), `composeApp/src/iosMain/kotlin/app/plainpaper/MainViewController.kt` (`fun MainViewController() = ComposeUIViewController { PlainpaperApp() }`). `homeModule`/`settingsModule` referenced here are created in US1/US2; add empty `module {}` placeholders in the feature modules now so this compiles.
- [X] T041 Create `composeApp/src/commonMain/kotlin/app/plainpaper/PlainpaperApp.kt`: `@Composable fun PlainpaperApp()` that creates `val navController = rememberNavController()` **before/outside** `AppEnvironment` (so `key(language)` re-keying never resets the back stack) and (for now) renders `PlainpaperTheme { AppEnvironment(SYSTEM, LTR) { AppScaffold(navController) { AppNavHost(navController) } } }` with `AppNavHost` in `composeApp/src/commonMain/kotlin/app/plainpaper/navigation/AppNavHost.kt` containing only the three placeholder routes and a temporary empty `Home` composable; US1 replaces Home, US2/US3 wire preferences.

### Hosts

- [X] T042 [P] Create `androidApp/build.gradle.kts` (`com.android.application`, namespace/applicationId `app.plainpaper`, compileSdk 36, minSdk 26, targetSdk 36, versionCode 1, versionName "0.1.0", Compose enabled via `composeCompiler` plugin, deps `projects.composeApp`, `activity-compose`, `koin-android`), `androidApp/src/main/AndroidManifest.xml` (`android:label="@string/app_name"`, `android:icon="@mipmap/ic_launcher"`, `android:name=".PlainpaperApplication"`, `MainActivity` exported with LAUNCHER intent, `android:configChanges` incl. `locale|layoutDirection|uiMode`, no INTERNET permission), `androidApp/src/main/kotlin/app/plainpaper/android/PlainpaperApplication.kt` (`startKoin { androidContext(this@...); modules(appModules(androidPlatformModule)) }`), `MainActivity.kt` (`enableEdgeToEdge(); setContent { PlainpaperApp() }`), `androidApp/src/main/res/values/strings.xml` and `values-ar/strings.xml` (`app_name` = Plainpaper).
- [X] T043 [P] Create the iOS host: `iosApp/iosApp.xcodeproj/project.pbxproj` (target `iosApp`, deployment target 15.0, links `ComposeApp.framework` via a "Compile Kotlin Framework" run-script phase calling `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`), `iosApp/Configuration/Config.xcconfig` (`TEAM_ID=`, `BUNDLE_ID=app.plainpaper`, `APP_NAME=Plainpaper`), `iosApp/iosApp/iOSApp.swift` (`@main`, `init { KoinKt.doInitKoin() }`), `iosApp/iosApp/ContentView.swift` (`UIViewControllerRepresentable` returning `MainViewControllerKt.MainViewController()`, `.ignoresSafeArea()`), `iosApp/iosApp/Info.plist` (`CFBundleDisplayName $(APP_NAME)`, `CFBundleLocalizations [en, ar]`, `UILaunchScreen {}`), `iosApp/iosApp/ar.lproj/InfoPlist.strings`.
- [X] T044 Create `CLAUDE.md` at repo root (pointer to the constitution, module map, the exact Gradle commands from quickstart.md, the "no strings in Kotlin" and test-first rules, the Xcode prerequisite note) and a short contributor `README.md` (what Plainpaper is, prerequisites table, the three commands to build/test/run, link to `specs/001-app-foundation/quickstart.md`) so SC-007 holds for humans as well as agents.

**Checkpoint**: `./gradlew spotlessCheck detekt testAndroidHostTest koverXmlReport` passes (domain + data tests green); `./gradlew :androidApp:assembleDebug` produces an APK that launches to the shell with tab bar + FAB. Commit after each green task group.

---

## Phase 3: User Story 1 - Open the app on either phone (Priority: P1) 🎯 MVP

**Goal**: Fresh install opens to Home "Documents" with the "No documents yet" empty state,
"Scan a page" / "Import photos" actions, tab bar, Scan FAB, working placeholders, and the
Plainpaper name + icon on both platforms.

**Independent Test**: quickstart.md step 3 on Android and step 6 on the iPhone simulator (or
CI `build-ios`): every element of acceptance scenarios 1–7 of US1 is present and navigable.

### Tests for User Story 1

- [X] T045 [P] [US1] Write failing `feature/home/src/commonTest/kotlin/app/plainpaper/feature/home/HomeViewModelTest.kt`: initial `uiState.value == HomeUiState(isEmpty = true)`; `onAction(ScanClicked)` emits exactly `HomeEffect.NavigateToScan` on `effects` (Turbine); `onAction(ImportClicked)` emits `NavigateToImport`; no state change on either.

### Implementation for User Story 1

- [X] T046 [P] [US1] Create `feature/home/src/commonMain/composeResources/values/strings.xml` and `values-ar/strings.xml` with all `:feature:home` keys from contracts/strings.md.
- [X] T047 [US1] Implement `feature/home/src/commonMain/kotlin/app/plainpaper/feature/home/HomeUiState.kt`, `HomeAction.kt`, `HomeEffect.kt`, `HomeViewModel.kt` (`StateFlow` via `MutableStateFlow`, `Channel<HomeEffect>(BUFFERED).receiveAsFlow()`), and `di/HomeModule.kt` (`viewModelOf(::HomeViewModel)`) until T045 passes.
- [X] T048 [US1] Implement `feature/home/src/commonMain/kotlin/app/plainpaper/feature/home/HomeScreen.kt`: `HomeScreen(state, onAction)` = `PpTopTitle(home_title)` + `PpEmptyState` (stacked-paper illustration drawn with `Canvas`/boxes per the design, `home_empty_title`, `home_empty_body`, `PpPrimaryButton(home_scan_button, ScanFrame icon)`, `PpGlassButton(home_import_button, ImportPhoto icon)`), bottom padding 120dp for the floating bar; `HomeRoute(onNavigateToScan, onNavigateToImport)` using `koinViewModel()` + `collectAsStateWithLifecycle()` + `LaunchedEffect` effect collection.
- [X] T049 [US1] Wire navigation in `composeApp/src/commonMain/kotlin/app/plainpaper/navigation/AppNavHost.kt`: `Home` → `HomeRoute`, `Folders` → `PlaceholderScreen(placeholder_folders_*)` (tab bar visible, back pops to Home), `Settings` → temporary `PlaceholderScreen` (replaced in US2), `ScanPlaceholder`/`ImportPlaceholder` → `PlaceholderScreen` with their strings; tab selection uses `popUpTo(Home) { saveState = true }; launchSingleTop = true; restoreState = true`; FAB navigates to `ScanPlaceholder` from any tab. Replace the temporary Home from T041.
- [X] T050 [P] [US1] Create the Android launcher icon: `androidApp/src/main/res/drawable/ic_launcher_foreground.xml` (white sheet with folded corner + two `#2340C8` lines, vector), `drawable/ic_launcher_monochrome.xml`, `values/ic_launcher_background.xml` (`#F2F1EE`), `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` (adaptive icon referencing foreground/background/monochrome).
- [X] T051 [P] [US1] Create the iOS app icon: render the same mark to `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/icon-1024.png` (1024×1024, opaque, via an SVG + `rsvg-convert` or `sips`) with `Contents.json` (single universal 1024 entry); set `ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon` in the Xcode target.
- [X] T052 [US1] Verify US1 on Android: `./gradlew :androidApp:installDebug`, launch, walk quickstart step 3 (label + icon, Home content, Scan a page/FAB/Import/Folders placeholders with back), and measure cold start with `adb shell am start -W -n app.plainpaper/.MainActivity` after a force-stop (SC-001 target: TotalTime < 3000 ms). Record results and the timing in the PR description. iOS verification runs via CI `build-ios` (Xcode not installed locally, research R12).

**Checkpoint**: MVP. Commit.

---

## Phase 4: User Story 2 - Use the app in Arabic with right-to-left layout (Priority: P2)

**Goal**: Settings screen with Language (Follow system / English / العربية); choosing Arabic
switches all text and mirrors layout immediately; choice persists; device language is
honoured under "Follow system"; unsupported languages fall back to English/LTR.

**Independent Test**: quickstart.md step 5 (and step 6 on iOS when Xcode is available):
acceptance scenarios 1–5 of US2 plus the edge cases "device language French → English LTR"
and "switch while on a placeholder keeps navigation".

### Tests for User Story 2

- [X] T053 [P] [US2] Write failing `feature/settings/src/commonTest/kotlin/app/plainpaper/feature/settings/SettingsViewModelTest.kt` (language cases): initial state equals `SettingsUiState(appVersion = "0.1.0")`; state reflects `FakePreferencesRepository` emissions (`language`, `themeMode`); `LanguageRowClicked` → `openSheet == LANGUAGE`; `LanguageSelected(ARABIC)` → `openSheet == null` and `SetLanguageUseCase` invoked once with `ARABIC` (via fake repository call list); `SheetDismissed` → `openSheet == null`. Uses `TestDispatcherProvider` + `Dispatchers.setMain`.
- [X] T054 [P] [US2] Write failing `composeApp/src/commonTest/kotlin/app/plainpaper/AppShellStateTest.kt`: `AppPreferences(language = ARABIC).toShellState(systemTag = "en", systemDark = false)` → `layoutDirection == RTL`; `(SYSTEM, "ar-EG")` → RTL; `(ENGLISH, "ar")` → LTR; `(SYSTEM, "fr")` → LTR.

### Implementation for User Story 2

- [X] T055 [P] [US2] Create `feature/settings/src/commonMain/composeResources/values/strings.xml` and `values-ar/strings.xml` with all `:feature:settings` keys from contracts/strings.md.
- [X] T056 [US2] Implement `feature/settings/src/commonMain/kotlin/app/plainpaper/feature/settings/SettingsUiState.kt` (`language`, `themeMode`, `openSheet: SettingsSheet?`, `appVersion`), `SettingsSheet.kt`, `SettingsAction.kt`, `SettingsEffect.kt` (empty sealed interface), `SettingsViewModel.kt` (constructor `observePreferences, setLanguage, setThemeMode, dispatchers, appVersion`; collects preferences in `viewModelScope`; selections launched on `dispatchers.io`), and `di/SettingsModule.kt` (`viewModel { SettingsViewModel(get(), get(), get(), get(), appVersion = get(named("appVersion"))) }`; `appVersion` provided by platform modules) until T053 passes.
- [X] T057 [US2] Implement `feature/settings/src/commonMain/kotlin/app/plainpaper/feature/settings/SettingsScreen.kt` (`PpTopTitle(settings_title)`; `PpSettingsGroup(settings_group_app)` with `PpSettingsRow(settings_language, value = current language label, Globe, primary)` and `PpSettingsRow(settings_appearance, value = current theme label, Moon, onSurfaceVariant)`; `PpSettingsGroup(settings_group_about)` with Privacy (`settings_privacy_value`, Shield, success, non-clickable) and Version (`state.appVersion`, Info, outline, non-clickable); bottom padding 120dp) and `LanguagePickerSheet.kt` (`ModalBottomSheet` radius 38, title `sheet_language_title`, three selectable rows with `Role.RadioButton` and `Check` icon on selected, `selected_cd`), plus `SettingsRoute()`; wire `Settings` route in `AppNavHost.kt` to `SettingsRoute` (replacing the US1 placeholder).
- [X] T058 [US2] Implement `composeApp/src/commonMain/kotlin/app/plainpaper/AppShellState.kt` (`data class AppShellState(language, themeMode, layoutDirection, isDark)` + `fun AppPreferences.toShellState(systemTag: String?, systemDark: Boolean)` using `resolveLayoutDirection` and `resolveIsDark`) until T054 passes; then update `PlainpaperApp.kt` to inject `ObservePreferencesUseCase` via `koinInject()`, `collectAsStateWithLifecycle(initial = AppPreferences())`, map with `systemLanguageTag()` and `isSystemInDarkTheme()`, and pass `language` + `layoutDirection` into `AppEnvironment` (theme flag is consumed in US3).
- [X] T059 [US2] Verify US2 on Android per quickstart step 5, including: switching language while on the Scan placeholder keeps the placeholder on screen in the new language; device language French with "Follow system" → English LTR. Record results. Add a note to `specs/001-app-foundation/spec.md` Assumptions only if the iOS R6 fallback becomes necessary after the iOS check (quickstart step 6).

**Checkpoint**: Settings + Arabic/RTL complete. Commit.

---

## Phase 5: User Story 3 - Choose light, dark or system theme (Priority: P3)

**Goal**: Appearance row opens a sheet with System / Light / Dark; the app re-themes
immediately; System follows live device changes; choice persists.

**Independent Test**: quickstart.md step 4: acceptance scenarios 1–3 of US3.

### Tests for User Story 3

- [X] T060 [US3] Extend `feature/settings/src/commonTest/kotlin/app/plainpaper/feature/settings/SettingsViewModelTest.kt` with failing theme cases: `ThemeRowClicked` → `openSheet == THEME`; `ThemeSelected(DARK)` → `openSheet == null` and `SetThemeModeUseCase` invoked once with `DARK`; state `themeMode` follows repository emissions.

### Implementation for User Story 3

- [X] T061 [US3] Implement `feature/settings/src/commonMain/kotlin/app/plainpaper/feature/settings/ThemePickerSheet.kt` (same pattern as `LanguagePickerSheet`, title `sheet_theme_title`, rows `theme_system|theme_light|theme_dark`) and wire `ThemeRowClicked`/`ThemeSelected` handling in `SettingsScreen.kt` until T060 passes.
- [X] T062 [US3] Update `composeApp/src/commonMain/kotlin/app/plainpaper/PlainpaperApp.kt` to pass `shellState.isDark` into `PlainpaperTheme(darkTheme = ...)`, where `isDark = themeMode.resolveIsDark(isSystemInDarkTheme())` is recomputed on every recomposition so System mode follows live device changes.
- [X] T063 [US3] Verify US3 on Android per quickstart step 4 (immediate re-theme, live System follow, persistence across force-stop). Record results.

**Checkpoint**: All UI stories complete. Commit.

---

## Phase 6: User Story 4 - Every change is verified automatically (Priority: P4)

**Goal**: A pull request to `main` runs formatting, static analysis, unit tests on both
platforms, the Kover gate and both debug builds, and a failing check blocks merge.

**Independent Test**: quickstart.md step 8: a PR shows five passing checks; a deliberately
mis-formatted commit fails `static-analysis`; coverage below 80% fails `test-android`.

### Implementation for User Story 4

- [X] T064 [US4] Create `scripts/check-hardcoded-strings.sh` (greps `**/src/commonMain/**/*.kt` excluding `*Preview*.kt` for `Text("`, `text = "`, `contentDescription = "`, `title = "` followed by a literal; exits 1 on a match; FR-008) and `.github/workflows/pr.yml` exactly per contracts/ci.md: triggers (`pull_request` → main, `push` → main, `workflow_dispatch`), concurrency group with cancel-in-progress, jobs `static-analysis` (ubuntu-latest: spotlessCheck + detekt + `scripts/check-hardcoded-strings.sh`, upload SARIF with the current major of `github/codeql-action/upload-sarif`; verify the current tags of `codeql-action` and `dorny/test-reporter` on GitHub before pinning), `test-android` (ubuntu-latest: unit tests + `koverXmlReport koverVerify`, `dorny/test-reporter@v2`, upload `build/reports/kover/report.xml`), `build-android` (ubuntu-latest: `:androidApp:assembleDebug`, upload APK), `test-ios` (macos-26: select latest Xcode 26.x, `iosSimulatorArm64Test`, test-reporter), `build-ios` (macos-26: `linkDebugFrameworkIosSimulatorArm64` + `xcodebuild ... CODE_SIGNING_ALLOWED=NO build`); shared steps `actions/checkout@v7`, `actions/setup-java@v6` (temurin 21), `gradle/actions/setup-gradle@v6`, `actions/cache@v4` on `~/.konan` for macOS jobs; env `ORG_GRADLE_PROJECT_ci=true`.
- [ ] T065 [US4] Push `001-app-foundation` and open the pull request with `gh pr create --base main --title "App foundation (001)" --body-file specs/001-app-foundation/quickstart.md`; wait for all five checks and fix any CI-only failures (iOS compile errors will surface here first, since Xcode is not available locally).
- [ ] T066 [US4] Negative check: push a commit that violates ktlint (e.g. trailing whitespace in `core/domain/.../AppLanguage.kt`), confirm `static-analysis` fails and the PR shows "not mergeable", then revert the commit and confirm green again. Record both run URLs in the PR description.
- [ ] T067 [US4] Apply branch protection on `main` requiring `static-analysis`, `test-android`, `build-android`, `test-ios`, `build-ios` using the `gh api` command in quickstart step 8 (one-time repository setting; confirm with the user before running because it changes repository configuration).

**Checkpoint**: Pipeline enforced. Commit.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T068 [P] Accessibility pass across `feature/home/.../HomeScreen.kt`, `feature/settings/.../SettingsScreen.kt`, both picker sheets, `composeApp/.../PlaceholderScreen.kt` and `AppScaffold.kt`: every icon-only control has a `contentDescription`, rows expose `Role`, selected tab exposes `selected`, all touch targets ≥ 48dp (`Modifier.minimumInteractiveComponentSize()`), no `left`/`right` modifiers anywhere (grep). Contrast (FR-012): compute WCAG ratios for every text/background pair in both schemes in `core/designsystem/.../theme/Color.kt` (body text ≥ 4.5:1, 22sp+/UI ≥ 3:1), paying attention to the derived dark values `primaryContainer #1A2447`, `success #4CC38A`, `error #F97066` and `onSurfaceVariant` on `surface`; adjust any failing token and record the ratios in a comment next to the token.
- [ ] T069 [P] Dynamic type check per quickstart step 7 on Android at the largest font size; fix any truncation by allowing text to wrap (`maxLines` removed, `softWrap = true`) in the files above.
- [ ] T070 Run `./gradlew spotlessApply detekt testAndroidHostTest koverVerify :androidApp:assembleDebug` and `scripts/check-hardcoded-strings.sh` locally; fix every finding; confirm Kover ≥ 80% and record the number in the PR.
- [ ] T071 Final quickstart.md walk-through (steps 1–5, 7, 8) and update `specs/001-app-foundation/spec.md` Assumptions and `CLAUDE.md` with anything learned (e.g. the iOS R6 outcome); ensure the PR description lists SC-001…SC-007 with evidence.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** → **Foundational (Phase 2)** → **US1 (Phase 3)** → **US2 (Phase 4)** → **US3 (Phase 5)**.
- **US4 (Phase 6)** needs only a compiling project (end of Phase 2) and can be started in
  parallel with US1–US3 by a second person; the negative check (T066) and protection (T067)
  should run after all stories are merged into the branch.
- **Polish (Phase 7)** after all stories.

### User Story Dependencies

- **US1**: depends on Foundational only.
- **US2**: depends on US1 (navigation host and Settings route wiring).
- **US3**: depends on US2 (Settings screen and ViewModel).
- **US4**: independent of US1–US3 (workflow file), verified against the final branch.

### Within Each Story

- Test task fails → implementation task until green → wire into navigation → verify on device.
- Strings before screens; ViewModel before screen; screen before NavHost wiring.

### Parallel Opportunities

- Phase 1: T005–T010 (six convention plugin/config files).
- Phase 2: T013–T016 together; T021/T023 with T017–T020; T026–T028 and T031–T035 together
  (design system files are independent); T036–T038 together; T042/T043 together.
- US1: T045, T046, T050, T051 together.
- US2: T053, T054, T055 together.
- Phase 7: T068, T069 together.

---

## Parallel Example: Phase 2 design system

```bash
# After T012 compiles, launch together:
Task: "T026 fonts into core/designsystem/src/commonMain/composeResources/font/"
Task: "T027 Color.kt tokens"
Task: "T028 Spacing.kt + Shape.kt"
Task: "T031 PpIcons.kt"
Task: "T032 PpPrimaryButton.kt + PpGlassButton.kt"
Task: "T033 PpFloatingTabBar.kt + PpScanFab.kt"
Task: "T034 PpEmptyState.kt + PpTopTitle.kt"
Task: "T035 PpSettingsGroup.kt + PpSettingsRow.kt"
```

---

## Implementation Strategy

### MVP First (US1)

1. Phase 1 → `./gradlew projects` green. Commit.
2. Phase 2 → domain/data tests green, APK launches to the shell. Commit per group.
3. Phase 3 → Home + placeholders + icon. **STOP and VALIDATE** quickstart step 3.

### Incremental Delivery

4. Phase 4 → Arabic/RTL; validate step 5.
5. Phase 5 → Theme; validate step 4.
6. Phase 6 → open the PR; CI validates iOS (first real iOS compile), negative check, protection.
7. Phase 7 → polish, final quickstart pass, merge.

### Notes

- Commit after every green task or task group with a short message (no co-author trailer).
- Never put a user-visible literal in Kotlin; all strings go through `Res.string.*`.
- Core modules use explicit API mode: every public declaration needs `public`.
- Xcode is not installed locally; the first iOS compile happens in CI (T065). Expect to
  iterate on `iosMain` actuals and the Xcode project there.
