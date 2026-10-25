package app.plainpaper.core.scanengine.bridge

/**
 * JNI entry points registered by `JNI_OnLoad` in native/jni/plainpaper_jni.cpp. The method names
 * and signatures are the contract; keep them in sync with the C++ table.
 */
@Suppress("TooManyFunctions") // one entry per C function; the table in plainpaper_jni.cpp mirrors it
public object JniScanBridge {
    @JvmStatic
    public external fun nativeApiVersion(): Int

    @JvmStatic
    public external fun nativeEngineCreate(): Long

    @JvmStatic
    public external fun nativeEngineDestroy(engine: Long)

    @JvmStatic
    public external fun nativeCancelFlagCreate(): Long

    @JvmStatic
    public external fun nativeCancelFlagSet(flag: Long)

    @JvmStatic
    public external fun nativeCancelFlagDestroy(flag: Long)

    /** RGBA8888 bytes; returns a session handle (0 on failure, status in [outStatus]). */
    @JvmStatic
    @Suppress("LongParameterList")
    public external fun nativeAnalyzeBytes(
        engine: Long,
        pixels: ByteArray,
        width: Int,
        height: Int,
        strideBytes: Int,
        capability: Int,
        workingLongEdge: Int,
        allowFlatten: Boolean,
        keepOrientation: Boolean,
        userCorners: FloatArray?,
        cancel: Long,
        outStatus: IntArray,
    ): Long

    /** Android only: `android.graphics.Bitmap` in ARGB_8888, read in place. */
    @JvmStatic
    @Suppress("LongParameterList")
    public external fun nativeAnalyzeBitmap(
        engine: Long,
        bitmap: Any,
        capability: Int,
        workingLongEdge: Int,
        allowFlatten: Boolean,
        keepOrientation: Boolean,
        userCorners: FloatArray?,
        cancel: Long,
        outStatus: IntArray,
    ): Long

    /** `pp_detect` on RGBA/BGRA bytes; [out] gets 8 corners, confidence, mean luma, fill, micros, detector, presence. Returns the status. */
    @JvmStatic
    @Suppress("LongParameterList")
    public external fun nativeDetectBytes(
        engine: Long,
        pixels: ByteArray,
        width: Int,
        height: Int,
        strideBytes: Int,
        flags: Int,
        cancel: Long,
        out: DoubleArray,
    ): Int

    /** `pp_detect` on a direct `java.nio.ByteBuffer` (camera analysis plane), read in place. */
    @JvmStatic
    @Suppress("LongParameterList")
    public external fun nativeDetectBuffer(
        engine: Long,
        buffer: java.nio.ByteBuffer,
        width: Int,
        height: Int,
        strideBytes: Int,
        flags: Int,
        cancel: Long,
        out: DoubleArray,
    ): Int

    /** Packs `pp_analysis_info` into [longs] (>= 35 entries) and [doubles] (>= 10 entries). */
    @JvmStatic
    public external fun nativeSessionInfo(
        session: Long,
        longs: LongArray,
        doubles: DoubleArray,
    )

    /** Returns `width * height * channels` bytes or null; [outInfo] gets status, channels, style, fallback, timings. */
    @JvmStatic
    @Suppress("LongParameterList")
    public external fun nativeRenderToBytes(
        session: Long,
        style: Int,
        fit: Int,
        width: Int,
        height: Int,
        cancel: Long,
        outInfo: LongArray,
    ): ByteArray?

    /** Android only: renders into an `android.graphics.Bitmap` (ALPHA_8 or ARGB_8888 matching the channels). */
    @JvmStatic
    @Suppress("LongParameterList")
    public external fun nativeRenderToBitmap(
        session: Long,
        style: Int,
        fit: Int,
        width: Int,
        height: Int,
        cancel: Long,
        bitmap: Any,
        outInfo: LongArray,
    ): Int

    /** `pp_engine_load_detector` with .ppdm bytes; returns the `pp_status` code. */
    @JvmStatic
    public external fun nativeLoadDetector(
        engine: Long,
        bytes: ByteArray,
    ): Int

    /** `pp_engine_set_detector_enabled`. */
    @JvmStatic
    public external fun nativeSetDetectorEnabled(
        engine: Long,
        enabled: Boolean,
    )

    /**
     * `pp_detector_run` (diagnostics, parity test): [input] 3x256x256 planar floats, [maskOut] 128x128
     * probabilities, [presenceOut] one probability. Returns the status code.
     */
    @JvmStatic
    public external fun nativeDetectorRun(
        engine: Long,
        input: FloatArray,
        maskOut: FloatArray,
        presenceOut: FloatArray,
    ): Int

    @JvmStatic
    public external fun nativeSessionDestroy(session: Long)

    @JvmStatic
    public external fun nativeLastError(): String

    /** Host builds only (tests): the native stage with this index throws std::bad_alloc; -1 disables. */
    @JvmStatic
    public external fun nativeDebugFailAllocationAtStage(stage: Int)
}

/** Loads `libplainpaper_scan` once; Android uses `System.loadLibrary`, the host a path property. */
public expect fun loadNativeLibrary()
