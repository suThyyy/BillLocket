# BillLocket — Voice, Localization and On-device AI Delivery Plan

Last updated: 2026-10-10

Status: BL-045 implementation and automated validation complete; physical-device acceptance remains pending and is retained in the BL-048 release gate.

## 1. Authority and safe baseline

The report-ready application is preserved before this roadmap begins:

- stable branch: `main`;
- stable commit: `771324b067f7050112c26fced42c5ac84c78a3f1`;
- annotated rollback tag: `baseline/report-ready-2026-10-09`;
- remote: both `origin/main` and the rollback tag point to the report-ready state;
- validation at the baseline: JDK 17 `test`, `assembleDebug`, `assembleDebugAndroidTest` and `assembleRelease` passed;
- working tree at the baseline: clean.

The development integration branch is `codex/voice-localization-ai`. `main` must remain usable for the course report while this roadmap is in progress. Do not force-push, rewrite the baseline tag, squash the public baseline, or merge incomplete feature work into `main`.

Rollback examples:

```powershell
# Inspect the known-good snapshot without changing a branch.
git switch --detach baseline/report-ready-2026-10-09

# Create a recovery branch from the known-good snapshot.
git switch -c recovery/report-ready baseline/report-ready-2026-10-09
```

## 2. Product objective

Add three optional convenience capabilities without weakening BillLocket's offline-first financial core:

1. complete Vietnamese and English localization;
2. microphone-assisted note dictation;
3. on-device image-assisted category suggestions.

These are assistance features. They must not become prerequisites for capture, manual entry, save, edit, backup, map, widgets or statistics.

## 3. Binding principles

- Manual entry remains authoritative.
- Speech and AI never save a transaction automatically.
- Speech and AI never silently overwrite an amount, note or category selected by the user.
- Failure, denial, missing device support or model unavailability returns to the normal editor with user input intact.
- Receipt images, notes and recognition results are not uploaded by BillLocket.
- No API key, remote AI account, Firebase project or paid service is introduced.
- New Android/platform APIs stay behind narrow interfaces and can be replaced with fakes in tests.
- Domain and Room remain independent from speech-recognition and ML Kit types.
- No database migration is expected. A migration requires a separate decision and test plan before implementation.
- New user-visible text must exist in both Vietnamese and English resources before a task is complete.
- Accessibility semantics, keyboard behavior and large-font usability are acceptance criteria, not cleanup work.

## 4. Git and delivery workflow

Use this repository workflow for every task below:

1. Start from `codex/voice-localization-ai`, which was created from the report-ready baseline.
2. Implement one BL task at a time.
3. Keep commits task-scoped; do not combine formatting, dependency upgrades or unrelated UI refactors.
4. Run targeted tests while coding.
5. Run the task's final validation before marking it complete.
6. Commit only after `git diff --check` and the required Gradle tasks pass.
7. Push the integration branch after each accepted task so the work is recoverable remotely.
8. Merge to `main` only after BL-048 passes and product-owner device testing accepts the combined experience.
9. Use a normal fast-forward or reviewed merge. Never use `reset --hard` or force-push to move `main`.

Recommended commit sequence:

```text
docs: plan localization, voice notes and on-device AI
feat(localization): add Vietnamese and English app language support
feat(voice): add optional note dictation
feat(ai): suggest transaction categories from photos on device
test: complete smart-assistance regression gate
```

## 5. Delivery sequence

| Task | Scope | Dependency | Expected risk |
|---|---|---|---|
| BL-043 | Monthly photo recap | Complete | Complete |
| BL-044 | Preserve report-ready baseline and approve extension plan | Complete | Low |
| BL-045 | Vietnamese/English localization foundation | BL-044 | Implemented; automated gate passed, device acceptance pending |
| BL-046 | Optional voice-to-text for transaction notes | BL-045 | Medium |
| BL-047 | On-device photo category suggestion | BL-045 | Medium |
| BL-048 | Combined regression, device acceptance and release decision | BL-046, BL-047 | Medium |

BL-046 and BL-047 are technically independent after BL-045, but they should be delivered sequentially to keep review and rollback simple.

## 6. BL-044 — Baseline and roadmap — complete

### Completed work

- Verified the working tree was clean at `771324b`.
- Ran JDK 17 unit tests and all debug/androidTest/release assemble tasks successfully.
- Fast-forwarded `main` through the monthly-recap and location-correctness commits.
- Created and pushed `baseline/report-ready-2026-10-09`.
- Created `codex/voice-localization-ai` from the exact baseline.
- Recorded the implementation sequence and acceptance criteria in this document.

### Acceptance

