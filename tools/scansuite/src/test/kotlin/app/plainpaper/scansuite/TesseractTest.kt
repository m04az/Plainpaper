package app.plainpaper.scansuite

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TesseractTest {
    @Test
    fun normalisationCollapsesWhitespaceAndStripsArabicDiacritics() {
        assertEquals("a b c", Tesseract.normalise("  a \n b\t\tc "))
        assertEquals("محمد", Tesseract.normalise("مُحَمَّد"))
        assertEquals("كتاب", Tesseract.normalise("كتـــاب")) // tatweel removed
    }

    @Test
    fun characterAccuracyFromLevenshtein() {
        assertEquals(0, Tesseract.levenshtein("kitten", "kitten"))
        assertEquals(3, Tesseract.levenshtein("kitten", "sitting"))
        assertEquals(1.0, Tesseract.accuracy("hello world", "hello world"), 1e-9)
        assertEquals(1.0 - 1.0 / 11, Tesseract.accuracy("hello warld", "hello world"), 1e-9)
        assertEquals(0.0, Tesseract.accuracy("", "hello"), 1e-9)
    }

    @Test
    fun languagesMapToTessdataNames() {
        assertEquals("ara", Tesseract.languagesFor(Language.Arabic))
        assertEquals("eng", Tesseract.languagesFor(Language.English))
        assertEquals("fra", Tesseract.languagesFor(Language.French))
        assertEquals("ara+eng+fra", Tesseract.languagesFor(Language.Mixed))
        assertNull(Tesseract.languagesFor(Language.None))
    }

    @Test
    fun missingBinaryMeansNoScorerUnlessRequired() {
        val required = System.getenv("PP_REQUIRE_OCR") == "1"
        val scorer = Tesseract.ifAvailable()
        if (required) assertNotNull(scorer, "PP_REQUIRE_OCR=1 but tesseract is not on PATH")
    }

    @Test
    fun recognisesARenderedLineWhenTesseractIsAvailable() {
        val scorer = Tesseract.ifAvailable() ?: return
        val image = BufferedImage(1400, 160, BufferedImage.TYPE_BYTE_GRAY)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color.WHITE
        g.fillRect(0, 0, image.width, image.height)
        g.color = Color.BLACK
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, 48)
        g.drawString("The quick brown fox jumps over the lazy dog", 40, 100)
        g.dispose()
        val dir = Files.createTempDirectory("tess").toFile()
        val png = File(dir, "line.png").also { ImageIO.write(image, "png", it) }
        val text = (scorer as Tesseract).recognise(png, "eng")
        val accuracy = Tesseract.accuracy(text, "The quick brown fox jumps over the lazy dog")
        assertTrue(accuracy > 0.9, "accuracy $accuracy for '$text'")
        val transcript = File(dir, "t.txt").also { it.writeText("The quick brown fox jumps over the lazy dog\n") }
        val relative = scorer.relativeScore(image, png, transcript, Language.English)
        assertNotNull(relative)
        assertEquals(1.0, relative, 0.05)
    }
}
