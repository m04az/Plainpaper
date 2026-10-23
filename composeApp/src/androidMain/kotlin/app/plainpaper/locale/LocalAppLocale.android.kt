package app.plainpaper.locale

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

actual object LocalAppLocale {
    /** Device locale captured before the first override so `null` can restore it. */
    private val deviceLocale: Locale by lazy { Locale.getDefault() }

    actual val current: String
        @Composable get() = Locale.getDefault().toLanguageTag()

    @Suppress("DEPRECATION")
    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val configuration = LocalConfiguration.current
        val resources = LocalContext.current.resources
        val locale = if (value == null) deviceLocale else Locale.forLanguageTag(value)
        Locale.setDefault(locale)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        resources.updateConfiguration(configuration, resources.displayMetrics)
        return LocalConfiguration.provides(configuration)
    }
}

actual fun systemLanguageTag(): String? =
    Resources
        .getSystem()
        .configuration.locales
        .takeIf { !it.isEmpty }
        ?.get(0)
        ?.toLanguageTag()
