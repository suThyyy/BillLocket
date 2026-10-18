package com.billlocket.app.data.repository

import com.billlocket.app.data.local.dao.*
import com.billlocket.app.data.local.entity.*
import com.billlocket.app.domain.model.*
import com.billlocket.app.domain.port.Clock
import com.billlocket.app.domain.port.IdGenerator
import com.billlocket.app.domain.repository.*
import com.billlocket.app.domain.result.DomainFailure
import com.billlocket.app.domain.result.Outcome
import com.billlocket.app.domain.validation.ValidateBudget
import com.billlocket.app.domain.validation.ValidateCreateTransfer
import com.billlocket.app.locale.FinancialNameLocalizer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val dao: AccountDao,
    private val settings: SettingsRepository,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val financialNames: FinancialNameLocalizer,
) : AccountRepository {
    override fun observeAll(includeArchived: Boolean): Flow<List<Account>> =
        combine(dao.observeAll(), financialNames.languageChanges) { rows, _ ->
            rows.filter { includeArchived || !it.isArchived }.map { row ->
                row.toDomain().copy(name = financialNames.accountName(row.id, row.name))
            }
        }

    override fun observeAllDetails(includeArchived: Boolean): Flow<List<AccountDetails>> =
        combine(dao.observeAll(), dao.observeAllBalances(includeArchived), financialNames.languageChanges) { rows, balances, _ ->
            val balancesById = balances.associateBy { it.accountId }
            rows.filter { includeArchived || !it.isArchived }.mapNotNull { row ->
                balancesById[row.id]?.let { balance ->
                    AccountDetails(
                        row.toDomain().copy(name = financialNames.accountName(row.id, row.name)),
                        Money(balance.balanceMinor, CurrencyCode.of(balance.currencyCode)),
                    )
                }
            }
        }

    override fun observeDetails(id: AccountId): Flow<AccountDetails?> =
        combine(dao.observeById(id.value), dao.observeBalance(id.value), financialNames.languageChanges) { account, balance, _ ->
            if (account == null || balance == null) null
            else AccountDetails(
                account.toDomain().copy(name = financialNames.accountName(account.id, account.name)),
                Money(balance.balanceMinor, CurrencyCode.of(balance.currencyCode)),
            )
        }

    override fun observeBalance(id: AccountId): Flow<Money?> =
        dao.observeBalance(id.value).map { it?.let { value -> Money(value.balanceMinor, CurrencyCode.of(value.currencyCode)) } }

    override fun observeNetWorth(): Flow<Money> =
        combine(dao.observeAllBalances(includeArchived = false), settings.settings) { balances, config ->
            Money(balances.sumOf { it.balanceMinor }, config.currency)
        }

    override suspend fun create(command: CreateAccountCommand): Outcome<AccountId> {
        val name = command.name.trim()
        if (name.isEmpty()) return Outcome.Failure(DomainFailure.Conflict("blank_account_name"))
        val configured = settings.settings.first().currency
        if (command.openingBalance.currency != configured) return Outcome.Failure(DomainFailure.CurrencyMismatch(configured, command.openingBalance.currency))
        return persistenceOutcome("create_account") {
            val id = AccountId(ids.nextId())
            val now = clock.nowEpochMillis()
            val sort = dao.listAll().count { !it.isArchived }
            dao.insert(AccountEntity(id.value, name, name.lowercase(), command.type.name, command.openingBalance.minorUnits, configured.value, sort, false, now, now))
            id
        }
    }

    override suspend fun update(command: UpdateAccountCommand): Outcome<Unit> {
        val current = dao.findById(command.id.value) ?: return Outcome.Failure(DomainFailure.NotFound("account", command.id.value))
        val name = command.name.trim()
        if (name.isEmpty()) return Outcome.Failure(DomainFailure.Conflict("blank_account_name"))
        return persistenceOutcome("update_account") {
            dao.update(current.copy(name = name, normalizedName = name.lowercase(), type = command.type.name, updatedAt = clock.nowEpochMillis()))
        }
    }

    override suspend fun archive(id: AccountId): Outcome<Unit> {
        val current = dao.findById(id.value) ?: return Outcome.Failure(DomainFailure.NotFound("account", id.value))
        return persistenceOutcome("archive_account") { dao.update(current.copy(isArchived = true, updatedAt = clock.nowEpochMillis())) }
    }

    override suspend fun reorder(ids: List<AccountId>): Outcome<Unit> {
        if (ids.distinct().size != ids.size) return Outcome.Failure(DomainFailure.Conflict("duplicate_account_order"))
        val active = dao.listAll().filterNot { it.isArchived }.map { it.id }.toSet()
        if (ids.map { it.value }.toSet() != active) return Outcome.Failure(DomainFailure.Conflict("incomplete_account_order"))
        return persistenceOutcome("reorder_accounts") { dao.reorder(ids.map { it.value }, clock.nowEpochMillis()) }
    }
}

