package com.billlocket.app.feature.insights

import com.billlocket.app.domain.model.*
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightsPeriodTest {
    private val vnd = CurrencyCode.of("VND")

    @Test fun `week starts Monday and comparison immediately precedes it`() {
        val query = periodQuery(PeriodKind.WEEK, LocalDate.of(2026, 10, 1))
        assertEquals(LocalDate.of(2026, 9, 28).toEpochDay(), query.range.startEpochDay)
        assertEquals(query.range.startEpochDay - 1, query.comparisonRange!!.endInclusiveEpochDay)
        assertEquals(7, query.range.endInclusiveEpochDay - query.range.startEpochDay + 1)
    }

    @Test fun `month and year boundaries include leap dates`() {
        assertEquals(LocalDate.of(2024, 2, 29).toEpochDay(), periodQuery(PeriodKind.MONTH, LocalDate.of(2024, 2, 10)).range.endInclusiveEpochDay)
        assertEquals(LocalDate.of(2024, 12, 31).toEpochDay(), periodQuery(PeriodKind.YEAR, LocalDate.of(2024, 4, 1)).range.endInclusiveEpochDay)
    }

    @Test fun `March compares with the complete previous calendar month`() {
        val leapYearComparison = periodQuery(PeriodKind.MONTH, LocalDate.of(2024, 3, 15)).comparisonRange!!
        assertEquals(LocalDate.of(2024, 2, 1).toEpochDay(), leapYearComparison.startEpochDay)
        assertEquals(LocalDate.of(2024, 2, 29).toEpochDay(), leapYearComparison.endInclusiveEpochDay)

        val regularYearComparison = periodQuery(PeriodKind.MONTH, LocalDate.of(2023, 3, 15)).comparisonRange!!
        assertEquals(LocalDate.of(2023, 2, 1).toEpochDay(), regularYearComparison.startEpochDay)
        assertEquals(LocalDate.of(2023, 2, 28).toEpochDay(), regularYearComparison.endInclusiveEpochDay)
    }

    @Test fun `year compares with the complete previous calendar year`() {
        val comparison = periodQuery(PeriodKind.YEAR, LocalDate.of(2024, 6, 1)).comparisonRange!!
        assertEquals(LocalDate.of(2023, 1, 1).toEpochDay(), comparison.startEpochDay)
        assertEquals(LocalDate.of(2023, 12, 31).toEpochDay(), comparison.endInclusiveEpochDay)
    }

    @Test fun `comparison text reports lower spending`() {
        val current = PeriodInsights(Money(0, vnd), Money(50, vnd), Money(-50, vnd), emptyList(), emptyList())
        val previous = PeriodComparison(Money(0, vnd), Money(100, vnd), Money(-100, vnd))
        assertTrue(comparisonText(current, previous).contains("giảm"))
    }
}
