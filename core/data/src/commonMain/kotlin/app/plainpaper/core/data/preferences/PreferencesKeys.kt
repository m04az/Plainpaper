package app.plainpaper.core.data.preferences

import androidx.datastore.preferences.core.stringPreferencesKey

internal object PreferencesKeys {
    val LANGUAGE = stringPreferencesKey("language")
    val THEME = stringPreferencesKey("theme")
}
