package app.plainpaper.core.domain.usecase.capture

import app.plainpaper.core.domain.model.PageSize
import app.plainpaper.core.domain.model.capture.CaptureMode
import app.plainpaper.core.testing.FakeRegionProvider
import kotlin.test.Test
import kotlin.test.assertEquals

class ResolveDefaultPageSizeTest {
    private val region = FakeRegionProvider()
    private val resolve = ResolveDefaultPageSizeUseCase(region)

    @Test
    fun documentFollowsTheRegion() {
        region.region = "US"
        assertEquals(PageSize.Letter, resolve(CaptureMode.Document))
        region.region = "EG"
        assertEquals(PageSize.A4, resolve(CaptureMode.Document))
        region.region = null
        assertEquals(PageSize.A4, resolve(CaptureMode.Document))
    }

    @Test
    fun receiptAndIdCardAreNaturalEverywhere() {
        region.region = "US"
        assertEquals(PageSize.Natural, resolve(CaptureMode.Receipt))
        assertEquals(PageSize.Natural, resolve(CaptureMode.IdCard))
    }
}
