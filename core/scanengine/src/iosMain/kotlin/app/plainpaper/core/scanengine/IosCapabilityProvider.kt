package app.plainpaper.core.scanengine

import app.plainpaper.core.domain.model.DeviceCapability
import app.plainpaper.core.scanengine.model.CapabilityDetails
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIDevice

/** `NSProcessInfo.physicalMemory`; every supported iPhone is 64-bit; low-RAM below 3 GiB (research R10). */
public class IosCapabilityProvider : CapabilityProvider {
    override fun current(): DeviceCapability =
        details().let { DeviceCapability.classifyCapability(it.totalMemoryBytes, it.is64Bit, it.isLowRamDevice) }

    override fun details(): CapabilityDetails {
        val memory = NSProcessInfo.processInfo.physicalMemory.toLong()
        return CapabilityDetails(
            totalMemoryBytes = memory,
            is64Bit = true,
            isLowRamDevice = memory < LOW_RAM_BYTES,
            platformName = "ios-${UIDevice.currentDevice.systemVersion}-${UIDevice.currentDevice.model}",
        )
    }

    private companion object {
        const val LOW_RAM_BYTES: Long = 3L * 1024 * 1024 * 1024
    }
}
