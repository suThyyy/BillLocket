# BillLocket V1 Architecture

Status: approved by BL-012 on 2026-09-30; active map adapter revised by ADR-016/BL-040 on 2026-10-06.

## Goals and constraints

BillLocket is a single-user, offline-first Android application. Room is the source of truth for financial records and media metadata. Image bytes live in app-managed storage; Room stores stable references and metadata. Core capture, CRUD, calendar, statistics, budgets, map history and widget behavior must not require an account or network.

The architecture optimizes for correctness, testability and a course-project-sized codebase. It does not introduce a generic framework, remote synchronization, multi-module Gradle layout or event bus.

## Runtime shape

```text
Compose screens / Glance widget
            |
      ViewModels / presenters
            |
          use cases
            |
    repository interfaces (domain)
            |
 repository implementations (data)
      /         |          \
   Room     media store   preferences
      |
 WorkManager observes/enqueues durable follow-up work
```

The dependency direction points inward: platform and data implementations depend on domain contracts; domain code does not depend on Android, Compose, Room, CameraX, map SDKs or Glance.

## Layers and package boundaries

### `presentation`

- Compose screens, reusable UI, navigation adapters, ViewModels and immutable UI state.
- ViewModels call use cases and expose `StateFlow`; composables render state and emit user actions.
- Composables do not call DAOs, repositories, file APIs or WorkManager directly.
- Navigation passes stable IDs and small filters, never full mutable entities or image bytes.
- One-off UI effects use an explicit effect stream or callback; durable state remains observable and replayable.

### `domain`

- Plain Kotlin models, value objects, repository interfaces and focused use cases.
- Contains validation and financial rules that must be testable with JUnit without Android.
- Money uses integer minor units. Time is represented as epoch milliseconds at persistence boundaries and converted using an explicit user/device zone for calendar grouping.
- Domain errors use typed results or exceptions with stable meanings; UI text/resources do not live here.

### `data`

- Room entities, DAOs, database, type converters and repository implementations.
- Maps persistence models to domain models; Room entities must not leak into presentation.
- Multi-record financial writes execute inside one Room transaction.
- Query streams use `Flow`; one-shot commands are `suspend` functions.
- Database migrations are explicit and tested. Destructive fallback is forbidden for production data.

### `platform`

- CameraX capture, system photo picker, app-managed media I/O, EXIF/location adapters, notifications, WorkManager, Glance and the future map SDK adapter.
- Platform APIs are hidden behind narrow interfaces when domain/application logic needs them.
- Permission state is presentation/platform state, not persisted consent. Location is requested contextually and remains optional.

### `di`

- Hilt constructs Room, DAOs, repositories, dispatchers and platform adapters.
- Bind interfaces to implementations at the application scope only where lifetime requires it.
- Tests replace repositories and platform adapters with fakes; global service locators are forbidden.

The current imported packages may be migrated incrementally. New V1 code should follow these boundaries; BL-012 does not move existing UI files solely for package aesthetics.

## Data ownership and consistency

- Room owns structured financial truth: transactions, images, categories, accounts, transfers, budgets and notification-delivery state.
- A transaction image row owns ordering, media reference and optional per-image location. Location is never stored only on the transaction.
- The media store owns normalized private image files. Database rows contain canonical app-owned content URIs and metadata, never image blobs, absolute paths or source-picker URIs.
- Preferences own small user choices such as theme, currency display and widget privacy. They do not own financial records.
- Derived totals, balances and budget progress are calculated from persisted facts through repository queries/use cases; duplicated cached totals require a documented invalidation strategy before introduction.
- Deletion is a coordinated operation: mark/delete database ownership atomically, then perform recoverable media cleanup using the BL-016 journal/reconciliation protocol.

## Write and observation flows

### Create or edit transaction

1. Presentation builds a draft and asks a use case to validate it.
2. Media selected/captured for the draft remains temporary until save.
3. The use case invokes one repository operation containing the complete mutation.
4. The data layer writes the transaction, ordered images and affected relations inside one Room transaction.
5. Successful persistence triggers only necessary follow-up work, such as widget refresh or orphan cleanup.
6. Screens update from Room-backed flows rather than patching an independent UI copy of financial truth.

