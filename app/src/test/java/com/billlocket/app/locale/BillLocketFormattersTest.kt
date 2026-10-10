package com.billlocket.app.locale

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class BillLocketFormattersTest {

    private val localeVi = BillLocketFormatters.LOCALE_VI
    private val localeEn = BillLocketFormatters.LOCALE_EN

    @Test
    fun `formatMoney formats Vietnamese locale with trailing currency symbol and period grouping`() {
        val original = 1_250_000L
        val formatted = BillLocketFormatters.formatMoney(original, localeVi)
        assertEquals("1.250.000 ₫", formatted)
        assertEquals(1_250_000L, original) // immutability
    }

    @Test
    fun `formatMoney formats English locale with leading currency symbol and comma grouping`() {
        val original = 1_250_000L
        val formatted = BillLocketFormatters.formatMoney(original, localeEn)
        assertEquals("₫1,250,000", formatted)
        assertEquals(1_250_000L, original) // immutability
    }

    @Test
    fun `formatMoney handles zero amount correctly`() {
        assertEquals("0 ₫", BillLocketFormatters.formatMoney(0L, localeVi))
        assertEquals("₫0", BillLocketFormatters.formatMoney(0L, localeEn))
    }

    @Test
    fun `formatMoney handles negative values correctly`() {
        val negative = -1_250_000L
        val formattedVi = BillLocketFormatters.formatMoney(negative, localeVi)
        val formattedEn = BillLocketFormatters.formatMoney(negative, localeEn)
        assertEquals("-1.250.000 ₫", formattedVi)
        assertEquals("-₫1,250,000", formattedEn)
        assertEquals(-1_250_000L, negative)
    }

    @Test
    fun `formatMoneyWithVisibility respects hidden and obscured guards`() {
        assertEquals("••••", BillLocketFormatters.formatMoneyWithVisibility(50_000L, localeVi, hidden = true))
        assertEquals("*** ₫", BillLocketFormatters.formatMoneyWithVisibility(50_000L, localeVi, obscured = true))
        assertEquals("50.000 ₫", BillLocketFormatters.formatMoneyWithVisibility(50_000L, localeVi, hidden = false, obscured = false))
        assertEquals("₫50,000", BillLocketFormatters.formatMoneyWithVisibility(50_000L, localeEn, hidden = false, obscured = false))
    }

    @Test
    fun `formatDate formats date with locale-appropriate pattern`() {
        val date = LocalDate.of(2026, 10, 9)
        assertEquals("09/10/2026", BillLocketFormatters.formatDate(date, localeVi))
        assertEquals("10/09/2026", BillLocketFormatters.formatDate(date, localeEn))
    }

    @Test
    fun `formatEpochDay formats day correctly for both locales`() {
        val epochDay = LocalDate.of(2026, 10, 9).toEpochDay()
        assertEquals("09/10/2026", BillLocketFormatters.formatEpochDay(epochDay, localeVi))
        assertEquals("10/09/2026", BillLocketFormatters.formatEpochDay(epochDay, localeEn))
    }

    @Test
    fun `formatEpochDayShort formats short date correctly`() {
        val epochDay = LocalDate.of(2026, 10, 9).toEpochDay()
        assertEquals("09/10", BillLocketFormatters.formatEpochDayShort(epochDay, localeVi))
        assertEquals("10/09", BillLocketFormatters.formatEpochDayShort(epochDay, localeEn))
    }

    @Test
    fun `formatTime formats 24h for Vietnamese and 12h AM-PM for English`() {
        val time = LocalTime.of(14, 30)
        assertEquals("14:30", BillLocketFormatters.formatTime(time, localeVi))
        val enTime = BillLocketFormatters.formatTime(time, localeEn)
        assert(enTime.contains("2:30") && (enTime.contains("PM") || enTime.contains("pm"))) {
            "Expected 2:30 PM but was $enTime"
        }
    }

    @Test
    fun `formatDateTime and formatDateTimeShort format combined date and time`() {
        val zone = ZoneId.of("Asia/Ho_Chi_Minh")
        val zdt = ZonedDateTime.of(2026, 10, 9, 14, 30, 0, 0, zone)
        val epochMillis = zdt.toInstant().toEpochMilli()

        val fullVi = BillLocketFormatters.formatDateTime(epochMillis, zone, localeVi)
        assertEquals("14:30, 09/10/2026", fullVi)

        val fullEn = BillLocketFormatters.formatDateTime(epochMillis, zone, localeEn)
        assert(fullEn.contains("2:30") && fullEn.contains("10/09/2026")) {
            "Expected 2:30 PM, 10/09/2026 but was $fullEn"
        }

        val shortVi = BillLocketFormatters.formatDateTimeShort(epochMillis, zone, localeVi)
        assertEquals("09/10 14:30", shortVi)
    }

    @Test
    fun `formatDistance formats meters and kilometers cleanly`() {
        assertEquals("500 m", BillLocketFormatters.formatDistance(500.0, localeVi))
        assertEquals("2.5 km", BillLocketFormatters.formatDistance(2500.0, localeEn))
    }

    @Test
    fun `formatWidgetRelativeTime uses supplied labels deterministically without clock boundary flakiness`() {
        val zone = ZoneId.of("Asia/Ho_Chi_Minh")
        val referenceToday = LocalDate.of(2026, 10, 9)

        // Today transaction at 14:30
        val todayTime = ZonedDateTime.of(2026, 10, 9, 14, 30, 0, 0, zone)
        val todayMillis = todayTime.toInstant().toEpochMilli()

        val viToday = BillLocketFormatters.formatWidgetRelativeTime(
            todayMillis, zone, localeVi, "Hôm nay", "Hôm qua", nowDate = referenceToday
        )
        assertEquals("Hôm nay 14:30", viToday)

        val enToday = BillLocketFormatters.formatWidgetRelativeTime(
            todayMillis, zone, localeEn, "Today", "Yesterday", nowDate = referenceToday
        )
        assert(enToday.startsWith("Today 2:30")) { "Expected Today 2:30 PM but was $enToday" }

        // Yesterday transaction at 09:15
        val yesterdayTime = ZonedDateTime.of(2026, 10, 8, 9, 15, 0, 0, zone)
        val yesterdayMillis = yesterdayTime.toInstant().toEpochMilli()

        val viYesterday = BillLocketFormatters.formatWidgetRelativeTime(
            yesterdayMillis, zone, localeVi, "Hôm nay", "Hôm qua", nowDate = referenceToday
        )
        assertEquals("Hôm qua 09:15", viYesterday)

        val enYesterday = BillLocketFormatters.formatWidgetRelativeTime(
            yesterdayMillis, zone, localeEn, "Today", "Yesterday", nowDate = referenceToday
        )
        assert(enYesterday.startsWith("Yesterday 9:15")) { "Expected Yesterday 9:15 AM but was $enYesterday" }

        // Older transaction fallback to short date
        val olderTime = ZonedDateTime.of(2026, 10, 5, 10, 0, 0, 0, zone)
        val olderMillis = olderTime.toInstant().toEpochMilli()

        val viOlder = BillLocketFormatters.formatWidgetRelativeTime(
            olderMillis, zone, localeVi, "Hôm nay", "Hôm qua", nowDate = referenceToday
        )
        assertEquals("05/10 10:00", viOlder)
    }
}
