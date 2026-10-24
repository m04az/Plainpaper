package app.plainpaper.feature.capture.import

import kotlin.test.Test
import kotlin.test.assertEquals

class ImportEstimateTest {
    @Test
    fun usesTheMovingAverageOfTheLastThreeRoundedUp() {
        val estimate = ImportEstimate()
        estimate.start(0)
        assertEquals(20, estimate.secondsLeft(10)) // default 2 s per photo before any completion
        estimate.onCompleted(1_000)
        estimate.onCompleted(2_000)
        estimate.onCompleted(3_000)
        estimate.onCompleted(9_000) // 6 s: the average of (1, 1, 6) is 2.67 s
        assertEquals(8, estimate.secondsLeft(3)) // 8.0 s
        assertEquals(3, estimate.secondsLeft(1)) // ceil(2.67)
        assertEquals(0, estimate.secondsLeft(0))
    }

    @Test
    fun neverBelowOneSecondWhileWorkRemains() {
        val estimate = ImportEstimate()
        estimate.start(0)
        estimate.onCompleted(10)
        estimate.onCompleted(20)
        assertEquals(1, estimate.secondsLeft(5))
    }
}