### Read models

- Repositories expose feature-focused projections for calendar, statistics, map and widget reads.
- Paging or bounded range queries must be used where an unbounded list would violate the 10,000-transaction target.
- Heavy image decoding, EXIF work and file operations run off the main thread.

### Widget and background work

- The Glance widget consumes a small widget snapshot supplied by a use case/repository; it does not share a screen ViewModel.
- Transaction mutations request widget refresh after the database commit. Missing media resolves to another eligible image or the empty state.
- WorkManager is reserved for durable deferrable work: media cleanup, backup/import stages and threshold notifications when appropriate. Immediate database CRUD does not need a Worker.
- Every Worker must be idempotent and safe after process death or retry.

### Map and location

- Map presentation consumes domain projections containing only validated coordinates and display metadata.
- Google Maps Compose is the active renderer selected by ADR-016 after the historical BL-032 MapLibre phase. Google Maps types stay in the platform/presentation adapter; domain/data remain provider-neutral and can support another renderer without a Room migration.
- Build configuration reads `MAPS_API_KEY` through the Secrets Gradle Plugin. The tracked default is blank so clean clones compile; a real runtime key belongs only in ignored `secrets.properties` and must be restricted to `com.billlocket.app`, the intended signing certificate and Maps SDK for Android.
- Current location, imported EXIF location and manual correction all pass through the same validation and explicit-confirmation boundary.
- Missing/invalid credentials, billing/API configuration or network access may prevent basemap rendering, but must leave all non-map features and locally stored geotagged records available through a controlled fallback state.

## Concurrency and threading

- Coroutines provide structured concurrency; `GlobalScope` is not allowed in new code.
- ViewModels launch UI work in `viewModelScope`; repositories expose main-safe APIs and switch dispatchers around blocking file/EXIF work.
- Room handles database query dispatch. Transactions are short and contain no network or long-running file copy.
- Concurrent edits use last-committed local state in V1. A repository mutation receives all fields needed for a deterministic write; future cloud conflict resolution is out of scope.

## Failure handling

- Validation errors are returned before persistence and identify the invalid field/rule.
- Storage, database and platform failures remain distinguishable so the UI can offer retry or recovery without claiming success.
- A failed database write leaves no committed partial financial state.
- A failed media finalization keeps enough draft/recovery information to retry or clean temporary files; exact sequencing belongs to BL-016.
- Logging must never include receipt images, full notes, exact coordinates or other sensitive user data.

## Testing strategy

- Domain: fast unit tests for money, transaction/category/account rules, transfers, budget thresholds, date ranges and use-case orchestration.
- Data: Room in-memory integration tests for relations, constraints, indices, projections and every migration path.
- Repository: contract tests shared by fake and Room-backed implementations where practical.
- Presentation: ViewModel state-transition tests and focused Compose tests for critical flows/accessibility.
- Platform: instrumentation tests for URI access, media lifecycle, permissions, widget refresh and map adapter behavior on supported APIs.
- Build baseline remains `test`, `assembleDebug` and `git diff --check` under JDK 17. Device tests are added when a task changes Android framework behavior.

## Security and privacy boundaries

- No authentication or cloud transport exists in V1.
- Location is opt-in per image, removable and never inferred from camera permission.
- Export is an explicit user action; backup rules and redaction are defined before implementation.
- Internal files/URIs are not exposed broadly. External sharing requires temporary least-privilege grants.
- Secrets must not be compiled into the client or committed to the repository.

## Decisions intentionally deferred

- BL-013: exact Room tables, foreign keys, indices, query projections and migrations.
- BL-014: exact repository method contracts and use-case catalog.
- BL-017: navigation graph, screen state contracts and UI effects.

These tasks may refine their owned details without violating the dependency direction, privacy rules or source-of-truth boundaries above. A change to those foundations requires a new ADR.
