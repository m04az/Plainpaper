package app.plainpaper.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue

/**
 * Platform hook that makes Compose resources resolve strings for a chosen BCP 47 tag, or for
 * the device language when `null` (JetBrains "resource environment" workaround, research R6).
 */
expect object LocalAppLocale {
    val current: String
        @Composable get

    @Composable
    infix fun provides(value: String?): ProvidedValue<*>
}

/** The device language tag (e.g. "ar-EG"), independent of any in-app override. */
expect fun systemLanguageTag(): String?
