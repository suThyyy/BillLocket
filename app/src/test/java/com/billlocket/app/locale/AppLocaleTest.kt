package com.billlocket.app.locale

import androidx.core.os.LocaleListCompat
import com.billlocket.app.locale.AppLocaleControllerImpl.Companion.mapLocaleListToAppLanguage
import com.billlocket.app.locale.AppLocaleControllerImpl.Companion.toLocaleListCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLocaleTest {

    private class FakeAppLocaleController(initial: AppLanguage = AppLanguage.SYSTEM) : AppLocaleController {
        private val _current = MutableStateFlow(initial)
        override val current: StateFlow<AppLanguage> = _current.asStateFlow()

        override fun apply(language: AppLanguage) {
            _current.value = language
        }

        override fun syncFromSystem() {
            // no-op in fake
        }
    }

    @Test
    fun `AppLanguage toLanguageTag maps correctly`() {
        assertNull(AppLanguage.SYSTEM.toLanguageTag())
        assertEquals("vi-VN", AppLanguage.VIETNAMESE.toLanguageTag())
        assertEquals("en-US", AppLanguage.ENGLISH.toLanguageTag())
    }

    @Test
    fun `AppLanguage toLocaleListCompat maps correctly`() {
        assertEquals(LocaleListCompat.getEmptyLocaleList(), AppLanguage.SYSTEM.toLocaleListCompat())
        assertEquals(LocaleListCompat.forLanguageTags("vi-VN"), AppLanguage.VIETNAMESE.toLocaleListCompat())
        assertEquals(LocaleListCompat.forLanguageTags("en-US"), AppLanguage.ENGLISH.toLocaleListCompat())
    }

    @Test
    fun `mapLocaleListToAppLanguage correctly handles empty and specific locales`() {
        assertEquals(AppLanguage.SYSTEM, mapLocaleListToAppLanguage(LocaleListCompat.getEmptyLocaleList()))
        assertEquals(AppLanguage.VIETNAMESE, mapLocaleListToAppLanguage(LocaleListCompat.forLanguageTags("vi-VN")))
        assertEquals(AppLanguage.VIETNAMESE, mapLocaleListToAppLanguage(LocaleListCompat.forLanguageTags("vi")))
        assertEquals(AppLanguage.ENGLISH, mapLocaleListToAppLanguage(LocaleListCompat.forLanguageTags("en-US")))
        assertEquals(AppLanguage.ENGLISH, mapLocaleListToAppLanguage(LocaleListCompat.forLanguageTags("en")))
        assertEquals(AppLanguage.SYSTEM, mapLocaleListToAppLanguage(LocaleListCompat.forLanguageTags("fr-FR")))
    }

    @Test
    fun `controller updates current state flow on apply`() {
        val controller = FakeAppLocaleController(AppLanguage.SYSTEM)
        assertEquals(AppLanguage.SYSTEM, controller.current.value)

        controller.apply(AppLanguage.ENGLISH)
        assertEquals(AppLanguage.ENGLISH, controller.current.value)

        controller.apply(AppLanguage.VIETNAMESE)
        assertEquals(AppLanguage.VIETNAMESE, controller.current.value)
    }

    @Test
    fun `AppLanguage entries contain expected options in order`() {
        val entries = AppLanguage.entries
        assertEquals(listOf(AppLanguage.SYSTEM, AppLanguage.VIETNAMESE, AppLanguage.ENGLISH), entries)
    }
}
