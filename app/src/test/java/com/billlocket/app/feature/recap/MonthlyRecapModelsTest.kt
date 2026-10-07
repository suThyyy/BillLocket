package com.billlocket.app.feature.recap

import com.billlocket.app.domain.model.AccountId
import com.billlocket.app.domain.model.CategoryDistribution
import com.billlocket.app.domain.model.CategoryId
import com.billlocket.app.domain.model.CurrencyCode
import com.billlocket.app.domain.model.GeoPoint
import com.billlocket.app.domain.model.InstantAtZone
import com.billlocket.app.domain.model.LocationSource
import com.billlocket.app.domain.model.Money
import com.billlocket.app.domain.model.PeriodInsights
import com.billlocket.app.domain.model.PhotoLocation
import com.billlocket.app.domain.model.TransactionDetails
import com.billlocket.app.domain.model.TransactionId
import com.billlocket.app.domain.model.TransactionImage
import com.billlocket.app.domain.model.TransactionImageId
import com.billlocket.app.domain.model.TransactionType
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonthlyRecapModelsTest {
    private val currency = CurrencyCode.of("VND")

    @Test
    fun `aggregates every image and chooses active day deterministically`() {
        val recap = buildMonthlyRecap(
            month = YearMonth.of(2026, 10),
            insights = insights(expense = 450_000, income = 1_000_000),
            transactions = listOf(
                transaction("a", 2, 100_000, images = 2),
                transaction("b", 2, 150_000, images = 1),
                transaction("c", 8, 200_000, images = 0),
            ),
            categoryNames = mapOf("food" to "Ăn uống"),
        )

        assertEquals(3, recap.transactionCount)
        assertEquals(LocalDate.of(2026, 10, 2).toEpochDay(), recap.mostActiveDayEpoch)
        assertEquals(2, recap.mostActiveDayCount)
        assertEquals(3, recap.photos.size)
        assertEquals("Ăn uống", recap.photos.first().categoryName)
    }

    @Test
    fun `empty month remains a valid recap`() {
        val recap = buildMonthlyRecap(YearMonth.of(2026, 10), insights(0, 0), emptyList(), emptyMap())

        assertEquals(0, recap.transactionCount)
        assertEquals(emptyList<MonthlyRecapPhoto>(), recap.photos)
        assertNull(recap.mostActiveDayEpoch)
        assertNull(recap.topCategory)
    }

    @Test
    fun `privacy redaction omits values instead of masking them`() {
        val recap = buildMonthlyRecap(
            YearMonth.of(2026, 10),
            insights(100_000, 0),
            listOf(transaction("a", 2, 100_000, images = 1, note = "Bữa tối")),
            mapOf("food" to "Ăn uống"),
        )

        val hidden = recap.redacted(RecapPrivacy())
        assertNull(hidden.expense)
        assertNull(hidden.photos.single().amount)
        assertNull(hidden.photos.single().note)
        assertNull(hidden.photos.single().placeName)

        val visible = recap.redacted(RecapPrivacy(showAmounts = true, showNotes = true, showPlaces = true))
        assertEquals(100_000L, visible.expense?.minorUnits)
        assertEquals("Bữa tối", visible.photos.single().note)
    }

    @Test
    fun `current month range ends today while past month uses month end`() {
        val today = LocalDate.of(2026, 10, 6)
        assertEquals(today.toEpochDay(), YearMonth.of(2026, 10).toRecapRange(today).endInclusiveEpochDay)
        assertEquals(LocalDate.of(2026, 9, 30).toEpochDay(), YearMonth.of(2026, 9).toRecapRange(today).endInclusiveEpochDay)
    }

    @Test
    fun `locatedPhotos maps confirmed place name and geographic coordinates`() {
        val location = PhotoLocation(
            point = GeoPoint(21.0285, 105.8542),
            source = LocationSource.MANUAL,
            confirmedAtEpochMillis = 1000L,
            placeName = "Hồ Hoàn Kiếm",
        )
        val details = transaction("loc-1", 5, 50_000, images = 1, location = location)
        val recap = buildMonthlyRecap(
            YearMonth.of(2026, 10),
            insights(50_000, 0),
            listOf(details),
            mapOf("food" to "Ăn uống"),
        )
        assertEquals(1, recap.locatedPhotos.size)
        assertEquals("Hồ Hoàn Kiếm", recap.locatedPhotos.first().placeName)
        assertEquals(21.0285, recap.locatedPhotos.first().latitude!!, 0.0001)
        assertEquals(105.8542, recap.locatedPhotos.first().longitude!!, 0.0001)
    }

    private fun insights(expense: Long, income: Long) = PeriodInsights(
        income = Money(income, currency),
        expense = Money(expense, currency),
        net = Money(income - expense, currency),
        categories = if (expense == 0L) emptyList() else listOf(CategoryDistribution(CategoryId("food"), "Ăn uống", Money(expense, currency))),
        trend = emptyList(),
    )

    private fun transaction(
        id: String,
        day: Int,
        amount: Long,
        images: Int,
        note: String? = null,
        location: PhotoLocation? = null,
    ) = TransactionDetails(
        id = TransactionId(id),
        type = TransactionType.EXPENSE,
        amount = Money(amount, currency),
        categoryId = CategoryId("food"),
        accountId = AccountId("cash"),
        note = note,
        occurred = InstantAtZone(
            epochMillis = LocalDate.of(2026, 10, day).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant().toEpochMilli(),
            localDateEpochDay = LocalDate.of(2026, 10, day).toEpochDay(),
            zoneId = "Asia/Ho_Chi_Minh",
        ),
        images = (0 until images).map { index ->
            TransactionImage(
                id = TransactionImageId("$id-$index"),
                mediaUri = "content://billlocket/$id/$index",
                position = index,
                mimeType = "image/jpeg",
                widthPx = 100,
                heightPx = 100,
                sizeBytes = 1,
                capturedAt = null,
                location = location,
            )
        },
        deletedAt = null,
    )
}
