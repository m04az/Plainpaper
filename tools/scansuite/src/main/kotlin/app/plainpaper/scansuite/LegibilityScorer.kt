package app.plainpaper.scansuite

import java.awt.image.BufferedImage
import java.io.File

/** Text-recognition based legibility (SC-003); implemented by [Tesseract] in User Story 5. */
interface LegibilityScorer {
    /** accuracy(output) / accuracy(clean reference), or null when scoring is unavailable. */
    fun relativeScore(
        output: BufferedImage,
        cleanReference: File,
        transcript: File,
        language: Language,
    ): Double?
}
