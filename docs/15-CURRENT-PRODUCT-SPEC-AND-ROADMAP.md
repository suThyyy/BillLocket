# BillLocket — Current Product Specification and Continuation Roadmap

Last updated: 2026-10-07

## 1. Purpose and authority

This document describes the current BillLocket product after its foundation, UI/UX, widget, map, memory-viewer and date-validation work. It is the primary product reference for deciding what to stabilize and what to build next.

When documentation and implementation differ, use this evidence order:

1. Current source code and working-tree diff.
2. Automated and physical-device evidence.
3. Domain/privacy decisions and database contracts.
4. This current-product specification.
5. Older planning documents.

Roadmap entries are proposals until they receive explicit product scope and acceptance criteria.

## 2. Product summary

BillLocket is a private, photo-first personal money journal for Android. A transaction is recorded through a camera-like experience, enriched with a small amount of financial information, and later revisited through a calendar, photo memories, statistics, a location map and home-screen widgets.

The product is not intended to be a full accounting suite. Its core promise is:

> Record a real expense or income quickly, remember the visual moment, and retain useful financial context without requiring an account or cloud backend.

### Product identity

- Visual language: Locket-inspired black/charcoal surfaces with restrained amber/gold accents.
- Information hierarchy: influenced by the supplied CapMoney references, without copying branding or assets.
- Semantic colors: coral/red for expense, mint/green for income.
- Primary platform: native Android, Kotlin and Jetpack Compose.
- Primary language: Vietnamese.
- Data model: local-first Room database plus app-owned normalized media.

## 3. Current implementation snapshot

### Stable foundation

- Minimum Android version: API 26.
- Compile/target SDK: 35.
- Gradle runtime: JDK 17; Java/Kotlin bytecode target: JVM 11.
- Core storage requires no login or remote backend.

### Post-foundation behavior already present

- Broad visual refactor of Home, camera, editor, detail, statistics, More/settings and finance screens.
- Compact camera and two-tap transaction-entry path.
- Photo Memory Viewer opened from calendar photo days.
- Three Glance widget experiences: latest transaction, today recap and weekly recap.
- Photo-led map with carousel/marker behavior.
- Google Maps renderer and theme switching were reintroduced after the earlier MapLibre migration.
- Category and budget UI refinements.
- Future transaction dates are rejected in UI, ViewModel, domain and repository paths.
- Physical-device testing has been performed on Android 13 for the primary capture, editor, calendar, map and widget flows.

### Accounts/navigation state

The implemented direction is:

- Accounts becomes a top-level destination in place of More.
- Accounts becomes a financial hub with balance visibility, period summaries and links to transfers, categories, budgets and settings.
- Account ordering and per-account actions become less visually noisy.

## 4. Actors and data ownership

### Primary actor

One device owner. There is no administrator, friend, merchant, family member or cloud-account actor in the current product.

### Ownership rules

- A transaction belongs to the device owner.
- A transaction has exactly one type: expense or income.
- A saved transaction has a positive exact monetary amount, one active matching category and one active account.
- A transaction may contain zero or more ordered images.
- Every saved image is copied into app-managed storage.
- Optional coordinates belong to each image, not only to the transaction.
- Room stores exact money and metadata; it does not store image bytes.
- Transfers move value between accounts and never count as income or expense.

## 5. Navigation model

### Committed product model before the current working-tree refactor

The floating bottom bar contains:

1. Home.
2. Statistics.
3. Central Capture action.
4. Map.
5. More.

### Working-tree target

The current uncommitted refactor changes the trailing top-level destinations to Accounts and Map. Management/settings actions move into the Accounts hub. This change is not considered complete until navigation tests, Back behavior, tab restoration and physical-device layout pass.

### Navigation invariants

- Home is the safe start destination.
- Capture is an action, not a persistent stateful tab.
- Detail/editor routes pass stable identifiers, not images, notes, amounts or coordinates.
- Invalid or missing identifiers show a recoverable state instead of crashing.
- Widget intents route to the intended detail, recap or capture destination.
- System Back dismisses an open keyboard/sheet/menu before leaving where appropriate.

## 6. End-to-end user journeys

### J1 — Record an expense with a new photo

1. User taps the central capture action.
2. App opens the camera surface and requests camera permission only if needed.
3. On the first relevant capture, app asks whether current location may be attached automatically.
4. If accepted, Android location permission is requested separately; denial does not block capture.
5. User captures one or more photos, reviews the staged thumbnails and continues.
6. Editor opens with expense selected, a sensible category/account default and the amount field ready.
7. User enters amount, optionally adjusts category, account, note, date, time, image order or location.
8. User confirms save.
9. Media is finalized and financial metadata is committed.
10. Home, statistics, budgets, map eligibility and widgets refresh.

