# Contract: Kotlin API (`:core:scanengine`, commonMain)

```kotlin
public interface ScanEngine {
    /** Detect, correct and normalise one photo. Suspends on dispatchers.default; cancellable. */
    public suspend fun analyze(photo: ScanPhoto, options: AnalyzeOptions = AnalyzeOptions()): AnalyzeResult

    /** Render an analysed page in a style/page size. Reuses geometry; cancellable. */
    public suspend fun render(analysis: ScanAnalysis, options: RenderOptions): RenderResult

    /** Capability the engine will use unless overridden. */
    public val capability: DeviceCapability
}

public sealed interface AnalyzeResult {
    public data class Ready(val analysis: ScanAnalysis) : AnalyzeResult      // outcome Success or TooDark
    public data class Failed(val outcome: Outcome) : AnalyzeResult            // InvalidInput, OutOfMemory, Internal
}
public sealed interface RenderResult {
    public data class Ready(val output: ScanOutput) : RenderResult
    public data class Failed(val outcome: Outcome) : RenderResult
}

public interface PhotoDecoder {
    /** Decodes JPEG/HEIC (platform permitting) honouring EXIF orientation, capped at maxPixels. */
    public suspend fun decode(path: String, maxPixels: Int = 12_000_000): DecodeResult
}
public sealed interface DecodeResult { Ready(photo: ScanPhoto); Failed(reason: DecodeFailure) }
public enum class DecodeFailure { NotFound, UnsupportedFormat, Corrupt, TooLarge }

public interface CapabilityProvider { fun current(): DeviceCapability; fun details(): CapabilityDetails }
```

Behavioural guarantees (tested in `commonTest` with `FakeNativeBridge`):

| # | Guarantee | Test |
|---|-----------|------|
| K1 | `analyze` runs on `DispatcherProvider.default`, never on main | dispatcher recorded by fake |
| K2 | Cancelling the calling coroutine sets the native flag and the result is never delivered | Turbine + `cancelAndJoin` |
| K3 | `OUT_OF_MEMORY` once → retry with `workingLongEdge / 2` and `Simplified`, `flags += RetriedAtLowerResolution`; twice → `Failed(OutOfMemory)` | fake scripted statuses |
| K4 | `TOO_DARK` → `Ready` with `outcome = TooDark` | |
| K5 | user corners are validated before touching native (`InvalidInput("corners")`) | |
| K6 | `render` after `close()` throws `IllegalStateException`; `close()` is idempotent | |
| K7 | Page size pixels: A4/Letter/Natural, portrait/landscape, Natural aspect clamps | `PageFraming` tests |
| K8 | Orientation vote: ≥ 60% weight and ≥ 3 lines flips; otherwise keep; axis ratio < 1.3 keeps | `OrientationVote` tests (pure Kotlin copy of the vote used by the suite report; the native vote is validated by the reference set) |
| K9 | `classifyCapability` boundaries (4 GiB, 64-bit, low-RAM) | `:core:domain` tests |
| K10 | API version mismatch → `Failed(Internal("api-version"))` on first use | |

Koin: `scanEngineModule` provides `CapabilityProvider` (platform), `PhotoDecoder`
(platform), `ScanEngine` (`DefaultScanEngine(bridge, capabilityProvider, dispatchers)`),
registered from `:composeApp` `AppModules`. `:core:testing` provides `FakeScanEngine`
(scripted results, records calls) and `FakeNativeBridge`.

Explicit API mode applies (core module). No string resources: the engine exposes enums
and codes; user-facing text belongs to Features 3/4.
