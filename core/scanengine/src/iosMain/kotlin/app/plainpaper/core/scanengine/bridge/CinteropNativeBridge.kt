@file:Suppress("MagicNumber") // wire format / pixel layout constants are the contract, not tunables

package app.plainpaper.core.scanengine.bridge

import app.plainpaper.core.domain.model.DeviceCapability
import app.plainpaper.core.domain.model.OutputStyle
import app.plainpaper.core.scanengine.ByteArrayPreviewFrame
import app.plainpaper.core.scanengine.CgImageScanOutput
import app.plainpaper.core.scanengine.CgImageScanPhoto
import app.plainpaper.core.scanengine.PreviewFrame
import app.plainpaper.core.scanengine.ScanPhoto
import app.plainpaper.core.scanengine.model.Confidence
import app.plainpaper.core.scanengine.model.CornerSource
import app.plainpaper.core.scanengine.model.DetectorKind
import app.plainpaper.core.scanengine.model.FallbackReason
import app.plainpaper.core.scanengine.model.FitMode
import app.plainpaper.core.scanengine.model.OrientationConfidence
import app.plainpaper.core.scanengine.model.Point
import app.plainpaper.core.scanengine.model.ProcessingStep
import app.plainpaper.core.scanengine.model.Quad
import app.plainpaper.core.scanengine.model.SkipReason
import app.plainpaper.core.scanengine.model.StepStatus
import app.plainpaper.core.scanengine.model.StepTiming
import app.plainpaper.scanengine.cinterop.PP_FLAG_INVERTED_DOCUMENT
import app.plainpaper.scanengine.cinterop.PP_FLAG_SPECULAR_HIGHLIGHT
import app.plainpaper.scanengine.cinterop.pp_analysis_info
import app.plainpaper.scanengine.cinterop.pp_analyze
import app.plainpaper.scanengine.cinterop.pp_analyze_options
import app.plainpaper.scanengine.cinterop.pp_api_version
import app.plainpaper.scanengine.cinterop.pp_detect
import app.plainpaper.scanengine.cinterop.pp_detect_info
import app.plainpaper.scanengine.cinterop.pp_engine_create
import app.plainpaper.scanengine.cinterop.pp_engine_load_detector
import app.plainpaper.scanengine.cinterop.pp_engine_set_detector_enabled
import app.plainpaper.scanengine.cinterop.pp_image_rgba
import app.plainpaper.scanengine.cinterop.pp_last_error
import app.plainpaper.scanengine.cinterop.pp_output_free
import app.plainpaper.scanengine.cinterop.pp_output_image
import app.plainpaper.scanengine.cinterop.pp_quad
import app.plainpaper.scanengine.cinterop.pp_render
import app.plainpaper.scanengine.cinterop.pp_session_destroy
import app.plainpaper.scanengine.cinterop.pp_session_info
import cnames.structs.pp_engine
import cnames.structs.pp_session
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.free
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.CGColorRenderingIntent
import platform.CoreGraphics.CGColorSpaceCreateDeviceGray
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGDataProviderCreateWithCFData
import platform.CoreGraphics.CGDataProviderRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageCreate
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.kCGBitmapByteOrder32Big
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create

private const val STEP_STATUS_FAILED = 6

