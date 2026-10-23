package app.plainpaper.core.scanengine

import app.plainpaper.core.domain.model.DeviceCapability
import app.plainpaper.core.scanengine.model.CapabilityDetails

/** The host is always "Full"; `-Dpp.capability=simplified` forces the simplified pipeline. */
public class HostCapabilityProvider : CapabilityProvider {
    override fun current(): DeviceCapability =
        if (System.getProperty(CAPABILITY_PROPERTY)?.equals("simplified", ignoreCase = true) == true) {
            DeviceCapability.Simplified
        } else {
            DeviceCapability.Full
        }

    override fun details(): CapabilityDetails =
        CapabilityDetails(
            totalMemoryBytes = Runtime.getRuntime().maxMemory(),
            is64Bit = true,
            isLowRamDevice = false,
            platformName = "host-${System.getProperty("os.name")}-${System.getProperty("os.arch")}",
        )

    public companion object {
        public const val CAPABILITY_PROPERTY: String = "pp.capability"
    }
}
