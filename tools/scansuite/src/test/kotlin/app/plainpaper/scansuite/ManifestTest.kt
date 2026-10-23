package app.plainpaper.scansuite

import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ManifestTest {
    private val root: File = Files.createTempDirectory("manifest").toFile()

    private fun photo(name: String) {
        val file = File(root, "photos/$name").apply { parentFile.mkdirs() }
        ImageIO.write(BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB), "png", file)
    }

    private fun file(path: String) =
        File(root, path).apply {
            parentFile.mkdirs()
            writeText("x")
        }

    private val example =
        """
        {
          "version": 1,
          "photos": [
            {
              "id": "receipt-ar-001",
              "file": "photos/receipt-ar-001.png",
              "tags": ["receipt", "arabic", "hard-shadow"],
              "language": "ar",
              "expectedCorners": [[41.0, 38.5], [289.0, 40.0], [287.0, 292.0], [39.0, 290.0]],
              "expectedRotation": 0,
              "transcript": "transcripts/receipt-ar-001.txt",
              "cleanReference": "clean/receipt-ar-001.png",
              "competitors": { "adobe-scan": "competitors/adobe-scan/receipt-ar-001.jpg" },
              "device": "Pixel 8", "notes": "thermal paper"
            }
          ]
        }
        """.trimIndent()

    @Test
    fun parsesTheContractExampleAndValidatesCleanly() {
        photo("receipt-ar-001.png")
        file("transcripts/receipt-ar-001.txt")
        file("clean/receipt-ar-001.png")
        file("competitors/adobe-scan/receipt-ar-001.jpg")
        val manifest = Manifest.parse(example)
        val entry = manifest.photos.single()
        assertEquals(setOf(Category.Receipt, Category.Arabic, Category.HardShadow), entry.tags)
        assertEquals(Language.Arabic, entry.language)
        assertEquals(emptyList(), manifest.validate(root))
    }

    @Test
    fun reportsEveryProblem() {
        photo("a.png")
        val manifest =
            Manifest(
                photos =
                    listOf(
                        ReferencePhoto("Bad_ID", "photos/a.png", setOf(Category.English), Language.English, null),
                        ReferencePhoto("dup", "photos/a.png", setOf(Category.English), Language.English, corners()),
                        ReferencePhoto(
                            "dup",
                            "photos/missing.png",
                            setOf(Category.English),
                            Language.English,
                            corners(),
                        ),
                        ReferencePhoto(
                            "wrong-order",
                            "photos/a.png",
                            setOf(Category.English),
                            Language.English,
                            listOf(listOf(300.0, 10.0), listOf(10.0, 10.0), listOf(300.0, 290.0), listOf(10.0, 290.0)),
                        ),
                        ReferencePhoto(
                            "outside",
                            "photos/a.png",
                            setOf(Category.English),
                            Language.English,
                            listOf(
                                listOf(-100.0, 10.0),
                                listOf(300.0, 10.0),
                                listOf(300.0, 290.0),
                                listOf(10.0, 290.0),
                            ),
                        ),
                        ReferencePhoto(
                            "needs-clean",
                            "photos/a.png",
                            setOf(Category.English),
                            Language.English,
                            corners(),
                            transcript = "transcripts/none.txt",
                        ),
                        ReferencePhoto(
                            "bad-competitor",
                            "photos/a.png",
                            setOf(Category.English),
                            Language.English,
                            corners(),
                            competitors = mapOf("scanbot" to "competitors/scanbot/x.jpg"),
                        ),
                        ReferencePhoto("no-page-ok", "photos/a.png", setOf(Category.NoPage), Language.None, null),
                    ),
            )
        val problems = manifest.validate(root)
        assertTrue(problems.any { it.startsWith("photo 'Bad_ID': id must match") })
        assertContains(problems, "photo 'Bad_ID': expectedCorners may be null only for no-page photos")
        assertContains(problems, "photo 'dup': duplicate id")
        assertContains(problems, "photo 'dup': missing file photos/missing.png")
        assertContains(problems, "photo 'wrong-order': expectedCorners must be TL, TR, BR, BL")
        assertContains(problems, "photo 'outside': corner 0 outside the photo")
        assertContains(problems, "photo 'needs-clean': missing transcript transcripts/none.txt")
        assertContains(problems, "photo 'needs-clean': a transcript requires a cleanReference image")
        assertContains(problems, "photo 'bad-competitor': unknown competitor 'scanbot'")
        assertTrue(problems.none { it.startsWith("photo 'no-page-ok'") })
    }

    private fun corners() = listOf(listOf(10.0, 10.0), listOf(300.0, 10.0), listOf(300.0, 290.0), listOf(10.0, 290.0))
}
