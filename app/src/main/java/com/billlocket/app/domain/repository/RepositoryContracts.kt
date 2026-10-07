package com.billlocket.app.domain.repository

import com.billlocket.app.domain.model.*
import com.billlocket.app.domain.result.Outcome
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun observe(id: TransactionId): Flow<TransactionDetails?>
    fun observeCalendar(range: DateRange, filter: TransactionFilter = TransactionFilter()): Flow<List<CalendarDaySummary>>
    fun observeRecapTransactions(range: DateRange): Flow<List<TransactionDetails>>
    fun observeMapPhotos(filter: TransactionFilter = TransactionFilter()): Flow<List<MapPhoto>>
    fun observeWidgetPhoto(selection: WidgetSelection): Flow<WidgetPhoto?>
    suspend fun widgetPhotoCandidates(limit: Int = 20): List<WidgetPhoto>
    suspend fun page(filter: TransactionFilter, page: PageRequest): Outcome<Page<TransactionSummary>>
    suspend fun create(command: CreateTransactionCommand): Outcome<TransactionId>
    suspend fun update(command: UpdateTransactionCommand): Outcome<Unit>
    suspend fun markDeleted(id: TransactionId, deletedAt: Long): Outcome<DeletedTransaction>
    suspend fun restore(id: TransactionId): Outcome<Unit>
    suspend fun purgeDeleted(id: TransactionId): Outcome<PurgedTransactionMedia>
}

interface AccountRepository {
    fun observeAll(includeArchived: Boolean = false): Flow<List<Account>>
    fun observeDetails(id: AccountId): Flow<AccountDetails?>
    fun observeBalance(id: AccountId): Flow<Money?>
    fun observeNetWorth(): Flow<Money>
    suspend fun create(command: CreateAccountCommand): Outcome<AccountId>
    suspend fun update(command: UpdateAccountCommand): Outcome<Unit>
    suspend fun archive(id: AccountId): Outcome<Unit>
    suspend fun reorder(ids: List<AccountId>): Outcome<Unit>
}

interface CategoryRepository {
    fun observe(type: TransactionType? = null, includeArchived: Boolean = false): Flow<List<Category>>
    suspend fun create(command: CreateCategoryCommand): Outcome<CategoryId>
    suspend fun update(command: UpdateCategoryCommand): Outcome<Unit>
    suspend fun archive(id: CategoryId): Outcome<Unit>
    suspend fun reorder(type: TransactionType, ids: List<CategoryId>): Outcome<Unit>
}

interface TransferRepository {
    fun observe(id: TransferId): Flow<Transfer?>
    suspend fun page(accountId: AccountId?, range: DateRange?, page: PageRequest): Outcome<Page<Transfer>>
    suspend fun create(command: CreateTransferCommand): Outcome<TransferId>
    suspend fun update(command: UpdateTransferCommand): Outcome<Unit>
    suspend fun markDeleted(id: TransferId, deletedAt: Long): Outcome<Unit>
    suspend fun restore(id: TransferId): Outcome<Unit>
}

interface BudgetRepository {
    fun observeMonth(monthStartEpochDay: Long): Flow<List<BudgetProgress>>
    suspend fun upsert(command: SaveBudgetCommand): Outcome<BudgetId>
    suspend fun delete(id: BudgetId): Outcome<Unit>
    suspend fun pendingThresholds(monthStartEpochDay: Long): Outcome<List<ThresholdCrossing>>
    suspend fun markThresholdDelivered(id: BudgetId, percent: Int, at: Long): Outcome<Unit>
    suspend fun resetThresholdIfBelow(id: BudgetId, percent: Int): Outcome<Unit>
}

interface InsightsRepository {
    fun observePeriod(query: InsightsQuery): Flow<PeriodInsights>
}

interface SettingsRepository {
    val settings: Flow<UserSettings>
    suspend fun updateCurrency(code: String): Outcome<Unit>
    suspend fun updateTheme(mode: ThemeMode): Outcome<Unit>
    suspend fun updateAmountVisibility(value: AmountVisibility): Outcome<Unit>
    suspend fun updateWidgetSettings(value: WidgetSettings): Outcome<Unit>
}
