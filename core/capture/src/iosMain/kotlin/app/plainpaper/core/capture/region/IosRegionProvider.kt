package app.plainpaper.core.capture.region

import app.plainpaper.core.domain.platform.RegionProvider
import platform.Foundation.NSLocale
import platform.Foundation.countryCode
import platform.Foundation.currentLocale

internal class IosRegionProvider : RegionProvider {
    override fun regionCode(): String? = NSLocale.currentLocale.countryCode?.takeIf { it.isNotBlank() }
}
