package com.billlocket.app.domain

import com.billlocket.app.domain.model.CurrencyCode
import com.billlocket.app.domain.model.DateRange
import com.billlocket.app.domain.model.GeoPoint
import com.billlocket.app.domain.model.Money
import com.billlocket.app.domain.model.MoneyCalculator
import com.billlocket.app.domain.model.PageRequest
import com.billlocket.app.domain.model.TransactionId
import com.billlocket.app.domain.result.DomainFailure
import com.billlocket.app.domain.result.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValueObjectsTest {
    private val vnd = CurrencyCode.of("vnd")

    @Test
    fun currencyCode_isNormalized() {
        assertEquals("VND", vnd.value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun currencyCode_rejectsInvalidValue() {
        CurrencyCode.of("VN")
    }

    @Test(expected = IllegalArgumentException::class)
    fun identifier_rejectsBlankValue() {
        TransactionId("  ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun geoPoint_rejectsOutOfRangeCoordinates() {
        GeoPoint(91.0, 106.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun dateRange_rejectsReversedRange() {
        DateRange(10, 9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun pageRequest_rejectsOversizedPage() {
        PageRequest(limit = 201)
    }

    @Test
    fun moneyCalculator_rejectsDifferentCurrencies() {
        val result = MoneyCalculator.add(Money(1, vnd), Money(1, CurrencyCode.of("USD")))

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).reason is DomainFailure.CurrencyMismatch)
    }

    @Test
    fun moneyCalculator_reportsOverflow() {
        val result = MoneyCalculator.add(Money(Long.MAX_VALUE, vnd), Money(1, vnd))

        assertEquals(Outcome.Failure(DomainFailure.NumericOverflow), result)
    }
}