/** Kotlin/Native cinterop transport over the C API (research R3). */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class CinteropNativeBridge : NativeBridge {
    private val engine: CPointer<pp_engine> by lazy { requireNotNull(pp_engine_create()) { "pp_engine_create" } }

    override fun apiVersion(): Int = pp_api_version()

    override fun createCancelFlag(): NativeCancelFlag = CinteropCancelFlag()

    override fun analyze(
        photo: ScanPhoto,
        capability: DeviceCapability,
        workingLongEdge: Int,
        allowFlatten: Boolean,
        keepOrientation: Boolean,
        userCorners: Quad?,
        cancel: NativeCancelFlag,
    ): NativeAnalyzeResult {
        val source =
            photo as? CgImageScanPhoto ?: return NativeAnalyzeResult.Failure(NativeStatus.InvalidInput, "photo")
        val pixels = source.toRgbaBytes()
        return pixels.usePinned { pinned ->
            memScoped {
                val image = alloc<pp_image_rgba>()
                image.pixels = pinned.addressOf(0).reinterpret()
                image.width = source.width
                image.height = source.height
                image.stride_bytes = source.width * 4
                val quad = alloc<pp_quad>()
                userCorners?.points?.forEachIndexed { index, point ->
                    quad.c[index].x = point.x
                    quad.c[index].y = point.y
                }
                val options = alloc<pp_analyze_options>()
                options.capability = (if (capability == DeviceCapability.Full) 0 else 1).toUInt()
                options.working_long_edge = workingLongEdge
                options.allow_flatten = if (allowFlatten) 1 else 0
                options.keep_orientation = if (keepOrientation) 1 else 0
                options.user_corners = if (userCorners != null) quad.ptr else null
                val sessionOut = alloc<CPointerVar<pp_session>>()
                val status =
                    NativeStatus.fromCode(
                        pp_analyze(
                            engine,
                            image.ptr,
                            options.ptr,
                            (cancel as CinteropCancelFlag).pointer,
                            sessionOut.ptr,
                        ).toInt(),
                    )
                val session = sessionOut.value
                val carriesSession = status == NativeStatus.Ok || status == NativeStatus.TooDark
                if (session != null && carriesSession) {
                    NativeAnalyzeResult.Session(CinteropSession(session), status)
                } else {
                    if (session != null) pp_session_destroy(session)
                    NativeAnalyzeResult.Failure(status, lastError())
                }
            }
        }
    }

    override fun detect(
        frame: PreviewFrame,
        cancel: NativeCancelFlag,
    ): NativeDetectResult {
        val source =
            frame as? ByteArrayPreviewFrame
                ?: return NativeDetectResult.decode(
                    NativeStatus.InvalidInput,
                    DoubleArray(NativeDetectResult.VALUES),
                    "frame",
                )
        return source.pixels.usePinned { pinned ->
            memScoped {
                val image = alloc<pp_image_rgba>()
                image.pixels = pinned.addressOf(0).reinterpret()
                image.width = source.width
                image.height = source.height
                image.stride_bytes = source.strideBytes
                val info = alloc<pp_detect_info>()
                val status =
                    NativeStatus.fromCode(
                        pp_detect(
                            engine,
                            image.ptr,
                            source.format.nativeFlags.toUInt(),
                            (cancel as CinteropCancelFlag).pointer,
                            info.ptr,
                        ).toInt(),
                    )
                val values = DoubleArray(NativeDetectResult.VALUES)
                for (i in 0 until 4) {
                    values[i * 2] =
                        info.corners.c[i]
                            .x
                            .toDouble()
                    values[i * 2 + 1] =
                        info.corners.c[i]
                            .y
                            .toDouble()
                }
                values[8] = info.confidence.toInt().toDouble()
                values[9] = info.mean_luma.toDouble()
                values[10] = info.fill_fraction.toDouble()
                values[11] = info.micros.toDouble()
                values[12] = info.detector.toInt().toDouble()
                values[13] = info.presence.toDouble()
                NativeDetectResult.decode(status, values, if (status == NativeStatus.Ok) "" else lastError())
            }
        }
    }

    override fun loadDetector(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        return bytes.usePinned { pinned ->
            pp_engine_load_detector(engine, pinned.addressOf(0).reinterpret<UByteVar>(), bytes.size.convert())
                .toInt() == NativeStatus.Ok.code
        }
    }

    override fun setDetectorEnabled(enabled: Boolean) {
        pp_engine_set_detector_enabled(engine, if (enabled) 1 else 0)
    }

    private fun lastError(): String = pp_last_error()?.toKString().orEmpty()

    private inner class CinteropSession(
        private val handle: CPointer<pp_session>,
    ) : NativeSession {
        override fun info(): NativeAnalysisInfo =
            memScoped {
                val info = alloc<pp_analysis_info>()
                pp_session_info(handle, info.ptr)
                val corners =
                    Quad(
                        Point(info.corners.c[0].x, info.corners.c[0].y),
                        Point(info.corners.c[1].x, info.corners.c[1].y),
                        Point(info.corners.c[2].x, info.corners.c[2].y),
                        Point(info.corners.c[3].x, info.corners.c[3].y),
                    )
                val steps =
                    List(info.step_count.coerceIn(0, ProcessingStep.entries.size)) { i ->
                        val timing = info.steps[i]
                        StepTiming(
                            step =
                                ProcessingStep.entries[
                                    timing.step.toInt().coerceIn(
                                        0,
                                        ProcessingStep.entries.size - 1,
                                    ),
                                ],
                            status = decodeStepStatus(timing.status.toInt()),
                            durationMicros = timing.micros,
                        )
                    }
                NativeAnalysisInfo(
                    corners = corners,
                    cornerSource = CornerSource.entries[info.corner_source.toInt().coerceIn(0, 2)],
                    confidence = Confidence.entries[info.confidence.toInt().coerceIn(0, 2)],
                    rotationApplied = info.rotation_applied,
                    orientationConfidence =
                        OrientationConfidence.entries[
                            info.orientation_confidence.toInt().coerceIn(
                                0,
                                2,
                            ),
                        ],
                    detectedAspect = info.detected_aspect,
                    invertedDocument = info.flags and PP_FLAG_INVERTED_DOCUMENT != 0u,
                    specularHighlight = info.flags and PP_FLAG_SPECULAR_HIGHLIGHT != 0u,
                    steps = steps,
                    peakBytes = info.peak_bytes,
                    detector = DetectorKind.entries[info.detector.toInt().coerceIn(0, 1)],
                    presence = info.presence,
                )
            }

        override fun render(
            style: OutputStyle,
            fit: FitMode,
            width: Int,
            height: Int,
            cancel: NativeCancelFlag,
        ): NativeRenderResult =
            memScoped {
                val output = alloc<pp_output_image>()
                val status =
                    NativeStatus.fromCode(
                        pp_render(
                            handle,
                            style.ordinal.toUInt(),
                            fit.ordinal.toUInt(),
                            width,
                            height,
                            (cancel as CinteropCancelFlag).pointer,
                            output.ptr,
                        ).toInt(),
                    )
                val pixels = output.pixels
                if (status != NativeStatus.Ok || pixels == null) {
                    pp_output_free(output.ptr)
                    return@memScoped NativeRenderResult.Failure(status, lastError())
                }
                val channels = output.channels
                val byteCount = output.stride_bytes.toULong() * output.height.toULong()
                val data = NSData.create(bytes = pixels, length = byteCount)
                pp_output_free(output.ptr)
                val image =
                    createImage(data, output.width, output.height, channels, output.stride_bytes)
                        ?: return@memScoped NativeRenderResult.Failure(NativeStatus.Internal, "CGImageCreate")
                NativeRenderResult.Ready(
                    CgImageScanOutput(
                        image = image,
                        width = output.width,
                        height = output.height,
                        channels = channels,
                        styleUsed = OutputStyle.entries.getOrElse(output.style_used.toInt()) { style },
                        fallbackReason =
                            if (output.fallback_reason.toInt() ==
                                1
                            ) {
                                FallbackReason.InvertedDocument
                            } else {
                                null
                            },
                        steps =
                            listOf(
                                StepTiming(ProcessingStep.Render, StepStatus.Applied, output.steps[0].micros),
                                StepTiming(ProcessingStep.Resample, StepStatus.Applied, output.steps[1].micros),
                            ),
                    ),
                )
            }

        override fun destroy() {
            pp_session_destroy(handle)
        }
    }

    private fun createImage(
        data: NSData,
        width: Int,
        height: Int,
        channels: Int,
        strideBytes: Int,
    ): CGImageRef? {
        val cfData: CFDataRef? = CFBridgingRetain(data)?.reinterpret()
        val provider = CGDataProviderCreateWithCFData(cfData)
        val colorSpace = if (channels == 1) CGColorSpaceCreateDeviceGray() else CGColorSpaceCreateDeviceRGB()
        val bitmapInfo =
            if (channels == 1) {
                CGImageAlphaInfo.kCGImageAlphaNone.value
            } else {
                CGImageAlphaInfo.kCGImageAlphaLast.value or kCGBitmapByteOrder32Big
            }
        val image =
            CGImageCreate(
                width = width.toULong(),
                height = height.toULong(),
                bitsPerComponent = 8u,
                bitsPerPixel = (8 * channels).toULong(),
                bytesPerRow = strideBytes.toULong(),
                space = colorSpace,
                bitmapInfo = bitmapInfo,
                provider = provider,
                decode = null,
                shouldInterpolate = false,
                intent = CGColorRenderingIntent.kCGRenderingIntentDefault,
            )
        CGColorSpaceRelease(colorSpace)
        CGDataProviderRelease(provider)
        if (cfData != null) CFRelease(cfData)
        return image
    }

    private fun decodeStepStatus(code: Int): StepStatus =
        when {
            code == 0 -> StepStatus.Applied
            code >= STEP_STATUS_FAILED -> StepStatus.Failed("native")
            else -> StepStatus.Skipped(SkipReason.entries[(code - 1).coerceIn(0, SkipReason.entries.size - 1)])
        }

    private class CinteropCancelFlag : NativeCancelFlag {
        private val storage = nativeHeap.alloc<IntVar>().also { it.value = 0 }
        val pointer: CPointer<IntVar> get() = storage.ptr

        override fun set() {
            storage.value = 1
        }

        override fun destroy() {
            nativeHeap.free(storage.ptr)
        }
    }
}

public actual fun platformNativeBridge(): NativeBridge = CinteropNativeBridge()
