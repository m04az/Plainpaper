# Plainpaper — Spec Kit Playbook & Design Prompt

Run the commands below **in order**, in Claude Code inside this repo.
Each `/speckit-*` command is one message — copy the whole block after it.

Order per feature:
`/speckit-specify` → `/speckit-clarify` → `/speckit-plan` → `/speckit-tasks` → `/speckit-analyze` → `/speckit-implement`

The constitution (step 0) is done once for the whole project.

---

## 0. Constitution (once)

```
/speckit-constitution Plainpaper is a mobile app that turns photos of paper into documents that look like they came from a real flatbed scanner. Create the constitution with these principles:

I. Privacy & Offline First (NON-NEGOTIABLE)
All image processing, OCR and document storage happen on the device. No account is required. No document, image or extracted text ever leaves the device unless the user explicitly shares or exports it. No ad SDKs, no tracking SDKs, no contacts/location permissions. Only crash reporting with no personal data is allowed.

II. Honest Product
No watermarks, no ads, no dark patterns, no surprise charges. Free tier includes unlimited scans. Paid features are a clearly described one-time Pro purchase with a visible "restore purchase".

III. Kotlin Multiplatform + Clean Architecture
Single codebase in Kotlin Multiplatform with Compose Multiplatform UI for Android and iOS. Strict layers: domain (pure Kotlin, no platform or framework imports) → data (repositories, platform sources) → presentation (MVVM). Dependencies point inward only. Platform-specific code lives behind interfaces in commonMain with expect/actual or DI-provided implementations. Code is organized by feature modules plus shared core modules.

IV. MVVM with Unidirectional Data Flow
Each screen has one ViewModel exposing a single immutable UiState via StateFlow and accepting user intents/events. One-off effects (navigation, toasts) go through a dedicated channel/flow. Composables are stateless where possible and receive state + callbacks. No business logic in composables.

V. Coroutines & Structured Concurrency
All async work uses Kotlin coroutines and Flow. No GlobalScope. Dispatchers are injected (never hard-coded) so tests can replace them. Heavy image work runs off the main thread and is cancellable.

VI. Test-First, Always (NON-NEGOTIABLE)
Every use case, repository, mapper and ViewModel has unit tests in commonTest written before or with the implementation. Use kotlin.test, kotlinx-coroutines-test and Turbine; prefer fakes over mocks. Scan-engine changes are verified against a fixed set of reference photos with measurable quality metrics. A pull request without tests for new logic is not mergeable. Coverage gate: domain and presentation ≥ 80%.

VII. Continuous Integration
Every pull request runs on GitHub Actions: formatting and static analysis (ktlint/Spotless + detekt), all unit tests (JVM/Android and iOS simulator), coverage gate (Kover), and debug builds for Android and iOS. main is always releasable.

VIII. User-Friendly & Accessible by Default
Scan-to-share in as few taps as possible with sensible defaults. Full right-to-left support (Arabic) and localization from day one; no hard-coded strings. Accessibility: screen-reader labels, minimum 48dp touch targets, dynamic text size, sufficient contrast, light and dark themes. Works well on low-end Android phones.

IX. Simplicity
Build the smallest thing that satisfies the spec. No speculative abstractions. Each new dependency must be justified in the plan.

Governance: the constitution overrides other practices; amendments require a version bump and a note of what changed. Every plan must include a Constitution Check section.
```

---

## Feature specs

Do these one at a time. Finish the full cycle (specify → implement) for each before starting the next.

### Feature 1 — App foundation

```
/speckit-specify App foundation for Plainpaper. As the developer, I need the project converted from the current Android-only template into a Kotlin Multiplatform app that runs on Android and iOS from one codebase, with an app shell users can open: a Home screen showing an empty "No documents yet" state with a primary "Scan" action and a secondary "Import photos" action (both can lead to placeholder screens for now), a Settings screen (language, theme: system/light/dark), English and Arabic with correct right-to-left layout, light and dark themes, and app name and icon "Plainpaper". Every pull request must automatically run formatting checks, static analysis, all unit tests on both platforms, a coverage report, and build both apps. Success: the app launches on an Android phone and an iPhone simulator, switching to Arabic flips the layout to right-to-left, and the CI pipeline passes on a pull request.
```

### Feature 2 — Scan engine (photo → clean scan)

```
/speckit-specify Scan engine. Given one photo of a paper document, produce a page that looks like it came from a real flatbed scanner. Steps: detect the page edges (with a fallback when detection fails), correct perspective so the page is a perfect rectangle, flatten curled or folded pages when the device is capable, remove shadows and uneven lighting, make the paper pure white while keeping text sharp and dark (including faint pen and stamps), correct small tilts and upside-down pages, and output at a true page size (A4 by default, Letter in US/Canada/Mexico/Philippines) at 300 DPI. Output styles: Scan (black & white), Grayscale, Color document (white background but keeps stamps, signatures and logos in color), Original (light cleanup only). Processing must be fully on-device, cancellable, and fast enough to feel instant on a mid-range phone (target under 2 seconds per page) and must not crash on low-end phones (fall back to simpler steps). Quality is verified against a reference set of real photos (IDs, receipts, curled pages, dark rooms, Arabic/English/French text) with measurable metrics, and must match or beat leading competitor apps on most of them.
```

