package app.plainpaper.core.capture.camera

import app.plainpaper.core.domain.coroutines.DispatcherProvider
import app.plainpaper.core.scanengine.ByteArrayPreviewFrame
import app.plainpaper.core.scanengine.PixelFormat
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureFlashModeOff
import platform.AVFoundation.AVCaptureFlashModeOn
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCapturePhoto
import platform.AVFoundation.AVCapturePhotoCaptureDelegateProtocol
import platform.AVFoundation.AVCapturePhotoOutput
import platform.AVFoundation.AVCapturePhotoSettings
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPreset640x480
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.defaultDeviceWithDeviceType
import platform.AVFoundation.fileDataRepresentation
import platform.AVFoundation.flashAvailable
import platform.AVFoundation.hasFlash
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.darwin.NSObject
import platform.darwin.dispatch_queue_create
import platform.posix.memcpy
import kotlin.coroutines.resume

private const val BYTES_PER_PIXEL = 4
private const val MILLIS_PER_SECOND = 1000.0
private const val INDEX_DIGITS = 3

/** Landscape sensor frames; the UI is portrait, so frames are rotated 90° clockwise to be upright. */
private const val SENSOR_ROTATION_DEGREES = 90
private const val PIXEL_FORMAT_KEY = "PixelFormatType"

/**
 * AVFoundation implementation (research R2): a 640x480 video data output feeds BGRA copies to
 * [frames]; a photo output takes the full-resolution JPEG; the preview layer is drawn by
 * [AvCameraPreviewRenderer].
 */
