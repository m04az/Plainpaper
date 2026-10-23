package app.plainpaper.core.domain.model

/** Rendering style of a scanned page (spec 002, FR-009). */
public enum class OutputStyle(
    /** 1 for single-channel (grey/black-and-white) output, 4 for RGBA. */
    public val channels: Int,
) {
    /** Pure black and white, paper forced to white. */
    Scan(channels = 1),

    /** Tonal grey, paper white, photos preserved. */
    Grayscale(channels = 1),

    /** White paper; stamps, signatures and logos keep their colour. */
    ColorDocument(channels = 4),

    /** Straightened photo with light cleanup only. */
    Original(channels = 4),
}
