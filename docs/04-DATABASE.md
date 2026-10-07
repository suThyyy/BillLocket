# BillLocket V1 Room Database Design

Status: approved by BL-013 on 2026-09-30. This is the implementation contract for the first Room schema; it does not add runtime persistence code by itself.

## Conventions

- Database file: `billlocket.db`; initial schema version: `1`.
- Table and column names use `snake_case`; Kotlin persistence types use the `Entity` suffix.
- Primary IDs are application-generated UUID strings. They remain stable across export/import.
- Money is a signed 64-bit integer in minor units plus ISO 4217 `currency_code`. Persisted floating-point money is forbidden. Positive input amounts are required; direction is represented by transaction/transfer semantics.
- Instants use UTC epoch milliseconds. User-facing transaction dates also persist `local_date_epoch_day` and `zone_id` so calendar grouping does not change when the device timezone changes.
- Booleans are stored as SQLite integers. Enums use explicit uppercase strings and strict converters; ordinal persistence is forbidden.
- User text is trimmed on write. Empty optional text becomes null.
- Foreign keys are enabled. All compound writes and FTS synchronization run in a Room `@Transaction`.

## Entity overview

```text
accounts 1 ---- * transactions * ---- 1 categories
    |                  |
    |                  * transaction_images
    |                         (optional location per row)
    |
    +---- * transfers * ---- accounts

categories 1 ---- * budgets ---- * budget_threshold_events
transactions 1 ---- 1 transaction_search (FTS)
```

Settings, including the selected single V1 currency, theme and widget privacy, are preferences rather than Room entities. Repository validation ensures all active monetary rows use the configured V1 currency. Changing to a different currency after financial records exist requires a later explicit migration/reinterpretation flow; V1 must not silently relabel values.

## Tables

### `accounts`

| Column | Type | Constraints / meaning |
| --- | --- | --- |
| `id` | TEXT | Primary key, UUID |
| `name` | TEXT | Non-empty display name |
| `normalized_name` | TEXT | Lowercased/trimmed lookup value |
| `type` | TEXT | `CASH`, `BANK`, `EWALLET` |
| `opening_balance_minor` | INTEGER | Exact opening balance; may be negative |
| `currency_code` | TEXT | Three-letter configured currency |
| `sort_order` | INTEGER | Non-negative stable order |
| `is_archived` | INTEGER | Default false |
| `created_at` / `updated_at` | INTEGER | Epoch milliseconds |

Indices: unique `normalized_name`; `(is_archived, sort_order)`.

Referenced accounts are never hard-deleted. Archiving preserves history and blocks new transactions/transfers in the repository.

### `categories`

| Column | Type | Constraints / meaning |
| --- | --- | --- |
| `id` | TEXT | Primary key, UUID; default-category IDs are stable constants |
| `name` | TEXT | Non-empty display name |
| `normalized_name` | TEXT | Lowercased/trimmed lookup value |
| `transaction_type` | TEXT | `INCOME` or `EXPENSE` |
| `color_argb` | INTEGER | Packed ARGB color |
| `icon_key` | TEXT? | App-owned icon identifier, not a resource integer |
| `sort_order` | INTEGER | Non-negative within type |
| `is_default` | INTEGER | Default false |
| `is_archived` | INTEGER | Default false; hiding a default category uses this flag |
| `created_at` / `updated_at` | INTEGER | Epoch milliseconds |

Indices: unique `(transaction_type, normalized_name)`; `(transaction_type, is_archived, sort_order)`.

Referenced categories are not hard-deleted. Type changes are rejected once referenced; this preserves transaction/category type consistency.

### `transactions`

| Column | Type | Constraints / meaning |
| --- | --- | --- |
| `id` | TEXT | Primary key, UUID |
| `type` | TEXT | `INCOME` or `EXPENSE` |
| `amount_minor` | INTEGER | Greater than zero |
| `currency_code` | TEXT | Must match the V1 configured currency |
| `category_id` | TEXT | FK `categories.id`, `ON DELETE RESTRICT` |
| `account_id` | TEXT | FK `accounts.id`, `ON DELETE RESTRICT` |
| `note` | TEXT? | Optional user note |
| `occurred_at` | INTEGER | UTC epoch milliseconds |
| `local_date_epoch_day` | INTEGER | Civil date selected by user |
| `zone_id` | TEXT | IANA zone used to interpret/display the instant |
| `created_at` / `updated_at` | INTEGER | Epoch milliseconds |
| `deleted_at` | INTEGER? | Null when active; timestamp during undo window |

