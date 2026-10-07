# BillLocket UI/UX Specification

Last updated: 2026-10-06

## 1. Purpose and authority

This document preserves the product and UI/UX specification validated through physical Android-device testing. It is the reference for future user-facing behavior.

Repository source, domain rules, privacy decisions and accepted architecture remain authoritative when this document conflicts with implemented data safety. The screenshots and videos under `UIUX_Capmoney/` are local visual references only: do not commit them and do not copy CapMoney or Locket branding, assets, icons or screenshots into BillLocket.

## 2. Product principle

BillLocket is a **photo-first personal money journal**, not a finance dashboard with an image attachment.

The product priority is:

1. Photo.
2. Amount.
3. Category and account.
4. Time, location and note.
5. Analytics.

The normal recording flow should remain short:

`Home → Capture / Pick / Skip → Preview and transaction fields → Save → Home/detail`

Core financial saving must never depend on location, map tiles, EXIF metadata or other optional enrichment.

## 3. Final visual direction

The product owner explicitly approved the current direction after BL-033.

- Dark mode is the primary presentation.
- Background is near-black; surfaces are dark charcoal with restrained elevation.
- BillLocket amber/gold is the primary brand and action color.
- Blue is optional and limited to neutral information or map affordances; it is not the primary brand color.
- Expense uses coral/red and income uses green.
- Cards use 20–28 dp corner radii; image cards may use 24–32 dp.
- Chips are pill-shaped; the bottom navigation is a large floating rounded container; capture is a large circular action.
- Borders are used sparingly. Prefer spacing, surface tone and typography for hierarchy.
- Photo and hero amount must be the visual focal points.
- Typography order: hero amount, screen title, section title, body, secondary metadata.
- Transitions should be short and functional (approximately 150–250 ms) for tab/chip/sheet/save/thumbnail state changes. Avoid decorative animation.

## 4. Navigation and known accepted deviations

The original reference proposed five destinations: Home, Statistics, Accounts, Budget and Profile. The implemented V1 currently uses Home, Statistics, Map and More plus a central capture action. Accounts, transfers, budgets and settings are reached from More.

This current navigation is accepted for the present V1. It must not be replaced merely to imitate CapMoney. Changing the top-level information architecture requires an explicit product decision and navigation tests.

The original reference also requested one image per transaction. The implemented repository safely supports ordered multi-image transactions. BL-033 kept that model and made the first image visually primary. Do not remove multi-image storage, truncate existing records or change the Room schema without an explicit product decision and migration plan.

## 5. Home / photo calendar

Required hierarchy:

- BillLocket/photo-journal identity and a concise activity message.
- Monthly expense/income summary with amount visibility control.
- Type/account filter chips, horizontally scrollable when needed.
- Previous/next month navigation; a month picker is optional later.
- Monday-first month calendar.
- Empty days remain visually quiet and actionable.
- Days with transactions show the primary/recent thumbnail and an unobtrusive count indicator when needed.
- Selected day is clear through gold color/border/dot treatment.
- Create-for-selected-day is easy to reach.
- Selected-day records open transaction detail.

Only day/month concepts are required; do not introduce a week mode.

## 6. Capture and import

Camera uses CameraX and Android-native behavior rather than copying iOS camera chrome.

Required controls and states:

- Cancel/back.
- Large rounded camera preview.
- Flash, lens switch and supported zoom.
- Large white shutter with a BillLocket gold ring.
- Android Photo Picker.
- Explicit skip/no-photo path.
- Permission denied/permanently denied recovery.
- Camera unavailable fallback.
- Draft thumbnails and safe remove/reorder for the current multi-image implementation.

Photo Picker must not request broad media permission when the system picker is sufficient. Imported images use EXIF location as an unconfirmed candidate; they must not inherit the device's current location at import time.

## 7. Transaction create/edit

This is the highest-priority product screen. Layout order:

1. Primary photo occupying the upper visual region, or a clear add-photo empty state.
2. Expense/income type and hero amount.
3. Category.
4. Account.
5. Date and time.
6. Optional note.
7. Image and per-image location management.
8. Persistent, unambiguous save action.

Expense is the default unless an existing draft says otherwise. Category options must match the selected transaction type. Saving requires only a positive amount, valid active category/account and valid date/time. Optional media/location metadata failures must not block a valid financial transaction.

Edit supports amount, category, account, type, note, date/time, image ordering/removal and per-image location. The first image is the primary visual image.

## 8. Keyboard and focus contract

Every text/amount form must satisfy all of the following:

- Tapping outside clears focus and hides the keyboard.
- Android Back closes the keyboard before leaving the screen.
- IME Done hides the keyboard.
- IME Next moves to the next meaningful field.
- Focused content remains visible with `adjustResize`, scrolling and IME insets.
- Amount may autofocus only when contextually appropriate.
- Recomposition must not repeatedly reopen the keyboard.

## 9. Category and account selection

Category selection separates expense and income. V1 defaults may include food, shopping, transport, coffee, entertainment, health, education, salary, bonus, sales and other; exact names do not need to match CapMoney. Category management supports create, rename, recolor, reorder and archive without breaking historical references.

Account selection shows the account name and, where space allows, current balance. V1 supports cash/wallet, bank and e-wallet/other concepts. Loans, investments and savings products are excluded.

## 10. Transaction detail and sharing

Transaction detail should emphasize photo and amount, followed by date/time, category, account, note and optional location. It must tolerate missing/corrupt media.

Supported actions remain bounded by repository functionality:

- Edit transaction.
- Share transaction/image using safe read-only content access.
- Delete with confirmation and recoverable undo where implemented.
- Open, edit or remove location.

Saving an arbitrary image to public gallery is not required unless separately scoped with storage/privacy behavior.