- `main`, the rollback tag and the integration branch initially identify the same source snapshot.
- The known-good APK can be rebuilt without any new voice or AI dependency.
- No uncommitted application change is hidden in the baseline.

## 7. BL-045 — Vietnamese and English localization — implementation complete, device acceptance pending

### Implementation checkpoint — 2026-10-10

- Added AndroidX per-app locale handling for `System`, `vi-VN`, and `en-US` through `AppCompatDelegate`, `AppCompatActivity`, `locale_config`, and AndroidX automatic locale storage for API 26–32.
- Migrated normal V1 product copy, accessibility descriptions, widgets, notifications, validation feedback, and presentation errors into matching Vietnamese fallback and English resources. Both resource sets contain 495 matching string keys.
- Centralized locale-aware VND, date, time, percentage, distance, relative-time, month, and weekday presentation formatting without changing stored money, timestamps, coordinates, user-entered text, or the Room schema.
- Preserved custom category, account, and note text. Existing seeded names are not rewritten because V1 storage cannot safely distinguish every untouched system label from a user customization.
- Added focused locale mapping, formatter, and resource-parity unit tests.
- Independently verified under JDK 17: `test`, `assembleDebug`, `assembleDebugAndroidTest`, `assembleRelease`, `lint`, and `git diff --check` all pass.
- Physical-device validation was unavailable because the Realme RMX3370 was not connected over ADB. The implementation is committed as a recoverable task checkpoint, while language switching, restart persistence, widget refresh, draft preservation, and layout inspection on the real device remain mandatory before the BL-048 merge decision.

### Goal

Make all normal product journeys usable in Vietnamese and English, with an explicit `System / Tiếng Việt / English` preference and locale-correct formatting.

### Design investigation

Before implementation, choose and record one application-locale mechanism that supports API 26–35. Prefer the standard AndroidX application-locale path if it can be integrated without disturbing the Compose single-activity architecture. The decision must cover:

- locale persistence across process death and reboot;
- activity recreation after a language change;
- Android 13+ per-app language behavior;
- API 26–32 fallback behavior;
- widget and notification resource resolution;
- whether `MainActivity` needs an AppCompat base class and the regression impact.

Do not introduce a hand-written global mutable locale singleton.

### Resource migration

1. Treat the default `values/strings.xml` as the complete fallback set.
2. Add a complete `values-en/strings.xml` translation.
3. Add `values-vi/strings.xml` only if the selected fallback-language strategy requires it; avoid duplicate resources without a reason.
4. Move hard-coded Compose text, content descriptions, dialogs, validation messages, widget text and notification text into resources.
5. Group resource names by feature: `home_*`, `capture_*`, `transaction_*`, `map_*`, `widget_*`, `settings_*`, `recap_*`, `voice_*`, `ai_*`.
6. Keep domain failures typed; translate them only at the presentation boundary.
7. Preserve user-entered category/account/note text exactly.

### Default categories

Default categories use stable IDs. Localize their display names by stable system identity where practical while preserving custom names entered by the user. Do not rewrite user data merely because the application language changes. If the current database cannot distinguish system and custom display names safely, document the limitation and defer a schema change rather than guessing from translated text.

### Locale-aware formatting

- Replace hard-coded `Locale("vi", "VN")` presentation formatting with the selected application locale.
- Centralize money, date, time, percentage and distance formatting in testable formatters.
- Keep exact minor-unit money and ISO currency codes unchanged in the domain/database.
- Vietnamese examples may display `1.250.000 ₫`; English examples should follow the chosen English locale consistently.
- Address geocoding language may follow the app locale, but stored coordinates and confirmed place text must remain valid when language changes.
- Backup filenames remain filesystem-safe and locale-independent.

### UI and accessibility

- Add language selection under Settings.
- Language change must not lose an editor draft or duplicate navigation.
- Verify long English labels at large font scale.
- Verify widgets, Monthly Recap, map sheets, dialogs and bottom navigation do not clip.
- Keep `supportsRtl="true"`; verify that start/end alignment is used even though V1 languages are LTR.

### Tests

- Resource completeness/lint checks for Vietnamese and English.
- Formatter unit tests for both locales.
- ViewModel test for language preference persistence where logic exists.
- Compose/instrumented smoke tests for Home, editor, Settings and one widget-facing route in both locales where practical.
- Manual device pass: change language, kill/restart app, open widget, create/edit a transaction and return to the original language.

### Acceptance criteria

