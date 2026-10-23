package app.plainpaper.core.domain.model

private const val ARABIC_PRIMARY_SUBTAG = "ar"

/**
 * Resolves the layout direction from the chosen [language] and the device language tag.
 * The effective tag is `language.tag ?: systemLanguageTag`; it is RTL only when its primary
 * language subtag is `ar` (case-insensitive). Unknown or empty tags resolve to LTR (FR-010).
 */
public fun resolveLayoutDirection(
    language: AppLanguage,
    systemLanguageTag: String?,
): AppLayoutDirection {
    val effectiveTag = language.tag ?: systemLanguageTag
    val primarySubtag = effectiveTag
        ?.trim()
        ?.split('-', '_')
        ?.firstOrNull()
        ?.lowercase()
    return if (primarySubtag == ARABIC_PRIMARY_SUBTAG) AppLayoutDirection.RTL else AppLayoutDirection.LTR
}
