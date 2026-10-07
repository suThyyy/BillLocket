# BillLocket V1 Navigation and UI-State Contracts

Status: approved by BL-017 on 2026-09-30.

## Principles

- Navigation carries stable IDs and small primitives only. Amounts, notes, coordinates, bitmaps, URIs and Room entities never appear in routes, deep links or `SavedStateHandle` arguments.
- ViewModels expose one immutable `StateFlow<State>` and accept typed `Action` values. Composables render state and emit actions; they do not call repositories, permission launchers, file APIs or navigation controllers directly.
- Navigation and platform prompts are one-off `Effect` values. Durable business/UI content remains state.
- A screen remains useful during denied permissions, absent images, offline/provider errors and process recreation.
- All user-visible strings are resource-backed, initially Vietnamese. Accessibility labels never reuse visual text accidentally when a clearer action description is needed.

## App navigation structure

The app has one activity and one root `NavHost`. The authenticated/social graph is permanently absent.

### Top-level shell

The persistent bottom bar has four stateful destinations plus one central action:

| Item | Destination/action | Purpose |
| --- | --- | --- |
| Home | `home` | Calendar, selected-day photos and period totals |
| Statistics | `statistics` | Week/month/year insights |
| Capture | action | Create a draft and open camera/photo entry; not a retained tab |
| Map | `map` | Optional photo-location map and local fallback |
| More | `more` | Accounts, transfers, budgets, categories and settings |

Top-level navigation uses `launchSingleTop`, restores saved tab state and pops to the graph start destination without creating duplicates. Capture, editors and details hide the bottom bar. Back from a top-level destination follows normal Android task behavior rather than cycling tabs.

The default start destination is `home`. A first-run empty state leads directly to capture or account setup without changing the start route.

### Destination catalog

Canonical conceptual routes:

```text
home
day/{epochDay}
statistics
capture/{draftId}
transaction/new/{draftId}
transaction/{transactionId}
transaction/{transactionId}/edit/{draftId}
image-location/{draftId}/{imageId}
map
more
accounts
account/{accountId}
account/edit/{accountId?}
transfers
transfer/edit/{transferId?}
categories/{transactionType?}
budgets/{monthEpochDay?}
settings
settings/widget
settings/storage
settings/backup
```

Actual Navigation Compose encoding may use type-safe destinations when dependencies are updated. IDs are parsed/validated at the destination boundary. Missing, malformed, deleted or inaccessible IDs show a recoverable not-found state and back/home action; they never crash.

Optional filters/month selection are screen state, not continually rewritten route query strings. A route argument is used only when it identifies the resource or initial user intent.

## Primary flows

### Quick capture and create

```text
any top-level screen
  -> create draft ID
  -> Capture(draftId)
  -> preview/reorder/location choices
  -> TransactionEditor(draftId)
  -> save
  -> TransactionDetail(transactionId)
```

- Capture allows CameraX, Photo Picker or “continue without image”.
- A captured/imported image belongs to the draft operation from BL-016.
- Back with an unchanged empty draft discards silently. Back with captured media or entered fields opens one discard confirmation.
- Successful save removes the draft from the back stack and opens detail. Repeated save actions are disabled/idempotent.

### Create from calendar day

Home/day creates a draft prefilled with the selected civil date, then follows the same capture/editor flow. The chosen date lives in draft state, not in camera code.

### Edit and delete

- Detail creates an edit draft from the persisted transaction and navigates using transaction/draft IDs.
- Save returns to the same detail destination and refreshes from Room-backed state.
- Delete requires confirmation, returns to the logical caller, shows an undo snackbar, and keeps media during the undo window.
- Undo invokes the restore use case; it does not reconstruct UI-only copies.

### Image location

Editor/detail opens `image-location` for one image ID. The screen returns a provider-neutral confirmed location result to the draft/action contract. Back without confirmation makes no persistence change. Applying one point to multiple selected images requires an explicit separate action/confirmation.

### Widget entry

Widget `PendingIntent`s target `MainActivity` with explicit BillLocket destinations:

- open `transaction/{id}`;
- start quick capture by creating/asking the app to create a new draft;
- open the normal app start when data is missing.

Widget intents contain only action and stable ID, use immutable/update-current flags as appropriate, and validate records after launch. They never expose amount, note, URI or coordinates.

## Back-stack and external-entry rules

- Editors/capture intercept back only for dirty-draft confirmation.
- Modal sheets dismiss before the destination pops.
- A deep-linked detail has a synthetic safe parent to Home; invalid records offer Home.
- After import/restore, clear incompatible detail/editor destinations and return Home before repositories emit the restored dataset.
- Process recreation restores destination IDs and small view selections. It reloads authoritative records/drafts from repositories/media journals.
- Signing out/auth routes never exist in V1.

## Unidirectional state contract

Each feature follows:

```kotlin
data class FeatureState(/* immutable render data */)

sealed interface FeatureAction // user/system inputs
sealed interface FeatureEffect // one-time platform/navigation outputs

class FeatureViewModel(...) : ViewModel() {
    val state: StateFlow<FeatureState>
    val effects: Flow<FeatureEffect>
    fun onAction(action: FeatureAction)
}
```

Rules:

- State collections are lifecycle-aware.
- State contains domain/presentation values, not `Context`, `NavController`, `Bitmap`, open stream, CameraX/MapLibre/Room objects or mutable collections.
- Actions describe intent (`SaveClicked`, `LocationConfirmed`), not widget callbacks (`OnClick`).
- Effects include navigation, permission/picker launch, snackbar and external document intents. Every emitted effect has a stable token or channel semantics preventing replay after configuration change.
- Loading is operation-specific (`isSaving`, `isDeleting`) rather than one global flag that blocks unrelated content.
- Last good content remains visible during refresh failures where safe.
- Validation errors are field-keyed and resource-backed; repository failures map to retryable screen messages without sensitive data.

Shared presentation types:

```kotlin
sealed interface AsyncValue<out T> {
    data object Uninitialized : AsyncValue<Nothing>
    data object Loading : AsyncValue<Nothing>
    data class Data<T>(val value: T, val refreshing: Boolean = false) : AsyncValue<T>
    data class Error<T>(val previous: T?, val message: UiText) : AsyncValue<T>
}

sealed interface UiText {
    data class Resource(val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plain(val value: String) : UiText // diagnostics/user-owned text only
}
```

## Feature state contracts

### Home / calendar

`HomeState` contains selected month/day, `AsyncValue<MonthCalendar>`, income/expense totals, totals visibility, type/account filters, selected-day summaries and empty state. Actions cover month/day selection, filter changes, total visibility, day/gallery opening and create-for-day. Navigation and snackbar are effects.

Changing month cancels obsolete collection and observes the new bounded range. Calendar cells do not own mutable transaction lists.

### Capture

`CaptureState` contains draft ID, camera permission state, lens, flash availability/mode, zoom range/value, binding/capture status, ordered draft images and capability flags. Actions cover permission explanation/request result, capture, lens/flash/zoom/focus, picker launch/result, image removal/reorder and continue without image.

Camera binding/capture errors are state. `LaunchCameraPermission`, `LaunchPhotoPicker` and navigation to editor are effects. Permission launch happens only in response to a visible explanation/action, not during composition.

### Transaction editor

`TransactionEditorState` contains draft ID/source transaction ID, type, amount text plus parsed money, category/account choices, note, date/time/zone, ordered image drafts, per-image location status, field errors, dirty/saving status and save eligibility.

The ViewModel owns parsing/validation and persists/reloads draft metadata. Amount text is presentation input; domain `Money` is produced only after locale-aware validation. Save emits one navigation effect after success. Image location candidates are visually distinct from confirmed saved locations.

### Transaction detail/day gallery

State is `AsyncValue<TransactionDetails>` plus delete/undo and selected-image index. Missing media is represented per image. Actions cover edit, delete confirmation, undo, image paging, location edit/remove and back. The UI does not infer deletion from navigation timing.

### Statistics

