package com.billlocket.app.domain.model

data class TransactionImage(
    val id: TransactionImageId,
    val mediaUri: String,
    val position: Int,
    val mimeType: String,
    val widthPx: Int,
    val heightPx: Int,
    val sizeBytes: Long,
    val capturedAt: Long?,
    val location: PhotoLocation?,
)

data class TransactionDetails(
    val id: TransactionId,
    val type: TransactionType,
    val amount: Money,
    val categoryId: CategoryId,
    val accountId: AccountId,
    val note: String?,
    val occurred: InstantAtZone,
    val images: List<TransactionImage>,
    val deletedAt: Long?,
)

data class TransactionSummary(
    val id: TransactionId,
    val type: TransactionType,
    val amount: Money,
    val categoryName: String,
    val accountName: String,
    val note: String?,
    val occurred: InstantAtZone,
    val representativeImageUri: String?,
)

data class CalendarDaySummary(
    val epochDay: Long,
    val income: Money,
    val expense: Money,
    val transactionCount: Int,
    val thumbnailUris: List<String>,
)

data class MapPhoto(
    val imageId: TransactionImageId,
    val transactionId: TransactionId,
    val mediaUri: String,
    val location: PhotoLocation,
    val amount: Money,
    val categoryName: String,
    val occurred: InstantAtZone,
)

data class WidgetPhoto(
    val transactionId: TransactionId,
    val imageId: TransactionImageId,
    val mediaUri: String,
    val amount: Money,
    val categoryName: String,
)

data class WidgetSelection(val transactionId: TransactionId? = null)

data class DeletedTransaction(val id: TransactionId, val deletedAt: Long)

data class PurgedTransactionMedia(val mediaUris: List<String>)

data class Account(
    val id: AccountId,
    val name: String,
    val type: AccountType,
    val openingBalance: Money,
    val sortOrder: Int,
    val isArchived: Boolean,
)

data class AccountDetails(val account: Account, val balance: Money)

data class CreateAccountCommand(val name: String, val type: AccountType, val openingBalance: Money)

data class UpdateAccountCommand(val id: AccountId, val name: String, val type: AccountType)

data class Category(
    val id: CategoryId,
    val name: String,
    val type: TransactionType,
    val colorArgb: Long,
    val iconKey: String?,
    val sortOrder: Int,
    val isDefault: Boolean,
    val isArchived: Boolean,
)

data class CreateCategoryCommand(
    val name: String,
    val type: TransactionType,
    val colorArgb: Long,
    val iconKey: String?,
)

data class UpdateCategoryCommand(
    val id: CategoryId,
    val name: String,
    val type: TransactionType,
    val colorArgb: Long,
    val iconKey: String?,
)

data class Transfer(
    val id: TransferId,
    val fromAccountId: AccountId,
    val toAccountId: AccountId,
    val amount: Money,
    val note: String?,
    val occurred: InstantAtZone,
    val deletedAt: Long?,
)

data class UpdateTransferCommand(val id: TransferId, val value: CreateTransferCommand)

data class BudgetProgress(
    val id: BudgetId,
    val monthStartEpochDay: Long,
    val categoryId: CategoryId?,
    val limit: Money,
    val spent: Money,
    val warningPercent: Int,
)

data class ThresholdCrossing(val budgetId: BudgetId, val percent: Int)

enum class TrendGranularity { DAY, WEEK, MONTH }

data class InsightsQuery(
    val range: DateRange,
    val comparisonRange: DateRange? = null,
    val accountId: AccountId? = null,
    val granularity: TrendGranularity = TrendGranularity.DAY,
)

data class CategoryDistribution(val categoryId: CategoryId, val name: String, val amount: Money)

data class TrendPoint(val epochDay: Long, val income: Money, val expense: Money)

data class PeriodInsights(
    val income: Money,
    val expense: Money,
    val net: Money,
    val categories: List<CategoryDistribution>,
    val trend: List<TrendPoint>,
    val comparison: PeriodComparison? = null,
)

data class PeriodComparison(
    val income: Money,
    val expense: Money,
    val net: Money,
)

data class WidgetSettings(
    val showAmount: Boolean = false,
    val showCategory: Boolean = true,
    val openAction: WidgetOpenAction = WidgetOpenAction.TRANSACTION_DETAIL,
)

data class UserSettings(
    val currency: CurrencyCode = CurrencyCode.of("VND"),
    val theme: ThemeMode = ThemeMode.DARK,
    val amountVisibility: AmountVisibility = AmountVisibility.SHOWN,
    val widget: WidgetSettings = WidgetSettings(),
)
