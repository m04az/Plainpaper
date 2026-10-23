# Feature Specification: App Foundation

**Feature Branch**: `001-app-foundation`

**Created**: 2026-10-23

**Status**: Draft

**Input**: User description: "App foundation for Plainpaper. As the developer, I need the project converted from the current Android-only template into a Kotlin Multiplatform app that runs on Android and iOS from one codebase, with an app shell users can open: a Home screen showing an empty "No documents yet" state with a primary "Scan" action and a secondary "Import photos" action (both can lead to placeholder screens for now), a Settings screen (language, theme: system/light/dark), English and Arabic with correct right-to-left layout, light and dark themes, and app name and icon "Plainpaper". Every pull request must automatically run formatting checks, static analysis, all unit tests on both platforms, a coverage report, and build both apps. Success: the app launches on an Android phone and an iPhone simulator, switching to Arabic flips the layout to right-to-left, and the CI pipeline passes on a pull request."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Open the app on either phone (Priority: P1)

A person installs Plainpaper on an Android phone or an iPhone, taps the "Plainpaper" icon, and
lands on a Home screen. Because they have no documents yet, Home shows a friendly empty state
that says "No documents yet", a prominent primary "Scan" action, and a secondary
"Import photos" action. Tapping either action opens a placeholder screen that names the
upcoming capability and lets the person go back to Home.

**Why this priority**: Nothing else in the product can be built or demonstrated until one
codebase produces a launchable app on both platforms with the first screen a user sees.

**Independent Test**: Install the build on an Android phone and on an iPhone simulator, launch
it, and confirm the Home empty state renders with both actions and that each action opens its
placeholder and returns to Home.

**Acceptance Scenarios**:

1. **Given** a fresh install on an Android phone, **When** the user taps the Plainpaper icon,
   **Then** the app opens to Home within a few seconds, showing "No documents yet", a primary
   "Scan" action and a secondary "Import photos" action.
2. **Given** a fresh install on an iPhone simulator, **When** the user launches the app,
   **Then** the same Home empty state is shown with identical content and actions.
3. **Given** the Home screen, **When** the user taps "Scan", **Then** a placeholder screen for
   scanning opens, and a back action returns the user to Home.
4. **Given** the Home screen, **When** the user taps "Import photos", **Then** a placeholder
   screen for importing opens, and a back action returns the user to Home.
5. **Given** the device home screen or app list, **When** the user looks for the app,
   **Then** it is labelled "Plainpaper" and shows the Plainpaper icon on both platforms.

---

### User Story 2 - Use the app in Arabic with right-to-left layout (Priority: P2)

An Arabic-speaking person opens Settings, picks Arabic as the app language, and every screen
immediately switches to Arabic text with a mirrored right-to-left layout: navigation back
arrows, leading icons, alignment and reading order all flip. If their phone is already set to
Arabic, the app starts in Arabic without any action. Switching back to English restores the
left-to-right layout. The choice survives closing and reopening the app.

**Why this priority**: Arabic users are a core audience from day one, and retrofitting
right-to-left support later is expensive and error-prone. This proves the localization and
layout foundation every later screen will rely on.

**Independent Test**: Set the language to Arabic in Settings and confirm all visible text is
Arabic and the layout is mirrored on Home, Settings and the placeholder screens; relaunch the
app and confirm Arabic persists.

**Acceptance Scenarios**:

1. **Given** the app in English, **When** the user selects Arabic in Settings, **Then** all
   visible text changes to Arabic and the layout of every screen flips to right-to-left
   without restarting the app.
2. **Given** the app language set to Arabic, **When** the user closes and reopens the app,
   **Then** it still opens in Arabic with right-to-left layout.
3. **Given** a device whose system language is Arabic and no in-app language choice made,
   **When** the user launches the app for the first time, **Then** it opens in Arabic with
   right-to-left layout.
4. **Given** the app in Arabic, **When** the user selects English, **Then** the text returns to
   English and the layout returns to left-to-right.
5. **Given** the language option "Follow system", **When** the device language changes,
   **Then** the app follows the new device language on next launch.

