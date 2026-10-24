package app.plainpaper.core.domain.usecase.capture

import app.plainpaper.core.domain.model.PageSize
import app.plainpaper.core.domain.model.capture.CaptureMode
import app.plainpaper.core.domain.platform.RegionProvider

/**
 * Page size for a new page: the device region's default for documents (Letter in US, CA, MX,
 * PH; A4 elsewhere) until a settings default exists, Natural for receipts and ID cards.
 */
public class ResolveDefaultPageSizeUseCase(
    private val region: RegionProvider,
) {
    public operator fun invoke(mode: CaptureMode): PageSize =
        mode.resolvePageSize(PageSize.defaultFor(region.regionCode()))
}
