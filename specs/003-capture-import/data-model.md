# Data Model: Capture & Import (003)

Immutable Kotlin values unless noted. Pure models used by later features live in
`:core:domain` (`app.plainpaper.core.domain.model.capture`); screen and service types live
in `:feature:capture` and `:core:capture`.

## `:core:domain` models

### CaptureSession

| Field | Type | Rules |
|-------|------|-------|
| `id` | `String` | UUID-like, unique per session; folder name on disk |
| `source` | `SessionSource` | `Camera` or `Import`; fixed at creation |
| `createdAtEpochMillis` | `Long` | |
| `pages` | `List<CapturedPage>` | ordered; `position` equals index; never reordered in this feature |
| `status` | `SessionStatus` | `Active` → `HandedOff` → (Feature 4 saves/discards) or `Discarded` |

### CapturedPage

| Field | Type | Rules |
|-------|------|-------|
| `id` | `String` | unique within the session |
| `position` | `Int` | 0-based capture/pick order |
| `originalPath` | `String` | app-private file; always present once the page exists |
| `pagePath` | `String?` | processed Scan-style page; null until `Ready` |
| `thumbnailPath` | `String?` | 400 px long edge; null until `Ready` |
| `state` | `PageState` | see transitions |
| `tag` | `PageTag` | `Document`, `IdFront(cardId)`, `IdBack(cardId)`, `Receipt`; imported pages are `Document` |
| `corners` | `Quad?` | engine corners in original-photo pixels; null until processed |
| `confidence` | `Confidence?` | engine confidence; `NotFound` ⇒ `NeedsAttention` |
| `pageSize` | `PageSize` | the size used for `pagePath` |
| `rotationApplied` | `Int` | from the engine (0/90/180/270) |

### PageState (sealed)

`Pending` → `Processing` → `Ready` | `NeedsAttention(reason)` | `Failed(reason)`

- `Pending`: original stored, not started.
- `Processing`: engine running; on process death it is treated as `Pending` again.
- `Ready`: `pagePath` and `thumbnailPath` set.
- `NeedsAttention(reason ∈ {NoPageFound, TooDark, LowConfidence})`: a page exists (whole
  photo cleaned or low-confidence crop); review should offer corner adjustment.
- `Failed(reason ∈ {Unreadable, OutOfMemory, Internal, Cancelled})`: original kept; no page.

### CaptureMode (enum) and PageTag

`Document`, `IdCard`, `Receipt`. `PageTag` is derived by `ModeSequencer`: in `IdCard` mode
the first capture gets `IdFront(cardId)`, the second `IdBack(cardId)`, the third starts a
new `cardId`. `Receipt` and `IdCard` pages use `PageSize.Natural`; `Document` uses the
region default.

### CaptureSettings

`flash: Boolean = false`, `autoCapture: Boolean = true`, `mode: CaptureMode = Document`.
Persisted in the Preferences DataStore (keys `capture.flash`, `capture.auto`, `capture.mode`).

### ImportBatch (feature state, not persisted beyond the session)

`items: List<ImportItem(uri, state ∈ {Queued, Converting, Done(pageId), Skipped(reason)})>`,
`doneCount`, `skippedCount`, `estimatedSecondsLeft`, `cancelled`. The time estimate is the
moving average of the last three conversions × remaining items, rounded up, minimum 1.

### CameraPermissionState (enum)

`NotAsked`, `Granted`, `Denied`, `DeniedPermanently`, `Unavailable`.

### ProStatus

`data class ProStatus(val isPro: Boolean)`; `ProStatusRepository.status: Flow<ProStatus>`;
`FreeOnlyProStatusRepository` emits `isPro = false`.

### Free import limit

`ImportLimit.evaluate(pickedCount, isPro): Decision` → `Proceed(all)`,
`ExceedsFreeLimit(limit = 10, picked)`. `ExceedsFreeLimit` yields the sheet with
`ImportFirst(limit)` and `SeePro`.

## `:core:capture` types

### CameraController (interface)

