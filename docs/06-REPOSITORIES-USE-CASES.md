# BillLocket V1 Repository and Use-Case Contracts

Status: approved by BL-014 on 2026-09-30; provider notes revised by ADR-016/BL-040 on 2026-10-06. These are domain contracts, not persistence or UI implementation.

## Contract principles

- Interfaces live under `domain.repository`; implementations live under `data.repository` or `platform` and are bound with Hilt.
- Contracts expose domain models, IDs, filters and projections—never Room entities, DAOs, `Cursor`, Android `Context`, Compose state, map SDK types or Glance types.
- Observable reads return cold `Flow` values backed by the source of truth. Commands are `suspend` functions.
- Repository methods express business operations, not generic table CRUD. Cross-row invariants are enforced inside one implementation transaction.
- Use cases are the only entry point for ViewModels, Workers and widget coordinators. They validate business rules, coordinate repositories/ports and return typed failures.
- Image bytes and external platform behavior use ports separate from financial repositories. BL-015/BL-016 supply their implementation details.

## Shared domain types

The following value types prevent primitive mix-ups:

```kotlin
@JvmInline value class TransactionId(val value: String)
@JvmInline value class TransactionImageId(val value: String)
@JvmInline value class AccountId(val value: String)
@JvmInline value class CategoryId(val value: String)
@JvmInline value class TransferId(val value: String)
@JvmInline value class BudgetId(val value: String)

data class Money(val minorUnits: Long, val currencyCode: String)
data class DateRange(val startEpochDay: Long, val endInclusiveEpochDay: Long)
data class InstantAtZone(val epochMillis: Long, val localDateEpochDay: Long, val zoneId: String)
data class PageRequest(val limit: Int, val beforeEpochMillis: Long? = null, val beforeId: String? = null)
data class Page<T>(val items: List<T>, val next: PageRequest?)
```

IDs reject blank values. `Money` rejects non-ISO currency codes; command validators enforce positive amounts where required. `DateRange` is inclusive and ordered. Page limits are bounded (default 50, maximum 200), and cursor ordering always uses time plus ID.

Stable enums include `TransactionType`, `AccountType`, `LocationSource`, `ThemeMode`, `AmountVisibility` and `WidgetOpenAction`. Domain enums must map explicitly to persistence strings.

## Result and failure model

Commands return `Outcome<T>`:

```kotlin
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val reason: DomainFailure) : Outcome<Nothing>
}
```

`DomainFailure` is a closed hierarchy with at least:

- `Validation(field, rule)`
- `NotFound(resource, id)`
- `Conflict(reason)` for duplicate names, stale state or uniqueness conflicts
- `InactiveReference(resource, id)`
- `CurrencyMismatch(expected, actual)`
- `StorageUnavailable(operation)`
- `DatabaseUnavailable(operation)`
- `PermissionDenied(capability, permanentlyDenied)`
- `Unexpected(causeToken)`

Failures contain no localized UI strings or sensitive record content. Presentation maps them to resources. Cancellation is rethrown and never wrapped as `Unexpected`. Observation flows may terminate with a typed data exception; use cases/ViewModels catch it and expose retryable UI state while retaining the last good value where appropriate.

## Commands, filters and projections

### Transaction commands

`CreateTransactionCommand` contains type, positive money, category/account IDs, note, `InstantAtZone`, and an ordered list of `ImageInput`. `UpdateTransactionCommand` includes the target ID and complete desired financial/image state so retries are deterministic. `ImageInput` carries a draft/stable media reference and optional confirmed `PhotoLocation`; it never carries image bytes.

`TransactionFilter` supports date range, transaction type, account ID, category ID and trimmed note query. An empty filter means active transactions only. Deleted rows are visible only to dedicated undo/finalization operations.

Core projections:

- `TransactionDetails`: transaction fields and ordered images.
- `TransactionSummary`: ID, type, money, category/account labels, note preview, date and representative image.
- `CalendarDaySummary`: epoch day, income/expense totals, transaction count and bounded thumbnail list.
- `MapPhoto`: image/transaction IDs, media reference, validated coordinates, money/category/time/place preview.
- `WidgetPhoto`: transaction/image IDs, media reference and privacy-ready amount/category fields.

### Account/category/budget commands

Create/update commands carry names and editable attributes; archive commands are explicit. Reordering takes the complete ordered ID list and rejects duplicates/missing active IDs. Transfer commands carry distinct accounts, positive money and date. Budget commands carry month start, optional expense category, limit and warning threshold.

## Repository interfaces

Signatures below are normative in responsibility and data direction; names may receive minor idiomatic adjustments during implementation if the documentation and contract tests are updated together.

### `TransactionRepository`

