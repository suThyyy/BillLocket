package com.billlocket.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index

@Entity(
    tableName = "accounts",
    indices = [
        Index(value = ["normalized_name"], unique = true),
        Index(value = ["is_archived", "sort_order"]),
    ],
)
data class AccountEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
    val type: String,
    @ColumnInfo(name = "opening_balance_minor") val openingBalanceMinor: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "is_archived", defaultValue = "0") val isArchived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["transaction_type", "normalized_name"], unique = true),
        Index(value = ["transaction_type", "is_archived", "sort_order"]),
    ],
)
data class CategoryEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
    @ColumnInfo(name = "transaction_type") val transactionType: String,
    @ColumnInfo(name = "color_argb") val colorArgb: Long,
    @ColumnInfo(name = "icon_key") val iconKey: String?,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "is_default", defaultValue = "0") val isDefault: Boolean = false,
    @ColumnInfo(name = "is_archived", defaultValue = "0") val isArchived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["deleted_at", "local_date_epoch_day"]),
        Index(value = ["account_id", "deleted_at", "local_date_epoch_day"]),
        Index(value = ["category_id", "deleted_at", "local_date_epoch_day"]),
        Index(value = ["type", "deleted_at", "local_date_epoch_day"]),
        Index(value = ["occurred_at", "id"]),
    ],
)
data class TransactionEntity(
    @androidx.room.PrimaryKey val id: String,
    val type: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "category_id") val categoryId: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    val note: String?,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "local_date_epoch_day") val localDateEpochDay: Long,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long?,
)

@Entity(
    tableName = "transaction_images",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["transaction_id", "position"], unique = true),
        Index(value = ["content_uri"], unique = true),
        Index(value = ["transaction_id"]),
        Index(value = ["latitude", "longitude"]),
    ],
)
data class TransactionImageEntity(
    @androidx.room.PrimaryKey val id: String,
    @ColumnInfo(name = "transaction_id") val transactionId: String,
    @ColumnInfo(name = "content_uri") val contentUri: String,
    val position: Int,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "width_px") val widthPx: Int,
    @ColumnInfo(name = "height_px") val heightPx: Int,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "captured_at") val capturedAt: Long?,
    val latitude: Double?,
    val longitude: Double?,
    @ColumnInfo(name = "accuracy_meters") val accuracyMeters: Double?,
    @ColumnInfo(name = "place_name") val placeName: String?,
    @ColumnInfo(name = "location_source") val locationSource: String?,
    @ColumnInfo(name = "location_confirmed_at") val locationConfirmedAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "transfers",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["from_account_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["to_account_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["from_account_id", "deleted_at", "local_date_epoch_day"]),
        Index(value = ["to_account_id", "deleted_at", "local_date_epoch_day"]),
        Index(value = ["occurred_at", "id"]),
    ],
)
data class TransferEntity(
    @androidx.room.PrimaryKey val id: String,
    @ColumnInfo(name = "from_account_id") val fromAccountId: String,
    @ColumnInfo(name = "to_account_id") val toAccountId: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    val note: String?,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "local_date_epoch_day") val localDateEpochDay: Long,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long?,
)

@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["month_start_epoch_day", "scope_key"], unique = true),
        Index(value = ["category_id", "month_start_epoch_day"]),
    ],
)
data class BudgetEntity(
    @androidx.room.PrimaryKey val id: String,
    @ColumnInfo(name = "month_start_epoch_day") val monthStartEpochDay: Long,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    @ColumnInfo(name = "scope_key") val scopeKey: String,
    @ColumnInfo(name = "limit_minor") val limitMinor: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "warning_percent", defaultValue = "80") val warningPercent: Int = 80,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "budget_threshold_events",
    primaryKeys = ["budget_id", "threshold_percent"],
    foreignKeys = [
        ForeignKey(
            entity = BudgetEntity::class,
            parentColumns = ["id"],
            childColumns = ["budget_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["budget_id"])],
)
data class BudgetThresholdEventEntity(
    @ColumnInfo(name = "budget_id") val budgetId: String,
    @ColumnInfo(name = "threshold_percent") val thresholdPercent: Int,
    @ColumnInfo(name = "delivered_at") val deliveredAt: Long,
)

@Fts4(notIndexed = ["transaction_id"])
@Entity(tableName = "transaction_search")
data class TransactionSearchEntity(
    @ColumnInfo(name = "transaction_id") val transactionId: String,
    val note: String,
)