Acceptance:

- A valid financial record saves even if location lookup, EXIF parsing, map tiles or widget refresh fail.
- Repeated save taps cannot create accidental duplicates.
- Process recreation preserves or safely recovers the draft.

### J2 — Import receipt photos

1. User opens Photo Picker from capture/editor.
2. User selects up to the supported multi-image limit.
3. App copies and normalizes selected media into its staging area.
4. GPS EXIF, when accessible, is shown only as a candidate for confirmation.
5. Imported photos never receive the current device location automatically.
6. User completes the normal editor flow.

### J3 — Record without a photo

1. User chooses `Bỏ qua ảnh`.
2. Editor opens with the same financial validation as a photo transaction.
3. Saving creates a normal transaction with no image rows.
4. It appears in totals, accounts, statistics and budgets, but not in the photo widget/map/memory viewer.

### J4 — Review a calendar memory

1. Home shows monthly totals, filters, month controls and a Monday-first calendar.
2. A day with photos shows a squircle thumbnail and count badge when appropriate.
3. Tapping a photo day opens the immersive Photo Memory Viewer.
4. User swipes through photos, uses the filmstrip, reads time/category/amount context and taps a photo for transaction detail.
5. Tapping an imageless day selects it and shows its transaction list without opening an empty pager.

### J5 — Edit, share or delete a transaction

1. Detail emphasizes the primary photo and amount.
2. Category/account, timestamp, note and optional location remain available without duplicate presentation.
3. Edit reuses the transaction editor and preserves existing IDs/media ownership.
4. Sharing grants temporary read-only access through the configured provider.
5. Delete requires confirmation and supports the implemented undo window.
6. Final purge removes media only when it is no longer referenced.

### J6 — Attach or correct photo location

1. User opens location management for one image.
2. App may offer confirmed EXIF candidate, current location or manual map selection.
3. Current location requests contextual permission and uses a bounded best-effort fix.
4. User confirms, edits place text or removes location.
5. Only confirmed valid coordinates make the image eligible for the map.

### J7 — Use the photo map

1. Map loads confirmed geotagged photos for the selected time/type/account/category filters.
2. Nearby images group deterministically.
3. Markers use a representative transaction photo and a count badge; missing media uses a stable fallback.
4. Selecting a marker exposes photo/transaction context and routes to detail.
5. Provider, credential or network failure must not make the financial records inaccessible.

### J8 — Manage accounts and transfers

1. User reviews active account balances and archived accounts.
2. User can create, edit, reorder and archive accounts without breaking historical records.
3. Current working-tree UX adds month/year/all-time summaries and amount visibility.
4. A transfer requires two distinct active accounts and a positive amount.
5. Transfer completion changes both balances symmetrically and leaves global income/expense unchanged.

### J9 — Review statistics and budgets

1. User selects week, month or year and optionally filters by account.
2. App shows income, expense, net, comparison, category distribution and trend.
3. Budgets show overall/category progress for expense transactions only.
4. Threshold notifications are idempotent and permission denial does not block budget tracking.
5. Future-dated legacy transactions do not affect current statistics, widgets or budget alerts.

### J10 — Use widgets

Current widget families:

- Latest transaction: hero photo with category/amount privacy behavior and direct detail navigation.
- Today recap: current-day count/expense context and relevant media.
- Weekly recap: weekly financial/photo summary.

Common behavior:

- Quick-capture affordance opens camera flow.
- Missing/oversized/corrupt media falls back safely.
- Images are decoded to bounded bitmap sizes before being sent through RemoteViews/Glance.
- Future-dated transactions are excluded.

### J11 — Backup and restore

1. User explicitly exports a versioned portable archive through the Storage Access Framework.
2. Archive contains authoritative domain data, integrity metadata and optional owned media.
3. User chooses an archive for replace-import.
4. App validates structure, paths, hashes and compatibility before replacing current data.
5. Failed/corrupt/malicious import leaves previous data recoverable.

## 7. Screen-level specification

### Home

- Near-black background and compact photo-journal header.
- Monthly expense is visually dominant; income and privacy visibility remain accessible.
- Filters do not wrap into unusable layouts; horizontal scrolling is allowed.
- Calendar prioritizes photos and keeps selected-day content near the fold.
- `Hôm nay`, previous month and next month retain a valid selected day.
- Loading, repository error, empty day and empty month states are explicit.