Indices:

- `(deleted_at, local_date_epoch_day)` for calendar/day totals.
- `(account_id, deleted_at, local_date_epoch_day)` for balances and account filters.
- `(category_id, deleted_at, local_date_epoch_day)` for category statistics/budgets.
- `(type, deleted_at, local_date_epoch_day)` for income/expense range summaries.
- `(occurred_at, id)` for deterministic chronological paging.

Every write validates positive amount, active account/category and matching transaction/category type. All normal projections require `deleted_at IS NULL`.

Deletion is two-phase: set `deleted_at` to enable the short undo period, then hard-delete after expiry. Hard deletion cascades image/search rows; media files are cleaned through the BL-016 media workflow only after ownership is gone.

### `transaction_images`

| Column | Type | Constraints / meaning |
| --- | --- | --- |
| `id` | TEXT | Primary key, UUID |
| `transaction_id` | TEXT | FK `transactions.id`, `ON DELETE CASCADE` |
| `content_uri` | TEXT | Stable app-owned media reference; no image blob |
| `position` | INTEGER | Non-negative display order |
| `mime_type` | TEXT | Supported image MIME type |
| `width_px` / `height_px` | INTEGER | Positive decoded dimensions |
| `size_bytes` | INTEGER | Non-negative size for storage reporting |
| `captured_at` | INTEGER? | Original capture time when known |
| `latitude` / `longitude` | REAL? | Both null or both valid |
| `accuracy_meters` | REAL? | Positive when known |
| `place_name` | TEXT? | User-confirmed display label |
| `location_source` | TEXT? | `CURRENT`, `EXIF`, or `MANUAL`; null without location |
| `location_confirmed_at` | INTEGER? | Required for persisted coordinates |
| `created_at` / `updated_at` | INTEGER | Epoch milliseconds |

Indices: unique `(transaction_id, position)`; unique `content_uri`; `(transaction_id)`; `(latitude, longitude)` for candidate map rows.

Checks require latitude and longitude to be both null or both present, latitude in -90..90, longitude in -180..180, and confirmed/source metadata when coordinates exist. Reordering uses a collision-safe two-phase position update inside one transaction. Removing coordinates clears accuracy, place, source and confirmation together.

### `transfers`

| Column | Type | Constraints / meaning |
| --- | --- | --- |
| `id` | TEXT | Primary key, UUID |
| `from_account_id` / `to_account_id` | TEXT | FKs `accounts.id`, `ON DELETE RESTRICT`; must differ |
| `amount_minor` | INTEGER | Greater than zero |
| `currency_code` | TEXT | Configured V1 currency |
| `note` | TEXT? | Optional note |
| `occurred_at` | INTEGER | UTC epoch milliseconds |
| `local_date_epoch_day` | INTEGER | Civil date |
| `zone_id` | TEXT | IANA zone |
| `created_at` / `updated_at` | INTEGER | Epoch milliseconds |
| `deleted_at` | INTEGER? | Null when active |

Indices: `(from_account_id, deleted_at, local_date_epoch_day)`, `(to_account_id, deleted_at, local_date_epoch_day)`, and `(occurred_at, id)`.

Transfers are separate from transactions, so global income/expense statistics and budgets cannot count them accidentally. Balance projections subtract active outgoing transfers and add active incoming transfers.

### `budgets`

| Column | Type | Constraints / meaning |
| --- | --- | --- |
| `id` | TEXT | Primary key, UUID |
| `month_start_epoch_day` | INTEGER | First civil day of budget month |
| `category_id` | TEXT? | Null for overall budget; otherwise FK `categories.id`, `ON DELETE RESTRICT` |
| `scope_key` | TEXT | `OVERALL` or the category UUID; repository-controlled uniqueness key |
| `limit_minor` | INTEGER | Greater than zero |
| `currency_code` | TEXT | Configured V1 currency |
| `warning_percent` | INTEGER | 1..99, default 80 |
| `created_at` / `updated_at` | INTEGER | Epoch milliseconds |

Indices: unique `(month_start_epoch_day, scope_key)` and `(category_id, month_start_epoch_day)`.

