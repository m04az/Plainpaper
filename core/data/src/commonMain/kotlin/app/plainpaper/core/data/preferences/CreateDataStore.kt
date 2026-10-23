package app.plainpaper.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import okio.Path.Companion.toPath

/** File name of the preferences store inside the platform-provided directory. */
public const val PREFERENCES_FILE_NAME: String = "plainpaper.preferences_pb"

/**
 * Creates the single Preferences DataStore at [path] (an absolute file path ending in
 * [PREFERENCES_FILE_NAME]). Only one instance per path may exist in a process.
 */
public fun createPreferencesDataStore(
    path: String,
    scope: CoroutineScope,
): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        scope = scope,
        produceFile = { path.toPath() },
    )
