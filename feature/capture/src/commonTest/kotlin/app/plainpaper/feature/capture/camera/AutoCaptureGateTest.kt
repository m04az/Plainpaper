package app.plainpaper.feature.capture.camera

import app.plainpaper.core.domain.model.Confidence
import app.plainpaper.core.domain.model.Point
import app.plainpaper.core.domain.model.Quad
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AutoCaptureGateTest {
    private val diagonal = 800f // 640x480 frame
    private val page = Quad(Point(100f, 60f), Point(540f, 60f), Point(540f, 420f), Point(100f, 420f))

    private fun sample(
        t: Long,
        corners: Quad? = page,
        confidence: Confidence = Confidence.High,
        fill: Float = 0.6f,
        luma: Float = 150f,
    ) = DetectionSample(t, corners, confidence, fill, luma)

    private fun Quad.shifted(dx: Float): Quad =
        Quad(
            Point(topLeft.x + dx, topLeft.y),
            Point(topRight.x + dx, topRight.y),
            Point(bottomRight.x + dx, bottomRight.y),
            Point(bottomLeft.x + dx, bottomLeft.y),
        )

    /** Feeds steady samples every 100 ms from [from] to [until] and returns the states. */
    private fun AutoCaptureGate.steady(
        from: Long,
        until: Long,
        corners: Quad = page,
    ): List<GateState> = (from..until step 100).map { onSample(sample(it, corners)) }

    @Test
    fun steadyHighConfidencePageCapturesOnceAfterTheWindow() {
        val gate = AutoCaptureGate(diagonal)
        val states = gate.steady(0, 1_000)
        assertEquals(1, states.count { it == GateState.Capture }, states.toString())
        assertEquals(GateState.Capture, states[7]) // 700 ms
        assertIs<GateState.Tracking>(states[3])
        assertTrue(states.drop(8).all { it == GateState.Cooldown })
    }

    @Test
    fun driftResetsProgress() {
        val gate = AutoCaptureGate(diagonal)
        gate.onSample(sample(0))
        val before = gate.onSample(sample(500))
        assertEquals(GateState.Tracking(500f / 700f), before)
        // 2 % of the diagonal is more than the 1.5 % tolerance.
        val moved = gate.onSample(sample(600, page.shifted(16f)))
        assertEquals(GateState.Tracking(0f), moved)
        assertEquals(GateState.Tracking(0.5f), gate.onSample(sample(950, page.shifted(16f))))
    }

    @Test
    fun smallPagesLowConfidenceAndDarknessNeverCapture() {
        val gate = AutoCaptureGate(diagonal)
        assertTrue(
            (0L..1_500L step 100).map { gate.onSample(sample(it, fill = 0.04f)) }.all {
                it ==
                    GateState.LowConfidence
            },
        )
        assertTrue(
            (0L..1_500L step 100).map { gate.onSample(sample(it, confidence = Confidence.NotFound)) }.all {
                it ==
                    GateState.LowConfidence
            },
        )
        assertTrue((0L..1_500L step 100).map { gate.onSample(sample(it, luma = 30f)) }.all { it == GateState.TooDark })
    }

    @Test
    fun cooldownEndsOnLargeMoveOrAfterThePageLeavesAndReturns() {
        val gate = AutoCaptureGate(diagonal)
        gate.steady(0, 700)
        assertEquals(GateState.Cooldown, gate.onSample(sample(800)))
        assertEquals(GateState.Cooldown, gate.onSample(sample(900, page.shifted(40f)))) // 5 %
        assertEquals(GateState.Tracking(0f), gate.onSample(sample(1_000, page.shifted(100f)))) // 12.5 %

        val gate2 = AutoCaptureGate(diagonal)
        gate2.steady(0, 700)
        assertEquals(GateState.NoPage(), gate2.onSample(sample(800, corners = null)))
        assertEquals(GateState.Tracking(0f), gate2.onSample(sample(900)))
    }

    @Test
    fun simplifiedWindowIsLonger() {
        val gate = AutoCaptureGate(diagonal, steadyWindowMillis = AutoCaptureGate.SIMPLIFIED_STEADY_WINDOW_MILLIS)
        val states = gate.steady(0, 1_200)
        assertEquals(10, states.indexOf(GateState.Capture)) // 1000 ms
    }

    @Test
    fun disabledGateTracksButNeverCaptures() {
        val gate = AutoCaptureGate(diagonal).apply { enabled = false }
        val states = gate.steady(0, 2_000)
        assertTrue(states.none { it == GateState.Capture })
        assertEquals(GateState.Tracking(1f), states.last())
    }

    @Test
    fun prolongedNoPageIsFlaggedAfterThreeSeconds() {
        val gate = AutoCaptureGate(diagonal)
        assertEquals(GateState.NoPage(prolonged = false), gate.onSample(sample(0, corners = null)))
        assertEquals(GateState.NoPage(prolonged = false), gate.onSample(sample(2_900, corners = null)))
        assertEquals(GateState.NoPage(prolonged = true), gate.onSample(sample(3_000, corners = null)))
        gate.onSample(sample(3_100))
        assertEquals(GateState.NoPage(prolonged = false), gate.onSample(sample(3_200, corners = null)))
    }

    @Test
    fun manualCaptureArmsTheCooldown() {
        val gate = AutoCaptureGate(diagonal)
        gate.onSample(sample(0))
        gate.enterCooldown(page)
        assertEquals(GateState.Cooldown, gate.onSample(sample(100)))
        gate.reset()
        assertEquals(GateState.Tracking(0f), gate.onSample(sample(200)))
    }

    @Test
    fun lowConfidenceDetectionCapturesAfterADoubleWindow() {
        val gate = AutoCaptureGate(diagonal)
        val states = (0L..1_600L step 100).map { gate.onSample(sample(it, confidence = Confidence.Low)) }
        assertEquals(14, states.indexOf(GateState.Capture)) // 1400 ms
        assertIs<GateState.Tracking>(states[8])
    }

    @Test
    fun anyLowSampleInTheTrackKeepsTheLongWindow() {
        val gate = AutoCaptureGate(diagonal)
        gate.onSample(sample(0))
        gate.onSample(sample(100, confidence = Confidence.Low))
        assertIs<GateState.Tracking>(gate.onSample(sample(800)))
    }

    @Test
    fun smallDocumentsAboveFivePercentTrack() {
        val gate = AutoCaptureGate(diagonal)
        assertIs<GateState.Tracking>(gate.onSample(sample(0, fill = 0.06f)))
    }
}
