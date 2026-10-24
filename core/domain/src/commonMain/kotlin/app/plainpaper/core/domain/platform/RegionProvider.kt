package app.plainpaper.core.domain.platform

/** The device region (ISO 3166-1 alpha-2) used for the default page size; null when unknown. */
public fun interface RegionProvider {
    public fun regionCode(): String?
}
