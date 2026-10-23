package app.plainpaper.core.scanengine.decision

import app.plainpaper.core.domain.model.DeviceCapability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RetryPolicyTest {
    @Test
    fun retryHalvesTheWorkingEdgeAndDowngradesToSimplified() {
        val first = RetryPolicy.initial(DeviceCapability.Full, 2000)
        val retry = RetryPolicy.afterOutOfMemory(first)
        assertEquals(ExecutionPlan(DeviceCapability.Simplified, 1000, attempt = 1), retry)
    }

    @Test
    fun retryNeverGoesBelowTheMinimumWorkingEdge() {
        val retry = RetryPolicy.afterOutOfMemory(RetryPolicy.initial(DeviceCapability.Simplified, 1000))
        assertEquals(WorkingResolution.MIN_LONG_EDGE, retry?.workingLongEdge)
    }

    @Test
    fun onlyOneRetry() {
        val retry = RetryPolicy.afterOutOfMemory(RetryPolicy.initial(DeviceCapability.Full, 2000))!!
        assertNull(RetryPolicy.afterOutOfMemory(retry))
    }
}
