package com.billlocket.app.feature.transaction.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceNoteTextMergerTest {

    @Test
    fun `empty note inserts normalized recognized text`() {
        val result = VoiceNoteTextMerger.merge("", "mua cà phê sáng")
        assertEquals("mua cà phê sáng", result)
    }

    @Test
    fun `blank note inserts normalized recognized text`() {
        val result = VoiceNoteTextMerger.merge("   ", "ăn trưa cùng đồng nghiệp")
        assertEquals("ăn trưa cùng đồng nghiệp", result)
    }

    @Test
    fun `existing note appends recognized text with exactly one space`() {
        val result = VoiceNoteTextMerger.merge("Ăn trưa", "với bạn đại học")
        assertEquals("Ăn trưa với bạn đại học", result)
    }

    @Test
    fun `existing note with trailing spaces appends with single separating space`() {
        val result = VoiceNoteTextMerger.merge("Ăn trưa   ", "với bạn")
        assertEquals("Ăn trưa với bạn", result)
    }

    @Test
    fun `blank recognized text does not modify current note`() {
        val original = "Ghi chú hiện tại"
        val result1 = VoiceNoteTextMerger.merge(original, "")
        val result2 = VoiceNoteTextMerger.merge(original, "   ")
        val result3 = VoiceNoteTextMerger.merge(original, "\t\n  ")
        assertEquals(original, result1)
        assertEquals(original, result2)
        assertEquals(original, result3)
    }

    @Test
    fun `normalize collapses multiple and irregular whitespace into single space`() {
        val result = VoiceNoteTextMerger.normalize("  một    hai \t\n ba   bốn  ")
        assertEquals("một hai ba bốn", result)
    }

    @Test
    fun `Unicode Vietnamese text with accents and tone marks is preserved exactly`() {
        val spoken = "Cà phê sữa đá vỉa hè Sài Gòn, quán Cô Ba đường Nguyễn Huệ"
        val result = VoiceNoteTextMerger.merge("", spoken)
        assertEquals(spoken, result)

        val appended = VoiceNoteTextMerger.merge("Hôm nay", spoken)
        assertEquals("Hôm nay $spoken", appended)
    }

    @Test
    fun `result exceeding 500 characters is safely capped at 500 characters`() {
        val baseNote = "A".repeat(490)
        val spoken = "12345678901234567890" // 20 chars
        val result = VoiceNoteTextMerger.merge(baseNote, spoken, maxLimit = 500)

        assertEquals(500, result.length)
        assertTrue(result.startsWith("A".repeat(490)))
        assertEquals(baseNote + " 123456789", result)
    }

    @Test
    fun `spoken text alone exceeding maxLimit is safely truncated`() {
        val spoken = "X".repeat(520)
        val result = VoiceNoteTextMerger.merge("", spoken, maxLimit = 500)
        assertEquals(500, result.length)
        assertEquals("X".repeat(500), result)
    }
}
