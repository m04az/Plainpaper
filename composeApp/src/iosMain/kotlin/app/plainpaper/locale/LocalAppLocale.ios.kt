package app.plainpaper.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages

private const val APPLE_LANGUAGES_KEY = "AppleLanguages"

actual object LocalAppLocale {
    private val localeLocal = staticCompositionLocalOf { systemLanguageTag().orEmpty() }

    actual val current: String
        @Composable get() = localeLocal.current

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val defaults = NSUserDefaults.standardUserDefaults
        if (value == null) {
            defaults.removeObjectForKey(APPLE_LANGUAGES_KEY)
        } else {
            defaults.setObject(listOf(value), forKey = APPLE_LANGUAGES_KEY)
        }
        return localeLocal.provides(value ?: systemLanguageTag().orEmpty())
    }
}

/**
 * Reads the first preferred language. When the app has overridden `AppleLanguages`, callers
 * clear the override (language == SYSTEM) before relying on this value.
 */
actual fun systemLanguageTag(): String? = NSLocale.preferredLanguages.firstOrNull() as? String
