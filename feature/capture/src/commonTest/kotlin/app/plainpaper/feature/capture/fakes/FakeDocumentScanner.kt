package app.plainpaper.feature.capture.fakes

import app.plainpaper.core.capture.scanner.DocumentScanner
import app.plainpaper.core.capture.scanner.ScannerAvailability
import app.plainpaper.core.capture.scanner.ScannerResult

class FakeDocumentScanner(
    var availability: ScannerAvailability = ScannerAvailability.Ready,
    var result: ScannerResult = ScannerResult.Cancelled,
) : DocumentScanner {
    val scannedInto = mutableListOf<String>()

    override suspend fun availability(): ScannerAvailability = availability

    override suspend fun scan(destinationDir: String): ScannerResult {
        scannedInto += destinationDir
        return result
    }
}
