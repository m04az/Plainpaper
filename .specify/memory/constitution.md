<!--
Sync Impact Report
- Version change: (template, unversioned) → 1.0.0
- Modified principles: none (initial ratification; all template placeholders replaced)
- Added sections:
  - Core Principles I–IX (Privacy & Offline First; Honest Product; Kotlin Multiplatform +
    Clean Architecture; MVVM with Unidirectional Data Flow; Coroutines & Structured
    Concurrency; Test-First, Always; Continuous Integration; User-Friendly & Accessible by
    Default; Simplicity)
  - Technology & Platform Constraints
  - Development Workflow & Quality Gates
  - Governance
- Removed sections: none
- Templates requiring updates: none (plan/spec/tasks templates read the constitution at runtime)
- Follow-up TODOs: none
-->

# Plainpaper Constitution

Plainpaper is a mobile app that turns photos of paper into documents that look like they came
from a real flatbed scanner. This constitution defines the non-negotiable rules every feature,
plan and pull request in this repository MUST follow.

## Core Principles

### I. Privacy & Offline First (NON-NEGOTIABLE)

- All image processing, OCR and document storage MUST happen on the device.
- No account MUST ever be required to use any feature of the app.
- No document, image or extracted text MUST leave the device unless the user explicitly
  shares or exports it through a user-initiated action.
- The app MUST NOT include ad SDKs or tracking/analytics SDKs, and MUST NOT request
  contacts or location permissions.
- The only permitted outbound telemetry is crash reporting that contains no personal data and
  no document content.

Rationale: users scan IDs, contracts and receipts. Trust that nothing leaves the phone is the
product's core promise and cannot be traded for convenience or growth metrics.

### II. Honest Product

- The app MUST NOT add watermarks, show ads, use dark patterns or introduce surprise charges.
- The free tier MUST include unlimited scans.
- Paid features MUST be delivered as a single, clearly described one-time "Pro" purchase with
  a visible "Restore purchase" action.
- Free features MUST never be interrupted by upsells; Pro is offered only when the user taps a
  Pro feature.

Rationale: competitors monetize with nagging, watermarks and subscriptions. Being plainly
honest is the differentiator and must hold in every screen and flow.

### III. Kotlin Multiplatform + Clean Architecture

- The app MUST be a single Kotlin Multiplatform codebase with Compose Multiplatform UI
  targeting Android and iOS.
- Layers are strict: domain (pure Kotlin, no platform or framework imports) → data
  (repositories, platform sources) → presentation (MVVM). Dependencies MUST point inward only.
- Platform-specific code MUST live behind interfaces declared in `commonMain`, implemented via
  `expect`/`actual` or DI-provided implementations.
- Code MUST be organized as feature modules plus shared core modules; features MUST depend
  only on domain and shared core modules, never on other features.

Rationale: one codebase keeps Android and iOS at parity; strict layering keeps the domain
testable without devices and keeps platform churn out of business logic.

### IV. MVVM with Unidirectional Data Flow

- Each screen MUST have exactly one ViewModel exposing a single immutable `UiState` through a
  `StateFlow` and accepting user intents/events as a sealed type.
- One-off effects (navigation, toasts, snackbars) MUST be emitted through a dedicated
  channel/flow, never stored in `UiState`.
- Composables MUST be stateless where possible, receiving state and callbacks as parameters.
- Composables MUST NOT contain business logic.

Rationale: a single state object per screen makes every UI condition reproducible in a unit
test and keeps rendering a pure function of state.

### V. Coroutines & Structured Concurrency

- All asynchronous work MUST use Kotlin coroutines and `Flow`.
- `GlobalScope` MUST NOT be used; every coroutine runs in a structured scope that is
  cancelled with its owner.
- Dispatchers MUST be injected (e.g. via a `DispatcherProvider`), never hard-coded, so tests
  can substitute them.
- Heavy image work MUST run off the main thread and MUST be cancellable.

Rationale: scanning is CPU-heavy. Structured, injectable concurrency keeps the UI responsive,
prevents leaks on screen exit and makes timing deterministic in tests.

### VI. Test-First, Always (NON-NEGOTIABLE)

- Every use case, repository, mapper and ViewModel MUST have unit tests in `commonTest`,
  written before or together with the implementation.
- Tests MUST use `kotlin.test`, `kotlinx-coroutines-test` and Turbine; fakes are preferred over
  mocks.
