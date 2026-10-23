package app.plainpaper.core.scanengine.decision

import app.plainpaper.core.domain.model.OutputStyle
import app.plainpaper.core.scanengine.model.AnalysisFlag
import app.plainpaper.core.scanengine.model.FallbackReason
import kotlin.test.Test
import kotlin.test.assertEquals

class StyleFallbackTest {
    @Test
    fun invertedDocumentTurnsScanIntoGrayscale() {
        val resolution = StyleFallback.resolve(OutputStyle.Scan, setOf(AnalysisFlag.InvertedDocument))
        assertEquals(StyleFallback.Resolution(OutputStyle.Grayscale, FallbackReason.InvertedDocument), resolution)
    }

    @Test
    fun otherStylesAreNeverSubstituted() {
        for (style in listOf(OutputStyle.Grayscale, OutputStyle.ColorDocument, OutputStyle.Original)) {
            assertEquals(
                StyleFallback.Resolution(style, null),
                StyleFallback.resolve(style, setOf(AnalysisFlag.InvertedDocument)),
            )
        }
    }

    @Test
    fun normalDocumentsKeepTheRequestedStyle() {
        assertEquals(
            StyleFallback.Resolution(OutputStyle.Scan, null),
            StyleFallback.resolve(OutputStyle.Scan, emptySet()),
        )
        assertEquals(
            StyleFallback.Resolution(OutputStyle.Scan, null),
            StyleFallback.resolve(OutputStyle.Scan, setOf(AnalysisFlag.SpecularHighlight)),
        )
    }
}
