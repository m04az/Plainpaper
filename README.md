# Plainpaper

Photos in. Plain paper out. Plainpaper turns photos of paper into documents that look like they
came from a real flatbed scanner. Everything happens on the phone: no account, no uploads, no
ads, no watermark.

One Kotlin Multiplatform codebase with Compose Multiplatform UI for Android (8.0+) and iOS (15+).

## Prerequisites

| Tool | Version |
|------|---------|
| JDK | 21 |
| Android SDK | platform 37, build-tools 36 |
| Xcode | 26.4 (iOS only) |

## Build, test, run

```sh
./gradlew spotlessCheck detekt testAndroidHostTest koverVerify   # quality + tests + coverage gate
./gradlew :androidApp:installDebug                               # install on a connected device/emulator
open iosApp/iosApp.xcodeproj                                     # run on an iPhone simulator
```

Full validation steps per feature live in `specs/<feature>/quickstart.md`, starting with
[specs/001-app-foundation/quickstart.md](specs/001-app-foundation/quickstart.md). Project rules
are in `.specify/memory/constitution.md`; agent guidance in `CLAUDE.md`.

## Structure

`build-logic/` (Gradle conventions) · `androidApp/` · `iosApp/` · `composeApp/` (shared app) ·
`core/{domain,data,designsystem,testing}` · `feature/{home,settings}` · `specs/` · `.github/workflows/`
