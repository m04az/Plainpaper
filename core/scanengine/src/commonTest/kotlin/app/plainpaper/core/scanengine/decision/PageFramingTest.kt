package app.plainpaper.core.scanengine.decision

import app.plainpaper.core.domain.model.PageSize
import app.plainpaper.core.domain.model.PixelSize
import app.plainpaper.core.scanengine.model.FitMode
import kotlin.test.Test
import kotlin.test.assertEquals

class PageFramingTest {
    @Test
    fun outputSizeDelegatesToPageSize() {
        assertEquals(PixelSize(2480, 3508), PageFraming.outputSize(PageSize.A4, 0.7, landscape = false))
        assertEquals(PixelSize(3300, 2550), PageFraming.outputSize(PageSize.Letter, 1.3, landscape = true))
        assertEquals(PixelSize(1754, 3508), PageFraming.outputSize(PageSize.Natural, 0.5, landscape = false))
    }

    @Test
    fun naturalFillsTheFrameAndFixedSizesAreFitted() {
        assertEquals(FitMode.Natural, PageFraming.fitFor(PageSize.Natural))
        assertEquals(FitMode.Fit, PageFraming.fitFor(PageSize.A4))
        assertEquals(FitMode.Fit, PageFraming.fitFor(PageSize.Letter))
    }

    @Test
    fun contentRectCentresATallReceiptWithSideMargins() {
        val rect = PageFraming.contentRect(pageAspect = 0.25, output = PixelSize(2480, 3508))
        assertEquals(3508, rect.height)
        assertEquals(877, rect.width)
        assertEquals((2480 - 877) / 2, rect.x)
        assertEquals(0, rect.y)
    }

    @Test
    fun contentRectCentresAWidePageWithTopAndBottomMargins() {
        val rect = PageFraming.contentRect(pageAspect = 1.0, output = PixelSize(2480, 3508))
        assertEquals(2480, rect.width)
        assertEquals(2480, rect.height)
        assertEquals(0, rect.x)
        assertEquals((3508 - 2480) / 2, rect.y)
    }

    @Test
    fun contentRectFillsWhenAspectMatches() {
        val rect = PageFraming.contentRect(pageAspect = 2480.0 / 3508.0, output = PixelSize(2480, 3508))
        assertEquals(ContentRect(0, 0, 2480, 3508), rect)
    }
}