- `val state: StateFlow<CameraState>` — `Idle`, `Starting`, `Running(hasFlash)`, `Error(reason)`.
- `val frames: Flow<PreviewFrame>` — analysis frames (640 px long edge, RGBA or BGRA);
  conflated; emitted only while `Running`.
- `suspend fun start()`, `fun stop()`.
- `suspend fun capture(flash: Boolean): CaptureResult` — `Ready(originalPath, width, height)`
  or `Failed(reason)`; writes the JPEG straight into the session folder path given at start.
- `fun setFlash(enabled: Boolean)`.

### PreviewFrame

`width`, `height`, `strideBytes`, `format ∈ {RGBA, BGRA}`, `timestampMillis`, and
`withPixels { ptr -> }`-style pinned access used by the engine bridge; frames are owned by
the camera stack and valid only inside the callback (the gate copies nothing).

### DetectResult (`:core:scanengine`, returned by `ScanEngine.detect`)

`corners: Quad?` (frame pixels, null when not found), `confidence: Confidence`,
`fillFraction: Float`, `meanLuma: Float` (0..255), `micros: Long`.

### DetectionSample / GateState (`:feature:capture`)

`DetectionSample(timestampMillis, corners: Quad?, confidence, fillFraction, meanLuma)`.
`GateState`: `NoPage`, `LowConfidence`, `TooDark`, `Tracking(progress: Float)`,
`Capture`, `Cooldown`. Thresholds: `steadyWindowMillis` (700 / 1000), `maxDriftFraction`
0.015 of the frame diagonal, `minFill` 0.20, `darkLuma` 40, `cooldownDriftFraction` 0.10.

### PhotoPicker

`suspend fun pick(maxItems: Int): List<PickedPhoto(uri, displayName?)>` through
`rememberPhotoPicker()`; `ImageFiles.copyOriginal(uri, destPath): CopyResult`.

### ImageFiles

`writeJpeg(output: ScanOutput, path, quality = 90)`, `writeThumbnail(fromPath, toPath,
maxEdge = 400)`, `loadThumbnail(path, maxEdge): ImageBitmap?`, `copyOriginal(uri, destPath)`,
`freeBytes(dir): Long` (storage-full check: refuse capture below 50 MB free).

### BackgroundTask

`suspend fun <T> keepAlive(block: suspend () -> T): T` — Android: runs `block`; iOS: wraps
in a background task with expiration → cancels `block`.

### RegionProvider

`fun regionCode(): String?` → `PageSize.defaultFor(...)`.

## `:feature:capture` UI states

### CameraUiState

`permission: CameraPermissionState`, `camera: CameraState`, `mode`, `flash`, `autoCapture`,
`outline: Quad?` (in preview-view coordinates), `gate: GateState`, `hint: HintKind`
(`HoldSteady`, `TapShutter`, `MoveCloser`, `TooDark`, `OnePageAtATime`, `FrontOfCard`,
`BackOfCard`, `FitReceipt`), `pageCount`, `latestThumbnail: ImageBitmap?`,
`latestPageState`, `showDiscardConfirm`, `storageFull`, `capturing` (flash animation).

### ImportUiState

`phase ∈ {Picking, LimitSheet(picked, limit), Converting, Finished, Cancelling, CancelChoice}`,
`items`, `doneCount`, `total`, `skippedCount`, `estimatedSecondsLeft`, `sessionId`.

### HandoffUiState

`pages: List<HandoffPage(id, position, state, thumbnail)>`, `processingRemaining`.

## State transitions

```text
Session:   (none) --start--> Active --done/review--> HandedOff
                             Active --discard------> Discarded (files deleted)
Page:      Pending --processor--> Processing --> Ready | NeedsAttention | Failed
           Processing --process death--> Pending (restart)
Permission: NotAsked --request--> Granted | Denied | DeniedPermanently
            Denied --request--> Granted | DeniedPermanently
            DeniedPermanently --settings/resume--> Granted | DeniedPermanently
Gate:      NoPage/LowConfidence/TooDark <-> Tracking(p) --p≥1--> Capture --> Cooldown --moved/lost--> NoPage
```
