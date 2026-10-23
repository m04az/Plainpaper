package app.plainpaper.core.testing

import app.plainpaper.core.domain.model.DeviceCapability
import app.plainpaper.core.scanengine.CapabilityProvider
import app.plainpaper.core.scanengine.model.CapabilityDetails

class FakeCapabilityProvider(
    var capability: DeviceCapability = DeviceCapability.Full,
) : CapabilityProvider {
    override fun current(): DeviceCapability = capability

    override fun details(): CapabilityDetails =
        CapabilityDetails(
            totalMemoryBytes = 8L * 1024 * 1024 * 1024,
            is64Bit = true,
            isLowRamDevice = false,
            platformName = "fake",
        )
}
