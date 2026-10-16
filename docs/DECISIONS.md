# BillLocket Architecture Decisions

## ADR-001 Android native

**Decision:** Use Kotlin and Jetpack Compose.

**Reason:** The project is for an advanced Android programming course and requires strong integration with CameraX, location, widgets, Room, permissions, and background work.

## ADR-002 Authorized Nocket Compose starting point

**Decision:** Use the `develop` branch of `luucaohoanq/nocket-compose` as an authorized technical starting point and substantially replace its social-domain behavior.

**Reason:** It provides a working Compose, CameraX, navigation, Hilt, and MVVM foundation. The user obtained permission from the author. Keep independent proof of permission outside the public repository if it contains personal information.

## ADR-003 Offline-first V1

**Decision:** Room is the source of truth for V1. Core features require no account or backend.

**Reason:** This reduces risk and makes the application useful without network access. Appwrite integration from the starting repository will be removed or isolated during foundation cleanup.

## ADR-004 Exact money values

**Decision:** Persist money using an exact representation, preferably integer minor units.

**Reason:** Binary floating-point creates rounding errors that are unacceptable in financial totals.

## ADR-005 Location belongs to images

**Decision:** Persist optional latitude, longitude, and place metadata on each transaction image.

**Reason:** A transaction may contain images captured or imported from different places, and the product map is explicitly photo-based.

## ADR-006 Narrow V1

**Decision:** V1 includes visual expense tracking, a photo-focused home-screen widget, and a transaction photo map. OCR, cloud sync, social features, advanced finance, and AI remain deferred.

**Reason:** The course project must become stable and explainable before scope is expanded.

## ADR-007 BillLocket identity

**Decision:** The target Android namespace and application ID are `com.billlocket.app`. The user-visible application name is **BillLocket**, and the repository's Gradle root project name will also become `BillLocket`.

BL-010 replaced the active inherited `com.example.nocket`, `Nocket`, `MVVM`, `org.com.hcmurs`, and `io.appwrite.starterkit` identities with the BillLocket identity. Historical audit and attribution references may remain where they document the authorized baseline and completed migration.

**Reason:** A product-specific package avoids shipping the inherited example/social identity and gives the V1 app one consistent namespace without claiming ownership of a public domain. BL-008 records the target only; BL-010 owns the behavior-preserving rename across Kotlin packages/imports, source directories, manifest entries and schemes, tests, resources/themes, project metadata, and documentation. BL-009 must remove obsolete Appwrite/social callback entries before BL-010 finalizes the manifest identity.

## ADR-008 Android SDK and build environment baseline

**Decision:** The intended V1 Android baseline is:

| Setting | Selected value | Rationale |
| --- | --- | --- |
| `compileSdk` | 35 | Matches the imported build and the Android SDK platform available during the audit. Compile against the selected platform while keeping runtime support separate. |
| `targetSdk` | 35 | Keeps target behavior aligned with the selected compile platform and the course demonstration baseline. BL-011 must verify permission, splash, backup, and edge-to-edge behavior on the demonstration device. |
| `minSdk` | 26 | The imported source uses `java.time` throughout and has no core-library desugaring setup. API 26 supplies that API natively and avoids adding desugaring configuration as part of the foundation definition. API 24–25 support is intentionally not part of this baseline. |

The selected baseline does **not** make every API use safe: `ComponentCaller` and other platform-specific startup behavior still require source/API review in BL-009 and validation in BL-011. If later requirements demand API 24–25 support, that is a new compatibility decision requiring core-library desugaring, guarded API usage, and build/test changes; it must not be enabled implicitly.

The required build environment is JDK 17 running the committed Gradle Wrapper 8.11.1 with Android Gradle Plugin 8.9.0, Kotlin 2.0.21, and the applied KSP plugin 2.0.21-1.0.27. Android SDK Platform 35 is required for local compilation. Java and Kotlin bytecode remain targeted to JVM 11; JDK 17 is the Gradle runtime, not the bytecode target. Developers should invoke the wrapper rather than rely on a separately installed Gradle version.

BL-009 removed the private configuration gate and obsolete/duplicate dependencies. BL-011 applies minSdk 26 and validates the existing selected toolchain without library upgrades. The unused catalog KSP alias (2.0.0-1.0.23) is not applied; the active root plugin remains 2.0.21-1.0.27. Mixed Compose pins/BOM and the dormant Room processor are retained unless validation demonstrates a failure; coherent Room declarations belong to BL-013/BL-014. Do not activate the stale KSP alias in later work without aligning it.

