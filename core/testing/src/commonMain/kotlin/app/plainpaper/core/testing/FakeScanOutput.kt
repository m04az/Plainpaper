@file:Suppress("MagicNumber") // wire format / pixel layout constants are the contract, not tunables

package app.plainpaper.core.testing

import app.plainpaper.core.domain.model.OutputStyle
import app.plainpaper.core.scanengine.ScanOutput
import app.plainpaper.core.scanengine.model.FallbackReason
import app.plainpaper.core.scanengine.model.ProcessingStep
import app.plainpaper.core.scanengine.model.StepStatus
import app.plainpaper.core.scanengine.model.StepTiming

class FakeScanOutput(
    override val width: Int,
    override val height: Int,
    override val styleUsed: OutputStyle,
    override val channels: Int = styleUsed.channels,
    override val fallbackReason: FallbackReason? = null,
    override val steps: List<StepTiming> =
        listOf(
            StepTiming(ProcessingStep.Render, StepStatus.Applied, 1_000),
            StepTiming(ProcessingStep.Resample, StepStatus.Applied, 500),
        ),
) : ScanOutput