@OptIn(ExperimentalForeignApi::class)
internal class AvCameraController(
    private val dispatchers: DispatcherProvider,
) : CameraController {
    val session = AVCaptureSession()

    private val stateFlow = MutableStateFlow<CameraState>(CameraState.Idle)
    override val state: StateFlow<CameraState> get() = stateFlow

    private val frameFlow = MutableSharedFlow<CameraFrame>(replay = 0, extraBufferCapacity = 1)
    override val frames: Flow<CameraFrame> get() = frameFlow

    private val photoOutput = AVCapturePhotoOutput()
    private val videoOutput = AVCaptureVideoDataOutput()
    private val videoQueue = dispatch_queue_create("app.plainpaper.camera.frames", null)
    private val frameDelegate = FrameDelegate()
    private var photoDelegate: PhotoDelegate? = null
    private var device: AVCaptureDevice? = null
    private var configured = false
    private var outputDirectory: String? = null
    private var counter = 0
    private var flashEnabled = false
    private var busy = false

    override suspend fun start(outputDirectory: String) {
        this.outputDirectory = outputDirectory
        stateFlow.value = CameraState.Starting
        val camera =
            AVCaptureDevice.defaultDeviceWithDeviceType(
                AVCaptureDeviceTypeBuiltInWideAngleCamera,
                mediaType = AVMediaTypeVideo,
                position = AVCaptureDevicePositionBack,
            )
        if (camera == null) {
            stateFlow.value = CameraState.Error(CameraError.Unavailable)
            return
        }
        device = camera
        if (!configured) {
            val input = AVCaptureDeviceInput.deviceInputWithDevice(camera, error = null)
            if (input == null) {
                stateFlow.value = CameraState.Error(CameraError.PermissionMissing)
                return
            }
            session.beginConfiguration()
            session.sessionPreset = AVCaptureSessionPreset640x480
            if (session.canAddInput(input)) session.addInput(input)
            videoOutput.videoSettings = mapOf<Any?, Any?>(PIXEL_FORMAT_KEY to kCVPixelFormatType_32BGRA.toInt())
            videoOutput.alwaysDiscardsLateVideoFrames = true
            videoOutput.setSampleBufferDelegate(frameDelegate, queue = videoQueue)
            if (session.canAddOutput(videoOutput)) session.addOutput(videoOutput)
            if (session.canAddOutput(photoOutput)) session.addOutput(photoOutput)
            photoOutput.highResolutionCaptureEnabled = true
            session.commitConfiguration()
            configured = true
        }
        withContext(dispatchers.io) { session.startRunning() }
        stateFlow.value = CameraState.Running(hasFlash = camera.hasFlash && camera.flashAvailable)
    }

    override fun stop() {
        if (session.running) session.stopRunning()
        stateFlow.value = CameraState.Idle
    }

    override fun setFlash(enabled: Boolean) {
        flashEnabled = enabled
    }

    override suspend fun capture(): CaptureResult {
        val directory = outputDirectory
        val failure =
            when {
                directory == null || !session.running -> CaptureFailure.NotRunning
                busy -> CaptureFailure.Internal
                else -> null
            }
        if (failure != null || directory == null) return CaptureResult.Failed(failure ?: CaptureFailure.NotRunning)
        busy = true
        // Quick scan may have added originals to this folder meanwhile: never overwrite one.
        var path = "$directory/original-${counter.toString().padStart(INDEX_DIGITS, '0')}.jpg"
        while (NSFileManager.defaultManager.fileExistsAtPath(path)) {
            counter++
            path = "$directory/original-${counter.toString().padStart(INDEX_DIGITS, '0')}.jpg"
        }
        counter++
        return try {
            suspendCancellableCoroutine { continuation ->
                val delegate =
                    PhotoDelegate(path) { result ->
                        photoDelegate = null
                        if (continuation.isActive) continuation.resume(result)
                    }
                photoDelegate = delegate
                photoOutput.capturePhotoWithSettings(photoSettings(), delegate)
            }
        } finally {
            busy = false
        }
    }

    private fun photoSettings(): AVCapturePhotoSettings {
        val settings = AVCapturePhotoSettings.photoSettings()
        settings.highResolutionPhotoEnabled = true
        val camera = device
        if (camera != null && camera.hasFlash) {
            settings.flashMode = if (flashEnabled) AVCaptureFlashModeOn else AVCaptureFlashModeOff
        }
        return settings
    }

    private inner class FrameDelegate :
        NSObject(),
        AVCaptureVideoDataOutputSampleBufferDelegateProtocol {
        override fun captureOutput(
            output: AVCaptureOutput,
            didOutputSampleBuffer: CMSampleBufferRef?,
            fromConnection: AVCaptureConnection,
        ) {
            val buffer = CMSampleBufferGetImageBuffer(didOutputSampleBuffer) ?: return
            CVPixelBufferLockBaseAddress(buffer, 0uL)
            try {
                val width = CVPixelBufferGetWidth(buffer).toInt()
                val height = CVPixelBufferGetHeight(buffer).toInt()
                val stride = CVPixelBufferGetBytesPerRow(buffer).toInt()
                val base = CVPixelBufferGetBaseAddress(buffer) ?: return
                if (width <= 0 || height <= 0 || stride < width * BYTES_PER_PIXEL) return
                val bytes = ByteArray(stride * height)
                bytes.usePinned { memcpy(it.addressOf(0), base, (stride * height).toULong()) }
                val frame =
                    ByteArrayPreviewFrame(
                        width = width,
                        height = height,
                        strideBytes = stride,
                        format = PixelFormat.BGRA,
                        timestampMillis = (NSDate().timeIntervalSince1970 * MILLIS_PER_SECOND).toLong(),
                        pixels = bytes,
                    )
                frameFlow.tryEmit(CameraFrame(frame, SENSOR_ROTATION_DEGREES))
            } finally {
                CVPixelBufferUnlockBaseAddress(buffer, 0uL)
            }
        }
    }

    private class PhotoDelegate(
        private val path: String,
        private val onResult: (CaptureResult) -> Unit,
    ) : NSObject(),
        AVCapturePhotoCaptureDelegateProtocol {
        override fun captureOutput(
            output: AVCapturePhotoOutput,
            didFinishProcessingPhoto: AVCapturePhoto,
            error: NSError?,
        ) {
            val data = if (error == null) didFinishProcessingPhoto.fileDataRepresentation() else null
            if (data == null || !data.writeToFile(path, atomically = true)) {
                onResult(CaptureResult.Failed(if (error == null) CaptureFailure.Storage else CaptureFailure.Internal))
                return
            }
            val (width, height) =
                didFinishProcessingPhoto.resolvedSettings.photoDimensions.useContents {
                    width to
                        height
                }
            onResult(CaptureResult.Ready(path, width, height))
        }
    }
}

internal class AvCameraControllerFactory(
    private val dispatchers: DispatcherProvider,
) : CameraControllerFactory {
    override fun create(): CameraController = AvCameraController(dispatchers)
}
