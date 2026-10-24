package app.plainpaper.core.capture.scanner

import androidx.compose.runtime.Composable

/** Whether the phone's built-in document scanner can be used (spec 004 FR-010). */
public sealed interface ScannerAvailability {
    public data object Ready : ScannerAvailability

    /** Android: the scanner module is still downloading through Play services. */
    public data object NeedsSetup : ScannerAvailability

    public data object Unavailable : ScannerAvailability
}

public sealed interface ScannerResult {
    /** JPEGs written into the destination directory, in page order. */
    public data class Pages(
        val paths: List<String>,
    ) : ScannerResult

    public data object Cancelled : ScannerResult

    public data class Failed(
        val reason: String,
    ) : ScannerResult
}

/** The phone's own scanner: Google's on Android, Apple's (VisionKit) on iOS (research R7, R8). */
public interface DocumentScanner {
    public suspend fun availability(): ScannerAvailability

    /** Opens the built-in scanner; pages are written as `original-<n>.jpg` into [destinationDir]. */
    public suspend fun scan(destinationDir: String): ScannerResult
}

public interface DocumentScannerFactory {
    @Composable
    public fun remember(): DocumentScanner
}
