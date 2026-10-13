package com.billlocket.app.data.repository

import com.billlocket.app.data.local.dao.AccountDao
import com.billlocket.app.data.local.dao.CategoryDao
import com.billlocket.app.data.local.dao.TransactionDao
import com.billlocket.app.data.local.dao.ProjectionDao
import com.billlocket.app.data.local.entity.TransactionEntity
import com.billlocket.app.data.local.entity.TransactionImageEntity
import com.billlocket.app.data.local.entity.TransactionSearchEntity
import com.billlocket.app.domain.model.*
import com.billlocket.app.domain.port.Clock
import com.billlocket.app.domain.port.IdGenerator
import com.billlocket.app.domain.repository.SettingsRepository
import com.billlocket.app.domain.repository.TransactionRepository
import com.billlocket.app.domain.result.DomainFailure
import com.billlocket.app.domain.result.Outcome
import com.billlocket.app.domain.validation.ValidateCreateTransaction
import com.billlocket.app.locale.FinancialNameLocalizer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val projectionDao: ProjectionDao,
    private val settingsRepository: SettingsRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
    private val financialNames: FinancialNameLocalizer,
) : TransactionRepository {
    private val validator = ValidateCreateTransaction()

    override fun observe(id: TransactionId): Flow<TransactionDetails?> =
        transactionDao.observeDetail(id.value).map { it?.toDomain() }

    override fun observeCalendar(range: DateRange, filter: TransactionFilter): Flow<List<CalendarDaySummary>> =
        transactionDao.observeCalendarRange(
            range.startEpochDay,
            range.endInclusiveEpochDay,
            filter.type?.name,
            filter.accountId?.value,
        ).map { rows ->
            val currency = settingsRepository.settings.first().currency
            rows.map { it.toDomain() }
                .filter { filter.categoryId == null || it.categoryId == filter.categoryId }
                .groupBy { it.occurred.localDateEpochDay }
                .map { (day, transactions) ->
                    CalendarDaySummary(
                        epochDay = day,
                        income = Money(transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount.minorUnits }, currency),
                        expense = Money(transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount.minorUnits }, currency),
                        transactionCount = transactions.size,
                        thumbnailUris = transactions.flatMap { it.images }.sortedBy { it.position }.map { it.mediaUri }.take(4),
                    )
                }
                .sortedBy { it.epochDay }
        }

    override fun observeRecapTransactions(range: DateRange): Flow<List<TransactionDetails>> =
        transactionDao.observeCalendarRange(
            range.startEpochDay,
            range.endInclusiveEpochDay,
            type = null,
            accountId = null,
        ).map { rows ->
            val today = java.time.Instant.ofEpochMilli(clock.nowEpochMillis())
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
                .toEpochDay()
            rows.map { it.toDomain() }
                .filter { it.occurred.localDateEpochDay <= today }
                .sortedWith(
                    compareByDescending<TransactionDetails> { it.occurred.epochMillis }
                        .thenByDescending { it.id.value },
                )
        }

    override fun observeMapPhotos(filter: TransactionFilter): Flow<List<MapPhoto>> =
        transactionDao.observeCalendarRange(
            filter.range?.startEpochDay ?: -365_243_219_162L,
            filter.range?.endInclusiveEpochDay ?: 365_241_780_471L,
            filter.type?.name,
            filter.accountId?.value,
        ).combine(financialNames.languageChanges) { rows, _ ->
            val categoryNames = categoryDao.listAll().associate { it.id to financialNames.categoryName(it.id, it.name) }
            rows.map { it.toDomain() }
                .filter { it.matches(filter) }
                .flatMap { transaction ->
                    transaction.images.mapNotNull { image ->
                        image.location?.let { location ->
                            MapPhoto(
                                image.id,
                                transaction.id,
                                image.mediaUri,
                                location,
                                transaction.amount,
                                categoryNames[transaction.categoryId.value].orEmpty(),
                                transaction.occurred,
                            )
                        }
                    }
                }
        }

    override fun observeWidgetPhoto(selection: WidgetSelection): Flow<WidgetPhoto?> =
        transactionDao.observeActive().combine(financialNames.languageChanges) { rows, _ ->
            val now = clock.nowEpochMillis()
            val categoryNames = categoryDao.listAll().associate { it.id to financialNames.categoryName(it.id, it.name) }
            rows.asSequence()
                .map { it.toDomain() }
                .filter { it.occurred.epochMillis <= now }
                .filter { selection.transactionId == null || it.id == selection.transactionId }
                .mapNotNull { transaction ->
                    transaction.images.firstOrNull()?.let { image ->
                        WidgetPhoto(
                            transaction.id,
                            image.id,
                            image.mediaUri,
                            transaction.amount,
                            categoryNames[transaction.categoryId.value].orEmpty(),
                        )
                    }
                }
                .firstOrNull()
        }

    override suspend fun widgetPhotoCandidates(limit: Int): List<WidgetPhoto> {
        val now = clock.nowEpochMillis()
        val categoryIdsByTransaction = transactionDao.listActive()
            .associate { it.transaction.id to it.transaction.categoryId }
        return projectionDao.widgetCandidates(limit.coerceIn(1, 100))
            .filter { it.occurredAt <= now }
            .map { row ->
                WidgetPhoto(
                    transactionId = TransactionId(row.transactionId),
                    imageId = TransactionImageId(row.imageId),
                    mediaUri = row.contentUri,
                    amount = Money(row.amountMinor, CurrencyCode.of(row.currencyCode)),
                    categoryName = financialNames.categoryName(
                        categoryIdsByTransaction[row.transactionId].orEmpty(),
                        row.categoryName,
                    ),
                )
            }
    }

    override suspend fun page(filter: TransactionFilter, page: PageRequest): Outcome<Page<TransactionSummary>> =
        persistenceOutcome("page_transactions") {
            val accounts = accountDao.listAll().associate { it.id to financialNames.accountName(it.id, it.name) }
            val categories = categoryDao.listAll().associate { it.id to financialNames.categoryName(it.id, it.name) }
            val matching =
                transactionDao.listActive().map { it.toDomain() }
                    .filter { it.matches(filter) }
                    .filter { row ->
                        page.before?.let { cursor ->
                            row.occurred.epochMillis < cursor.epochMillis ||
                                (row.occurred.epochMillis == cursor.epochMillis && row.id.value < cursor.id)
                        } ?: true
                    }
                    .sortedWith(compareByDescending<TransactionDetails> { it.occurred.epochMillis }.thenByDescending { it.id.value })
            val selected = matching.take(page.limit)
            val summaries = selected.map { it.toSummary(accounts, categories) }
            val next =
                if (matching.size > page.limit && selected.isNotEmpty()) {
                    val last = selected.last()
                    PageRequest(page.limit, PageCursor(last.occurred.epochMillis, last.id.value))
                } else null
            Page(summaries, next)
        }

    override suspend fun create(command: CreateTransactionCommand): Outcome<TransactionId> {
        val fresh = freshCommand(command) ?: return missingReference(command)
        when (val valid = validator(fresh, settingsRepository.settings.first().currency, clock.nowEpochMillis())) {
            is Outcome.Failure -> return valid
            is Outcome.Success -> Unit
        }
        return persistenceOutcome("create_transaction") {
            val id = TransactionId(idGenerator.nextId())
            val now = clock.nowEpochMillis()
            transactionDao.create(fresh.toEntity(id, now, now), fresh.toImageEntities(id, now), TransactionSearchEntity(id.value, fresh.note.orEmpty()))
            id
        }
    }

    override suspend fun update(command: UpdateTransactionCommand): Outcome<Unit> {
        val existing = transactionDao.findEntity(command.id.value)
            ?: return Outcome.Failure(DomainFailure.NotFound("transaction", command.id.value))
        val freshCreate = freshCommand(command.asCreateCommand()) ?: return missingReference(command.asCreateCommand())
        when (val valid = validator(freshCreate, settingsRepository.settings.first().currency, clock.nowEpochMillis())) {
            is Outcome.Failure -> return valid
            is Outcome.Success -> Unit
        }
        return persistenceOutcome("update_transaction") {
            val now = clock.nowEpochMillis()
            transactionDao.replace(
                freshCreate.toEntity(command.id, existing.createdAt, now).copy(deletedAt = existing.deletedAt),
                freshCreate.toImageEntities(command.id, now),
                TransactionSearchEntity(command.id.value, freshCreate.note.orEmpty()),
            )
        }
    }

    override suspend fun markDeleted(id: TransactionId, deletedAt: Long): Outcome<DeletedTransaction> =
        persistenceOutcome("delete_transaction") {
            check(transactionDao.softDelete(id.value, deletedAt) == 1)
            DeletedTransaction(id, deletedAt)
        }

    override suspend fun restore(id: TransactionId): Outcome<Unit> =
        persistenceOutcome("restore_transaction") { check(transactionDao.restore(id.value, clock.nowEpochMillis()) == 1) }

    override suspend fun purgeDeleted(id: TransactionId): Outcome<PurgedTransactionMedia> {
        val row = transactionDao.detail(id.value) ?: return Outcome.Failure(DomainFailure.NotFound("transaction", id.value))
        if (row.transaction.deletedAt == null) return Outcome.Failure(DomainFailure.Conflict("transaction_not_deleted"))
        return persistenceOutcome("purge_transaction") {
            val media = row.images.map { it.contentUri }
            check(transactionDao.hardDelete(id.value) == 1)
            PurgedTransactionMedia(media)
        }
    }

    private suspend fun freshCommand(command: CreateTransactionCommand): CreateTransactionCommand? {
        val account = accountDao.findById(command.account.id.value) ?: return null
        val category = categoryDao.findById(command.category.id.value) ?: return null
        return command.copy(
            account = AccountRef(AccountId(account.id), AccountType.valueOf(account.type), !account.isArchived, CurrencyCode.of(account.currencyCode)),
            category = CategoryRef(CategoryId(category.id), TransactionType.valueOf(category.transactionType), !category.isArchived),
        )
    }

    private suspend fun missingReference(command: CreateTransactionCommand): Outcome.Failure =
        if (accountDao.findById(command.account.id.value) == null) {
            Outcome.Failure(DomainFailure.NotFound("account", command.account.id.value))
        } else Outcome.Failure(DomainFailure.NotFound("category", command.category.id.value))
}

