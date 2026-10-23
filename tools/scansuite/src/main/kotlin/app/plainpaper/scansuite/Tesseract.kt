package app.plainpaper.scansuite

import java.awt.image.BufferedImage
import java.io.File

/** Tesseract CLI scorer; the full implementation lands with User Story 5. */
class Tesseract private constructor() : LegibilityScorer {
    override fun relativeScore(
        output: BufferedImage,
        cleanReference: File,
        transcript: File,
        language: Language,
    ): Double? = null

    companion object {
        /** Null when the `tesseract` binary is not on PATH. */
        fun ifAvailable(): LegibilityScorer? = if (binaryOnPath("tesseract")) Tesseract() else null

        fun binaryOnPath(name: String): Boolean =
            System
                .getenv("PATH")
                .orEmpty()
                .split(File.pathSeparator)
                .any { File(it, name).canExecute() }
    }
}
