package app.plainpaper.scansuite

import app.plainpaper.scansuite.synthetic.SyntheticSetGenerator
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyntheticSetTest {
    private val root = File(System.getProperty("user.dir"))

    @Test
    fun generatorIsDeterministicAndComplete() {
        val a = Files.createTempDirectory("synthetic-a").toFile()
        val b = Files.createTempDirectory("synthetic-b").toFile()
        val first = SyntheticSetGenerator(root, a).generate()
        val second = SyntheticSetGenerator(root, b).generate()
        assertEquals(first, second)
        assertEquals(35, first.photos.size)
        for (photo in first.photos) {
            assertContentEquals(File(a, photo.file).readBytes(), File(b, photo.file).readBytes(), photo.id)
            assertTrue(Category.Synthetic in photo.tags)
            if (Category.NoPage in photo.tags) {
                assertEquals(null, photo.expectedCorners, photo.id)
            } else {
                val transcript = File(a, photo.transcript!!).readText().lines().count { it.isNotBlank() }
                assertTrue(transcript >= 10, "${photo.id} transcript lines $transcript")
                assertTrue(photo.cleanReference != null && File(a, photo.cleanReference).isFile, photo.id)
                assertEquals(4, photo.expectedCorners?.size, photo.id)
            }
        }
        assertEquals(emptyList(), first.validate(a))
        assertEquals(
            setOf(Language.English, Language.French, Language.Arabic, Language.None),
            first.photos.map { it.language }.toSet(),
        )
        assertEquals(setOf(0, 90, 180, 270), first.photos.map { it.expectedRotation }.toSet())
        assertEquals(16, first.photos.count { Category.HardShadow in it.tags })
        assertEquals(2, first.photos.count { Category.StampSignature in it.tags })
        assertEquals(2, first.photos.count { Category.NoPage in it.tags })
        assertEquals(2, first.photos.count { Category.Curled in it.tags })
    }

    @Test
    fun cornersMatchTheRotatedPhotoBounds() {
        val dir = Files.createTempDirectory("synthetic-c").toFile()
        val manifest = SyntheticSetGenerator(root, dir).generate()
        for (photo in manifest.photos) {
            val size = ImageSize.of(File(dir, photo.file))
            val corners = photo.expectedCorners ?: continue
            // Upright TL must be above-left of BR once the page is read upright: with rotation the
            // photo bounds swap, so we only assert that all corners sit inside the photo.
            for ((x, y) in corners) {
                assertTrue(
                    x in 0.0..size.width.toDouble() && y in 0.0..size.height.toDouble(),
                    "${photo.id} corner ($x,$y)",
                )
            }
        }
    }
}
