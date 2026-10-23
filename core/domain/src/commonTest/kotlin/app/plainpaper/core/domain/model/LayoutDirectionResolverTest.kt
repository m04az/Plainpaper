package app.plainpaper.core.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class LayoutDirectionResolverTest {
    @Test
    fun arabicChoiceIsRtlRegardlessOfDevice() {
        assertEquals(AppLayoutDirection.RTL, resolveLayoutDirection(AppLanguage.ARABIC, "en-US"))
        assertEquals(AppLayoutDirection.RTL, resolveLayoutDirection(AppLanguage.ARABIC, null))
    }

    @Test
    fun englishChoiceIsLtrEvenOnArabicDevice() {
        assertEquals(AppLayoutDirection.LTR, resolveLayoutDirection(AppLanguage.ENGLISH, "ar"))
    }

    @Test
    fun followSystemUsesDeviceLanguage() {
        assertEquals(AppLayoutDirection.RTL, resolveLayoutDirection(AppLanguage.SYSTEM, "ar-EG"))
        assertEquals(AppLayoutDirection.RTL, resolveLayoutDirection(AppLanguage.SYSTEM, "AR"))
        assertEquals(AppLayoutDirection.RTL, resolveLayoutDirection(AppLanguage.SYSTEM, "ar_SA"))
        assertEquals(AppLayoutDirection.LTR, resolveLayoutDirection(AppLanguage.SYSTEM, "fr"))
        assertEquals(AppLayoutDirection.LTR, resolveLayoutDirection(AppLanguage.SYSTEM, "en"))
    }

    @Test
    fun unknownDeviceLanguageFallsBackToLtr() {
        assertEquals(AppLayoutDirection.LTR, resolveLayoutDirection(AppLanguage.SYSTEM, null))
        assertEquals(AppLayoutDirection.LTR, resolveLayoutDirection(AppLanguage.SYSTEM, ""))
    }

    @Test
    fun arabicPrefixedLanguagesAreNotArabic() {
        // "arc" (Aramaic) must not match the "ar" primary subtag.
        assertEquals(AppLayoutDirection.LTR, resolveLayoutDirection(AppLanguage.SYSTEM, "arc"))
    }
}
