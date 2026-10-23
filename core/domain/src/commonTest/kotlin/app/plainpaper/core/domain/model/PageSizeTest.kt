package app.plainpaper.core.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class PageSizeTest {
    @Test
    fun a4AndLetterHaveFixed300DpiSizes() {
        assertEquals(PixelSize(2480, 3508), PageSize.A4.outputPixels(detectedAspect = 0.7, landscape = false))
        assertEquals(PixelSize(2550, 3300), PageSize.Letter.outputPixels(detectedAspect = 0.7, landscape = false))
    }

    @Test
    fun landscapeSwapsWidthAndHeightForFixedSizes() {
        assertEquals(PixelSize(3508, 2480), PageSize.A4.outputPixels(detectedAspect = 1.4, landscape = true))
        assertEquals(PixelSize(3300, 2550), PageSize.Letter.outputPixels(detectedAspect = 1.4, landscape = true))
    }

    @Test
    fun naturalKeepsAspectWithA4LongSide() {
        assertEquals(PixelSize(1754, 3508), PageSize.Natural.outputPixels(detectedAspect = 0.5, landscape = false))
        assertEquals(PixelSize(3508, 1754), PageSize.Natural.outputPixels(detectedAspect = 2.0, landscape = false))
        // The landscape flag is ignored for Natural: orientation follows the aspect ratio.
        assertEquals(PixelSize(1754, 3508), PageSize.Natural.outputPixels(detectedAspect = 0.5, landscape = true))
    }

    @Test
    fun naturalClampsAspectAndRoundsToEven() {
        assertEquals(PixelSize(702, 3508), PageSize.Natural.outputPixels(detectedAspect = 0.1, landscape = false))
        assertEquals(PixelSize(3508, 702), PageSize.Natural.outputPixels(detectedAspect = 10.0, landscape = false))
        // 3508 * 0.7071 = 2480.5 -> 2481 -> even 2482
        assertEquals(PixelSize(2482, 3508), PageSize.Natural.outputPixels(detectedAspect = 0.7071, landscape = false))
    }

    @Test
    fun letterRegionsDefaultToLetter() {
        listOf("US", "us", " ca ", "MX", "ph").forEach { region ->
            assertEquals(PageSize.Letter, PageSize.defaultFor(region), "region $region")
        }
    }

    @Test
    fun everythingElseDefaultsToA4() {
        listOf(null, "", "EG", "SA", "FR", "xx").forEach { region ->
            assertEquals(PageSize.A4, PageSize.defaultFor(region), "region $region")
        }
    }
}