The repository writes `scope_key = OVERALL` exactly when `category_id` is null and otherwise copies the category UUID. The non-null key gives Room an ordinary unique index that enforces one overall budget and one budget per category/month without relying on SQLite's nullable-unique behavior. Contract tests verify key/category consistency.

Only expense categories are accepted for category budgets. Progress queries sum active expense transactions by `local_date_epoch_day`.

### `budget_threshold_events`

| Column | Type | Constraints / meaning |
| --- | --- | --- |
| `budget_id` | TEXT | FK `budgets.id`, `ON DELETE CASCADE` |
| `threshold_percent` | INTEGER | The delivered warning/exceeded threshold |
| `delivered_at` | INTEGER | Epoch milliseconds |

Primary key: `(budget_id, threshold_percent)`. Deleting the row after progress falls below the threshold allows a later crossing to notify again, satisfying BR-BUD-05.

### `transaction_search` (FTS4)

An FTS4 table contains `transaction_id` (not indexed) and normalized `note`. It is maintained in the same repository transaction as `transactions`; soft-deleted hits are removed or filtered by joining `transactions.deleted_at IS NULL`. Search results always join back to the authoritative transaction row and apply requested date/type/category/account filters.

## Required DAO projections

Entities are not UI contracts. DAOs may return dedicated projections for:

- transaction detail with ordered images;
- paged transaction summaries;
- calendar day/month totals and representative thumbnails;
- account balance from opening balance, transactions and transfers;
- income/expense/net period statistics and category breakdown;
- budget consumption;
- map markers from active transactions and confirmed valid image coordinates;
- widget candidates with existing image metadata.

All queries define deterministic ordering with ID as a final tie-breaker. Aggregate SQL uses `COALESCE(..., 0)` and 64-bit results. Repository/use-case tests cover overflow handling before presentation formatting.

## Atomic operations

The following are mandatory Room transactions:

- create/edit/delete/restore a transaction together with images and FTS row;
- reorder images;
- create/edit/delete/restore a transfer;
- archive checks that depend on reference state;
- import batches and default-data seeding;
- threshold event state changes coupled to evaluated progress where needed.

File copying/deletion, EXIF parsing, image decoding, widget rendering and notification delivery never run while a database transaction is open.

## Migration policy

1. Export Room JSON schemas to a tracked `app/schemas` directory for every released database version.
2. Enable identity-hash validation and add `room-testing` migration tests when persistence implementation begins.
3. Every supported version has an explicit path to the latest version. `fallbackToDestructiveMigration` is forbidden.
4. Auto-migrations are allowed only for additive changes whose generated SQL and exported schema have been reviewed. Renames, splits, data transforms, constraints, FTS or partial-index changes use manual migrations.
5. Manual migrations recreate affected tables when SQLite cannot enforce the new constraint in place, copy/validate data, recreate indices/triggers, and run foreign-key checks.
6. Each migration test creates the oldest supported schema, inserts representative active/archived/deleted rows and per-image location data, migrates, validates schema, then verifies values and invariants.
7. Fresh creation and migrated creation must both install the budget uniqueness index and any required FTS synchronization mechanism.
8. Database downgrade is unsupported; backup/import compatibility is versioned separately in BL-016.

Version 1 has no migration object because it is the initial schema. Default categories and the initial account are seeded idempotently with stable IDs after database creation, using one transaction. Seed logic never overwrites user edits.

## Retention and integrity rules

- Accounts/categories with history are archived rather than deleted.
- Transaction hard-delete cascades database-owned image/search rows; media cleanup remains independently retryable.
- A transaction cannot reference archived account/category at creation, but historical rows remain readable after later archival.
- Location is valid only on an image row and only after confirmation.
- Database constraints protect structural integrity; repository/use-case validation provides domain-specific error messages.
- Import disables no constraints permanently: data is validated before commit and `foreign_key_check` must pass.

## Implementation checklist for the persistence task

- Add coherent `room-runtime`, `room-ktx`, `room-compiler` and `room-testing` versions; align the active KSP plugin before compiling entities.
- Implement entities/converters/DAOs/database and Hilt provider in `data/local`.
- Configure schema export and commit version-1 JSON.
- Add constraint, projection, transaction, FTS and fresh-schema tests before feature repositories rely on the database.
- Keep Room types inside data; map them to the domain contracts defined by BL-014.
