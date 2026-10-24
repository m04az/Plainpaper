package app.plainpaper.core.capture.camera

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import app.plainpaper.core.domain.coroutines.DispatcherProvider
import app.plainpaper.core.scanengine.ByteBufferPreviewFrame
import app.plainpaper.core.scanengine.PixelFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.resume

private const val ANALYSIS_WIDTH = 640
private const val ANALYSIS_HEIGHT = 480
private const val CAPTURE_WIDTH = 4000
private const val CAPTURE_HEIGHT = 3000

/**
 * CameraX implementation (research R1): Preview + ImageAnalysis (RGBA, keep latest) +
 * ImageCapture bound to a private lifecycle the controller drives from start()/stop().
 */
internal class CameraXController(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
) : CameraController,
    LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    private val stateFlow = MutableStateFlow<CameraState>(CameraState.Idle)
    override val state: StateFlow<CameraState> get() = stateFlow

    private val frameFlow = MutableSharedFlow<CameraFrame>(replay = 0, extraBufferCapacity = 1)
    override val frames: Flow<CameraFrame> get() = frameFlow

    /** The preview surface request the viewfinder renders. */
    val surfaceRequests = MutableStateFlow<SurfaceRequest?>(null)

    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val pool = FramePool()
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var outputDirectory: File? = null
    private var frameCounter = 0
    private var flashEnabled = false

    override suspend fun start(outputDirectory: String) {
        this.outputDirectory = File(outputDirectory).apply { mkdirs() }
        stateFlow.value = CameraState.Starting
        try {
            withContext(dispatchers.main) {
                val provider =
                    this@CameraXController.provider
                        ?: ProcessCameraProvider.awaitInstance(context).also { this@CameraXController.provider = it }
                registry.currentState = Lifecycle.State.RESUMED
                val preview =
                    Preview.Builder().build().apply {
                        setSurfaceProvider { request ->
                            surfaceRequests.value =
                                request
                        }
                    }
                val analysis =
                    ImageAnalysis
                        .Builder()
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setResolutionSelector(
                            selector(
                                Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                            ),
                        ).build()
                        .apply { setAnalyzer(analysisExecutor, ::onImage) }
                val capture =
                    ImageCapture
                        .Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setResolutionSelector(
                            selector(
                                Size(CAPTURE_WIDTH, CAPTURE_HEIGHT),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                            ),
                        ).setFlashMode(if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                        .build()
                provider.unbindAll()
                val bound =
                    provider.bindToLifecycle(
                        this@CameraXController,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                        capture,
                    )
                camera = bound
                imageCapture = capture
                stateFlow.value = CameraState.Running(hasFlash = bound.cameraInfo.hasFlashUnit())
            }
        } catch (_: SecurityException) {
            stateFlow.value = CameraState.Error(CameraError.PermissionMissing)
        } catch (_: IllegalStateException) {
            stateFlow.value = CameraState.Error(CameraError.InUse)
        } catch (
            @Suppress("TooGenericExceptionCaught") _: Exception,
        ) {
            // CameraX reports a missing back camera or a failed provider init as checked
            // exceptions wrapped in several layers; every one of them means "no usable camera".
            stateFlow.value = CameraState.Error(CameraError.Unavailable)
        }
    }

    override fun stop() {
        runOnMain {
            registry.currentState = Lifecycle.State.CREATED
            provider?.unbindAll()
        }
        surfaceRequests.value = null
        camera = null
        imageCapture = null
        stateFlow.value = CameraState.Idle
    }

    /** Lifecycle and CameraX binding calls must run on the main thread; callers may not be on it. */
    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else Handler(Looper.getMainLooper()).post(block)
    }

    override fun setFlash(enabled: Boolean) {
        flashEnabled = enabled
        imageCapture?.flashMode = if (enabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }

    override suspend fun capture(): CaptureResult {
        val capture = imageCapture ?: return CaptureResult.Failed(CaptureFailure.NotRunning)
        val directory = outputDirectory ?: return CaptureResult.Failed(CaptureFailure.NotRunning)
        val file = File(directory, "original-${frameCounter.toString().padStart(INDEX_DIGITS, '0')}.jpg")
        frameCounter++
        return suspendCancellableCoroutine { continuation ->
            capture.takePicture(
                ImageCapture.OutputFileOptions.Builder(file).build(),
                analysisExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.path, bounds)
                        continuation.resume(CaptureResult.Ready(file.path, bounds.outWidth, bounds.outHeight))
                    }

                    override fun onError(exception: ImageCaptureException) {
                        file.delete()
                        val reason =
                            if (exception.imageCaptureError ==
                                ImageCapture.ERROR_FILE_IO
                            ) {
                                CaptureFailure.Storage
                            } else {
                                CaptureFailure.Internal
                            }
                        continuation.resume(CaptureResult.Failed(reason))
                    }
                },
            )
        }
    }

    private fun onImage(image: ImageProxy) {
        image.use {
            val plane = it.planes[0]
            val rowStride = plane.rowStride
            val bytes = rowStride * it.height
            val (buffer, release) = pool.acquire(bytes) ?: return
            val source = plane.buffer
            source.rewind()
            buffer.put(source)
            buffer.rewind()
            val frame =
                ByteBufferPreviewFrame(
                    width = it.width,
                    height = it.height,
                    strideBytes = rowStride,
                    format = PixelFormat.RGBA,
                    timestampMillis = it.imageInfo.timestamp / NANOS_PER_MILLI,
                    buffer = buffer,
                )
            if (!frameFlow.tryEmit(CameraFrame(frame, it.imageInfo.rotationDegrees, release))) release()
        }
    }

    private fun selector(
        size: Size,
        rule: Int,
    ): ResolutionSelector = ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy(size, rule)).build()

    private companion object {
        const val INDEX_DIGITS = 3
        const val NANOS_PER_MILLI = 1_000_000L
    }
}

internal class CameraXControllerFactory(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
) : CameraControllerFactory {
    override fun create(): CameraController = CameraXController(context, dispatchers)
}