---

### User Story 3 - Choose light, dark or system theme (Priority: P3)

A person opens Settings and chooses between "System", "Light" and "Dark" appearance. The app
re-themes immediately. With "System" selected, the app follows the phone's appearance setting,
including when it changes while the app is open. The choice persists across launches.

**Why this priority**: Light and dark themes are a constitutional requirement and are cheapest
to establish before any feature screens exist, but the app is usable without the toggle.

**Independent Test**: Toggle each of the three theme options in Settings and verify the
appearance of Home and Settings; change the phone's dark-mode setting with "System" selected
and verify the app follows it.

**Acceptance Scenarios**:

1. **Given** Settings open, **When** the user selects "Dark", **Then** all screens switch to
   the dark theme immediately and text remains readable with sufficient contrast.
2. **Given** "System" selected and the phone in light mode, **When** the phone switches to
   dark mode, **Then** the app switches to dark without user action.
3. **Given** "Light" selected, **When** the user relaunches the app, **Then** it opens in the
   light theme regardless of the phone's appearance setting.

---

### User Story 4 - Every change is verified automatically (Priority: P4)

As the developer, when I open a pull request against the main branch, an automated pipeline
runs formatting checks, static analysis, all unit tests for both platforms, produces a coverage
report, and builds both the Android and iOS apps. The pull request clearly shows a pass or fail
for each check, and a failing check blocks merging.

**Why this priority**: The constitution requires that main is always releasable. The pipeline
has no user-facing value on its own, but without it every later feature's quality gate is
unenforced.

**Independent Test**: Open a pull request with a deliberately failing formatting change and
confirm the pipeline reports a failure; fix it and confirm all checks pass.

**Acceptance Scenarios**:

1. **Given** a pull request with correctly formatted, passing code, **When** it is opened,
   **Then** every check (formatting, static analysis, unit tests on both platforms, coverage
   report, Android build, iOS build) runs automatically and reports success.
2. **Given** a pull request that violates formatting rules, **When** it is opened, **Then** the
   formatting check fails and the pull request is marked as not mergeable.
3. **Given** a pull request that adds a failing unit test, **When** it is opened, **Then** the
   test check fails and the failure identifies the failing test.
4. **Given** a pull request where domain or presentation coverage falls below the required
   threshold, **When** the coverage step runs, **Then** the pipeline fails and reports the
   measured coverage.

---

### Edge Cases

- The device language is neither English nor Arabic: the app falls back to English with a
  left-to-right layout.
- The device has a mixed setting (for example Arabic device language with an explicit in-app
  choice of English): the explicit in-app choice wins.
- The user switches language while on a placeholder screen: the current screen re-renders in
  the new language and direction without losing its place in navigation.
- The user enables a large system text size: all text on Home and Settings scales and no
  labels are truncated or overlap at the largest accessibility size.
- The app is launched on a low-memory, low-end Android device: it still opens to Home within
  the launch-time target.
- The person uses a screen reader: every action and setting has a spoken label, including the
  placeholder screens' back actions.
- The pipeline is triggered on a pull request that only changes documentation: it still runs
  and passes so the merge gate is uniform.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The app MUST be built from a single shared codebase that produces an installable
  Android app and an installable iOS app.
- **FR-002**: The app MUST be named "Plainpaper" on both platforms and MUST show a Plainpaper
  launcher icon in the system app list and home screen.
- **FR-003**: On launch, the app MUST open to a Home screen. When no documents exist, Home
  MUST show an empty state with the message "No documents yet", a primary "Scan" action and a
  secondary "Import photos" action.
- **FR-004**: Tapping "Scan" MUST open a placeholder screen that names the upcoming scanning
  capability; tapping "Import photos" MUST open a placeholder screen for importing. Each
  placeholder MUST offer a way back to Home.
- **FR-005**: The app MUST provide a Settings screen reachable from Home.
- **FR-006**: Settings MUST let the user choose the app language from: Follow system, English,
  Arabic. The selection MUST apply immediately without restarting the app and MUST persist
  across launches.
