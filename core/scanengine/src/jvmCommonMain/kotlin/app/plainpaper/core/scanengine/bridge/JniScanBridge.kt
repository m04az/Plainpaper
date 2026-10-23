package app.plainpaper.core.scanengine.bridge

/**
 * JNI entry points registered by `JNI_OnLoad` in native/jni/plainpaper_jni.cpp. The method names
 * and signatures are the contract; keep them in sync with the C++ table.
 */
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
        userCorners: FloatArray?,
        cancel: Long,
        outStatus: IntArray,
    ): Long

    /** Packs `pp_analysis_info` into [longs] (>= 34 entries) and [doubles] (>= 9 entries). */
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

    @JvmStatic
    public external fun nativeSessionDestroy(session: Long)

    @JvmStatic
    public external fun nativeLastError(): String
}

/** Loads `libplainpaper_scan` once; Android uses `System.loadLibrary`, the host a path property. */
public expect fun loadNativeLibrary()
