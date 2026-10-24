package app.plainpaper

import app.plainpaper.detector.ComposeResourceDetectorModelSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ComposeResourceDetectorModelSourceTest {
    @Test
    fun returnsTheBytesOfTheModelResource() =
        runTest {
            var requested: String? = null
            val source =
                ComposeResourceDetectorModelSource { path ->
                    requested = path
                    byteArrayOf(1, 2)
                }
            assertContentEquals(byteArrayOf(1, 2), source.bytes())
            assertEquals("files/detector.ppdm", requested)
        }

    @Test
    fun missingResourceReadsAsNull() =
        runTest {
            val source = ComposeResourceDetectorModelSource { error("Missing resource") }
            assertNull(source.bytes())
        }
}
