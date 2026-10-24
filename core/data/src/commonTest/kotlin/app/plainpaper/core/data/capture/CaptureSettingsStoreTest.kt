package app.plainpaper.core.data.capture

import app.plainpaper.core.data.preferences.PREFERENCES_FILE_NAME
import app.plainpaper.core.data.preferences.createPreferencesDataStore
import app.plainpaper.core.data.pro.FreeOnlyProStatusRepository
import app.plainpaper.core.data.tempDirectoryPath
import app.plainpaper.core.domain.model.capture.CaptureMode
import app.plainpaper.core.domain.model.capture.CaptureSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CaptureSettingsStoreTest {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val path =
        tempDirectoryPath().trimEnd('/') + "/plainpaper-capture-${Random.nextLong()}/$PREFERENCES_FILE_NAME"
    private val repository = DataStoreCaptureSettingsRepository(createPreferencesDataStore(path, scope))

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun freshStoreHasDefaults() =
        runTest {
            assertEquals(
                CaptureSettings(flash = false, autoCapture = true, mode = CaptureMode.Document),
                repository.settings.first(),
            )
        }

    @Test
    fun settingsRoundTrip() =
        runTest {
            val wanted = CaptureSettings(flash = true, autoCapture = false, mode = CaptureMode.Receipt)
            repository.set(wanted)
            assertEquals(wanted, repository.settings.first())
        }

    @Test
    fun everyoneIsFreeForNow() =
        runTest {
            assertFalse(FreeOnlyProStatusRepository().status.first().isPro)
        }
}