@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val dao: CategoryDao,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val financialNames: FinancialNameLocalizer,
) : CategoryRepository {
    override fun observe(type: TransactionType?, includeArchived: Boolean): Flow<List<Category>> =
        combine(dao.observeAll(), financialNames.languageChanges) { rows, _ ->
            rows.filter { (type == null || it.transactionType == type.name) && (includeArchived || !it.isArchived) }.map { row ->
                row.toDomain().copy(name = financialNames.categoryName(row.id, row.name))
            }
        }

    override suspend fun create(command: CreateCategoryCommand): Outcome<CategoryId> {
        val name = command.name.trim()
        if (name.isEmpty()) return Outcome.Failure(DomainFailure.Conflict("blank_category_name"))
        return persistenceOutcome("create_category") {
            val id = CategoryId(ids.nextId())
            val now = clock.nowEpochMillis()
            val sort = dao.listAll().count { it.transactionType == command.type.name && !it.isArchived }
            dao.insert(CategoryEntity(id.value, name, name.lowercase(), command.type.name, command.colorArgb, command.iconKey, sort, false, false, now, now))
            id
        }
    }

    override suspend fun update(command: UpdateCategoryCommand): Outcome<Unit> {
        val current = dao.findById(command.id.value) ?: return Outcome.Failure(DomainFailure.NotFound("category", command.id.value))
        if (current.transactionType != command.type.name && dao.transactionReferenceCount(command.id.value) > 0) {
            return Outcome.Failure(DomainFailure.Conflict("referenced_category_type"))
        }
        val name = command.name.trim()
        if (name.isEmpty()) return Outcome.Failure(DomainFailure.Conflict("blank_category_name"))
        return persistenceOutcome("update_category") {
            dao.update(current.copy(name = name, normalizedName = name.lowercase(), transactionType = command.type.name, colorArgb = command.colorArgb, iconKey = command.iconKey, updatedAt = clock.nowEpochMillis()))
        }
    }

    override suspend fun archive(id: CategoryId): Outcome<Unit> {
        val current = dao.findById(id.value) ?: return Outcome.Failure(DomainFailure.NotFound("category", id.value))
        return persistenceOutcome("archive_category") { dao.update(current.copy(isArchived = true, updatedAt = clock.nowEpochMillis())) }
    }

    override suspend fun reorder(type: TransactionType, ids: List<CategoryId>): Outcome<Unit> {
        if (ids.distinct().size != ids.size) return Outcome.Failure(DomainFailure.Conflict("duplicate_category_order"))
        val active = dao.listAll().filter { it.transactionType == type.name && !it.isArchived }.map { it.id }.toSet()
        if (ids.map { it.value }.toSet() != active) return Outcome.Failure(DomainFailure.Conflict("incomplete_category_order"))
        return persistenceOutcome("reorder_categories") { dao.reorder(ids.map { it.value }, clock.nowEpochMillis()) }
    }
}

