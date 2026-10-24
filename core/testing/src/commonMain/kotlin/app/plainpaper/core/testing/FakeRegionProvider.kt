package app.plainpaper.core.testing

import app.plainpaper.core.domain.platform.RegionProvider

class FakeRegionProvider(
    var region: String? = "EG",
) : RegionProvider {
    override fun regionCode(): String? = region
}
