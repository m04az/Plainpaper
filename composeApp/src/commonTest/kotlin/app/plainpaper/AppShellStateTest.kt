package app.plainpaper

import app.plainpaper.core.domain.model.AppLanguage
import app.plainpaper.core.domain.model.AppLayoutDirection
import app.plainpaper.core.domain.model.AppPreferences
import app.plainpaper.core.domain.model.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppShellStateTest {
    @Test
    fun arabicChoiceIsRtlOnEnglishDevice() {
        val state = AppPreferences(language = AppLanguage.ARABIC).toShellState(systemTag = "en", systemDark = false)
        assertEquals(AppLayoutDirection.RTL, state.layoutDirection)
        assertTrue(state.useArabicTypeface)
    }

    @Test
    fun followSystemOnArabicDeviceIsRtl() {
        val state = AppPreferences().toShellState(systemTag = "ar-EG", systemDark = false)
        assertEquals(AppLayoutDirection.RTL, state.layoutDirection)
    }

    @Test
    fun englishChoiceOnArabicDeviceIsLtr() {
        val state = AppPreferences(language = AppLanguage.ENGLISH).toShellState(systemTag = "ar", systemDark = false)
        assertEquals(AppLayoutDirection.LTR, state.layoutDirection)
        assertFalse(state.useArabicTypeface)
    }

    @Test
    fun unsupportedDeviceLanguageFallsBackToLtr() {
        assertEquals(AppLayoutDirection.LTR, AppPreferences().toShellState("fr", false).layoutDirection)
    }

    @Test
    fun darkFlagFollowsThemeMode() {
        assertTrue(AppPreferences(themeMode = ThemeMode.SYSTEM).toShellState("en", systemDark = true).isDark)
        assertFalse(AppPreferences(themeMode = ThemeMode.LIGHT).toShellState("en", systemDark = true).isDark)
        assertTrue(AppPreferences(themeMode = ThemeMode.DARK).toShellState("en", systemDark = false).isDark)
    }
}
