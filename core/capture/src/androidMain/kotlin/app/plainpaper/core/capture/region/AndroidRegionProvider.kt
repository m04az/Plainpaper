package app.plainpaper.core.capture.region

import android.content.res.Resources
import app.plainpaper.core.domain.platform.RegionProvider

/** The device region from the system locale (research R10). */
internal class AndroidRegionProvider : RegionProvider {
    override fun regionCode(): String? =
        Resources
            .getSystem()
            .configuration.locales
            .takeIf { !it.isEmpty }
            ?.get(0)
            ?.country
            ?.takeIf { it.isNotBlank() }
}
