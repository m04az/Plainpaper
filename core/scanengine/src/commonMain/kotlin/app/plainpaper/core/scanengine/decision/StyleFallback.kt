package app.plainpaper.core.scanengine.decision

import app.plainpaper.core.domain.model.OutputStyle
import app.plainpaper.core.scanengine.model.AnalysisFlag
import app.plainpaper.core.scanengine.model.FallbackReason

/**
 * Style substitution rule (research R9): an inverted document (dark paper, light text) cannot be
 * binarised meaningfully, so a Scan request renders as Grayscale. The native renderer applies the
 * same rule; this mirror keeps the decision testable and lets callers predict the outcome.
 */
public object StyleFallback {
    public data class Resolution(
        val style: OutputStyle,
        val fallbackReason: FallbackReason?,
    )

    public fun resolve(
        requested: OutputStyle,
        flags: Set<AnalysisFlag>,
    ): Resolution =
        if (requested == OutputStyle.Scan && AnalysisFlag.InvertedDocument in flags) {
            Resolution(OutputStyle.Grayscale, FallbackReason.InvertedDocument)
        } else {
            Resolution(requested, null)
        }
}