@Singleton
class TransferRepositoryImpl @Inject constructor(
    private val dao: TransferDao,
    private val accountDao: AccountDao,
    private val settings: SettingsRepository,
    private val ids: IdGenerator,
    private val clock: Clock,
) : TransferRepository {
    private val validator = ValidateCreateTransfer()

    override fun observe(id: TransferId): Flow<Transfer?> = dao.observeById(id.value).map { it?.toDomain() }

    override suspend fun page(accountId: AccountId?, range: DateRange?, page: PageRequest): Outcome<Page<Transfer>> =
        persistenceOutcome("page_transfers") {
            val rows = dao.pageFiltered(
                accountId = accountId?.value,
                fromDay = range?.startEpochDay,
                toDay = range?.endInclusiveEpochDay,
                beforeEpochMillis = page.before?.epochMillis,
                beforeId = page.before?.id,
                limit = page.limit + 1,
            )
            val selected = rows.take(page.limit)
            val next = selected.lastOrNull()?.takeIf { rows.size > page.limit }
                ?.let { PageRequest(page.limit, PageCursor(it.occurredAt, it.id)) }
            Page(selected.map { it.toDomain() }, next)
        }

    override suspend fun create(command: CreateTransferCommand): Outcome<TransferId> {
        val fresh = fresh(command) ?: return Outcome.Failure(DomainFailure.NotFound("account", "transfer_reference"))
        when (val valid = validator(fresh, settings.settings.first().currency)) {
            is Outcome.Failure -> return valid
            is Outcome.Success -> Unit
        }
        return persistenceOutcome("create_transfer") {
            val id = TransferId(ids.nextId())
            val now = clock.nowEpochMillis()
            dao.insert(fresh.toEntity(id, now, now))
            id
        }
    }

    override suspend fun update(command: UpdateTransferCommand): Outcome<Unit> {
        val current = dao.findById(command.id.value) ?: return Outcome.Failure(DomainFailure.NotFound("transfer", command.id.value))
        val fresh = fresh(command.value) ?: return Outcome.Failure(DomainFailure.NotFound("account", "transfer_reference"))
        when (val valid = validator(fresh, settings.settings.first().currency)) {
            is Outcome.Failure -> return valid
            is Outcome.Success -> Unit
        }
        return persistenceOutcome("update_transfer") { dao.update(fresh.toEntity(command.id, current.createdAt, clock.nowEpochMillis()).copy(deletedAt = current.deletedAt)) }
    }

    override suspend fun markDeleted(id: TransferId, deletedAt: Long): Outcome<Unit> =
        persistenceOutcome("delete_transfer") { check(dao.softDelete(id.value, deletedAt) == 1) }

    override suspend fun restore(id: TransferId): Outcome<Unit> =
        persistenceOutcome("restore_transfer") { check(dao.restore(id.value, clock.nowEpochMillis()) == 1) }

    private suspend fun fresh(command: CreateTransferCommand): CreateTransferCommand? {
        val from = accountDao.findById(command.from.id.value) ?: return null
        val to = accountDao.findById(command.to.id.value) ?: return null
        return command.copy(
            from = AccountRef(AccountId(from.id), AccountType.valueOf(from.type), !from.isArchived, CurrencyCode.of(from.currencyCode)),
            to = AccountRef(AccountId(to.id), AccountType.valueOf(to.type), !to.isArchived, CurrencyCode.of(to.currencyCode)),
        )
    }
}

private fun CreateTransferCommand.toEntity(id: TransferId, createdAt: Long, updatedAt: Long) =
    TransferEntity(id.value, from.id.value, to.id.value, amount.minorUnits, amount.currency.value, note?.trim()?.takeIf { it.isNotEmpty() }, occurred.epochMillis, occurred.localDateEpochDay, occurred.zoneId, createdAt, updatedAt, null)

