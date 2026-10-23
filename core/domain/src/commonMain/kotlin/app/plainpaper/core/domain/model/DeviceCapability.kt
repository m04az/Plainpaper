package app.plainpaper.core.domain.model

/** Which scan pipeline a device gets (spec 002, FR-004/FR-013; research R10). */
public enum class DeviceCapability {
    /** Full pipeline including page flattening; working resolution 2000 px. */
    Full,

    /** Simplified pipeline: no flattening, lower working resolution, fewer threads. */
    Simplified,
    ;

    public companion object {
        /** 4 GiB: the memory floor for the full pipeline. */
        public const val FULL_MEMORY_BYTES: Long = 4L * 1024 * 1024 * 1024

        /** Pure classification used by every platform provider (data-model.md). */
        public fun classifyCapability(
            totalMemoryBytes: Long,
            is64Bit: Boolean,
            isLowRamDevice: Boolean,
        ): DeviceCapability =
            if (totalMemoryBytes >= FULL_MEMORY_BYTES && is64Bit && !isLowRamDevice) Full else Simplified
    }
}
