package app.plainpaper.core.scanengine.decision

import app.plainpaper.core.domain.model.DeviceCapability
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkingResolutionTest {
    @Test
    fun defaultsPerCapability() {
        assertEquals(2000, WorkingResolution.resolve(DeviceCapability.Full, null))
        assertEquals(1500, WorkingResolution.resolve(DeviceCapability.Simplified, null))
    }

    @Test
    fun overrideIsClamped() {
        assertEquals(800, WorkingResolution.resolve(DeviceCapability.Full, 100))
        assertEquals(3000, WorkingResolution.resolve(DeviceCapability.Full, 9000))
        assertEquals(2600, WorkingResolution.resolve(DeviceCapability.Simplified, 2600))
    }
}
