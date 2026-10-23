package app.plainpaper.core.scanengine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import app.plainpaper.core.domain.coroutines.DispatcherProvider
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * `ImageDecoder` on API 28+ (EXIF applied, HEIC where the device supports HEVC); `BitmapFactory`
 * plus `ExifInterface` on API 26-27, where HEIC is reported as unsupported (research R12).
 */
public class AndroidPhotoDecoder(
    private val dispatchers: DispatcherProvider,
) : PhotoDecoder {
    override suspend fun decode(
        path: String,
        maxPixels: Int,
    ): DecodeResult =
        withContext(dispatchers.io) {
            val file = File(path)
            if (!file.isFile) return@withContext DecodeResult.Failed(DecodeFailure.NotFound)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    decodeModern(file, maxPixels)
                } else {
                    decodeLegacy(file, maxPixels)
                }
            }.getOrElse { DecodeResult.Failed(DecodeFailure.Corrupt) }
        }

    @Suppress("NewApi") // guarded by the SDK_INT check in decode()
    private fun decodeModern(
        file: File,
        maxPixels: Int,
    ): DecodeResult {
        val source = ImageDecoder.createSource(file)
        val bitmap =
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                val pixels = info.size.width.toLong() * info.size.height
                if (pixels > maxPixels) {
                    decoder.setTargetSampleSize(sampleSizeFor(pixels, maxPixels))
                }
            }
        return DecodeResult.Ready(BitmapScanPhoto(bitmap))
    }

    private fun decodeLegacy(
        file: File,
        maxPixels: Int,
    ): DecodeResult {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            val heic = file.extension.lowercase() in setOf("heic", "heif")
            return DecodeResult.Failed(if (heic) DecodeFailure.UnsupportedFormat else DecodeFailure.Corrupt)
        }
        val pixels = bounds.outWidth.toLong() * bounds.outHeight
        val options =
            BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                if (pixels > maxPixels) inSampleSize = sampleSizeFor(pixels, maxPixels)
            }
        val decoded = BitmapFactory.decodeFile(file.path, options) ?: return DecodeResult.Failed(DecodeFailure.Corrupt)
        val rotation = exifRotationDegrees(file)
        val upright =
            if (rotation == 0f) {
                decoded
            } else {
                val matrix = Matrix().apply { postRotate(rotation) }
                Bitmap
                    .createBitmap(
                        decoded,
                        0,
                        0,
                        decoded.width,
                        decoded.height,
                        matrix,
                        true,
                    ).also { decoded.recycle() }
            }
        return DecodeResult.Ready(BitmapScanPhoto(upright))
    }

    @Suppress("MagicNumber") // EXIF orientation tags map to fixed rotations
    private fun exifRotationDegrees(file: File): Float =
        when (ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

    private fun sampleSizeFor(
        pixels: Long,
        maxPixels: Int,
    ): Int {
        var sample = 1
        val needed = ceil(sqrt(pixels.toDouble() / maxPixels)).toInt()
        while (sample < needed) sample *= 2
        return sample
    }
}