- **FR-007**: Settings MUST let the user choose the appearance from: System, Light, Dark. The
  selection MUST apply immediately and MUST persist across launches. "System" MUST follow the
  device appearance, including live changes while the app is open.
- **FR-008**: All user-visible text MUST be available in English and Arabic, and no
  user-visible text MAY be hard-coded outside the localization resources.
- **FR-009**: When the active language is Arabic, every screen MUST render in right-to-left
  layout: text alignment, reading order, navigation chevrons/back arrows, leading/trailing icon
  placement and progress direction MUST be mirrored.
- **FR-010**: When the device language is not supported, the app MUST fall back to English and
  left-to-right layout.
- **FR-011**: Every interactive element MUST have a screen-reader label and a touch target of
  at least 48dp (or the platform's equivalent). All text MUST scale with the system text size
  setting.
- **FR-012**: Both light and dark themes MUST meet the platform contrast guideline for body
  text and interactive elements.
- **FR-013**: The app MUST NOT require an account, MUST NOT request contacts or location
  permissions, and MUST NOT include any advertising or tracking component.
- **FR-014**: Every pull request to the main branch MUST automatically run: formatting checks,
  static analysis, all unit tests for both the Android/common target and the iOS target, a
  coverage report with the constitutional 80% gate for domain and presentation, an Android
  debug build and an iOS build.
- **FR-015**: A pull request with any failing check MUST be reported as not mergeable.
- **FR-016**: The Home, Settings and placeholder screens MUST each have unit tests for their
  state and user actions, written with or before the implementation, in line with the
  constitution's test-first rule.

### Key Entities

- **App Preferences**: The user's persisted choices. Attributes: language (Follow system,
  English, Arabic) and appearance (System, Light, Dark). Stored only on the device.
- **Document**: Not created by this feature; Home's empty state is shown when the document
  count is zero. The entity is defined fully in the Document library feature.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A fresh install opens to the Home empty state on an Android phone and on an
  iPhone simulator, with the "Scan" and "Import photos" actions visible, in under 3 seconds
  from tap to interactive on a mid-range Android device.
- **SC-002**: Switching the language to Arabic in Settings flips 100% of screens in this
  feature (Home, Settings, both placeholders) to right-to-left layout with Arabic text, with no
  English strings remaining visible.
- **SC-003**: Language and appearance choices survive 100% of app relaunches and device
  restarts.
- **SC-004**: Every text element on Home and Settings remains fully visible and untruncated at
  the largest system text size on both platforms.
- **SC-005**: A pull request against main triggers the full automated pipeline and reports a
  result for every check; a deliberately broken formatting change produces a failing status.
- **SC-006**: Domain and presentation code for this feature report at least 80% coverage in the
  pipeline's coverage report.
- **SC-007**: A new contributor can go from clone to a successful local Android debug build
  and a passing test run by following the repository's documented steps, without any
  undocumented manual configuration.

## Assumptions

- The existing Android-only template contains no user-facing functionality worth preserving;
  it will be replaced rather than migrated.
- Minimum supported platforms are Android 8.0 (API 26) and iOS 15, covering the low-end
  Android audience while keeping modern platform capabilities available.
- "Placeholder" screens contain only a title, a one-line explanation that the feature is coming,
  and a back action; they carry no camera or gallery behavior.
- Settings is reached from an icon or action on the Home screen; the exact placement is a
  design decision for the plan.
- The launcher icon for this feature is a simple provisional Plainpaper mark (sheet of paper
  with a folded corner); final brand artwork may replace it later without a spec change.
- The iOS target is verified on the iPhone simulator; verification on a physical iPhone is not
  required for this feature.
- Only English and Arabic are delivered here; French, Spanish, Portuguese and Indonesian are
  added in the Onboarding & localization feature.
- The automated pipeline runs on the repository's hosted continuous-integration service and
  the repository is hosted on GitHub.
- Crash reporting is out of scope for this feature; the constitution permits it later only
  without personal data.
