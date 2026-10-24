package app.plainpaper.core.capture

import android.graphics.SurfaceTexture
import android.util.Log
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.plainpaper.core.capture.camera.CameraState
import app.plainpaper.core.capture.camera.CameraXController
import app.plainpaper.core.capture.camera.CameraXControllerFactory
import app.plainpaper.core.capture.camera.CaptureResult
import app.plainpaper.core.domain.coroutines.DispatcherProvider
import app.plainpaper.core.scanengine.AndroidCapabilityProvider
import app.plainpaper.core.scanengine.AndroidPhotoDecoder
import app.plainpaper.core.scanengine.DecodeResult
import app.plainpaper.core.scanengine.DefaultScanEngine
import app.plainpaper.core.scanengine.bridge.platformNativeBridge
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.Executors

/**
 * CameraX plumbing on a device or the emulator's virtual-scene camera (research R13): the
 * controller runs, delivers analysis frames, captures one JPEG that decodes, and stops.
 * Requires the CAMERA permission to be granted (`scripts/camera-smoke-android.sh` does it).
 */
@RunWith(AndroidJUnit4::class)
class CameraSmokeTest {
    private class DeviceDispatchers : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Main
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val default: CoroutineDispatcher = Dispatchers.Default
    }

    @Test
    fun framesFlowAndACaptureDecodes() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val dispatchers = DeviceDispatchers()
            val controller = CameraXControllerFactory(context, dispatchers).create()
            val directory = File(context.cacheDir, "camera-smoke").apply { mkdirs() }
            // No viewfinder in a test: answer the preview's surface request with an off-screen texture,
            // otherwise CameraX waits for the preview surface and never starts the session.
            val executor = Executors.newSingleThreadExecutor()
            val surfaces =
                launch {
                    (controller as CameraXController).surfaceRequests.filterNotNull().collect { request ->
                        val texture =
                            SurfaceTexture(
                                0,
                            ).apply { setDefaultBufferSize(request.resolution.width, request.resolution.height) }
                        val surface = Surface(texture)
                        request.provideSurface(surface, executor) {
                            surface.release()
                            texture.release()
                        }
                    }
                }
            Log.i(TAG, "starting")
            withTimeout(TIMEOUT_MILLIS) { controller.start(directory.path) }
            Log.i(TAG, "started: ${controller.state.value}")
            val running =
                withTimeout(TIMEOUT_MILLIS) {
                    controller.state.first {
                        it !is CameraState.Starting &&
                            it !is CameraState.Idle
                    }
                }
            assertTrue("camera must run: $running", running is CameraState.Running)

            // Release every frame as it arrives: the controller recycles a small buffer pool.
            val sizes =
                withTimeout(TIMEOUT_MILLIS) {
                    controller.frames
                        .map { frame -> (frame.frame.width to frame.frame.height).also { frame.release() } }
                        .take(MIN_FRAMES)
                        .toList()
                }
            Log.i(TAG, "frames: $sizes")
            assertEquals(MIN_FRAMES, sizes.size)
            sizes.forEach { (width, height) -> assertTrue(width >= MIN_FRAME_EDGE && height >= MIN_FRAME_EDGE) }

            // SC-002 sample: pp_detect on live analysis frames (median micros printed for results.md).
            val engine = DefaultScanEngine(platformNativeBridge(), AndroidCapabilityProvider(context), dispatchers)
            val micros =
                withTimeout(TIMEOUT_MILLIS) {
                    controller.frames
                        .map { frame -> engine.detect(frame.frame).micros.also { frame.release() } }
                        .take(DETECT_SAMPLES)
                        .toList()
                        .sorted()
                }
            Log.i(
                TAG,
                "detect micros median=${micros[micros.size / 2]} max=${micros.last()} capability=${engine.capability}",
            )

            val capture = withTimeout(TIMEOUT_MILLIS) { controller.capture() }
            Log.i(TAG, "capture: $capture")
            assertTrue("capture must succeed: $capture", capture is CaptureResult.Ready)
            val path = (capture as CaptureResult.Ready).originalPath
            val decoded = AndroidPhotoDecoder(dispatchers).decode(path)
            assertTrue("captured JPEG must decode: $decoded", decoded is DecodeResult.Ready)
            assertTrue(File(path).length() > 0)

            controller.stop()
            surfaces.cancel()
            executor.shutdown()
            assertEquals(CameraState.Idle, controller.state.value)
        }

    private companion object {
        const val TAG = "CameraSmoke"
        const val TIMEOUT_MILLIS = 20_000L
        const val MIN_FRAMES = 5
        const val MIN_FRAME_EDGE = 240
        const val DETECT_SAMPLES = 20
    }
}