### Feature 3 — Capture & import

```
/speckit-specify Capture and import. Users can create a document either with the camera or by importing existing photos. Camera: live outline of the detected page, auto-capture when the page is steady, manual shutter, flash toggle, and multi-page capture in one session with a page counter. Import: pick many photos from the gallery at once (free users up to 10 per import, Pro unlimited) and convert them all into scanned pages in one go with progress shown and the ability to cancel. Camera permission is requested only when needed with a clear explanation; if denied, the user can still import photos. Modes on the camera: Document (default), ID card, Receipt.
```

### Feature 4 — Review & edit pages

```
/speckit-specify Review and edit. After capture or import, users see all pages as thumbnails and can: adjust the crop corners with a magnifier, switch output style per page or for all pages, rotate, reorder by drag and drop, delete, retake, and add more pages. Changes are non-destructive (the original photo is kept so the user can re-edit). The user can finish with one tap to save the document with an automatic name like "Scan 23 Oct 2026".
```

### Feature 5 — Export, share & print

```
/speckit-specify Export, share and print. Users can export a document as PDF or JPG images, choose file size (Small for WhatsApp/email, Standard, Print quality) with the estimated size shown, and share in one tap through the system share sheet with WhatsApp surfaced prominently, save to device files, or print directly. Pro users can protect a PDF with a password.
```

### Feature 6 — Document library

```
/speckit-specify Document library. The Home screen lists documents newest first with thumbnail, name, page count and date. Users can rename, delete (with undo), move into folders, create/rename/delete folders, and search by document name (and by recognized text for Pro users). The list stays fast with hundreds of documents.
```

### Feature 7 — ID card & receipt modes

```
/speckit-specify ID card and receipt modes. ID card mode guides the user to capture the front and then the back of an ID card or passport page and places both, at real size, on a single A4/Letter page. Receipt mode keeps long narrow receipts at their natural proportions instead of forcing them onto a page, with optional fit-to-page on export.
```

### Feature 8 — OCR & searchable PDF (Pro)

```
/speckit-specify Text recognition (Pro). Recognize text on scanned pages in Arabic, English, French, Spanish, Portuguese and Indonesian, fully on-device. Users can copy text from a page, search documents by their text, and export searchable PDFs (selectable text layer over the image). Language packs that are large are downloaded on first use with clear consent and size shown. Mixed Arabic/English pages must be handled.
```

### Feature 9 — Pro purchase & app lock

```
/speckit-specify Pro purchase and app lock. A single one-time "Plainpaper Pro" purchase with local prices per country, a clear screen listing what Pro adds (OCR & searchable PDF, unlimited batch import, password-protected PDFs, app lock), restore purchase, and graceful handling when offline. Free features are never interrupted by upsells; Pro is offered only when the user taps a Pro feature. App lock (Pro) protects the app with device biometrics or PIN.
```

### Feature 10 — Onboarding & localization

```
/speckit-specify Onboarding and localization. First launch shows three short skippable screens: "Photos in, plain paper out", "100% private — nothing leaves your phone", "No watermark. No ads. Ever." No sign-up. The app is available in English, Arabic, French, Spanish, Portuguese and Indonesian, follows the device language by default, and can be changed in Settings. Default page size follows the user's region.
```

---

## Clarify, plan, tasks, analyze, implement (per feature)

After each `/speckit-specify`:

```
/speckit-clarify
```

Answer its questions. Then the plan — **paste this tech stack every time** (add feature-specific notes at the end):

