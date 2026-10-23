package app.plainpaper.core.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class OutputStyleTest {
    @Test
    fun channelCountsMatchTheSpec() {
        assertEquals(1, OutputStyle.Scan.channels)
        assertEquals(1, OutputStyle.Grayscale.channels)
        assertEquals(4, OutputStyle.ColorDocument.channels)
        assertEquals(4, OutputStyle.Original.channels)
    }
}
