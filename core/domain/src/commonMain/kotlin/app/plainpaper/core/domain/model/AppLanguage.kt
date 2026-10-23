package app.plainpaper.core.domain.model

/**
 * The language the app displays. [tag] is the BCP 47 language tag applied to resources;
 * `null` means "follow the device language".
 */
public enum class AppLanguage(public val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    ARABIC("ar"),
}