### Camera

- Centered 1:1 rounded viewfinder.
- Flash and lens controls float within the preview.
- Supported quick zoom levels are reachable with one hand.
- Dominant shutter, secondary gallery picker and tertiary skip flow.
- Multi-image strip shows count and supports safe removal/reordering.
- Permission denial, permanent denial and unavailable camera have useful recovery actions.

### Transaction editor

- Must keep amount and confirm action usable while numeric keyboard is open.
- Expense/income selection changes semantic sign/color and category options.
- Amount is formatted for Vietnamese display without corrupting exact stored minor units.
- Category/account have non-null sensible defaults for new transactions.
- Date picker allows today and past dates only.
- Note, photos and image locations are optional.
- Validation errors are field-specific and clear as soon as the field becomes valid.
- Existing legacy future transaction cannot be saved until corrected.

### Transaction detail

- Primary image uses a legibility scrim for amount and compact category/account metadata.
- Timestamp, note, edit, share, delete and location are not removed as “duplicates”.
- Missing image preserves all financial content.
- Multi-image navigation remains available.

### Statistics

- Period and account filters are visually distinct.
- Income, expense and net fit without clipping at supported font scales.
- Category distribution and trends include text equivalents, not color-only meaning.
- Empty periods explain that no matching transactions exist.

### Accounts hub

- Current redesign target: top-level financial overview and management gateway.
- Total balance respects global amount-visibility settings.
- Period summaries define whether they represent all accounts or a selected account.
- Archived accounts are visually separate and cannot receive new transactions/transfers.
- Reorder mode is explicit so row taps do not accidentally reorder.
- Transfers, categories, budgets and settings remain discoverable after removing More from the bottom bar.

### Map

- Current source uses Google Maps Compose and a manifest API-key placeholder.
- Theme follows application/system theme where supported.
- Map is optional to transaction creation and financial review.
- Photo/location data remains provider-neutral in domain and Room.
- A missing/invalid key must degrade predictably rather than crash the app.

### Settings

- Theme, currency/format, amount visibility, widget behavior, permissions, storage and backup remain grouped and understandable.
- No social login, subscription or paywall.
- Permission explanations are contextual and do not imply that optional permissions are required for core finance.

## 8. Business rules and validation

### Money

- Persist exact integer minor units; never persist binary floating-point money.
- Amount must be greater than zero.
- Current product uses one configured display currency and performs no exchange conversion.

### Dates

- Today and past dates are valid normal transaction dates.
- Tomorrow and later dates are rejected.
- UI date constraints are not sufficient alone; domain/repository validation is mandatory.
- Planned/recurring transactions require a separate model and must not weaken this rule.

### Categories and accounts

- Category type must match transaction type.
- Archived category/account remains valid for historical reads but not new writes.
- Transfers require distinct active accounts.

### Images and privacy

- App-owned normalized files strip GPS/device EXIF metadata.
- Confirmed location is stored separately in Room.
- Location denial/removal never deletes the image or transaction.
- Sharing uses read-only time-bounded URI access where the platform supports it.

## 9. Non-functional requirements

### Reliability

- Financial commits complete before retryable widget/notification/cleanup side effects.
- Multi-record financial mutations are atomic.
- Missing media and provider failure are degraded states, not fatal states.

### Performance

- Target remains responsive at 10,000 transactions and 20,000 image references.
- Full-resolution media is never decoded into widget or marker memory without bounds.
- Room list/history/map queries use bounded date ranges and indexed fields.

### Accessibility

- Practical Material touch targets.
- TalkBack labels for meaningful controls and images.
- Color is not the only representation of transaction type or chart meaning.
- Largest supported font scales must not hide primary actions.
- Decorative motion remains short and restrained.

### Localization

- Vietnamese is the initial product language.
- New user-visible copy belongs in string resources; existing hard-coded Compose copy is technical debt to migrate gradually without mass refactoring unrelated screens.

## 10. Current gaps and risks

### G1 — Google Maps configuration and release rehearsal

BL-040 resolved the architecture/documentation mismatch: ADR-016 and `docs/07-MAP-PROVIDER.md` now define Google Maps Compose as the active renderer. Remaining operational checks are intentionally narrower:

