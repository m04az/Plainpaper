package app.plainpaper.core.capture.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject

private const val IMAGE_TYPE = "public.image"

/** PHPickerViewController (out of process, no permission); picked files are copied to a temp dir (research R6). */
@OptIn(ExperimentalForeignApi::class)
internal class PhPhotoPickerFactory : PhotoPickerFactory {
    @Composable
    override fun remember(): PhotoPicker = remember { PhPhotoPicker() }
}

@OptIn(ExperimentalForeignApi::class)
private class PhPhotoPicker : PhotoPicker {
    private var delegate: Delegate? = null

    override suspend fun pick(maxItems: Int): List<PickedPhoto> {
        val deferred = CompletableDeferred<List<PickedPhoto>>()
        withContext(Dispatchers.Main) {
            val configuration =
                PHPickerConfiguration().apply {
                    selectionLimit = maxItems.toLong()
                    filter = PHPickerFilter.imagesFilter
                }
            val controller = PHPickerViewController(configuration)
            val pickerDelegate = Delegate(deferred)
            delegate = pickerDelegate
            controller.delegate = pickerDelegate
            val presenter = topViewController()
            if (presenter == null) {
                deferred.complete(emptyList())
            } else {
                presenter.presentViewController(controller, animated = true, completion = null)
            }
        }
        return deferred.await().also { delegate = null }
    }

    private class Delegate(
        private val deferred: CompletableDeferred<List<PickedPhoto>>,
    ) : NSObject(),
        PHPickerViewControllerDelegateProtocol {
        override fun picker(
            picker: PHPickerViewController,
            didFinishPicking: List<*>,
        ) {
            picker.dismissViewControllerAnimated(true, completion = null)
            val results = didFinishPicking.filterIsInstance<PHPickerResult>()
            if (results.isEmpty()) {
                deferred.complete(emptyList())
                return
            }
            val copied = arrayOfNulls<PickedPhoto>(results.size)
            var remaining = results.size
            val directory = NSTemporaryDirectory() + "plainpaper-import-" + NSUUID().UUIDString
            NSFileManager.defaultManager.createDirectoryAtPath(
                directory,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
            results.forEachIndexed { index, result ->
                result.itemProvider.loadFileRepresentationForTypeIdentifier(IMAGE_TYPE) { url, _ ->
                    val source = url?.path
                    if (source != null) {
                        val destination = "$directory/photo-$index-${(url.lastPathComponent ?: "image.jpg")}"
                        if (NSFileManager.defaultManager.copyItemAtPath(source, toPath = destination, error = null)) {
                            copied[index] = PickedPhoto(destination, url.lastPathComponent)
                        }
                    }
                    remaining -= 1
                    if (remaining == 0) deferred.complete(copied.filterNotNull())
                }
            }
        }
    }

    private fun topViewController(): platform.UIKit.UIViewController? {
        val window =
            UIApplication.sharedApplication.windows
                .filterIsInstance<UIWindow>()
                .firstOrNull { it.isKeyWindow() }
                ?: UIApplication.sharedApplication.windows
                    .filterIsInstance<UIWindow>()
                    .firstOrNull()
        var controller = window?.rootViewController
        while (controller?.presentedViewController != null) controller = controller.presentedViewController
        return controller
    }
}