## 11. Statistics and category analysis

Statistics supports week, month and year with previous/next period and optional account filtering.

Required information:

- Income, expense and net/balance.
- Savings rate when the calculation is meaningful.
- Preceding-period comparison.
- Expense distribution by category with textual equivalents.
- Trend data without requiring complex animation.
- Category drill-down to matching transactions when scoped.

Category and Map may be presented as sibling analysis modes, but navigation changes require the decision noted in section 4.

## 12. Photo map and automatic location

The map is a memory surface showing where transaction photos were recorded, not a complex geospatial analytics product.

- Input filters: time/month, transaction type, account and category where available.
- Only images with confirmed location appear.
- Nearby points are grouped deterministically (the V1 design uses an approximately 100 m rule).
- A marker/cluster uses a representative thumbnail and count.
- The photo pin is a rounded-square transaction thumbnail with a clear coordinate anchor/pointer and a restrained BillLocket border. A cluster adds a readable count badge without covering the representative image.
- Missing or undecodable marker media falls back to a stable gold BillLocket pin/count marker; it never removes the saved location or crashes the map.
- Selecting a marker exposes transactions at that place and routes to detail.
- Map/network/provider failure leaves local financial and location-list data usable.
- Google Maps Compose is the current renderer under ADR-016. Keep the key in ignored `secrets.properties`, restrict it to the BillLocket Android package/signing certificate and Maps SDK for Android, and preserve provider-neutral domain/storage boundaries.

Automatic location contract:

1. Before the first camera capture, ask whether BillLocket may automatically attach the current location.
2. Only after explicit agreement request Android location permission.
3. Remember the choice locally.
4. On future camera captures, request a best-effort current fix and attach it to that image.
5. Timeout, denial, disabled providers or unavailable fixes never block capture/save.
6. Imported images prefer EXIF candidates and never use current device location automatically.
7. Users can review, correct or remove location per image.
8. Normalized owned image files remain stripped of EXIF GPS metadata.

## 13. Accounts and transfers

Accounts provide a total-balance summary plus income/expense context, create/edit/archive and transaction history. Account detail shows name, currency, balance, transaction count and period-filtered income/expense where available.

Transfer flow is `from account → to account → amount → date → optional note → save`. Transfers affect the two balances symmetrically and never count as income or expense.

## 14. Budgets

Budget screens support an empty state, creation, edit/delete, category or overall monthly limits and threshold alerts. Notification denial must not block budget storage/tracking. Shared budgets and members are excluded.

## 15. Settings, storage, backup and widget

Settings groups should expose categories, accounts, currency, theme, location/permissions, storage, backup/import, widget and about information without social login, upgrade/paywall or cloud-account dependency.

Portable backup includes authoritative local database content and owned media, with validated replace import and rollback behavior. Cache, secrets and temporary files are excluded.

The Glance widget prioritizes a recent readable transaction photo, optional amount/category according to privacy settings, detail navigation and quick capture. Missing media falls back safely.

## 16. Empty and error states

Empty states are required for no transactions/day, budget, category result, map location, account history and statistics. Each should include a concise icon, explanation and CTA when an immediate action is useful.

The app must not crash for camera denial, unavailable picker/media, location denial, missing/corrupt images, map/network failure, save failure, disk exhaustion or backup/import failure. Errors should explain the next safe action and preserve valid financial data.

## 17. V1 scope boundary

Included: expense, income, accounts, categories, transfers, photo transactions, calendar, statistics, budgets, map, opt-in automatic location, settings, portable backup/import and widget.

Excluded: loans, investments, savings/deposits products, social graph, friends, chat, shared finance, subscription/paywall and mandatory cloud backend.

Multi-image is a documented implementation deviation retained for data safety and current user value. Treat any simplification to one image as a separate product/schema decision, not cleanup.

## 18. Current implementation alignment

| Area | State after BL-033 | Next evidence/action |
|---|---|---|
| Home/calendar | Implemented and visually accepted overall | Physical-device checklist and capture screenshots |
| Camera/import/skip | Implemented | Device permission and lifecycle matrix |
| Transaction editor | Reference-led layout implemented | Verify IME Back/Done/Next and small-screen scrolling |
| Automatic capture location | Implemented as one-time opt-in, best effort | Verify fresh GPS/network fix on device |
| Imported EXIF policy | Implemented | Verify with an image containing GPS EXIF |
| Detail/search/CRUD | Implemented | Device smoke test including missing media |
| Statistics/budgets/accounts/transfers | Functionally implemented | Secondary-screen visual consistency audit |
| Google Maps Compose | Implemented with themed map, BillLocket photo markers, deterministic grouping, carousel/detail navigation and manual/current-location flows | Preserve credential restrictions; include invalid-key/offline and attribution checks in release rehearsal |
| Widget | Implemented | Launcher add/resize/tap/privacy verification |
| Backup/import | Implemented and compiled/tested | Physical-device SAF round trip |
| Empty/error/accessibility states | Partially verified | Systematic UI audit and remediation |
| Five-tab CapMoney navigation | Not adopted; current four-tab + capture accepted | Change only after explicit product decision |
| Single-image-only model | Not adopted; primary-image UI over multi-image data | Change only after explicit migration decision |

## 19. Contribution safeguards

Before UI work, read this document, `docs/DECISIONS.md` and the relevant architecture, domain, media and map documents. Inspect the current source and working-tree changes before editing.

Contributors must:

- implement only the active assigned task;
- preserve the approved yellow/black identity;
- use local CapMoney media only as reference;
- avoid architecture/schema/navigation changes unless the task explicitly authorizes them;
- preserve valid multi-image records while keeping the primary image visually dominant;
- keep location optional, explicit and per image;
- validate with JDK 17 and record device limitations honestly.