**Reason:** This is the smallest documented baseline supported by repository evidence and the course device assumption. It resolves the SDK/JDK decision without silently changing source compatibility, adding desugaring, supplying backend credentials, or broadening V1 support.

## ADR-009 Repository build hygiene policy

**Decision:** Text files use LF, Windows batch/cmd scripts use CRLF, and binary assets remain binary. Formatting is an explicit validation/maintenance action; a normal build must not rewrite tracked source as a side effect. No `.gitattributes`, formatter wiring, or line-ending normalization is added in BL-008.

BL-011 adds `.gitattributes`, removes the automatic formatter dependency, and restricts Kotlin formatting to source directories. Normal build/test commands must leave tracked files unchanged; `spotlessCheck` and `spotlessApply` remain explicit actions. Existing untouched files are not mass-normalized. BL-009 removed the Appwrite `local.properties` configuration gate and its BuildConfig consumers. Neither task adds credentials or a committed `local.properties`.

**Reason:** The audit found mixed index/worktree line endings and a `preBuild` dependency on `spotlessApply`. Recording the policy now prevents EOL noise and formatter rewrites from being confused with identity or product changes while keeping implementation changes in their owning tasks.

## ADR-010 Layered offline-first architecture

**Decision:** Use a single Android application module with presentation, domain, data and platform boundaries. Compose/ViewModels depend on domain use cases; domain depends on repository interfaces; Room, preferences and platform integrations implement those interfaces behind Hilt. Room is the sole structured financial source of truth, while image bytes remain in app-managed storage and Room stores references/metadata.

Multi-record financial writes are atomic Room transactions. UI and widget state are derived from observable persisted data. Background jobs are idempotent and limited to durable, deferrable work. New domain code remains Android-free and testable with local unit tests.

**Reason:** This structure supports offline reliability, exact financial rules, map/widget consumers and process recreation without the complexity of a multi-module or remote-sync architecture. See [V1 architecture](03-ARCHITECTURE.md).

## ADR-011 Room V1 persistence model

**Decision:** The initial Room schema separates accounts, categories, transactions, transaction images, transfers, monthly budgets, delivered budget thresholds and transaction-note FTS. Transactions use soft deletion for the undo window; final deletion cascades database metadata. Transfers remain separate from income/expense transactions. Images store stable media references, explicit ordering and optional confirmed coordinates per image.

IDs are UUID strings, money is 64-bit minor units with a currency code, instants are epoch milliseconds, and transaction grouping also stores civil epoch-day plus zone ID. Foreign keys and query-specific indices are mandatory. Schema JSON is tracked, destructive migration fallback is forbidden, and every later version requires a tested path.

**Reason:** The model directly enforces BillLocket's historical-preservation, exact-money, transfer-neutrality, photo ordering, location privacy and offline query requirements while supporting 10,000 transactions. See [Room database design](04-DATABASE.md).

## ADR-012 Repository and use-case boundaries

**Decision:** Presentation, Workers and widget coordination depend on focused use cases; use cases depend on domain repository interfaces and narrow platform ports. Contracts expose domain models/projections, `Flow` observations, suspend commands and typed `Outcome` failures. Room/Android/map/Glance types do not cross into domain contracts, and generic CRUD repositories are not used.

Financial mutations commit before retryable widget, notification or cleanup side effects. File/platform I/O never runs inside Room transactions. Transaction repositories own atomic transaction/image/search metadata changes; transfers remain separate; aggregate, map and widget reads use feature projections.

**Reason:** Explicit responsibilities keep financial rules testable, prevent UI-to-DAO coupling and make process-death/retry behavior predictable without over-engineering a generic framework. See [repository and use-case contracts](06-REPOSITORIES-USE-CASES.md).

## ADR-013 MapLibre/OpenFreeMap provider behind an abstraction — superseded

**Historical decision:** The original BL-015 Google Maps choice was superseded on 2026-10-02 by MapLibre Native for Android using OpenFreeMap's public Liberty style. This was the active decision through BL-039. ADR-016 supersedes the renderer/configuration portion after the post-foundation Google Maps migration; the provider-neutral domain, optional-location and failure-isolation principles remain binding.

Map and location are optional. Consent occurs explicitly in the transaction-image workflow, coordinates remain per image, and denying permission or losing provider/network availability never blocks core capture or finance features. The app stores no MapLibre types outside its renderer and sends no transaction amount, note, image bytes or stable record IDs to the map provider.

MapLibre-specific types and errors stay in a platform/presentation adapter. Domain, Room, repository and use-case contracts continue to use BillLocket `GeoPoint`, `MapPhoto`, filters and stable IDs, so another lawful renderer/style host can replace this provider without a database migration.