```kotlin
interface TransactionRepository {
    fun observe(id: TransactionId): Flow<TransactionDetails?>
    fun observeCalendar(range: DateRange, filter: TransactionFilter): Flow<List<CalendarDaySummary>>
    fun observeMapPhotos(filter: TransactionFilter): Flow<List<MapPhoto>>
    fun observeWidgetPhoto(selection: WidgetSelection): Flow<WidgetPhoto?>
    suspend fun page(filter: TransactionFilter, page: PageRequest): Outcome<Page<TransactionSummary>>
    suspend fun create(command: CreateTransactionCommand): Outcome<TransactionId>
    suspend fun update(command: UpdateTransactionCommand): Outcome<Unit>
    suspend fun markDeleted(id: TransactionId, deletedAt: Long): Outcome<DeletedTransaction>
    suspend fun restore(id: TransactionId): Outcome<Unit>
    suspend fun purgeDeleted(id: TransactionId): Outcome<PurgedTransactionMedia>
}
```

Create/update commits transaction, ordered image rows and search index atomically. `markDeleted` immediately removes the record from normal queries. `restore` is valid only during the undo window. `purgeDeleted` hard-deletes metadata and returns media references for retryable cleanup; it does not delete files inside the Room transaction.

### `AccountRepository`

```kotlin
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
```

Balances derive from opening balance, active transactions and active transfers. Archived accounts remain observable historically but cannot accept new writes.

### `CategoryRepository`

```kotlin
interface CategoryRepository {
    fun observe(type: TransactionType?, includeArchived: Boolean = false): Flow<List<Category>>
    suspend fun create(command: CreateCategoryCommand): Outcome<CategoryId>
    suspend fun update(command: UpdateCategoryCommand): Outcome<Unit>
    suspend fun archive(id: CategoryId): Outcome<Unit>
    suspend fun reorder(type: TransactionType, ids: List<CategoryId>): Outcome<Unit>
}
```

Default categories use stable IDs, can be renamed/archived, and are not hard-deleted while referenced. A referenced category cannot change transaction type.

### `TransferRepository`

```kotlin
interface TransferRepository {
    fun observe(id: TransferId): Flow<Transfer?>
    suspend fun page(accountId: AccountId?, range: DateRange?, page: PageRequest): Outcome<Page<Transfer>>
    suspend fun create(command: CreateTransferCommand): Outcome<TransferId>
    suspend fun update(command: UpdateTransferCommand): Outcome<Unit>
    suspend fun markDeleted(id: TransferId, deletedAt: Long): Outcome<Unit>
    suspend fun restore(id: TransferId): Outcome<Unit>
}
```

A transfer mutation verifies two distinct active accounts, positive amount and one currency, then commits as one record. It never creates income/expense transactions.

### `BudgetRepository`

```kotlin
interface BudgetRepository {
    fun observeMonth(monthStartEpochDay: Long): Flow<List<BudgetProgress>>
    suspend fun upsert(command: SaveBudgetCommand): Outcome<BudgetId>
    suspend fun delete(id: BudgetId): Outcome<Unit>
    suspend fun pendingThresholds(monthStartEpochDay: Long): Outcome<List<ThresholdCrossing>>
    suspend fun markThresholdDelivered(id: BudgetId, percent: Int, at: Long): Outcome<Unit>
    suspend fun resetThresholdIfBelow(id: BudgetId, percent: Int): Outcome<Unit>
}
```

Progress includes active expense transactions only. Overall and category budgets may coexist. Delivery state prevents repeated notifications while allowing a later new crossing after progress falls below the threshold.

### `InsightsRepository`

```kotlin
interface InsightsRepository {
    fun observePeriod(query: InsightsQuery): Flow<PeriodInsights>
}
```

`InsightsQuery` defines date range, comparison range, optional account and bucket granularity. `PeriodInsights` contains income, expense, net, category distribution and ordered trend points. Transfers are excluded by schema/query construction.

### `SettingsRepository`

```kotlin
interface SettingsRepository {
    val settings: Flow<UserSettings>
    suspend fun updateCurrency(code: String): Outcome<Unit>
    suspend fun updateTheme(mode: ThemeMode): Outcome<Unit>
    suspend fun updateAmountVisibility(value: AmountVisibility): Outcome<Unit>
    suspend fun updateWidgetSettings(value: WidgetSettings): Outcome<Unit>
}
```

Changing currency to another code is rejected once financial rows exist until an explicit reinterpretation/migration feature is designed. The current generic string `Store` is not this contract and should not be expanded into a financial repository.

## Platform ports

These interfaces belong at the domain/application boundary but their detailed policies are owned by later tasks:

- `MediaStorePort`: stage camera/picker input, normalize/finalize app-owned media, test accessibility, reconcile and delete owned media idempotently using the BL-016 journal protocol.
- `LocationPort`: obtain current location only after contextual permission/consent, parse candidate EXIF coordinates, and validate/correct coordinates. It does not persist consent.
- `WidgetUpdatePort`: request refresh for affected widget instances after successful commits.
- `NotificationPort`: publish budget warnings with no sensitive lock-screen content by default.
- `Clock` and `IdGenerator`: injectable deterministic sources for use cases/tests.
- `BackupPort`: stream the BL-016 portable versioned export and validated replace-restore; it never exposes Room entities directly.

No provider-specific `MapRepository` is defined. Map history comes from `TransactionRepository.observeMapPhotos`; the active Google Maps Compose integration is isolated in the renderer adapter. Optional search/geocoding uses a separate `PlaceSearchPort` and requires its own approval.

