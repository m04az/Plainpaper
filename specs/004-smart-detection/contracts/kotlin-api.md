# Kotlin API

## :core:scanengine

```kotlin
public fun interface DetectorModelSource { public suspend fun bytes(): ByteArray? }

public interface ScanEngine {
    // existing members …
    /** Loads the detection model once; false when unavailable (classic detector stays in use). */
    public suspend fun loadDetector(source: DetectorModelSource): Boolean
}

public data class AnalyzeOptions(/* existing */ val keepOrientation: Boolean = false)
public enum class DetectorKind { Classic, Model }
// DetectionResult / ScanAnalysis gain `detector: DetectorKind` and `presence: Float`.
```

## :core:capture

```kotlin
public sealed interface ScannerAvailability {
    public data object Ready : ScannerAvailability
    public data object NeedsSetup : ScannerAvailability   // Android: module still downloading
    public data object Unavailable : ScannerAvailability
}

public sealed interface ScannerResult {
    public data class Pages(val paths: List<String>) : ScannerResult  // JPEGs written into destinationDir
    public data object Cancelled : ScannerResult
    public data class Failed(val reason: String) : ScannerResult
}

public interface DocumentScanner {
    public suspend fun availability(): ScannerAvailability
    /** Opens the built-in scanner; pages are written as original-<n>.jpg into [destinationDir]. */
    public suspend fun scan(destinationDir: String): ScannerResult
}

public interface DocumentScannerFactory { @Composable public fun remember(): DocumentScanner }
```

## :core:domain

- `PageSource { Camera, Import, QuickScan }`; `CapturedPage.source`.
- `AddCapturedPageUseCase(sessionId, originalPath, tag, pageSize, source = Camera)`.
- QuickScan pages are added with `corners = fullFrame`, `confidence = High`; `PageProcessor`
  analyses them with `AnalyzeOptions(userCorners = corners, keepOrientation = true)`.
