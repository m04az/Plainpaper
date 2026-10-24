package app.plainpaper.core.capture.scanner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.writeToFile
import platform.UIKit.UIApplication
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.VisionKit.VNDocumentCameraScan
import platform.VisionKit.VNDocumentCameraViewController
import platform.VisionKit.VNDocumentCameraViewControllerDelegateProtocol
import platform.darwin.NSObject

private const val JPEG_QUALITY = 0.9

/**
 * Apple's document camera (VisionKit), fully on-device and part of the OS (research R8). Each page
 * comes back already cropped and perspective-corrected and is written as a JPEG.
 */
@OptIn(ExperimentalForeignApi::class)
internal class VisionKitDocumentScannerFactory : DocumentScannerFactory {
    @Composable
    override fun remember(): DocumentScanner = remember { VisionKitDocumentScanner() }
}

@OptIn(ExperimentalForeignApi::class)
private class VisionKitDocumentScanner : DocumentScanner {
    // The controller keeps its delegate weakly: hold it for the duration of the scan.
    private var delegate: Delegate? = null

    override suspend fun availability(): ScannerAvailability =
        if (VNDocumentCameraViewController.isSupported()) ScannerAvailability.Ready else ScannerAvailability.Unavailable

    override suspend fun scan(destinationDir: String): ScannerResult {
        val deferred = CompletableDeferred<ScannerResult>()
        withContext(Dispatchers.Main) {
            val presenter = topViewController()
            if (presenter == null) {
                deferred.complete(ScannerResult.Failed("no view controller"))
            } else {
                val controller = VNDocumentCameraViewController()
                val scannerDelegate = Delegate(destinationDir, deferred)
                delegate = scannerDelegate
                controller.delegate = scannerDelegate
                presenter.presentViewController(controller, animated = true, completion = null)
            }
        }
        return deferred.await().also { delegate = null }
    }

    private class Delegate(
        private val destinationDir: String,
        private val deferred: CompletableDeferred<ScannerResult>,
    ) : NSObject(),
        VNDocumentCameraViewControllerDelegateProtocol {
        override fun documentCameraViewController(
            controller: VNDocumentCameraViewController,
            didFinishWithScan: VNDocumentCameraScan,
        ) {
            controller.dismissViewControllerAnimated(true, completion = null)
            deferred.complete(write(didFinishWithScan))
        }

        override fun documentCameraViewControllerDidCancel(controller: VNDocumentCameraViewController) {
            controller.dismissViewControllerAnimated(true, completion = null)
            deferred.complete(ScannerResult.Cancelled)
        }

        override fun documentCameraViewController(
            controller: VNDocumentCameraViewController,
            didFailWithError: NSError,
        ) {
            controller.dismissViewControllerAnimated(true, completion = null)
            deferred.complete(ScannerResult.Failed(didFailWithError.localizedDescription))
        }

        private fun write(scan: VNDocumentCameraScan): ScannerResult {
            val count = scan.pageCount.toInt()
            if (count == 0) return ScannerResult.Cancelled
            val files = NSFileManager.defaultManager
            files.createDirectoryAtPath(
                destinationDir,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
            val existing =
                files
                    .contentsOfDirectoryAtPath(
                        destinationDir,
                        error = null,
                    )?.filterIsInstance<String>()
                    .orEmpty()
            val first = nextOriginalIndex(existing)
            val written = mutableListOf<String>()
            for (page in 0 until count) {
                val image = scan.imageOfPageAtIndex(page.toULong())
                val path = "$destinationDir/${originalName(first + page)}"
                val data = UIImageJPEGRepresentation(image, JPEG_QUALITY)
                if (data == null || !data.writeToFile(path, atomically = true)) {
                    written.forEach { files.removeItemAtPath(it, error = null) }
                    return ScannerResult.Failed("could not write page ${page + 1}")
                }
                written += path
            }
            return ScannerResult.Pages(written)
        }
    }

    private fun topViewController(): UIViewController? {
        val windows = UIApplication.sharedApplication.windows.filterIsInstance<UIWindow>()
        var controller = (windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull())?.rootViewController
        while (controller?.presentedViewController != null) controller = controller.presentedViewController
        return controller
    }
}
