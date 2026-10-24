package app.plainpaper.core.capture.scanner

import kotlin.test.Test
import kotlin.test.assertEquals

class OriginalNamesTest {
    @Test
    fun emptyFolderStartsAtZero() {
        assertEquals(0, nextOriginalIndex(emptyList()))
    }

    @Test
    fun continuesAfterTheHighestOriginalAndIgnoresOtherFiles() {
        val names = listOf("original-000.jpg", "original-004.jpg", "page-p1-r0-scan.jpg", "session.json", "original-x.jpg")
        assertEquals(5, nextOriginalIndex(names))
    }

    @Test
    fun namesArePaddedToThreeDigits() {
        assertEquals("original-007.jpg", originalName(7))
        assertEquals("original-1234.jpg", originalName(1234))
    }
}
