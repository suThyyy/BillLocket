package com.billlocket.app.feature.recap

import com.billlocket.app.domain.model.CategoryDistribution
import com.billlocket.app.domain.model.DateRange
import com.billlocket.app.domain.model.Money
import com.billlocket.app.domain.model.PeriodInsights
import com.billlocket.app.domain.model.TransactionDetails
import com.billlocket.app.domain.model.TransactionId
import com.billlocket.app.domain.model.TransactionImageId
import com.billlocket.app.domain.model.TransactionType
import java.time.LocalDate
import java.time.YearMonth

data class MonthlyRecapPhoto(
    val imageId: TransactionImageId,
    val transactionId: TransactionId,
    val mediaUri: String,
    val amount: Money,
    val type: TransactionType,
    val categoryName: String,
    val note: String?,
    val epochDay: Long,
    val occurredAt: Long,
    val placeName: String?,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

data class MonthlyRecap(
    val month: YearMonth,
    val income: Money,
    val expense: Money,
    val net: Money,
    val transactionCount: Int,
    val topCategory: CategoryDistribution?,
    val mostActiveDayEpoch: Long?,
    val mostActiveDayCount: Int,
    val photos: List<MonthlyRecapPhoto>,
) {
    val locatedPhotos: List<MonthlyRecapPhoto>
        get() = photos.filter { !it.placeName.isNullOrBlank() || (it.latitude != null && it.longitude != null) }
}

data class RecapPrivacy(
    val showAmounts: Boolean = false,
    val showNotes: Boolean = false,
    val showPlaces: Boolean = false,
)

data class ShareableRecapPhoto(
    val mediaUri: String,
    val categoryName: String,
    val amount: Money?,
    val note: String?,
    val placeName: String?,
)

data class ShareableMonthlyRecap(
    val month: YearMonth,
    val income: Money?,
    val expense: Money?,
    val net: Money?,
    val transactionCount: Int,
    val topCategoryName: String?,
    val photos: List<ShareableRecapPhoto>,
)

fun MonthlyRecap.redacted(privacy: RecapPrivacy): ShareableMonthlyRecap =
    ShareableMonthlyRecap(
        month = month,
        income = income.takeIf { privacy.showAmounts },
        expense = expense.takeIf { privacy.showAmounts },
        net = net.takeIf { privacy.showAmounts },
        transactionCount = transactionCount,
        topCategoryName = topCategory?.name,
        photos = photos.map { photo ->
            ShareableRecapPhoto(
                mediaUri = photo.mediaUri,
                categoryName = photo.categoryName,
                amount = photo.amount.takeIf { privacy.showAmounts },
                note = photo.note?.takeIf { privacy.showNotes },
                placeName = photo.placeName?.takeIf { privacy.showPlaces },
            )
        },
    )

fun buildMonthlyRecap(
    month: YearMonth,
    insights: PeriodInsights,
    transactions: List<TransactionDetails>,
    categoryNames: Map<String, String>,
): MonthlyRecap {
    val startDay = month.atDay(1).toEpochDay()
    val endDay = month.atEndOfMonth().toEpochDay()
    val inMonth = transactions.filter { it.deletedAt == null && it.occurred.localDateEpochDay in startDay..endDay }
    val activeDay = inMonth
        .groupBy { it.occurred.localDateEpochDay }
        .map { (day, rows) ->
            val expense = rows.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount.minorUnits }
            Triple(day, rows.size, expense)
        }
        .sortedWith(compareByDescending<Triple<Long, Int, Long>> { it.second }.thenByDescending { it.third }.thenByDescending { it.first })
        .firstOrNull()
    val photos = inMonth
        .flatMap { transaction ->
            transaction.images.sortedBy { it.position }.map { image ->
                MonthlyRecapPhoto(
                    imageId = image.id,
                    transactionId = transaction.id,
                    mediaUri = image.mediaUri,
                    amount = transaction.amount,
                    type = transaction.type,
                    categoryName = categoryNames[transaction.categoryId.value].orEmpty(),
                    note = transaction.note,
                    epochDay = transaction.occurred.localDateEpochDay,
                    occurredAt = transaction.occurred.epochMillis,
                    placeName = image.location?.let { location ->
                        location.placeName?.trim()?.takeIf(String::isNotEmpty) ?: "Vị trí đã lưu"
                    },
                    latitude = image.location?.point?.latitude,
                    longitude = image.location?.point?.longitude,
                )
            }
        }
        .sortedWith(compareByDescending<MonthlyRecapPhoto> { it.occurredAt }.thenBy { it.imageId.value })
    return MonthlyRecap(
        month = month,
        income = insights.income,
        expense = insights.expense,
        net = insights.net,
        transactionCount = inMonth.size,
        topCategory = insights.categories.firstOrNull(),
        mostActiveDayEpoch = activeDay?.first,
        mostActiveDayCount = activeDay?.second ?: 0,
        photos = photos,
    )
}

fun YearMonth.toRecapRange(today: LocalDate = LocalDate.now()): DateRange {
    val lastDay = minOf(atEndOfMonth(), today)
    return DateRange(atDay(1).toEpochDay(), lastDay.toEpochDay())
}
