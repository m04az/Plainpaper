package app.plainpaper.core.data.preferences

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import app.plainpaper.core.domain.model.AppLanguage
import app.plainpaper.core.domain.model.AppPreferences
import app.plainpaper.core.domain.model.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

class PreferencesMapperTest {
    @Test
    fun languageTokensRoundTrip() {
        AppLanguage.entries.forEach { language ->
            assertEquals(language, language.token.toAppLanguage())
        }
        assertEquals("system", AppLanguage.SYSTEM.token)
        assertEquals("en", AppLanguage.ENGLISH.token)
        assertEquals("ar", AppLanguage.ARABIC.token)
    }

    @Test
    fun themeTokensRoundTrip() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, mode.token.toThemeMode())
        }
        assertEquals("system", ThemeMode.SYSTEM.token)
        assertEquals("light", ThemeMode.LIGHT.token)
        assertEquals("dark", ThemeMode.DARK.token)
    }

    @Test
    fun unknownTokensMapToDefaults() {
        assertEquals(AppLanguage.SYSTEM, "zz".toAppLanguage())
        assertEquals(AppLanguage.SYSTEM, null.toAppLanguage())
        assertEquals(ThemeMode.SYSTEM, "sepia".toThemeMode())
        assertEquals(ThemeMode.SYSTEM, null.toThemeMode())
    }

    @Test
    fun missingKeysMapToDefaultPreferences() {
        assertEquals(AppPreferences(), emptyPreferences().toAppPreferences())
    }

    @Test
    fun presentKeysAreMapped() {
        val prefs =
            mutablePreferencesOf(
                PreferencesKeys.LANGUAGE to "ar",
                PreferencesKeys.THEME to "dark",
            )
        assertEquals(
            AppPreferences(language = AppLanguage.ARABIC, themeMode = ThemeMode.DARK),
            prefs.toAppPreferences(),
        )
    }
}
