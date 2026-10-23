package app.plainpaper.scansuite

import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

/**
 * Legibility through the `tesseract` CLI (research R14): character accuracy of the recognised
 * text against the transcript, reported relative to the same measurement on the clean reference.
 */
class Tesseract(
    private val binary: String = "tesseract",
) : LegibilityScorer {
    private val cleanAccuracy = ConcurrentHashMap<String, Double>()

    override fun relativeScore(
        output: BufferedImage,
        cleanReference: File,
        transcript: File,
        language: Language,
    ): Double? {
        val languages = languagesFor(language)
        val truth = normalise(transcript.readText())
        if (languages == null || truth.isEmpty()) return null
        val reference =
            cleanAccuracy.getOrPut(cleanReference.absolutePath) {
                accuracy(recognise(cleanReference, languages), truth)
            }
        // A reference Tesseract cannot read says nothing about the engine.
        return if (reference < MIN_REFERENCE_ACCURACY) null else scoreOutput(output, languages, truth) / reference
    }

    private fun scoreOutput(
        output: BufferedImage,
        languages: String,
        truth: String,
    ): Double {
        val temp = Files.createTempFile("scansuite-ocr", ".png").toFile()
        try {
            ImageIO.write(output, "png", temp)
            return accuracy(recognise(temp, languages), truth).coerceAtMost(MAX_RELATIVE)
        } finally {
            temp.delete()
        }
    }

    /** Runs the CLI; returns the recognised text (empty on failure). */
    fun recognise(
        image: File,
        languages: String,
    ): String {
        val process =
            ProcessBuilder(binary, image.absolutePath, "stdout", "-l", languages, "--psm", "6")
                .redirectErrorStream(false)
                .start()
        val text = process.inputStream.bufferedReader(Charsets.UTF_8).readText()
        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return ""
        }
        return if (process.exitValue() == 0) text else ""
    }

    companion object {
        private const val TIMEOUT_SECONDS = 120L
        private const val MIN_REFERENCE_ACCURACY = 0.2
        private const val MAX_RELATIVE = 1.5
        private val DIACRITICS = Regex("[\\u064B-\\u0652\\u0670\\u0640]")
        private val WHITESPACE = Regex("\\s+")

        fun languagesFor(language: Language): String? =
            when (language) {
                Language.Arabic -> "ara"
                Language.English -> "eng"
                Language.French -> "fra"
                Language.Mixed -> "ara+eng+fra"
                Language.None -> null
            }

        /** NFC, Arabic diacritics and tatweel removed, whitespace collapsed, trimmed. */
        fun normalise(text: String): String =
            Normalizer
                .normalize(text, Normalizer.Form.NFC)
                .replace(DIACRITICS, "")
                .replace(WHITESPACE, " ")
                .trim()

        /** 1 - character error rate (Levenshtein distance over the truth length), floored at 0. */
        fun accuracy(
            recognised: String,
            truth: String,
        ): Double {
            val normalisedTruth = normalise(truth)
            if (normalisedTruth.isEmpty()) return 0.0
            val distance = levenshtein(normalise(recognised), normalisedTruth)
            return (1.0 - distance.toDouble() / normalisedTruth.length).coerceAtLeast(0.0)
        }

        fun levenshtein(
            a: String,
            b: String,
        ): Int {
            if (a.isEmpty()) return b.length
            if (b.isEmpty()) return a.length
            var previous = IntArray(b.length + 1) { it }
            var current = IntArray(b.length + 1)
            for (i in 1..a.length) {
                current[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
                }
                val swap = previous
                previous = current
                current = swap
            }
            return previous[b.length]
        }

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