- keep the real key only in ignored `secrets.properties`;
- restrict it to `com.billlocket.app`, the installed signing certificate and Maps SDK for Android;
- retain a blank tracked fallback for clean-clone compilation;
- rehearse blank/invalid key, disabled API/billing, offline and attribution behavior before distribution.

### G2 — Accounts/top-level navigation regression watch

The refactor is committed in `c531a17` and product-owner testing is currently PASS. Reopen BL-042 only if a concrete navigation, destination-reachability, amount-summary or layout regression can be reproduced.

### G3 — Physical-device gaps

- Full backup export → destructive replace-import → verification rehearsal.
- Offline/invalid map key/provider behavior.
- Multi-photo cluster count with real records.
- All widget sizes, launchers and privacy combinations.
- TalkBack order, high font scale and contrast on secondary screens.
- Camera denial/permanent-denial and process-death recovery matrix.

### G4 — Release configuration

- Version remains `1.0` / code `1` despite substantial post-foundation changes.
- Course demo uses debug signing; public release signing is intentionally not configured.
- Google Maps key must never be committed and must be restricted to package/signing certificate and required APIs.

### G5 — Documentation drift

Keep public documentation focused on current product behavior, architecture and reproducible validation. Historical implementation notes should not override newer source or current architecture decisions.

## 11. Stabilization backlog before new scope

These items should be completed before a large new feature:

### BL-040 — Reconcile current architecture and documentation — complete

Scope:

- Record Google Maps as the active renderer and MapLibre/OpenFreeMap as historical.
- Update ADR/provider/build/readiness documents to match source.
- Record clean-clone and credential behavior.
- Preserve provider-neutral domain/storage boundaries.

Acceptance completed on 2026-10-06:

- One unambiguous active provider decision.
- No committed API key; the blank tracked fallback and ignored real-key path match source.
- Build-without-key and runtime-rendering requirements are documented separately. The product owner reports current keyed device flows as working; invalid-key/offline behavior remains a release-rehearsal item rather than an open functional defect.

### BL-041 — Finish Accounts hub and top-level navigation — complete

Scope:

- Review and complete the current seven-file working-tree change.
- Verify Accounts replaces More without losing categories, transfers, budgets, settings or Back navigation.
- Add focused ViewModel/navigation tests.

Acceptance recorded on 2026-10-06:

- Implementation is committed in `c531a17` and practical product-owner testing is PASS.
- No known P0/P1 regression is open; focused automated/device evidence should continue to accompany any later navigation changes.

### BL-042 — Small-defect closure and regression gate

Scope:

- Create a reproducible bug list from current phone testing.
- Fix P0/P1 first; group only closely related P2/P3 fixes.
- Run core creation, edit, delete, location, map, widget and backup scenarios.

Acceptance:

- Every reported defect has steps, expected/actual result, severity and evidence.
- No unresolved P0/P1 issue.
- JDK 17 unit/debug/androidTest compile and `git diff --check` pass.

## 12. Proposed new features

The ranking below balances user value, BillLocket identity, implementation effort and course-project risk.

| Rank | Proposal | Value | Effort | Risk | Recommendation |
|---|---|---:|---:|---:|---|
| 1 | Monthly photo recap | High | Medium | Low | Build first after stabilization |
| 2 | CSV and privacy-aware PDF export | High | Medium | Low | Strong reporting/demo value |
| 3 | Enhanced widget configuration | High | Medium | Medium | Extend existing strength |
| 4 | Biometric app lock | Medium-high | Medium | Medium | Valuable for financial-photo privacy |
| 5 | Receipt OCR suggestions | High | High | Medium-high | Prototype after stable flows |
| 6 | Duplicate receipt warning | Medium-high | Medium | Medium | Useful with OCR/photo hashes |
| 7 | Savings goals | Medium | Medium-high | Medium | Requires clear transfer semantics |
| 8 | Recurring transaction templates | Medium | High | High | Requires planned-vs-confirmed model |
| 9 | Location insights | Medium | High | Privacy/network | Only with explicit consent design |
| 10 | Cloud sync/shared budgets | High for some users | Very high | Very high | Not recommended for course V1 |

### F1 — Monthly photo recap

Concept:

- Generate a month-end visual story from transaction photos.
- Show total spent, income, top category, most active day and memorable locations.
- Export a redacted image or short local slideshow.

Constraints:

- User chooses whether amount, note and location appear.
- Generation works from local data and never uploads images implicitly.
- Missing images are skipped safely.

Suggested acceptance:

- Correct month totals match Statistics.
- Recap can be generated with zero, one or many photos.
- Exported artifact respects selected privacy toggles.

