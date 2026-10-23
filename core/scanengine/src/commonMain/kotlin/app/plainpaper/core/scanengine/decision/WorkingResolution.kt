package app.plainpaper.core.scanengine.decision

import app.plainpaper.core.domain.model.DeviceCapability

/** Working-resolution policy (research R5, R10). */
public object WorkingResolution {
    public const val MIN_LONG_EDGE: Int = 800
    public const val MAX_LONG_EDGE: Int = 3000
    public const val FULL_LONG_EDGE: Int = 2000
    public const val SIMPLIFIED_LONG_EDGE: Int = 1500

    public fun defaultFor(capability: DeviceCapability): Int =
        when (capability) {
            DeviceCapability.Full -> FULL_LONG_EDGE
            DeviceCapability.Simplified -> SIMPLIFIED_LONG_EDGE
        }

    /** Caller override clamped to the accepted range; null means the capability default. */
    public fun resolve(
        capability: DeviceCapability,
        override: Int?,
    ): Int = (override ?: defaultFor(capability)).coerceIn(MIN_LONG_EDGE, MAX_LONG_EDGE)
}
