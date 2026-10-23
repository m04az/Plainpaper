package app.plainpaper.core.scanengine

import app.plainpaper.core.domain.model.OutputStyle
import app.plainpaper.core.scanengine.model.FallbackReason
import app.plainpaper.core.scanengine.model.StepTiming

/** A rendered page. Platform implementations expose the native image (data-model.md). */
public interface ScanOutput {
    public val width: Int
    public val height: Int

    /** 1 for Scan/Grayscale, 4 (RGBA) for Color document/Original. */
    public val channels: Int

    /** Differs from the requested style only on the inverted-document fallback. */
    public val styleUsed: OutputStyle
    public val fallbackReason: FallbackReason?

    /** Render and Resample timings. */
    public val steps: List<StepTiming>
}