No credential is required. Google Maps dependencies, manifest key injection, the Secrets Gradle Plugin and the blank default secret file are removed. Provider/network failure remains isolated from local records, and any future paid or authenticated provider requires a separate decision.

**Reason:** MapLibre/OpenFreeMap removes billing and credential friction for the course demo while the existing abstraction limits provider lock-in and preserves offline-first financial behavior. OpenFreeMap is network-hosted and has no BillLocket-controlled SLA, so local fallback behavior remains mandatory. See [map provider decision](07-MAP-PROVIDER.md).

## ADR-016 Google Maps Compose as the active renderer

**Decision:** As of 2026-10-06, BillLocket uses Google Maps SDK for Android through Maps Compose 6.1.2 as its active map renderer. The post-foundation migration was introduced in commit `dac4b51` and retained after physical-device testing. It supersedes ADR-013 only for the concrete renderer and credential/build configuration.

Map/location remain optional. Room and domain continue to store only BillLocket `GeoPoint`, `MapPhoto`, filters and stable IDs. Google `LatLng`, camera, marker and map-style types remain inside presentation/platform code. Changing renderer must not require a financial/location schema migration.

The Secrets Gradle Plugin reads `MAPS_API_KEY`. The repository tracks only a blank `local.defaults.properties` fallback so a clean clone can compile tests and APKs without private credentials. A real key is placed in ignored `secrets.properties`, is never committed, and must be restricted in Google Cloud to:

- the Android application `com.billlocket.app`;
- the SHA-1 certificate fingerprints used for the intended debug/release build;
- Maps SDK for Android only, unless another API receives a separate reviewed decision.

A billing-enabled Google Cloud project and enabled Maps SDK for Android are required for runtime tile rendering. Missing/invalid key, disabled billing/API or network failure must not block capture, CRUD, calendar, accounts, statistics, budgets, widgets, backup or access to local geotagged records.

**Reason:** The current Google renderer provides the implemented themed map, photo markers, camera behavior, manual point selection and draggable transaction carousel that were accepted on the target device. Keeping domain/storage provider-neutral limits lock-in, while explicit key restrictions, ignored secret storage and graceful degradation contain credential, billing and availability risk. See [map provider decision](07-MAP-PROVIDER.md).

## ADR-014 App-owned media and portable local backup

**Decision:** Every saved transaction image is copied/captured into private app-owned storage and normalized to a bounded readable JPEG. Room stores a stable app-owned content URI and metadata, never bytes, absolute paths or source-picker URIs. Final files strip GPS/device metadata; confirmed optional location remains only on the corresponding Room image row.

Media writes use a file-first journaled protocol with reconciliation, idempotent trash/cleanup and missing-media fallbacks because Room and filesystem changes cannot be one transaction. Soft-delete retains media through undo; hard purge cleans files only after database commit.

V1 opts financial data/media out of automatic Android cloud/device backup and provides an explicit versioned ZIP export/import through Storage Access Framework. The portable format contains domain JSON, optional final images and SHA-256 integrity data. Import is validated replace-restore with recovery/rollback, not merge. V1 backup is not encrypted and warns about sensitive images/location.

**Reason:** App ownership avoids expiring grants, normalized media bounds storage, stripped EXIF protects consent, and a portable user-controlled format avoids partial Auto Backup restores and Room-schema coupling. See [media storage and backup design](08-MEDIA-STORAGE-BACKUP.md).

## ADR-015 Navigation and unidirectional UI state

**Decision:** Use one activity/root Navigation Compose host with Home, Statistics, Map and More as stateful tabs plus a central Capture action. Home is the start destination. Details/editors/capture hide the bar and carry only stable IDs/small primitives. Widget/deep-link entry validates IDs and falls back safely.

Every feature exposes immutable `StateFlow<State>`, typed user/system `Action`s and one-time `Effect`s. Composables render/dispatch only; ViewModels call use cases. Permission/picker/SAF/navigation launches are effects, never composition side effects. Draft IDs connect saved state to restart-safe draft/media storage; routes never contain money, notes, coordinates, URI or image bytes.

**Reason:** The contract prevents duplicate navigation/effects, supports process recreation and permission denial, keeps platform objects out of state, and makes ViewModels/navigation testable. The current implementation in the application module is authoritative for route and UI-state details.

## Pending decisions

- Widget sizes and refresh strategy.
- Exact widget size/layout selection and release-device validation.
- Future dependency alignment, lockfiles/verification metadata, and wrapper distribution checksum hardening beyond the validated pinned-toolchain baseline.
