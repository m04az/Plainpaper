# CI Contract

**Feature**: 001-app-foundation | **File**: `.github/workflows/pr.yml`

## Triggers

- `pull_request` targeting `main` (all paths, so documentation-only PRs also run; spec edge case)
- `push` to `main`
- `workflow_dispatch`
- Concurrency: `group: ${{ github.workflow }}-${{ github.ref }}`, `cancel-in-progress: true`

## Jobs

| Job id | Runner | Gradle / shell commands | Artifacts |
|--------|--------|-------------------------|-----------|
| `static-analysis` | ubuntu-latest | `./gradlew spotlessCheck detekt --no-daemon`, then `scripts/check-hardcoded-strings.sh` | detekt HTML reports (`**/build/reports/detekt/*.html`) as a workflow artifact |
| `test-android` | ubuntu-latest | `./gradlew testAndroidHostTest --continue` (Android host-test task of the KMP library plugin; exact name confirmed in T012 and kept in sync here), then `./gradlew koverXmlReport koverVerify` | `**/build/test-results/**/*.xml`, `build/reports/kover/report.xml` |
| `build-android` | ubuntu-latest | `./gradlew :androidApp:assembleDebug` | `androidApp/build/outputs/apk/debug/*.apk` |
| `test-ios` | macos-26 | `sudo xcode-select -s /Applications/Xcode_26.4.app` (or latest 26.x present), `./gradlew iosSimulatorArm64Test` | `**/build/test-results/iosSimulatorArm64Test/*.xml` |
| `build-ios` | macos-26 | `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64`, then `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build` | none (build log) |

Shared steps for every job: `actions/checkout@v7`, `actions/setup-java@v6` (temurin 21),
`gradle/actions/setup-gradle@v6` (cache enabled, `cache-read-only` on non-main),
`actions/upload-artifact@v7`, and for macOS jobs `actions/cache@v6` on `~/.konan` keyed by
`libs.versions.toml` hash. Xcode: the newest `/Applications/Xcode_26*.app` on the image.

Environment: `GRADLE_OPTS=-Dorg.gradle.jvmargs=-Xmx4g`, `ORG_GRADLE_PROJECT_ci=true`.

## Required status checks (branch protection on `main`)

`static-analysis`, `test-android`, `build-android`, `test-ios`, `build-ios`. All must pass;
no bypass for administrators. Configured with:

```sh
gh api -X PUT repos/m04az/Plainpaper/branches/main/protection \
  -f required_status_checks.strict=true \
  -f 'required_status_checks.contexts[]=static-analysis' ... \
  -F enforce_admins=true -F required_pull_request_reviews=null -F restrictions=null
```

(Exact command is in quickstart step 8; it runs once, manually, since it changes repository
settings.)

## Coverage gate

Kover root configuration merges `:core:domain`, `:feature:home`, `:feature:settings` and
excludes functions annotated `@Composable`, plus `*Screen*`, `*Sheet*`, `*PickerOption*`,
`*Route*`, `*Preview*`, `*ComposableSingletons*`, `*Module*Kt` and generated `Res` classes. Rule: minimum 80% `LINE` coverage, `koverVerify` fails the build
otherwise and prints the measured value (spec acceptance scenario US4-4).

## Localization guard

`static-analysis` also runs `scripts/check-hardcoded-strings.sh`, which greps `**/src/commonMain/**/*.kt`
for `Text("`, `text = "`, `contentDescription = "` and `title = "` with a literal argument and
fails on any match (FR-008). Allowed exception: `*Preview*.kt` files.

## Failure semantics

- A formatting violation fails `static-analysis` with the Spotless diff in the log (US4-2).
- A failing unit test fails `test-android` and/or `test-ios`; the JUnit XML is uploaded and
  summarised with `dorny/test-reporter@v3` so the failing test name appears in the PR checks
  (US4-3).
