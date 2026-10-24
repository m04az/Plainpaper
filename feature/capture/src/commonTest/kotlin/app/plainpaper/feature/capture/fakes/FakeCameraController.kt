package app.plainpaper.feature.capture.fakes

import app.plainpaper.core.capture.camera.CameraController
import app.plainpaper.core.capture.camera.CameraFrame
import app.plainpaper.core.capture.camera.CameraState
import app.plainpaper.core.capture.camera.CaptureResult
import app.plainpaper.core.scanengine.ByteArrayPreviewFrame
import app.plainpaper.core.scanengine.PixelFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Scripted camera: tests push frames with [emitFrame] and script [nextCapture]. */
class FakeCameraController(
    var hasFlash: Boolean = true,
) : CameraController {
    val stateFlow = MutableStateFlow<CameraState>(CameraState.Idle)
    override val state: StateFlow<CameraState> get() = stateFlow

    private val frameFlow = MutableSharedFlow<CameraFrame>(extraBufferCapacity = 64)
    override val frames: Flow<CameraFrame> get() = frameFlow

    var outputDirectory: String? = null
        private set
    var startCount = 0
        private set
    var stopCount = 0
        private set
    var flash: Boolean = false
        private set
    val captures = mutableListOf<CaptureResult>()
    var captureCounter = 0
        private set

    /** Result of the next [capture]; by default a JPEG path inside [outputDirectory]. */
    var nextCapture: (() -> CaptureResult)? = null

    /** Set to make [start] end in an error instead of running. */
    var startError: CameraState.Error? = null

    override suspend fun start(outputDirectory: String) {
        startCount++
        this.outputDirectory = outputDirectory
        stateFlow.value = CameraState.Starting
        stateFlow.value = startError ?: CameraState.Running(hasFlash)
    }

    override fun stop() {
        stopCount++
        stateFlow.value = CameraState.Idle
    }

    override fun setFlash(enabled: Boolean) {
        flash = enabled
    }

    override suspend fun capture(): CaptureResult {
        val result =
            nextCapture?.invoke()
                ?: CaptureResult.Ready(
                    "${outputDirectory ?: "/fake"}/original-${captureCounter.toString().padStart(3, '0')}.jpg",
                    4000,
                    3000,
                )
        captureCounter++
        captures += result
        return result
    }

    suspend fun emitFrame(
        frame: CameraFrame = frame(),
    ) {
        frameFlow.emit(frame)
    }

    companion object {
        fun frame(
            width: Int = 640,
            height: Int = 480,
            rotationDegrees: Int = 90,
            timestampMillis: Long = 0L,
        ): CameraFrame =
            CameraFrame(
                ByteArrayPreviewFrame(
                    width,
                    height,
                    width * 4,
                    PixelFormat.RGBA,
                    timestampMillis,
                    ByteArray(width * height * 4),
                ),
                rotationDegrees,
            )
    }
}