@Singleton
class BudgetRepositoryImpl @Inject constructor(
    private val dao: BudgetDao,
    private val projectionDao: ProjectionDao,
    private val categoryDao: CategoryDao,
    private val settings: SettingsRepository,
    private val ids: IdGenerator,
    private val clock: Clock,
) : BudgetRepository {
    private val validator = ValidateBudget()

    override fun observeMonth(monthStartEpochDay: Long): Flow<List<BudgetProgress>> =
        combine(dao.observeMonth(monthStartEpochDay), projectionDao.observeFinancialVersion()) { budgets, _ ->
            val next = java.time.LocalDate.ofEpochDay(monthStartEpochDay).plusMonths(1).toEpochDay()
            val today = java.time.Instant.ofEpochMilli(clock.nowEpochMillis()).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
            val maxDay = minOf(next - 1, today)
            val progress = projectionDao.budgetProgress(monthStartEpochDay, next, maxDay).associateBy { it.budgetId }
            budgets.map { budget ->
                val row = requireNotNull(progress[budget.id])
                BudgetProgress(BudgetId(budget.id), budget.monthStartEpochDay, budget.categoryId?.let(::CategoryId), Money(row.limitMinor, CurrencyCode.of(row.currencyCode)), Money(row.spentMinor, CurrencyCode.of(row.currencyCode)), row.warningPercent)
            }
        }

    override suspend fun upsert(command: SaveBudgetCommand): Outcome<BudgetId> {
        val freshCategory = command.category?.let { ref ->
            categoryDao.findById(ref.id.value)?.let { CategoryRef(CategoryId(it.id), TransactionType.valueOf(it.transactionType), !it.isArchived) }
                ?: return Outcome.Failure(DomainFailure.NotFound("category", ref.id.value))
        }
        val fresh = command.copy(category = freshCategory)
        when (val valid = validator(fresh, settings.settings.first().currency)) {
            is Outcome.Failure -> return valid
            is Outcome.Success -> Unit
        }
        return persistenceOutcome("save_budget") {
            val existing = dao.observeMonth(command.monthStartEpochDay).first().firstOrNull { it.categoryId == command.category?.id?.value }
            val id = existing?.id?.let(::BudgetId) ?: BudgetId(ids.nextId())
            val now = clock.nowEpochMillis()
            val entity = BudgetEntity(id.value, command.monthStartEpochDay, command.category?.id?.value, command.category?.id?.value ?: "OVERALL", command.limit.minorUnits, command.limit.currency.value, command.warningPercent, existing?.createdAt ?: now, now)
            if (existing == null) dao.insert(entity) else dao.update(entity)
            id
        }
    }

    override suspend fun delete(id: BudgetId): Outcome<Unit> {
        val budget = dao.findById(id.value) ?: return Outcome.Failure(DomainFailure.NotFound("budget", id.value))
        return persistenceOutcome("delete_budget") { dao.delete(budget) }
    }

    override suspend fun pendingThresholds(monthStartEpochDay: Long): Outcome<List<ThresholdCrossing>> =
        persistenceOutcome("budget_thresholds") {
            val next = java.time.LocalDate.ofEpochDay(monthStartEpochDay).plusMonths(1).toEpochDay()
            val today = java.time.Instant.ofEpochMilli(clock.nowEpochMillis()).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
            val maxDay = minOf(next - 1, today)
            projectionDao.budgetProgress(monthStartEpochDay, next, maxDay).flatMap { progress ->
                val delivered = dao.thresholdEvents(progress.budgetId).map { it.thresholdPercent }.toSet()
                val percent = if (progress.limitMinor == 0L) 0 else ((progress.spentMinor * 100) / progress.limitMinor).coerceAtMost(100).toInt()
                listOf(progress.warningPercent, 100).filter { percent >= it && it !in delivered }.map { ThresholdCrossing(BudgetId(progress.budgetId), it) }
            }
        }

    override suspend fun markThresholdDelivered(id: BudgetId, percent: Int, at: Long): Outcome<Unit> =
        persistenceOutcome("deliver_budget_threshold") { dao.recordThreshold(BudgetThresholdEventEntity(id.value, percent, at)); Unit }

    override suspend fun resetThresholdIfBelow(id: BudgetId, percent: Int): Outcome<Unit> =
        persistenceOutcome("reset_budget_threshold") { dao.clearThreshold(id.value, percent); Unit }
}

@Singleton
class InsightsRepositoryImpl @Inject constructor(
    private val dao: ProjectionDao,
    private val settings: SettingsRepository,
    private val financialNames: FinancialNameLocalizer,
) : InsightsRepository {
    override fun observePeriod(query: InsightsQuery): Flow<PeriodInsights> =
        combine(dao.observeFinancialVersion(), financialNames.languageChanges) { _, _ ->
            val currency = settings.settings.first().currency
            val accountId = query.accountId?.value
            val totals = dao.periodTotals(query.range.startEpochDay, query.range.endInclusiveEpochDay, accountId)
            val categories = dao.categoryTotals("EXPENSE", query.range.startEpochDay, query.range.endInclusiveEpochDay, accountId)
            val days = dao.dayTotals(query.range.startEpochDay, query.range.endInclusiveEpochDay, accountId)
            val comparison = query.comparisonRange?.let { range ->
                dao.periodTotals(range.startEpochDay, range.endInclusiveEpochDay, accountId).let { previous ->
                    PeriodComparison(
                        Money(previous.incomeMinor, currency),
                        Money(previous.expenseMinor, currency),
                        Money(previous.incomeMinor - previous.expenseMinor, currency),
                    )
                }
            }
            PeriodInsights(
                Money(totals.incomeMinor, currency), Money(totals.expenseMinor, currency), Money(totals.incomeMinor - totals.expenseMinor, currency),
                categories.map {
                    CategoryDistribution(
                        CategoryId(it.categoryId),
                        financialNames.categoryName(it.categoryId, it.categoryName),
                        Money(it.totalMinor, currency),
                    )
                },
                days.map { TrendPoint(it.localDateEpochDay, Money(it.incomeMinor, currency), Money(it.expenseMinor, currency)) },
                comparison,
            )
        }
}