`StatisticsState` contains period kind/range, optional account filter, totals, comparison, category distribution, trend buckets, hidden-amount preference and loading/error. Charts receive already formatted domain projections and accessible text alternatives.

### Map

`MapState` contains provider readiness, local `MapPhoto` items, filters, viewport, selected marker/cluster, current-location capability/permission and optional picker mode. Provider state is `Ready`, `Loading`, `Unavailable(reason)` or `OfflineFallback`; local records remain available in every state.

Actions use provider-neutral points/IDs. Google `LatLng`, camera and marker objects remain in the adapter. Location permission is requested only after `CurrentLocationClicked` and explanation. Marker selection never writes coordinates.

### Accounts, categories and transfers

List states expose active/archived sections and stable ordering. Editor states expose field values/errors and save progress. Archive effects explain historical preservation. Transfer editor filters to two distinct active accounts and presents no income/expense category.

### Budgets

`BudgetState` contains selected month, overall/category progress, thresholds and notification permission/capability. Saving a budget does not automatically request notifications. The contextual notification prompt is a separate action; budget tracking works without permission.

### More/settings/storage/backup

Settings state derives theme, currency/format, amount visibility, widget privacy and permission summaries from repositories/system adapters. Rows route by typed action/ID, never English title matching.

Storage state separates final media, temporary/recovery and cache sizes with safe cleanup actions. Backup state models export/import phases and progress, archive inspection summary, destructive replace confirmation, cancellation and final result. SAF launch is an effect; streams never enter state.

## Permission state machine

```text
Unknown -> Explain -> Requesting -> Granted
                            |-> Denied(canAskAgain)
                            |-> DeniedPermanently(open settings)
```

- Camera: requested from Capture only; denial still allows Photo Picker/no-image transaction.
- Photo import: system picker, no broad storage permission.
- Location: requested in context for one draft image/current-location control; denial allows manual pin/no location.
- Notifications: requested only after budget alerts are enabled or a clear settings action; denial does not block budgets.
- Permission result updates state through an action. Composables never repeatedly launch based only on a boolean state.

Returning from system settings triggers capability re-check on resume without assuming approval.

## Draft and process-death recovery

- `SavedStateHandle` stores destination IDs, selected tab/month/image index, filter primitives and draft ID only.
- Transaction draft fields are serialized to a provider-neutral draft store; image bytes remain in BL-016 staging. Draft writes are debounced/atomic.
- On recreation, the ViewModel loads draft metadata and verifies every staged media reference. Missing media becomes a removable error item.
- Drafts older than the documented retention window are eligible for reconciliation only when no active journal/navigation recovery references them.
- Never place image byte arrays, long lists or sensitive notes into navigation routes or platform saved instance bundles.

## UI shell and visual contract

- Material 3 theme retains BillLocket yellow/black direction without copying Locket/CapMoney assets.
- Edge-to-edge/insets are handled once by the activity/shell, not independently by screens.
- Bottom navigation indicates selected destination and central capture semantics; it does not hardcode navigation inside reusable item components.
- Content descriptions, touch targets, contrast and chart text alternatives meet NFR-05.
- Amount hiding/obscuring is applied consistently to Home, statistics, detail previews, map cards and widgets according to context/privacy settings.

## Tests required during implementation

- Pure ViewModel tests for every state/action transition, validation, cancellation and one-time effect.
- Navigation tests for top-level singleTop/restore, capture draft flow, edit/save/delete/undo, invalid ID, widget entry and import reset.
- Compose tests for empty/loading/error/content, dirty-back confirmation, permission denial, missing media and accessibility labels.
- Process recreation tests reload by ID/draft without duplicating save/delete/effects.
- Map tests use provider-neutral fake state; Google-specific instrumentation remains at adapter boundary.

## Migration from current prototype

The current `Screen(Camera/SubmitPhoto/Setting/Detail)` graph, camera start destination, hardcoded title-driven settings and `NavController`-coupled UI components are prototypes. Implementation must replace them incrementally with the contracts above; BL-017 itself does not modify runtime UI behavior or rename source packages.
