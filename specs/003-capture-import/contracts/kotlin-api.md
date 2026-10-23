# Contract: Kotlin APIs (003)

## `:core:domain`

```kotlin
public interface CaptureSessionRepository {
    public val activeSession: Flow<CaptureSession?>           // emits current value + every change
    public suspend fun start(source: SessionSource): CaptureSession
    public suspend fun addPage(sessionId: String, originalPath: String, tag: PageTag, pageSize: PageSize): CapturedPage
    public suspend fun updatePage(sessionId: String, page: CapturedPage)
    public suspend fun markHandedOff(sessionId: String)
    public suspend fun discard(sessionId: String)              // deletes the folder
    public suspend fun recoverable(): CaptureSession?          // Active/HandedOff session found at launch
    public fun sessionDirectory(sessionId: String): String     // where originals are written
}

public interface ProStatusRepository { public val status: Flow<ProStatus> }

public interface CaptureSettingsRepository {
    public val settings: Flow<CaptureSettings>
    public suspend fun set(settings: CaptureSettings)
}
```

Use cases (one class each, `operator fun invoke`): `ObserveActiveSession`, `StartSession`,
`AddCapturedPage`, `FinishSession` (marks handed off), `DiscardSession`, `RecoverSession`,
`EvaluateImportLimit(pickedCount): ImportLimit.Decision`, `ResolveDefaultPageSize(mode)`.

## `:core:scanengine` addition

```kotlin
public interface ScanEngine {
    // existing analyze/render/capability …
    /** Detection only, for live preview frames. Never throws; < 40 ms on a 640 px frame (Full). */
    public suspend fun detect(frame: PreviewFrame): DetectResult
}
public data class DetectResult(val corners: Quad?, val confidence: Confidence, val fillFraction: Float, val meanLuma: Float, val micros: Long)
```

`PreviewFrame` is declared in `:core:scanengine` (so the engine does not depend on
`:core:capture`); `:core:capture` produces it.

## `:core:capture`

```kotlin
public interface CameraController {
    public val state: StateFlow<CameraState>
    public val frames: Flow<PreviewFrame>
    public suspend fun start(outputDirectory: String)
    public fun stop()
    public fun setFlash(enabled: Boolean)
    public suspend fun capture(): CaptureResult      // Ready(originalPath, width, height) | Failed(reason)
}
@Composable public expect fun CameraPreview(controller: CameraController, modifier: Modifier)
/** Maps frame-pixel corners to preview-view coordinates (aspect-fill crop). */
public fun PreviewGeometry.frameToView(quad: Quad): Quad

public interface CameraPermission {
    public fun status(): CameraPermissionState
    public suspend fun request(): CameraPermissionState
    public fun openSettings()
}
@Composable public expect fun rememberCameraPermission(): CameraPermission

public interface PhotoPicker { public suspend fun pick(maxItems: Int): List<PickedPhoto> }
@Composable public expect fun rememberPhotoPicker(): PhotoPicker

public interface ImageFiles { writeJpeg; writeThumbnail; loadThumbnail; copyOriginal; freeBytes }
public interface BackgroundTask { public suspend fun <T> keepAlive(block: suspend () -> T): T }
public interface RegionProvider { public fun regionCode(): String? }

public val captureModule: Module   // ImageFiles, BackgroundTask, RegionProvider, CameraController factory
```

Koin: `captureModule` (core, platform actuals via `expect val capturePlatformModule`),
`captureFeatureModule` (ViewModels, `PageProcessor` single, use cases), `dataModule`
gains `CaptureSessionRepository`, `CaptureSettingsRepository`, `ProStatusRepository` and
needs `named(CAPTURE_ROOT_QUALIFIER): String` from the platform module
(Android `filesDir/capture`, iOS `Documents/capture`).

## `:feature:capture` routes (type-safe, `@Serializable`)

```kotlin
@Serializable data object CameraRoute : AppRoute
@Serializable data object ImportRoute : AppRoute                 // opens the picker immediately
@Serializable data class HandoffRoute(val sessionId: String) : AppRoute
```

`composeApp` wires: Home `onNavigateToScan → CameraRoute`, `onNavigateToImport →
ImportRoute`, Scan FAB → `CameraRoute`; `CameraRoute` Done → `HandoffRoute`; Import finished
→ `HandoffRoute`; Home shows the recovery prompt when `RecoverSession` returns a session.
`ScanPlaceholder` and `ImportPlaceholder` routes and strings are removed.

## `PageProcessor` (feature-internal, app-scoped)

```kotlin
internal class PageProcessor(engine: ScanEngine, decoder: PhotoDecoder, files: ImageFiles,
    sessions: CaptureSessionRepository, background: BackgroundTask, dispatchers: DispatcherProvider, capability: DeviceCapability) {
    fun enqueue(sessionId: String, pageId: String)
    fun cancel(sessionId: String, pageId: String? = null)
    fun resume()                      // re-queues Pending pages of the active session (launch/foreground)
    val inFlight: StateFlow<Set<String>>
}
```
