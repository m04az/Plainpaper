package app.plainpaper.core.data.capture

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.plainpaper.core.domain.model.capture.CaptureMode
import app.plainpaper.core.domain.model.capture.CaptureSettings
import app.plainpaper.core.domain.repository.CaptureSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import okio.IOException

internal object CaptureSettingsKeys {
    val FLASH = booleanPreferencesKey("capture.flash")
    val AUTO = booleanPreferencesKey("capture.auto")
    val MODE = stringPreferencesKey("capture.mode")
}

/** Flash, Auto and mode in the shared Preferences DataStore (spec FR-005). */
public class DataStoreCaptureSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : CaptureSettingsRepository {
    override val settings: Flow<CaptureSettings> =
        dataStore.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .map { prefs ->
                val defaults = CaptureSettings()
                CaptureSettings(
                    flash = prefs[CaptureSettingsKeys.FLASH] ?: defaults.flash,
                    autoCapture = prefs[CaptureSettingsKeys.AUTO] ?: defaults.autoCapture,
                    mode =
                        CaptureMode.entries.firstOrNull { it.name == prefs[CaptureSettingsKeys.MODE] } ?: defaults.mode,
                )
            }

    override suspend fun set(settings: CaptureSettings) {
        dataStore.edit {
            it[CaptureSettingsKeys.FLASH] = settings.flash
            it[CaptureSettingsKeys.AUTO] = settings.autoCapture
            it[CaptureSettingsKeys.MODE] = settings.mode.name
        }
    }
}