- Scan-engine changes MUST be verified against a fixed set of reference photos with measurable
  quality metrics, and a change MUST NOT regress those metrics.
- A pull request that adds new logic without tests for it is NOT mergeable.
- Coverage gate: domain and presentation modules MUST stay at or above 80% line coverage.

Rationale: image pipelines and state machines fail in subtle ways. Tests written first define
the behavior and protect it when the pipeline or UI is tuned later.

### VII. Continuous Integration

- Every pull request MUST run on GitHub Actions: formatting and static analysis
  (ktlint/Spotless + detekt), all unit tests (JVM/Android and iOS simulator), the Kover
  coverage gate, and debug builds for both Android and iOS.
- A pull request MUST NOT be merged while any of these checks fail.
- `main` MUST always be releasable.

Rationale: two platforms from one codebase only works if both are built and tested on every
change; a red `main` blocks every other contributor.

### VIII. User-Friendly & Accessible by Default

- Scan-to-share MUST be achievable in as few taps as possible with sensible defaults; the Scan
  action is always one tap away from Home.
- Full right-to-left support (Arabic) and localization MUST exist from day one; strings MUST
  NOT be hard-coded in UI code.
- Accessibility requirements: screen-reader labels on interactive elements, minimum 48dp touch
  targets, support for dynamic text size, sufficient color contrast, and both light and dark
  themes.
- The app MUST work well on low-end Android phones; features that need more capable hardware
  MUST degrade gracefully rather than crash.

Rationale: the core audience is non-technical, often on mid/low-end Android devices, and a
large share reads Arabic. Accessibility and RTL retrofits are far costlier than building them in.

### IX. Simplicity

- Build the smallest thing that satisfies the spec. No speculative abstractions, no
  "for later" flexibility.
- Each new third-party dependency MUST be justified in the feature plan (what it replaces, why
  hand-writing it is worse, and its impact on app size and privacy).
- Complexity that violates another principle MUST be recorded in the plan's Complexity
  Tracking section with the simpler alternative that was rejected and why.

Rationale: a small, understandable codebase is faster to test, review and keep private.

## Technology & Platform Constraints

- Targets: Android and iOS from one Kotlin Multiplatform codebase; UI in Compose Multiplatform.
- Dependency versions MUST be managed centrally in `gradle/libs.versions.toml`, with shared
  module configuration in Gradle convention plugins.
- Static analysis and formatting: ktlint via Spotless, plus detekt. Core modules use explicit
  API mode.
- Coverage: Kover, gated at ≥ 80% for domain and presentation.
- Any SDK or library that transmits data off-device MUST be rejected unless it is the approved
  privacy-safe crash reporter or the platform purchase service required for Pro.
- Permissions MUST be requested only at the moment they are needed, with a clear explanation,
  and every flow MUST remain usable when a permission is denied.

## Development Workflow & Quality Gates

- Every feature follows the Spec Kit cycle: specify → clarify → plan → tasks → analyze →
  implement. Work on a feature does not start before its spec and plan exist.
- Every plan MUST include a **Constitution Check** section that evaluates the feature against
  Principles I–IX and lists any justified deviations.
- Pull requests MUST: pass all CI checks (Principle VII), include tests for new logic
  (Principle VI), add no untranslated strings (Principle VIII), and introduce no dependency
  that is not justified in the plan (Principle IX).
- Reviewers MUST verify constitutional compliance, not only correctness. A PR that breaks a
  NON-NEGOTIABLE principle is rejected regardless of other merits.
- Scan-engine PRs MUST attach the reference-photo metric results.

## Governance

- This constitution supersedes all other practices, conventions and templates in this
  repository. Where guidance conflicts, the constitution wins.
- Amendments MUST be made by editing this file with a version bump and a note of what changed
  (in the Sync Impact Report and the commit message). Semantic versioning applies:
  MAJOR for removing or redefining a principle in a backward-incompatible way, MINOR for adding
  a principle or materially expanding guidance, PATCH for clarifications and wording.
- Every feature plan MUST include a Constitution Check section; every PR review MUST confirm
  compliance with the principles the change touches.
- Runtime development guidance for agents lives in `CLAUDE.md` and MUST stay consistent with
  this document.

**Version**: 1.0.0 | **Ratified**: 2026-10-23 | **Last Amended**: 2026-10-23
