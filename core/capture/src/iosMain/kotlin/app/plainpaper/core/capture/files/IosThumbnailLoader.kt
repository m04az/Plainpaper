package app.plainpaper.core.capture.files

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import app.plainpaper.core.domain.coroutines.DispatcherProvider
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy

/** ImageIO thumbnail → Skia image → Compose bitmap. */
@OptIn(ExperimentalForeignApi::class)
internal class IosThumbnailLoader(
    private val dispatchers: DispatcherProvider,
) : ThumbnailLoader {
    override suspend fun load(
        path: String,
        maxEdge: Int,
    ): ImageBitmap? =
        withContext(dispatchers.io) {
            val thumbnail = thumbnailImage(path, maxEdge) ?: return@withContext null
            val data = UIImagePNGRepresentation(thumbnail) ?: return@withContext null
            runCatching { Image.makeFromEncoded(data.toByteArray()).toComposeImageBitmap() }.getOrNull()
        }

    private fun NSData.toByteArray(): ByteArray {
        val size = length.toInt()
        val bytes = ByteArray(size)
        if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
        return bytes
    }
}