- No normal V1 journey exposes untranslated developer-facing text.
- Every new string exists in Vietnamese and English.
- Switching language persists and does not alter financial/media records.
- Dates, numbers and currency follow the active app locale.
- User-created names and notes are not translated or overwritten.
- Baseline flows pass in both languages.
- Final validation: `test`, `assembleDebug`, `assembleDebugAndroidTest`, `assembleRelease`, `git diff --check`.

## 8. BL-046 — Voice-to-text transaction notes

### Goal

Allow the user to dictate the optional transaction note in Vietnamese or English using Android's speech-recognition service.

### Scope

- Add a microphone affordance only to the transaction-note editor.
- Voice does not enter amount, date, category or account in this task.
- One press starts one bounded dictation session; continuous background listening is forbidden.
- The active app language selects `vi-VN` or the approved English language tag.

### Platform architecture

Create a narrow platform abstraction, for example:

```text
VoiceNoteTranscriber
  availability(locale)
  start(locale, partialResults)
  stop()
  cancel()
  close()
```

The Android implementation uses `SpeechRecognizer` and `RecognitionListener` on the main thread:

1. On API 31+, prefer `createOnDeviceSpeechRecognizer` when on-device recognition is available.
2. Otherwise use the system `createSpeechRecognizer` implementation.
3. Check service availability before showing an enabled action.
4. Call `destroy()` when the owner is disposed.
5. Add Android 11+ recognition-service manifest visibility only as required by the platform contract.

No OpenAI, Gemini, Google Cloud or other paid speech API is allowed in this task.

### Permission and privacy

- Add `RECORD_AUDIO` only with an in-context explanation.
- Denial leaves the keyboard note field fully usable.
- Permanent denial provides a Settings recovery action.
- Explain that fallback recognition may use the device's configured recognition service and may require network access.
- BillLocket does not retain microphone audio.

### State model

Use explicit states rather than booleans:

```text
Unavailable
Idle
RequestingPermission
Listening(partialText)
Processing
Completed(text)
Error(recoverableMessage)
```

Only one session may exist. Repeated taps cannot create multiple recognizers.

### Text behavior

- Empty note: insert recognized text.
- Existing note: append with normalized whitespace by default.
- Never replace an existing note silently.
- The user can edit the result before saving.
- A recognition error retains the pre-session note.
- Transaction save remains enabled according to existing financial validation, not speech state.

### UI behavior

- Idle microphone button with localized content description.
- Listening state with restrained animation and explicit stop/cancel action.
- Partial text is visually distinguishable until final.
- Keyboard and microphone transitions must not trap focus or leave the IME impossible to dismiss.
- Navigating away cancels and releases the recognizer.

### Tests

- Pure state-machine tests using a fake transcriber.
- Permission denied/permanently denied tests.
- Empty-note insert and existing-note append tests.
- Rapid-tap, cancel, error and lifecycle cleanup tests.
- Locale selection tests for `vi-VN` and English.
- Physical device: on-device available, fallback available, offline, airplane mode and unavailable-service behavior where the test device permits.

### Acceptance criteria

- A Vietnamese or English utterance can populate the note field on a supported device.
- Manual notes remain fully functional without permission, network or recognizer support.
- No audio is stored by BillLocket.
- Existing text is never lost after cancel/error.
- No recognizer survives navigation or lifecycle disposal.
- Final validation matches BL-045 plus a real-device dictation pass.

## 9. BL-047 — On-device image category suggestions

### Goal

Analyze a captured/imported transaction image locally and offer a dismissible category suggestion for common, visually identifiable purchases.

### Technology decision target

Use ML Kit on-device Image Labeling with the bundled default model unless a short implementation spike demonstrates a blocker. The bundled choice is preferred because it is available immediately, supports offline use and requires no cloud project or API key. Record the exact dependency version selected by the dependency catalog and the measured APK-size change.

Do not use a remote generative-vision API in this roadmap.

### Supported suggestion groups

The initial whitelist should remain intentionally small:

- food, meal, dish, restaurant, coffee, tea, beverage → food and drink;
- shirt, clothing, dress, footwear, bag → clothing/shopping;
- car, bus, train, motorcycle, fuel station → transport/fuel;
- medicine, pharmacy, healthcare item → health;
- book, stationery, laptop, office item → study/work;
- cinema, game, sports equipment → entertainment;
- furniture, home appliance → home.

Person, face, landscape, paper, generic text and low-information labels must not create a confident financial-category suggestion.

### Architecture

Keep ML Kit outside domain and ViewModels behind an interface such as:

```text
ImageCategorySuggestionEngine
  suggest(imageUri): CategorySuggestion?

CategorySuggestion
  semanticCategoryKey
  confidence
  supportingLabels
```

