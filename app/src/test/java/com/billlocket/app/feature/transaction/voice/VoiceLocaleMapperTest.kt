package com.billlocket.app.feature.transaction.voice

import com.billlocket.app.locale.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class VoiceLocaleMapperTest {

    @Test
    fun `Vietnamese app language maps to vi-VN`() {
        val tag = VoiceLocaleMapper.resolveLocaleTag(AppLanguage.VIETNAMESE, Locale.US)
        assertEquals("vi-VN", tag)
    }

    @Test
    fun `English app language maps to en-US`() {
        val tag = VoiceLocaleMapper.resolveLocaleTag(AppLanguage.ENGLISH, Locale("vi", "VN"))
        assertEquals("en-US", tag)
    }

    @Test
    fun `Follow system derives en-US when system language is English`() {
        val tagUS = VoiceLocaleMapper.resolveLocaleTag(AppLanguage.SYSTEM, Locale.US)
        val tagUK = VoiceLocaleMapper.resolveLocaleTag(AppLanguage.SYSTEM, Locale.UK)
        val tagEnCA = VoiceLocaleMapper.resolveLocaleTag(AppLanguage.SYSTEM, Locale.CANADA)
        assertEquals("en-US", tagUS)
        assertEquals("en-US", tagUK)
        assertEquals("en-US", tagEnCA)
    }

    @Test
    fun `Follow system derives vi-VN when system language is Vietnamese or other`() {
        val tagVi = VoiceLocaleMapper.resolveLocaleTag(AppLanguage.SYSTEM, Locale("vi", "VN"))
        val tagFr = VoiceLocaleMapper.resolveLocaleTag(AppLanguage.SYSTEM, Locale.FRANCE)
        val tagJa = VoiceLocaleMapper.resolveLocaleTag(AppLanguage.SYSTEM, Locale.JAPAN)
        assertEquals("vi-VN", tagVi)
        assertEquals("vi-VN", tagFr)
        assertEquals("vi-VN", tagJa)
    }

    @Test
    fun `Effective locale direct mapping works for English and Vietnamese`() {
        assertEquals("en-US", VoiceLocaleMapper.resolveLocaleTag(Locale.ENGLISH))
        assertEquals("en-US", VoiceLocaleMapper.resolveLocaleTag(Locale("en", "GB")))
        assertEquals("vi-VN", VoiceLocaleMapper.resolveLocaleTag(Locale("vi", "VN")))
        assertEquals("vi-VN", VoiceLocaleMapper.resolveLocaleTag(Locale.GERMAN))
    }
}
