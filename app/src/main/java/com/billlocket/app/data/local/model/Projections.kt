package com.billlocket.app.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Relation
import com.billlocket.app.data.local.entity.TransactionEntity
import com.billlocket.app.data.local.entity.TransactionImageEntity

data class TransactionWithImages(
    @Embedded val transaction: TransactionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "transaction_id",
        entity = TransactionImageEntity::class,
    )
    val images: List<TransactionImageEntity>,
)

data class TransactionSummary(
    val id: String,
    val type: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    val note: String?,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "local_date_epoch_day") val localDateEpochDay: Long,
    @ColumnInfo(name = "account_name") val accountName: String,
    @ColumnInfo(name = "category_name") val categoryName: String,
    @ColumnInfo(name = "thumbnail_uri") val thumbnailUri: String?,
)

data class DayTotals(
    @ColumnInfo(name = "local_date_epoch_day") val localDateEpochDay: Long,
    @ColumnInfo(name = "income_minor") val incomeMinor: Long,
    @ColumnInfo(name = "expense_minor") val expenseMinor: Long,
    @ColumnInfo(name = "representative_uri") val representativeUri: String?,
)

data class AccountBalance(
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "balance_minor") val balanceMinor: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
)

data class PeriodTotals(
    @ColumnInfo(name = "income_minor") val incomeMinor: Long,
    @ColumnInfo(name = "expense_minor") val expenseMinor: Long,
)

data class CategoryTotal(
    @ColumnInfo(name = "category_id") val categoryId: String,
    @ColumnInfo(name = "category_name") val categoryName: String,
    @ColumnInfo(name = "total_minor") val totalMinor: Long,
)

data class BudgetProgress(
    @ColumnInfo(name = "budget_id") val budgetId: String,
    @ColumnInfo(name = "limit_minor") val limitMinor: Long,
    @ColumnInfo(name = "spent_minor") val spentMinor: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "warning_percent") val warningPercent: Int,
)

data class MapImageMarker(
    @ColumnInfo(name = "image_id") val imageId: String,
    @ColumnInfo(name = "transaction_id") val transactionId: String,
    @ColumnInfo(name = "content_uri") val contentUri: String,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "place_name") val placeName: String?,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
)

data class WidgetCandidate(
    @ColumnInfo(name = "transaction_id") val transactionId: String,
    @ColumnInfo(name = "image_id") val imageId: String,
    @ColumnInfo(name = "content_uri") val contentUri: String,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "category_name") val categoryName: String,
)