The mapping from image labels to semantic keys must be pure Kotlin and independently unit tested. Presentation resolves a semantic key to an active category of the correct transaction type using stable category identity, never translated display text.

### Trigger and lifecycle

- Run once for the primary normalized draft image after capture/import processing completes.
- Execute off the main thread and bound image decoding.
- Do not analyze every camera preview frame.
- Cancel obsolete work when the primary image changes or the draft closes.
- Keep the suggestion in draft/UI state only; no Room column is required.
- Do not rerun unnecessarily after rotation/process recreation if a valid draft suggestion is already available.

### Confidence policy

- Start with a conservative threshold around `0.70`, then calibrate using representative test images.
- Aggregate multiple supporting labels that map to the same semantic category.
- Return no suggestion when competing categories are too close or evidence is generic.
- Confidence is an aid, not a correctness guarantee.

### User experience

- Show a compact localized chip/card such as `AI gợi ý: Ăn uống`.
- Offer Apply and Dismiss; applying is an explicit user action.
- If the user manually changes category, AI must not change it afterward.
- Do not repeatedly show a dismissed suggestion for the same analyzed image in the current draft.
- Do not block Continue/Save while analysis runs.
- Missing model, corrupt image, unsupported format or inference failure is silent/recoverable and leaves manual selection unchanged.

### Privacy and observability

- Processing remains on device.
- Do not log image contents, notes, exact locations or raw label lists in production.
- Optional debug timing may record only duration and broad success/failure reason.
- Add clear Settings/About copy that AI suggestions are local and may be wrong.

### Tests

- Label-to-category mapping tests for every supported group.
- Threshold, aggregation, ambiguity and no-suggestion tests.
- ViewModel tests proving manual choice wins and failure does not block save.
- Instrumented image test with small licensed/generated fixtures for at least food, clothing and unknown/person-like input.
- Measure inference latency and memory on the course demonstration device.
- Verify both Vietnamese and English suggestion copy.

### Acceptance criteria

- Supported representative images produce reasonable optional suggestions.
- Unknown/ambiguous images do not force a category.
- Suggestions never overwrite a manual selection.
- The feature works offline with the chosen bundled model.
- Capture-to-editor latency remains acceptable; analysis does not block transaction entry.
- APK-size and dependency impact are documented.
- All financial features pass when the engine is replaced by an unavailable/failing fake.
- Final validation matches BL-045 plus physical-device image trials.

## 10. BL-048 — Combined regression and release decision

### Automated gate

Run under JDK 17:

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat assembleRelease
.\gradlew.bat lint
git diff --check
```

Confirm a normal build does not rewrite tracked source.

### Physical-device matrix

Test at minimum:

- Vietnamese and English cold start, process restart and language switch;
- capture with photo, import photo and no-photo transaction;
- manual note, Vietnamese dictation, English dictation, cancel and denied microphone permission;
- AI suggestion accepted, dismissed, unavailable and overridden manually;
- keyboard dismissal and Back behavior;
- location capture and map markers;
- transaction create/edit/delete/search;
- Home, Statistics, Accounts, Monthly Recap and backup entry points;
- all widget families after language change and transaction mutation;
- offline mode and invalid/missing Google Maps runtime configuration behavior;
- large font, TalkBack labels for new controls and dark/light themes.

### Release decision

Only after this gate may `codex/voice-localization-ai` be merged into `main`. The merge report must include:

- final commit and branch graph;
- dependency and APK-size changes;
- automated validation results;
- device/OS tested;
- known limitations for speech service availability and image-classification accuracy;
- rollback tag for the pre-feature report-ready build.

If voice or AI misses acceptance, localization may be merged independently only after its own device acceptance. Never hold the stable report build hostage to an optional assistance feature.

## 11. Explicitly deferred

- Voice entry for amount, account, date or category.
- Continuous/background listening.
- Cloud speech recognition controlled by a BillLocket API key.
- Receipt OCR for total/date/merchant extraction.
- Merchant database and line-item accounting.
- Custom-trained vision model.
- Remote generative AI analysis.
- Automatic transaction save or fully automatic category assignment.

Receipt OCR remains a logical later phase because a photo of a paper receipt often yields generic image labels. It requires a separate privacy, parsing, confidence and field-conflict design.

## 12. Definition of done

A task is done only when:

- its acceptance criteria are satisfied;
- user-visible copy is complete in Vietnamese and English;
- manual transaction entry still works when the new service is absent or failing;
- focused tests and the required final Gradle validation pass;
- `git diff --check` passes;
- documentation reflects actual implementation rather than intended implementation;
- no unrelated refactor or generated artifact is committed;
- the integration branch is pushed without rewriting `main` or the rollback tag.
