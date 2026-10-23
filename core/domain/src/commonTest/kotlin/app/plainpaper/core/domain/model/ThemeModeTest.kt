package app.plainpaper.core.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeModeTest {
    @Test
    fun systemFollowsDevice() {
        assertTrue(ThemeMode.SYSTEM.resolveIsDark(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.resolveIsDark(systemDark = false))
    }

    @Test
    fun explicitChoicesIgnoreDevice() {
        assertFalse(ThemeMode.LIGHT.resolveIsDark(systemDark = true))
        assertTrue(ThemeMode.DARK.resolveIsDark(systemDark = false))
    }
}
