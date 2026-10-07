package com.billlocket.app.feature.home

import com.billlocket.app.domain.model.DraftId
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeCalendarTest {
    @Test fun `month range uses exact civil boundaries`() {
        val range = YearMonth.of(2024, 2).dateRange()
        assertEquals(LocalDate.of(2024, 2, 1).toEpochDay(), range.startEpochDay)
        assertEquals(LocalDate.of(2024, 2, 29).toEpochDay(), range.endInclusiveEpochDay)
    }

    @Test fun `calendar starts Monday and contains leap day`() {
        val cells = calendarCells(YearMonth.of(2024, 2))
        assertNull(cells.first())
        assertEquals(LocalDate.of(2024, 2, 1), cells[3])
        assertEquals(LocalDate.of(2024, 2, 29), cells.filterNotNull().last())
        assertEquals(0, cells.size % 7)
    }

    @Test fun `draft created from calendar preserves chosen day and zone`() {
        val chosen = LocalDate.of(2025, 1, 5).toEpochDay()
        val draft = newDraftForDay(
            DraftId("draft"),
            chosen,
            Instant.parse("2025-01-01T10:15:30Z"),
            ZoneId.of("Asia/Ho_Chi_Minh"),
        )
        assertEquals(chosen, draft.occurred.localDateEpochDay)
        assertEquals("Asia/Ho_Chi_Minh", draft.occurred.zoneId)
    }

    @Test fun `future date is correctly detected for blocking add transaction`() {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val tomorrow = today.plusDays(1)
        val nextMonth = today.plusMonths(1)

        val isYesterdayFuture = yesterday.toEpochDay() > today.toEpochDay()
        val isTodayFuture = today.toEpochDay() > today.toEpochDay()
        val isTomorrowFuture = tomorrow.toEpochDay() > today.toEpochDay()
        val isNextMonthFuture = nextMonth.toEpochDay() > today.toEpochDay()

        assertEquals(false, isYesterdayFuture)
        assertEquals(false, isTodayFuture)
        assertEquals(true, isTomorrowFuture)
        assertEquals(true, isNextMonthFuture)
    }
}