private fun TransactionDetails.matches(filter: TransactionFilter): Boolean =
    (filter.range == null || occurred.localDateEpochDay in filter.range.startEpochDay..filter.range.endInclusiveEpochDay) &&
        (filter.type == null || type == filter.type) &&
        (filter.accountId == null || accountId == filter.accountId) &&
        (filter.categoryId == null || categoryId == filter.categoryId) &&
        (filter.noteQuery.isNullOrBlank() || note.orEmpty().contains(filter.noteQuery.trim(), ignoreCase = true))

private fun TransactionDetails.toSummary(accounts: Map<String, String>, categories: Map<String, String>) =
    TransactionSummary(id, type, amount, categories[categoryId.value].orEmpty(), accounts[accountId.value].orEmpty(), note, occurred, images.firstOrNull()?.mediaUri)

private fun CreateTransactionCommand.toEntity(id: TransactionId, createdAt: Long, updatedAt: Long) =
    TransactionEntity(
        id.value, type.name, amount.minorUnits, amount.currency.value, category.id.value, account.id.value,
        note?.trim()?.takeIf { it.isNotEmpty() }, occurred.epochMillis, occurred.localDateEpochDay, occurred.zoneId,
        createdAt, updatedAt, null,
    )

private fun CreateTransactionCommand.toImageEntities(transactionId: TransactionId, now: Long) =
    images.mapIndexed { position, image ->
        TransactionImageEntity(
            image.id.value, transactionId.value, image.mediaUri, position, image.mimeType, image.widthPx, image.heightPx,
            image.sizeBytes, image.capturedAt, image.location?.point?.latitude, image.location?.point?.longitude,
            image.location?.accuracyMeters, image.location?.placeName, image.location?.source?.name,
            image.location?.confirmedAtEpochMillis, now, now,
        )
    }
