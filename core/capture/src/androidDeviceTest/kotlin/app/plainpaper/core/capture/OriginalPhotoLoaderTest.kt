package app.plainpaper.core.capture

import android.graphics.Bitmap
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.plainpaper.core.capture.files.AndroidOriginalPhotoLoader
import app.plainpaper.core.domain.coroutines.DispatcherProvider
import app.plainpaper.core.scanengine.AndroidPhotoDecoder
import app.plainpaper.core.scanengine.DecodeResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Engine corners live in the pixel space of `AndroidPhotoDecoder`; the crop editor's loader must
 * report that exact size and an upright bitmap of the same aspect, whatever the EXIF orientation.
 */
@RunWith(AndroidJUnit4::class)
class OriginalPhotoLoaderTest {
    private class DeviceDispatchers : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Main
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val default: CoroutineDispatcher = Dispatchers.Default
    }

    private val dispatchers = DeviceDispatchers()

    private fun jpeg(
        width: Int,
        height: Int,
        orientation: Int,
    ): File {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "orig-$orientation.jpg")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        return file
    }

    private fun check(
        orientation: Int,
        maxPixels: Int,
    ) = runBlocking {
        val file = jpeg(1200, 800, orientation)
        val engine = (AndroidPhotoDecoder(dispatchers).decode(file.path, maxPixels) as DecodeResult.Ready).photo
        val loaded = checkNotNull(AndroidOriginalPhotoLoader(dispatchers).load(file.path, 400, maxPixels))
        assertEquals(engine.width, loaded.decodedWidth)
        assertEquals(engine.height, loaded.decodedHeight)
        assertTrue(maxOf(loaded.bitmap.width, loaded.bitmap.height) <= 400)
        val engineAspect = engine.width.toFloat() / engine.height
        assertEquals(engineAspect, loaded.bitmap.width.toFloat() / loaded.bitmap.height, 0.02f)
    }

    @Test
    fun upright() = check(ExifInterface.ORIENTATION_NORMAL, 12_000_000)

    @Test
    fun rotated90() = check(ExifInterface.ORIENTATION_ROTATE_90, 12_000_000)

    @Test
    fun rotated270() = check(ExifInterface.ORIENTATION_ROTATE_270, 12_000_000)

    @Test
    fun rotated90AndSampledByTheEngine() = check(ExifInterface.ORIENTATION_ROTATE_90, 200_000)
}
