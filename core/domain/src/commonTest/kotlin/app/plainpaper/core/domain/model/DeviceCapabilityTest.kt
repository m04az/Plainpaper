package app.plainpaper.core.domain.model

import app.plainpaper.core.domain.model.DeviceCapability.Companion.FULL_MEMORY_BYTES
import app.plainpaper.core.domain.model.DeviceCapability.Companion.classifyCapability
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceCapabilityTest {
    @Test
    fun exactlyFourGigabytesSixtyFourBitIsFull() {
        assertEquals(
            DeviceCapability.Full,
            classifyCapability(FULL_MEMORY_BYTES, is64Bit = true, isLowRamDevice = false),
        )
    }

    @Test
    fun oneByteBelowFourGigabytesIsSimplified() {
        assertEquals(
            DeviceCapability.Simplified,
            classifyCapability(FULL_MEMORY_BYTES - 1, is64Bit = true, isLowRamDevice = false),
        )
    }

    @Test
    fun thirtyTwoBitIsSimplified() {
        assertEquals(
            DeviceCapability.Simplified,
            classifyCapability(8L * FULL_MEMORY_BYTES, is64Bit = false, isLowRamDevice = false),
        )
    }

    @Test
    fun lowRamFlagIsSimplified() {
        assertEquals(
            DeviceCapability.Simplified,
            classifyCapability(8L * FULL_MEMORY_BYTES, is64Bit = true, isLowRamDevice = true),
        )
    }
}
