package app.plainpaper.core.scanengine.decision

import app.plainpaper.core.scanengine.decision.OrientationVote.LineVote
import app.plainpaper.core.scanengine.model.OrientationConfidence
import kotlin.test.Test
import kotlin.test.assertEquals

class OrientationVoteTest {
    private fun lines(
        count: Int,
        upsideDown: Boolean,
        mass: Double = 1.0,
    ) = List(count) { LineVote(mass, upsideDown) }

    @Test
    fun sixtyPercentOfTheMassFlips() {
        val votes = lines(6, upsideDown = true) + lines(4, upsideDown = false)
        assertEquals(180, OrientationVote.decide(0, votes).rotation)
        assertEquals(270, OrientationVote.decide(90, votes).rotation)
    }

    @Test
    fun belowSixtyPercentKeeps() {
        val votes = lines(5, upsideDown = true) + lines(4, upsideDown = false) // 55%
        assertEquals(0, OrientationVote.decide(0, votes).rotation)
    }

    @Test
    fun massWeightsTheVote() {
        val votes = listOf(LineVote(10.0, upsideDown = true), LineVote(1.0, false), LineVote(1.0, false))
        assertEquals(180, OrientationVote.decide(0, votes).rotation)
    }

    @Test
    fun fewerThanThreeLinesKeepsWithNoConfidence() {
        val decision = OrientationVote.decide(90, lines(2, upsideDown = true))
        assertEquals(90, decision.rotation)
        assertEquals(OrientationConfidence.None, decision.confidence)
    }

    @Test
    fun confidenceNeedsSixLinesAndStrongAgreement() {
        assertEquals(OrientationConfidence.High, OrientationVote.decide(0, lines(8, upsideDown = false)).confidence)
        assertEquals(OrientationConfidence.Low, OrientationVote.decide(0, lines(4, upsideDown = false)).confidence)
        val split = lines(5, upsideDown = true) + lines(3, upsideDown = false) // 62.5% agreement
        assertEquals(OrientationConfidence.Low, OrientationVote.decide(0, split).confidence)
    }

    @Test
    fun axisNeedsAClearRatio() {
        assertEquals(0, OrientationVote.axisRotation(rowVariance = 1.0, columnVariance = 1.2))
        assertEquals(90, OrientationVote.axisRotation(rowVariance = 1.0, columnVariance = 1.5))
    }
}