## Use-case catalog

Each use case has one public `operator fun invoke`. Thin reads may delegate directly; mutations own validation and side-effect ordering.

### Transactions and images

- `CreateTransaction`: validate amount/type/category/account/currency/date/images; coordinate media finalization boundary; persist atomically; request widget refresh; return ID.
- `UpdateTransaction`: apply the same validation, calculate removed media, persist desired state, then schedule safe cleanup and widget refresh.
- `DeleteTransaction`: mark deleted and return an undo token/deadline; refresh widget immediately.
- `UndoDeleteTransaction`: restore within deadline and refresh widget.
- `FinalizeTransactionDeletion`: purge expired metadata, then idempotently delete returned owned media.
- `ObserveTransactionDetails`, `GetTransactionPage`, `SearchTransactions`.
- `ReorderTransactionImages` is handled through the complete update command unless a later UI performance need justifies a dedicated atomic method.

### Calendar and statistics

- `ObserveCalendarMonth`, `ObserveCalendarDay` and `ObservePeriodTotals` normalize civil date ranges before querying.
- `ObserveInsights` builds the selected and preceding-equivalent periods and delegates aggregate reads.

### Accounts, categories and transfers

- `CreateAccount`, `UpdateAccount`, `ArchiveAccount`, `ReorderAccounts`, `ObserveAccounts`, `ObserveAccountDetails`.
- `CreateCategory`, `UpdateCategory`, `ArchiveCategory`, `ReorderCategories`, `ObserveCategories`.
- `CreateTransfer`, `UpdateTransfer`, `DeleteTransfer`, `UndoDeleteTransfer`, `GetTransferPage`.

Archive use cases return a typed conflict when a requested action would violate active-reference rules; they never cascade financial history.

### Budgets and notifications

- `SaveMonthlyBudget`, `DeleteMonthlyBudget`, `ObserveMonthlyBudgets`.
- `EvaluateBudgetThresholds`: calculate crossings, publish eligible notifications, and mark only successfully delivered thresholds. It is idempotent and safe for WorkManager retry.

### Map, widget and settings

- `ObserveMapPhotos` validates/filter projections but contains no map SDK logic.
- `ObserveWidgetPhoto` applies eligibility and privacy settings; inaccessible media falls through to another candidate or empty state.
- `RefreshWidgetAfterMutation` coalesces refresh requests after successful data commits.
- `ObserveSettings`, `UpdateTheme`, `UpdateAmountVisibility`, `UpdateWidgetSettings`, `UpdateCurrency`.

## Mutation ordering and recovery

- Database success is never reported before its Room transaction commits.
- Widget refresh/notifications are post-commit side effects; failure schedules retry and does not roll back valid financial data.
- Media operations must be idempotent. Transaction use cases depend only on `MediaStorePort` outcomes and never assume the BL-016 private filesystem layout.
- If media succeeds but database commit fails, the use case records/schedules orphan cleanup. If database commit succeeds but a post-commit side effect fails, persisted data remains authoritative and the side effect retries.
- Use cases never hold a Room transaction open while performing file, map, notification or widget I/O.

## Hilt and implementation rules

- Repository implementations are application-scoped where they wrap singleton Room/preferences instances; use cases may be constructor-injected without forced singleton scope.
- Qualify dispatchers (`IoDispatcher`, `DefaultDispatcher`) instead of hardcoding `Dispatchers.IO` in testable classes.
- DAOs remain internal to data implementations. ViewModels never inject a DAO, database, media port or Worker directly.
- Existing `MainLog`/`Store` adapters may be removed or adapted in a later implementation task only after callers migrate; BL-014 changes no runtime wiring.

## Contract test matrix

Before a repository implementation is accepted, tests cover:

- every BR-TRX, BR-IMG, BR-LOC, BR-ACC, BR-CAT, BR-BUD, BR-WID and BR-MON rule owned by that contract;
- active/archived/deleted visibility and undo expiry;
- category/type, account activity and currency validation;
- transaction + images + FTS atomic rollback;
- transfer neutrality and account balance signs;
- stable image/account/category ordering;
- calendar timezone boundaries and deterministic pagination;
- budget threshold deduplication/reset;
- widget fallback for deleted/inaccessible media;
- cancellation propagation and typed error mapping;
- retry idempotency for media cleanup, widget refresh and notification delivery.

Fakes must implement the same observable and mutation semantics needed by ViewModel tests; they are not allowed to bypass validation in ways that make presentation tests misleading.

## Deferred decisions

- BL-032 historically moved the renderer to MapLibre/OpenFreeMap. ADR-016 later returned the active UI renderer to Google Maps Compose while preserving the same provider-neutral repository/domain contracts; neither decision enables a place-search/geocoding service.
- BL-016 finalizes `MediaStorePort` and `BackupPort` ownership, journal, cleanup and portable backup behavior.
- BL-017 defines ViewModel/UI state, effects and navigation arguments that consume these use cases.
- Concrete Room interfaces and implementations are added in later implementation tasks against [the database design](04-DATABASE.md).

Any implementation-driven signature refinement must preserve these responsibilities and update this document, tests and affected ADR together.