### F2 — Rich export

Concept:

- CSV export for exact transaction data.
- Optional PDF monthly report with charts and selected photos.
- Date/account/category filters and explicit note/location redaction.

Why next:

- Reuses stable queries and SAF infrastructure.
- Provides obvious course-demo and real-user utility without changing Room schema.

### F3 — Enhanced widgets

Concept:

- Configuration screen for latest photo, random memory, today recap, weekly recap or budget progress.
- Per-widget category/account filter.
- Privacy and refresh choices per widget instance.

Constraints:

- Keep bitmap bounds and RemoteViews limits.
- Each widget instance stores independent configuration.
- Battery-friendly refresh; updates should be event-driven where possible.

### F4 — Biometric app lock

Concept:

- Optional biometric/device-credential gate on app launch or sensitive surfaces.
- Widget privacy remains separately configurable.

Constraints:

- Never lock users out of backup recovery permanently.
- Define behavior after process death, task switching and biometric changes.
- Do not implement custom cryptography merely to hide the UI.

### F5 — Receipt OCR suggestions

Concept:

- Detect candidate total, date and merchant from a receipt photo.
- Suggestions populate editable fields; user must confirm before save.
- Show confidence and allow complete dismissal.

Constraints:

- Financial save cannot depend on OCR availability.
- Do not silently overwrite manual input.
- Prefer on-device processing for privacy when practical.
- Line-item accounting is a separate later scope.

### F6 — Duplicate receipt warning

Concept:

- Compare bounded perceptual image hash, amount and time proximity.
- Warn before save when a likely duplicate exists.
- User may continue; the app never auto-deletes.

### F7 — Savings goals

Concept:

- Target amount/date and progress presentation.
- Contributions are transfers/allocations, not income.
- Optional goal widget.

Architecture note:

- Requires an explicit data-model decision and migration.
- Must not distort account balances or global net statistics.

### F8 — Recurring transaction templates

Concept:

- Templates/reminders for rent, allowance, salary and subscriptions.
- WorkManager creates a pending draft or notification, not a confirmed financial transaction.

Critical rule:

- Current transactions cannot be future-dated. Planned occurrences therefore require separate entities/states and explicit user confirmation.

### F9 — Location insights

Concept:

- Frequently visited places, spending by place and city/month filters.
- Optional local place aliases and near-place merging.

Constraints:

- No background location tracking.
- Use only coordinates already confirmed per image.
- Heatmaps and geocoding require a provider/privacy/cost decision.

## 13. Recommended delivery sequence

### Milestone A — Stabilize the actual current app

1. BL-042 only if a new reproducible defect is reported.
2. Backup, widget, invalid-key/offline-map and accessibility release checks as needed for distribution.
3. Select BL-043 explicitly when ready to begin the first new feature.

### Milestone B — High-value local extensions

1. Monthly photo recap.
2. CSV export, then optional PDF report.
3. Per-widget configuration and budget widget.
4. Optional biometric lock.

### Milestone C — Smart assistance

1. On-device OCR suggestions.
2. Duplicate receipt warning.
3. Savings goals after schema review.

### Milestone D — Major-scope features

Recurring planning, cloud synchronization, shared budgets, bank import and social sharing require new security, conflict, privacy and migration designs. They should not be started as “small additions”.

## 14. Definition of done for future tasks

A continuation task is complete only when:

- Its acceptance criteria are written before implementation.
- Existing financial/media/privacy rules remain intact.
- New schema has a documented and tested migration.
- UI includes loading, empty, error and recovery states.
- Relevant unit and navigation/UI tests pass.
- JDK 17 `test`, `assembleDebug`, `assembleDebugAndroidTest` and `git diff --check` pass where applicable.
- Physical-device evidence is recorded for camera, location, widget, map, SAF or biometric behavior.
- ADRs and this specification are updated without leaving contradictory current-state claims.

## 15. Contribution checklist

Before modifying BillLocket:

1. Read this file, `DECISIONS.md` and the relevant architecture, domain, media and map documents.
2. Inspect branch, HEAD, `git status` and the complete diff.
3. Preserve the committed Accounts/navigation behavior unless a task explicitly changes it.
4. Confirm the active map provider from source and the latest ADR before changing dependencies or secrets.
5. Work on one bounded task; do not start the next roadmap feature automatically.
6. Do not copy CapMoney/Locket assets or branding.
7. Do not commit API keys, signing material, personal receipt images or local UI-reference media.