```
/speckit-plan Tech stack and architecture:
- Kotlin Multiplatform targeting Android and iOS, Compose Multiplatform for all UI (latest stable versions, managed in gradle/libs.versions.toml).
- Gradle modules: :composeApp (app entry, navigation, DI wiring), :iosApp (Xcode host), :core:domain (pure Kotlin models, repository interfaces, use cases), :core:data (repository implementations), :core:database, :core:designsystem (theme, typography, components, RTL-safe), :core:scanengine (scan pipeline interface + platform implementations), :core:testing (fakes, test dispatchers), and :feature:<name> modules each containing presentation (ViewModel, UiState, screens) and wiring. Use Gradle convention plugins in build-logic to share module config.
- Clean Architecture: domain has no Android/iOS/Compose imports; data depends on domain; features depend on domain and designsystem only.
- MVVM + UDF: androidx.lifecycle ViewModel (multiplatform), one immutable UiState data class exposed as StateFlow, user actions as a sealed interface, one-off effects via Channel. Navigation with Navigation Compose multiplatform, type-safe routes.
- Coroutines and Flow everywhere; a DispatcherProvider is injected.
- DI: Koin. Local DB: Room (KMP). Preferences: DataStore (KMP). Resources/strings: Compose Multiplatform resources with values-ar etc.
- Scan engine: shared C++ core using OpenCV, bridged with JNI on Android and cinterop on iOS, behind a ScanEngine interface in commonMain. On-device ML models (TFLite/ONNX) only where needed, with capability detection for low-end devices.
- Platform services behind interfaces: camera (CameraX / AVFoundation), OCR (ML Kit / Apple Vision, Tesseract or PaddleOCR for Arabic if needed), PDF writing (PdfDocument / PDFKit), purchases (RevenueCat KMP), biometrics.
- Testing: kotlin.test, kotlinx-coroutines-test, Turbine, fakes in :core:testing, Kover coverage (≥80% domain + presentation), screenshot tests for key screens in light/dark and LTR/RTL, reference-photo regression tests for the scan engine.
- Quality: Spotless + ktlint, detekt, explicit API mode for core modules.
- CI: GitHub Actions — on every PR: lint/detekt, unit tests (JVM/Android on ubuntu, iOS simulator on macos), Kover report and gate, assembleDebug for Android, iOS framework/app build; Gradle and Kotlin/Native caches enabled.
```

Then:

```
/speckit-tasks
```

```
/speckit-analyze
```

Fix anything `analyze` reports, then:

```
/speckit-implement
```

---

## Claude design prompt

Paste this into Claude (claude.ai) to design the UI. It asks for an interactive mockup you can click through.

```
Design the mobile app UI for "Plainpaper", a document scanner app for Android and iPhone that turns photos of paper into documents that look like they came from a real flatbed scanner.

Brand
- Promise: "Photos in. Plain paper out." Private (everything stays on the phone), honest (no ads, no watermark), simple.
- Personality: calm, clean, trustworthy, a little warm — like good paper and ink. Not techy, not playful-childish.
- Colors: paper white and soft off-white surfaces, near-black "ink" text, ONE accent color (suggest a deep ink blue or a warm stamp red-orange — pick one and justify). Full dark theme where the document pages stay white.
- Typography: one clean sans-serif family that also has a good Arabic companion (e.g. Inter + IBM Plex Sans Arabic, or Noto Sans + Noto Sans Arabic). Large, readable sizes.
- Icon/logo idea: a simple sheet of paper with a folded corner or a subtle "flatten" motion.

Audience
- Global, with a strong base in Egypt and the Gulf. Many users on mid/low-end Android phones. Many are not tech-savvy. The main way they send documents is WhatsApp.

Screens to design (mobile portrait, show each in English LTR AND Arabic RTL, light and dark):
1. Onboarding (3 slides): "Photos in, plain paper out" / "100% private — nothing leaves your phone" / "No watermark. No ads. Ever." Skip button, no sign-up.
2. Home: empty state (friendly illustration, big Scan button, "Import photos" secondary) and a filled state (document list with thumbnails, name, pages, date; search bar; folders).
3. Camera: live page outline overlay, auto-capture indicator, mode switcher (Document / ID card / Receipt), flash, page counter, thumbnail of last page, done button.
4. Import progress: converting 12 photos with progress and cancel.
5. Review & edit: page thumbnails strip, large preview, style chips (Scan · Grayscale · Color · Original), crop, rotate, reorder, delete, add page, "Save" primary action.
6. Before/after moment: a satisfying comparison of the original photo vs the clean scan (this is the "wow" of the app).
7. Share sheet: PDF / JPG toggle, size (Small for WhatsApp · Standard · Print) with estimated MB, big WhatsApp button, other share options, print, save to files.
8. Document view: pages, rename, move to folder, copy text (Pro badge), share.
9. Pro screen: one-time price, list of what Pro adds, restore purchase, no pressure tone.
10. Settings: language, theme, default page size (A4/Letter), default style, app lock, about/privacy.

Requirements
- Scan-to-share in as few taps as possible; the Scan button is always one tap away.
- Accessibility: 48dp minimum touch targets, high contrast, supports large text, clear labels.
- RTL must be truly mirrored (navigation, chevrons, progress) while document images are not mirrored.
- Follow Material 3 structure so it maps cleanly to Compose Multiplatform, but give it its own identity.

Deliverables
- An interactive clickable prototype of the screens above with an EN/AR and light/dark toggle.
- A design tokens list (colors with hex for light and dark, type scale, spacing, corner radius, elevation) and the reusable components list (buttons, chips, document card, page thumbnail, top bar, bottom sheet, empty state), named so they can be implemented in Compose.
```
