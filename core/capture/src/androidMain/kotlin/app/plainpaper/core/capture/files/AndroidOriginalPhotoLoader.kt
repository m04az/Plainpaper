package app.plainpaper.core.capture.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.os.Build
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface
import app.plainpaper.core.domain.coroutines.DispatcherProvider
import app.plainpaper.core.scanengine.DecodeSizing
import app.plainpaper.core.scanengine.PhotoSize
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Same orientation rules as `AndroidPhotoDecoder`: `ImageDecoder` on API 28+ (EXIF applied, and
 * its size is the oriented size), `BitmapFactory` plus `ExifInterface` rotation before that. The
 * decoded size is computed with the shared [DecodeSizing] the engine's decoder uses.
 */
internal class AndroidOriginalPhotoLoader(
    private val dispatchers: DispatcherProvider,
) : OriginalPhotoLoader {
    override suspend fun load(
        path: String,
        maxEdge: Int,
        maxPixels: Int,
    ): OriginalPhoto? =
        withContext(dispatchers.io) {
            val file = File(path)
            if (!file.isFile) return@withContext null
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    loadModern(file, maxEdge, maxPixels)
                } else {
                    loadLegacy(file, maxEdge, maxPixels)
                }
            }.getOrNull()
        }

    @Suppress("NewApi") // guarded by the SDK_INT check in load()
    private fun loadModern(
        file: File,
        maxEdge: Int,
        maxPixels: Int,
    ): OriginalPhoto {
        var decoded = PhotoSize(0, 0)
        val bitmap =
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoded = DecodeSizing.sampledSize(info.size.width, info.size.height, maxPixels)
                val target = DecodeSizing.fitLongEdge(info.size.width, info.size.height, maxEdge)
                decoder.setTargetSize(target.width, target.height)
            }
        return OriginalPhoto(bitmap.asImageBitmap(), decoded.width, decoded.height)
    }

    private fun loadLegacy(
        file: File,
        maxEdge: Int,
        maxPixels: Int,
    ): OriginalPhoto? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val rotation = exifRotationDegrees(file)
        val quarterTurn = rotation == QUARTER_TURN || rotation == THREE_QUARTER_TURN
        val width = if (quarterTurn) bounds.outHeight else bounds.outWidth
        val height = if (quarterTurn) bounds.outWidth else bounds.outHeight
        val decoded = DecodeSizing.sampledSize(width, height, maxPixels)
        var sample = 1
        while (maxOf(width, height) / sample > maxEdge) sample *= 2
        val raw =
            BitmapFactory.decodeFile(
                file.path,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            ) ?: return null
        val upright =
            if (rotation == 0) {
                raw
            } else {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also { raw.recycle() }
            }
        return OriginalPhoto(upright.asImageBitmap(), decoded.width, decoded.height)
    }

    private fun exifRotationDegrees(file: File): Int =
        when (ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> QUARTER_TURN
            ExifInterface.ORIENTATION_ROTATE_180 -> HALF_TURN
            ExifInterface.ORIENTATION_ROTATE_270 -> THREE_QUARTER_TURN
            else -> 0
        }

    private companion object {
        const val QUARTER_TURN = 90
        const val HALF_TURN = 180
        const val THREE_QUARTER_TURN = 270
    }
}
