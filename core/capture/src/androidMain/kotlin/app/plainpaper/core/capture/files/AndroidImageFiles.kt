package app.plainpaper.core.capture.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.net.Uri
import android.os.StatFs
import app.plainpaper.core.domain.coroutines.DispatcherProvider
import app.plainpaper.core.scanengine.BitmapScanOutput
import app.plainpaper.core.scanengine.ScanOutput
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer

private const val THUMBNAIL_QUALITY = 85
private const val NV21_CHROMA_NEUTRAL: Byte = 128.toByte()

/** Decodes with `inSampleSize` so a thumbnail never allocates the full page. */
internal fun decodeScaled(
    path: String,
    maxEdge: Int,
): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}

/** Bitmap-based image files; grayscale pages are JPEG-encoded through an NV21 [YuvImage] (no ARGB copy). */
internal class AndroidImageFiles(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
) : ImageFiles {
    override suspend fun writeJpeg(
        output: ScanOutput,
        path: String,
        quality: Int,
    ): Boolean =
        withContext(dispatchers.io) {
            val bitmap = (output as? BitmapScanOutput)?.bitmap ?: return@withContext false
            runCatching {
                FileOutputStream(File(path)).use { stream ->
                    if (bitmap.config == Bitmap.Config.ALPHA_8) {
                        grayToNv21(bitmap).compressToJpeg(Rect(0, 0, bitmap.width, bitmap.height), quality, stream)
                    } else {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                    }
                }
            }.getOrDefault(false)
        }

    override suspend fun writeThumbnail(
        fromPath: String,
        toPath: String,
        maxEdge: Int,
    ): Boolean =
        withContext(dispatchers.io) {
            val bitmap = decodeScaled(fromPath, maxEdge) ?: return@withContext false
            runCatching {
                FileOutputStream(File(toPath)).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, stream)
                }
            }.getOrDefault(false).also { bitmap.recycle() }
        }

    override suspend fun copyOriginal(
        uri: String,
        destinationPath: String,
    ): CopyResult =
        withContext(dispatchers.io) {
            val destination = File(destinationPath)
            try {
                val input =
                    context.contentResolver.openInputStream(Uri.parse(uri))
                        ?: return@withContext CopyResult.Failed(CopyFailure.NotFound)
                input.use { stream -> destination.outputStream().use { out -> stream.copyTo(out) } }
            } catch (_: IOException) {
                destination.delete()
                return@withContext CopyResult.Failed(CopyFailure.Io)
            } catch (_: SecurityException) {
                return@withContext CopyResult.Failed(CopyFailure.NotFound)
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(destinationPath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                destination.delete()
                CopyResult.Failed(CopyFailure.NotAnImage)
            } else {
                CopyResult.Copied(destinationPath, destination.length())
            }
        }

    override fun freeBytes(directory: String): Long =
        runCatching { StatFs(File(directory).let { if (it.exists()) it else it.parentFile ?: it }.path).availableBytes }
            .getOrDefault(Long.MAX_VALUE)

    private fun grayToNv21(bitmap: Bitmap): YuvImage {
        val width = bitmap.width
        val height = bitmap.height
        val gray = ByteBuffer.allocate(bitmap.rowBytes * height)
        bitmap.copyPixelsToBuffer(gray)
        val plane = gray.array()
        val nv21 = ByteArray(width * height + 2 * ((width + 1) / 2) * ((height + 1) / 2))
        for (y in 0 until height) {
            System.arraycopy(plane, y * bitmap.rowBytes, nv21, y * width, width)
        }
        nv21.fill(NV21_CHROMA_NEUTRAL, width * height, nv21.size)
        return YuvImage(nv21, ImageFormat.NV21, width, height, intArrayOf(width, width))
    }
}

/** The production [ImageFiles] for Android; exposed for device tests of other modules. */
public fun androidImageFiles(
    context: Context,
    dispatchers: DispatcherProvider,
): ImageFiles = AndroidImageFiles(context, dispatchers)
