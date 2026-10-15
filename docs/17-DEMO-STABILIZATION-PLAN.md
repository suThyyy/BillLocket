# BillLocket demo stabilization plan

Status: approved implementation plan; execution not started  
Target device: Realme RMX3370, Android 13  
Primary objective: produce a stable final-project demo without weakening the existing offline-first transaction flow

## 1. Decision

Keep both experimental features, but change their implementation boundaries:

1. Photo capture is always the critical path. Location and AI must never delay or invalidate a captured photo.
2. Automatic location becomes best-effort metadata obtained from a warmed/cached fix. A missing fix is an ordinary state, not a capture error.
3. Speech-to-text remains based on Android `SpeechRecognizer`, but gets a complete observable state model and a live input-level indicator.
4. Photo intelligence becomes one hybrid pipeline. Bundled OCR and Image Labeling run independently and concurrently, then a deterministic fusion layer produces category and receipt-amount suggestions.
5. Category and amount results are human-in-the-loop suggestions. BillLocket never silently writes an AI value into a financial field; the user must tap Apply.
6. Receipt date extraction and Gemini remain explicitly deferred. They are useful report extensions, not demo-critical dependencies.
7. No AI result may overwrite user input, apply to a different primary photo, or write directly to the database.
8. “Done” requires a physical-device acceptance pass. Unit tests and a successful build are necessary but not sufficient.

This plan intentionally avoids cloud APIs, custom model training, background microphone capture, and automatic transaction saving. Those additions create privacy, cost, and demo-reliability risks without improving the core assessment enough.

## 2. Recommended ownership

The safest split is:

- Codex builds the base contracts and the UX-sensitive components in Base Sprint: atomic draft mutation, capture/location state contract, voice level event/state contract, and the two reusable status components.
- Antigravity implements the bounded tasks after the base compiles and its tests pass.
- Codex performs final review against this document and the physical-device evidence.

If Antigravity must do everything, give it one prompt at a time from `docs/18-ANTIGRAVITY-TASK-PROMPTS.md`. Do not give it the whole roadmap as a single task. Each task must be committed and tested independently before the next begins.

## 3. Product success criteria

### 3.1 Capture and location

- Pressing the shutter never waits for a fresh GPS/network fix.
- From CameraX `onImageSaved` to a visible thumbnail: target p50 <= 700 ms, p95 <= 1,500 ms on the Realme device.
- A failed or unavailable location never produces “Không thể chụp ảnh” and never removes the image.
- The first photo and subsequent photos behave consistently.
- The UI distinguishes `location attached`, `no location yet`, and `location unavailable` without using an error-red treatment for the latter two.
- Denying location permission leaves camera, import, continue, and save fully functional.

### 3.2 Speech-to-text

- The user can distinguish `starting microphone`, `listening`, `speech detected`, and `processing`.
- The level meter visibly reacts to quiet/normal/loud speech using `onRmsChanged` and falls toward idle during silence.
- The stop action uses a stop-square icon and text/content description; it must not look like a confirmation check mark.
- Vietnamese and English dictation each pass at least 8 of 10 short, predefined phrases online on the Realme device in a quiet room.
- Offline/unavailable recognition fails gracefully and manual notes remain editable.
- Cancel/error never changes the pre-session note.
- No microphone audio bytes are stored, logged, or uploaded by BillLocket.

### 3.3 Photo intelligence: category and receipt amount

- Analysis never blocks editor interaction or transaction saving.
- OCR and Image Labeling run concurrently under a failure-isolated coroutine boundary. Failure of one source does not cancel the other.
- Analysis remains invisible for the first 400 ms. If it lasts longer, a compact non-blocking inline status may appear so the feature does not look broken.
- Category and amount results are displayed as Apply/Dismiss suggestions; neither is silently autofilled.
- On the agreed evaluation set, shown suggestions target >= 80% precision. Coverage may be lower; it is preferable to return no suggestion than a wrong confident suggestion.
- High-confidence receipt amount suggestions target >= 85% exact-match precision before they are enabled in the demo build.
- Receipt OCR and image labeling work offline by using bundled models.
- User-edited amount/category always wins, including after rotation, process recreation, image reorder, and a late AI result.
- The report records OCR/labeling/fusion latency, category precision/coverage, amount exact-match precision, APK-size delta, and known limitations.

## 4. Non-goals

- Do not silently autofill any AI-derived field. A high-confidence receipt amount may only be offered as an explicit suggestion.
- Do not infer or modify account or note from OCR.
- Do not apply receipt dates in the demo iteration; date extraction may be evaluated as future work only.
- Do not save a transaction automatically.
- Do not continuously listen in the background.
- Do not add OpenAI, Gemini, Firebase-hosted models, or paid speech/vision APIs to the demo implementation. Gemini belongs only in future-work documentation.
- Do not train a custom classifier for the final demo unless all tasks in this plan are already accepted.
- Do not redesign unrelated screens or change the established black/yellow BillLocket visual language.
- Do not uninstall the app or clear application data on the Realme device.
- Never use `connectedDebugAndroidTest`, `installDebugAndroidTest`, or an equivalent Gradle install task on the user's daily device.
- JVM tests, lint, and builds run on the computer and must not involve ADB.
- Reuse the installed app for repeated UI checks. `adb shell am start`, scoped `logcat`, accessibility inspection, screenshots, and manual interaction do not require reinstalling it.
- Installing a new build requires an explicit notice to the user and exactly one `adb install -r` of the main app APK. Do not add `-d` unless a downgrade is actually required.
- Do not install the test APK automatically. Run `am instrument` only when a matching test APK is already installed. If a new instrumentation test genuinely requires a new test APK, explain the extra OEM Install prompt and obtain explicit approval first.

## 5. Architecture overview

```text
CameraX onImageSaved
        |
        v
normalize image off main thread
        |
        +----> persist/show DraftImage immediately ----> editor remains usable
        |
        `----> attach only already-warmed location within a tiny budget
                    failure = neutral metadata state

Primary draft image
        |
        `----> supervisorScope ---------------------------------------------+
                 |                                                          |
                 +----> bundled Latin OCR                                   |
                 |       +-- receipt likelihood                             |
                 |       +-- amount candidates                              |
                 |       `-- sanitized semantic evidence                    |
                 |                                                          |
                 `----> existing image labels --> visual evidence ----------+
                                                                            |
                                                                            v
                                                        deterministic evidence fusion
                                                                            |
                                                        +-------------------+------------------+
                                                        |                                      |
                                               category suggestion                    amount suggestion
                                                        |                                      |
                                                        `--------------- Apply / Dismiss ------'

Microphone tap
        |
        v
availability -> permission -> starting -> ready/listening(level, partial text)
                                      -> processing -> completed/error
```

No location, OCR, label, or speech dependency is allowed to become a prerequisite for manual transaction entry.

## 6. Delivery sequence

### Gate G0 — protect the known-good product

Before feature work:

1. Record current commit, branch, APK hash, and installed app version.
2. Export and verify a portable backup. Do not test restore against the user's live data.
3. Create a rollback tag from the current known-good state if one does not already exist.
4. Run the existing focused 95 tests and the normal build gate under JDK 17.
5. Create a dedicated stabilization branch. Do not develop directly on `main`.
6. Record baseline timings for five first captures and five subsequent captures with automatic location enabled.

Required evidence:

- commit/hash table;
- test/build output;
- capture timing table;
- confirmation that app data was not cleared.

### Base Sprint — contracts Antigravity must not invent

This sprint should be implemented or reviewed by Codex before delegation.

#### BASE-01 — atomic draft image mutation

Add a narrow mutation to `TransactionDraftStore`, implemented under the existing `SharedPreferencesTransactionDraftStore` mutex:

```kotlin
suspend fun updateImageLocation(
    draftId: DraftId,
    imageId: String,
    location: PhotoLocation?,
): Boolean
```

Rules:

- Load and update inside the same mutex critical section.
- Return `false` when the draft or image no longer exists.
- Change only `confirmedLocation` and `updatedAtEpochMillis`.
- Preserve amount, note, category, account, image order, suggestion metadata, and dirty state.
- Never recreate a removed image.
- Add tests for concurrent metadata update and image-location update.

Relevant files:

- `feature/transaction/draft/TransactionDraft.kt`
- `feature/transaction/draft/SharedPreferencesTransactionDraftStore.kt`
- existing draft store tests

#### BASE-02 — capture/location UI state contract

Add an ephemeral presentation state, not a Room column:

```kotlin
sealed interface CaptureLocationStatus {
    data object NotRequested : CaptureLocationStatus
    data object Resolving : CaptureLocationStatus
    data object Attached : CaptureLocationStatus
    data object Unavailable : CaptureLocationStatus
}
```

Store it by image ID in `CaptureState`. It is presentation feedback only; the source of truth for a successfully attached location remains `DraftImage.confirmedLocation`.

Do not add a global full-screen location spinner.

#### BASE-03 — voice event/state contract

Extend the transcriber event model:

```kotlin
sealed interface VoiceTranscriberEvent {
    data object Ready : VoiceTranscriberEvent
    data object SpeechStarted : VoiceTranscriberEvent
    data class AudioLevel(val normalized: Float) : VoiceTranscriberEvent
    data class PartialResult(val text: String) : VoiceTranscriberEvent
    data object Processing : VoiceTranscriberEvent
    data class FinalResult(val text: String) : VoiceTranscriberEvent
    data class Error(val error: VoiceRecognitionError) : VoiceTranscriberEvent
    data object Cancelled : VoiceTranscriberEvent
}
```

Use these explicit listening phases:

```kotlin
enum class VoiceListeningPhase { STARTING, READY, SPEECH_DETECTED }

data class Listening(
    val phase: VoiceListeningPhase,
    val partialText: String,
    val inputLevel: Float,
)
```

`inputLevel` must always be finite and clamped to `0f..1f`.

#### BASE-04 — reusable UX-sensitive components

Build two components before Antigravity edits screens:

1. `VoiceInputMeter`: five vertical rounded bars, total width 34–42 dp, height 22–26 dp, primary yellow, minimum bar height 4 dp, maximum 22 dp. Animation duration 90–140 ms. It receives only normalized level and does not access the microphone.
2. `AnalysisStatusCard`: compact, field-agnostic editor card supporting `Analyzing`, a generic `Suggested` headline (usable for category or amount), `NoSuggestion`, and a visually neutral `Failed`. It must preserve the current visual language and occupy no more than roughly 80 dp in its normal expanded state. Automatic analysis integration should normally omit `Failed` rather than interrupt the user with a red error.

Both components require previews for dark and light themes and content descriptions that do not announce every audio-level update.

#### Base Sprint implementation status — 2026-10-14

- `BASE-01` complete: the draft store now exposes an atomic, image-ID-scoped location update; instrumentation coverage includes concurrent metadata/location mutation and removed-image protection.
- `BASE-02` complete: capture state has per-image `NotRequested`, `Resolving`, `Attached`, and `Unavailable` contracts. Screen wiring remains intentionally assigned to `LOC-01/02`.
- `BASE-03` complete: voice listening phases and bounded input-level events are present, with coordinator unit coverage. OEM RMS mapping remains intentionally assigned to `VOICE-01/02`.
- `BASE-04` complete: reusable five-bar voice meter and field-agnostic analysis status card have dark/light previews. Product-screen wiring remains intentionally deferred.
- Automated checkpoint: 194 debug unit tests passed, debug app and debug test APK assembled, Android lint passed, and `git diff --check` passed.
- Physical checkpoint: the Realme disconnected from ADB before the final five-test draft-store instrumentation run. Device acceptance is therefore **pending**, not passed. After reconnecting, run `adb shell am instrument -w -e class com.billlocket.app.TransactionDraftStoreTest com.billlocket.app.test/androidx.test.runner.AndroidJUnitRunner` before accepting `LOC-01`.

### Sprint 1 — make capture fast and location non-blocking

#### LOC-01 — remove location from the critical path

Current behavior to remove:

- `normalizeAndAddNow` waits up to 8.5 seconds for location.
- If prefetched location fails, it calls `deviceLocation.current()` again on the photo path.
- The global processing state stays active until location finishes.

Replacement behavior:

1. Continue warming location when the capture screen opens and permission is already granted.
2. When the shutter is pressed, remember the current prefetch handle/result but do not start a blocking location request.
3. After `onImageSaved`, normalize the file off the main thread.
4. Construct, persist, and display `DraftImage` immediately.
5. Clear the global image-processing state immediately after image persistence.
6. Consume a successful warmed result if already available. Waiting for an in-flight warm result is allowed for at most 150 ms after normalization.
7. Never fall back to a fresh multi-provider call on the capture completion path.
8. If no eligible fix is ready, set the per-image presentation status to `Unavailable`; keep the image.
9. Keep warming the location port so a later capture can use its cache.

The 150 ms value is a hard upper bound, not a target. Put it in a named constant and unit-test the timeout path.

#### LOC-02 — location messaging and controls

Thumbnail behavior:

- Resolving: tiny neutral progress ring plus localized “Đang lấy vị trí”.
- Attached: location pin/check, no success snackbar.
- Unavailable: muted location-off icon and localized “Chưa có vị trí”. This is not red.
- Tapping the status from the editor opens the existing manual location screen.

Copy:

| State | Vietnamese | English |
|---|---|---|
| Resolving | Đang lấy vị trí | Getting location |
| Attached | Đã thêm vị trí | Location added |
| Unavailable | Chưa có vị trí · Có thể thêm sau | No location · Add later |
| Permission denied | Không có quyền vị trí. Ảnh vẫn được lưu. | Location permission is off. Your photo is still saved. |

Do not show “Không lấy được vị trí chính xác” as a capture error after every photo.

#### LOC-03 — location tests

Add tests proving:

- normalized image is added before an unresolved location result;
- unresolved and failed location never blocks `Continue`;
- first-capture timeout is <= the named 150 ms budget in virtual time;
- late/stale location cannot attach to a removed image;
- permission denial leaves the photo intact;
- two rapid captures attach results only to their own image IDs;
- manual location still overrides absent automatic location;
- Vietnamese/English copy has key parity.

Physical matrix:

1. Cold app, GPS on, first photo immediately after screen opens.
2. Cold app, wait five seconds, first photo.
3. Second photo.
4. GPS off.
5. Location permission denied.
6. Airplane mode with GPS on.
7. Continue to editor immediately after capture.

Editor control acceptance on the Realme must additionally verify through accessibility bounds that the thumbnail/location action and delete action are separate, non-overlapping targets of at least 48 dp. A JVM test that invokes locally declared callbacks is not UI evidence and must not be reported as such.

### Sprint 2 — trustworthy speech-to-text UX

#### VOICE-01 — OEM-tolerant availability and lifecycle

Change availability logic so a false/empty `queryIntentServices(..., MATCH_DEFAULT_ONLY)` result cannot by itself disable a configured OEM recognition service. Availability should primarily use:

- `SpeechRecognizer.isOnDeviceRecognitionAvailable(context)` where supported; or
- `SpeechRecognizer.isRecognitionAvailable(context)`.

Creation remains guarded with `runCatching`; creation/start failure becomes a recoverable `ServiceUnavailable` or `ClientError` state.

Rules:

- All recognizer methods remain on the main thread.
- One session owns one recognizer generation.
- `cancel`, `stop`, and `close` remain idempotent.
- Destroy recognizer on terminal result/error and when leaving the editor.
- Ignore callbacks from an older generation.
- Do not log recognized text or raw audio-level streams.

#### VOICE-02 — normalized audio level

Implement `onRmsChanged` instead of discarding it.

Recommended mapping:

```kotlin
val raw = ((rmsDb + 2f) / 12f).coerceIn(0f, 1f)
val smoothed = previous * 0.72f + raw * 0.28f
```

Guard non-finite input. Emit at most once every 50 ms to avoid excessive recomposition. Reset to zero on start, processing, error, cancel, and completion. This is visualization metadata only; do not retain buffers from `onBufferReceived`.

#### VOICE-03 — watchdogs and state transitions

Required transitions:

```text
Idle -> RequestingPermission -> Listening(STARTING)
Listening(STARTING) -> Listening(READY)
Listening(READY) -> Listening(SPEECH_DETECTED)
Listening(any) -> Processing -> Completed/Error
Listening/Processing -> cancel -> Idle
```

Watchdogs:

- Start/ready watchdog: 5 seconds. If no ready/begin/error callback arrives, cancel and show service unavailable.
- Processing watchdog: keep the existing 8 seconds; use the last nonblank partial result only as a fallback.
- Optional session ceiling: 30 seconds, then call stop and process.

Do not silently commit partial text while still listening.

#### VOICE-04 — final interaction design

Listening capsule specification:

- Height: 52 dp; corner radius: 18–22 dp.
- Left: mic icon plus `VoiceInputMeter`.
- Center line 1: phase label. Center line 2: partial text, ellipsized to one line.
- Right primary action: stop-square icon, not check.
- Right secondary action: close/cancel icon.
- READY copy: “Đang nghe · Hãy nói” / “Listening · Start speaking”.
- SPEECH_DETECTED copy: “Đã nhận âm thanh” / “Voice detected”.
- If partial text exists, it takes visual priority over the second label.

Accessibility:

- Meter is decorative and hidden from accessibility services.
- Announce phase changes only, not each level update.
- Touch targets are at least 48 dp even if the visible icons are smaller.
- Large font must not overlap stop/cancel actions.

#### VOICE-05 — device failure handling

Show actionable outcomes:

- No speech: “Không nghe rõ giọng nói. Hãy thử lại gần mic hơn.”
- Network required/unavailable: “Dịch vụ nhận dạng có thể cần Internet trên thiết bị này.”
- Busy: cancel/destroy the recognizer, wait for user retry; do not auto-loop.
- Permanent permission denial: open app settings.
- Service unavailable: retain manual input and explain that keyboard entry still works.

A system `ACTION_RECOGNIZE_SPEECH` activity fallback is optional and must be a separately reviewed task. Do not add it in the same commit as the state/UI rewrite.

#### VOICE-06 — tests and device script

Unit tests:

- RMS normalization: negative, zero, loud, NaN, infinity.
- Throttling and smoothing.
- phase transition order;
- ready timeout, session ceiling, processing timeout;
- old-generation RMS/result ignored;
- cancel resets level and preserves note;
- final result appends exactly once;
- rotation/disposal closes recognizer.

Realme phrase set, repeated in Vietnamese and English:

1. Ăn sáng với bạn.
2. Mua cà phê ở cửa hàng.
3. Đổ xăng cho xe máy.
4. Mua sách và bút.
5. Thanh toán tiền thuốc.
6. Breakfast with a friend.
7. Coffee at the shop.

8. Fuel for the motorbike.
9. Bought a book and a pen.
10. Paid for medicine.

#### Sprint 2 checkpoint — 2026-10-15

`VOICE-01` through `VOICE-04` are implemented on `codex/demo-stabilization`:

- ColorOS availability falls back to the configured secure recognition service when the framework preflight incorrectly returns `false`.
- The framework default binding path is preferred; explicit component binding remains a construction fallback.
- RMS input is normalized, smoothed, throttled, and displayed by the five-bar meter without retaining audio buffers.
- The editor exposes `STARTING`, `READY`, `SPEECH_DETECTED`, and `Processing`, with separate 48 dp stop and cancel targets.
- Ready, session, and processing watchdogs prevent an unresponsive provider from trapping the editor.
- The focused voice suite passes 46 JVM tests. The Realme validation passed the permission rationale, Android permission request, enabled mic entry point, listening capsule, and service-unavailable recovery.

Realme demo preflight: Android can auto-revoke `RECORD_AUDIO` from the configured Google app after long inactivity. When that happens, BillLocket correctly reaches `Starting microphone…` and exits through the five-second watchdog, but Google never emits `onReadyForSpeech`. Before the final demo, open **Settings > Apps > Google > Permissions > Microphone** and choose **Allow only while using the app**, then verify one Vietnamese and one English phrase. This is a device/provider permission and must not be changed silently by BillLocket. Manual note entry remains available when the provider is unavailable.

Record expected, recognized, pass/fail, network state, latency to ready, and latency to final text. Do not record or retain audio.

### Sprint 3 — concurrent OCR, image labeling, and transaction suggestions

#### AI-01 — dependency and scope

Add bundled Latin-script text recognition through the version catalog:

```text
com.google.mlkit:text-recognition:16.0.1
```

The bundled model is selected because the demo must work immediately offline. The official ML Kit guide estimates about a 4 MB increase per script architecture; measure the actual BillLocket APK delta rather than copying that estimate into the report.

Vietnamese and English both use Latin script. Do not add Chinese/Japanese/Korean recognizers.

#### AI-02 — stable input, evidence, and suggestion contracts

Do not put OCR parsing inside `TransactionEditorViewModel`. Introduce:

```kotlin
data class AiEvidence(
    val source: EvidenceSource,
    val normalizedTerm: String,
    val confidence: Float,
    val semanticKey: SemanticCategoryKey? = null,
)

enum class EvidenceSource {
    OCR_TOTAL_KEYWORD,
    OCR_MERCHANT,
    OCR_KEYWORD,
    IMAGE_LABEL,
}

data class AmountSuggestion(
    val minorUnits: Long,
    val confidence: Float,
    val evidence: List<AiEvidence>,
)

data class CategoryAiSuggestion(
    val semanticKey: SemanticCategoryKey,
    val confidence: Float,
    val evidence: List<AiEvidence>,
)

data class TransactionAiSuggestion(
    val imageKey: String,
    val amount: AmountSuggestion? = null,
    val category: CategoryAiSuggestion? = null,
)

interface ReceiptTextExtractor {
    suspend fun extract(imageUri: String): ExtractedReceiptText
}

interface TransactionPhotoAnalysisEngine {
    suspend fun analyze(imageKey: String, imageUri: String): TransactionAiSuggestion
}
```

Use BillLocket's real `CategoryId`/stable semantic mapping; never introduce a numeric `Long` category ID or map by translated display text. `ExtractedReceiptText` may contain raw lines in memory only. Raw receipt text must not be persisted in the draft, Room, analytics, exception messages, or logs. Persist only sanitized approved evidence when persistence is necessary.

#### AI-03 — OCR normalization

Pure Kotlin normalization pipeline:

1. Unicode lowercase using `Locale.ROOT`.
2. Normalize repeated whitespace.
3. Retain Vietnamese letters; do not strip diacritics as the only representation.
4. Produce both accented and diacritic-folded lookup forms.
5. Remove phone numbers, likely account/card numbers, timestamps, and long digit sequences from semantic evidence. Amount parsing receives a separate bounded numeric representation.
6. Match whole tokens/phrases, not arbitrary substrings.
7. Cap processed lines and characters to a documented bound.

No raw OCR content may appear in exception messages or production logs.

#### AI-04 — initial deterministic dictionary

Start small and auditable. Suggested groups:

- Food/drink: `cafe`, `coffee`, `cà phê`, `restaurant`, `nhà hàng`, `quán ăn`, `trà sữa`, `phở`, `cơm`, `bakery`.
- Transport/fuel: `petrolimex`, `pvoil`, `xăng`, `fuel`, `grab`, `be`, `taxi`, `parking`, `gửi xe`.
- Health: `pharmacy`, `nhà thuốc`, `long châu`, `pharmacity`, `medicine`, `thuốc`.
- Study/work: `book`, `sách`, `văn phòng phẩm`, `stationery`, `notebook`, `printer`.
- Clothing/shopping: `clothing`, `fashion`, `áo`, `quần`, `giày`, `shoe`.
- Entertainment: `cinema`, `movie`, `cgv`, `lotte cinema`, `game`, `bowling`.
- Home: `furniture`, `nội thất`, `gia dụng`, `appliance`, `cookware`.

Treat named merchants as a versioned local dictionary with an explicit default semantic category. Do not claim that every merchant purchase belongs to that category; the UI must still call the result a suggestion.

#### AI-05 — receipt detection and amount extraction

Generate amount candidates from currency-like values, but never select the first or largest number by default. Score candidates from nearby normalized context.

Strong total markers include `tổng cộng`, `thành tiền`, `số tiền`, `grand total`, `total`, and `amount`. Negative markers include `tiền khách đưa`, `cash received`, `tiền thừa`, `change`, line-item quantity, phone/account patterns, and tax-only lines.

Policy:

- amount must be positive and fit the existing VND parser/domain bounds;
- minimum amount confidence starts at 0.90;
- ambiguous top candidates return no amount suggestion;
- amount output is an Apply/Dismiss suggestion, never silent autofill;
- parser is pure Kotlin and separately unit-tested;
- do not add merchant, item, tax, receipt number, or payment-method fields.

#### AI-06 — concurrent analysis and category fusion

OCR and Image Labeling must start together under `supervisorScope`. Await each result independently so one engine failure does not discard usable evidence from the other. Do not launch analysis until the editor can already render the normalized photo.

Starting category weights:

- exact merchant phrase: 0.82;
- strong OCR keyword: 0.72;
- weak OCR keyword: 0.55;
- existing image label: use its model confidence, capped at 0.70 for hybrid scoring.

Combine independent evidence for one category using:

```text
combined = 1 - product(1 - evidenceConfidence)
```

Policy:

- minimum result confidence: initially 0.72;
- minimum lead over second category: 0.12;
- require at least one OCR merchant/keyword OR one image label >= 0.65;
- no result when evidence conflicts within the ambiguity margin;
- calibrate thresholds only from the evaluation set, not from one successful demo image.

Keep all constants named and unit-tested. Use purpose-specific bounded input: start benchmarking around a 640 px long edge for labeling and 1280–1600 px for OCR. Do not force bitmap sharing if it increases memory retention or couples resource lifecycles.

Timeout values must be calibrated on the Realme device. Initial safety ceilings are 3 seconds for labeling and 5 seconds for OCR; they are not UI waits because the editor is already usable. A result is discarded when its image key is stale, the user has made the relevant field ineligible, or the editor has closed.

#### AI-07 — editor integration and UI

States:

- Processing shorter than 400 ms: show nothing.
- Longer processing: compact inline “Đang phân tích ảnh trên thiết bị…”; editor/save remains enabled.
- Category suggestion: category name, confidence rendered as `Cao` or `Vừa`, one sanitized evidence explanation, Apply and Dismiss.
- Amount suggestion: formatted VND amount plus a short total-marker explanation, Apply and Dismiss.
- NoSuggestion: optional neutral “Chưa đủ thông tin để gợi ý”; auto-collapse after about three seconds.
- Engine failure: silent for automatic assistance. Do not show an error-red card or snackbar; manual controls remain unchanged.

Examples:

- “Gợi ý: Ăn uống · Nhận diện ‘cà phê’ trên hóa đơn”.
- “AI suggestion: Transport · Found ‘Petrolimex’ on the receipt”.
- “Số tiền trên hóa đơn: 84.000 ₫ · Áp dụng”.

Only display approved sanitized dictionary/total-marker terms. Never display an arbitrary full OCR line that may contain personal information. Applying a suggestion must go through the existing editor validation path; AI never writes directly to Room.

Use narrow field protection rather than refactoring the entire form into generic wrappers:

```kotlin
data class AiFieldProtection(
    val amountEditedByUser: Boolean,
    val categoryEditedByUser: Boolean,
)
```

Manual amount/category edits immediately make the corresponding late suggestion ineligible. Explicitly applying a suggestion is recorded as an AI-origin action but remains user-authorized.

#### AI-08 — evaluation set and calibration

Create a local, non-user dataset excluded from production packaging:

- 50–70 ordinary/object photos distributed across supported categories and negative/ambiguous cases;
- 30–50 clear and difficult receipts with ground-truth total and category;
- both Vietnamese and English/ASCII receipt text where available.

Include both receipts and clear object photos. Do not copy the user's private transaction photos into Git.

Report:

```text
precision = correct shown suggestions / all shown suggestions
coverage  = all shown suggestions / all test images
accuracy  = correct suggestions / all test images
amount exact-match precision = correct shown amounts / all shown amount suggestions
```

Release gates prioritize category precision >= 80% and shown-amount exact-match precision >= 85%. Do not inflate coverage by lowering thresholds until wrong suggestions become common. Record OCR, labeling, fusion, and end-to-result latency separately.

### Sprint 4 — integration, regression, and report evidence

#### REG-01 — automated gate

Run under JDK 17:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat assembleRelease
.\gradlew.bat lint
.\gradlew.bat spotlessCheck
git diff --check
```

Review test-result XML, not only the final Gradle exit code.

#### REG-02 — physical-device gate

Execute in this order:

1. Existing manual transaction without photo.
2. Capture with location permission denied.
3. Cold first capture with location enabled.
4. Import an image.
5. Continue/save while AI is analyzing.
6. AI suggestion apply, dismiss, manual override, no suggestion, engine failure.
7. Vietnamese and English voice phrase matrix.
8. Voice cancel, silence, denied permission, offline mode.
9. Create/edit/search/delete/undo.
10. Map marker behavior for photo with and without location.
11. All widgets, Monthly Recap, backup export.
12. Light/dark theme, 1.3x font scale, TalkBack labels.

No destructive restore test is required on the live dataset.

#### REG-03 — stop/go criteria

Ship all three features when every product success criterion passes.

Fallback decisions:

- If speech transcription remains unreliable but mic states/level work: keep voice behind a `Beta` label or disable its entry point for the final build; do not remove manual notes.
- If hybrid AI precision is below 80%: keep the pipeline in the report as an evaluated prototype but hide the suggestion card in the release/demo build with one local feature flag.
- If shown receipt amounts miss 85% exact-match precision: keep amount extraction as an evaluated prototype and disable only the amount suggestion while retaining category assistance.
- If location still delays capture: disable automatic location at capture and retain manual location editing/map support.
- Localization ships independently and must never be rolled back because an optional feature fails.

## 7. Commit strategy

### Mandatory branch and review checkpoints

All stabilization work must live on `codex/demo-stabilization`, not directly on `main`. The first accepted checkpoint may create that branch from the current HEAD while preserving the existing uncommitted Base Sprint + LOC-01 working tree.

Use this lifecycle for every delegated task:

1. Confirm the current branch, working tree, and accepted starting commit before implementation.
2. Antigravity implements exactly one task and runs its validation, but does **not** commit.
3. Codex reviews the diff and evidence. Fixes remain in the same uncommitted task until Codex explicitly accepts it.
4. Run the separate checkpoint prompt from `docs/18-ANTIGRAVITY-TASK-PROMPTS.md` only after acceptance.
5. Stage accepted files with explicit paths and create one checkpoint commit. Never use `git add .` or `git add -A` in a mixed working tree.
6. Use that checkpoint hash as `<BASE_COMMIT>` for the next prompt. Do not start the next implementation task before the checkpoint is clean.

Never commit `docs/BILLOCKET-DEFENSE-HANDBOOK.md`, `local.properties`, secrets, APKs, build outputs, private transaction media, device logs, or unrelated user changes. If the handbook is already staged, unstage it with `git restore --staged -- docs/BILLOCKET-DEFENSE-HANDBOOK.md` without deleting or modifying the file. Never push, amend, rebase, merge, reset, or clean unless the user separately and explicitly authorizes that operation.

The accumulated Base Sprint + accepted LOC-01 work is the one documented exception to the one-task-per-commit preference. Its first checkpoint should use:

```text
feat(stabilization): establish base and unblock photo capture
```

After that checkpoint, return to one accepted concern per commit.

One concern per commit:

1. `test(demo): add stabilization baselines and fixtures`
2. `refactor(draft): add atomic image location mutation`
3. `fix(capture): remove location from photo critical path`
4. `feat(capture): show non-blocking location status`
5. `refactor(voice): expose listening phase and input level`
6. `feat(voice): add responsive listening meter and clear controls`
7. `fix(voice): harden OEM recognizer availability and watchdogs`
8. `feat(ai): add bundled receipt text extraction`
9. `feat(ai): parse high-confidence receipt totals`
10. `feat(ai): run OCR and labeling concurrently`
11. `feat(ai): fuse OCR and image-label evidence`
12. `feat(ai): add explicit category and amount suggestions`
13. `test(device): document Realme acceptance evidence`

Do not mix formatting sweeps, dependency upgrades, or unrelated UI refactors into these commits.

## 8. Definition of done for every delegated task

Antigravity must return:

1. exact files changed;
2. behavioral summary;
3. tests added and exact commands run;
4. build/test results;
5. screenshots or device evidence when UI/platform behavior changed;
6. known limitations;
7. confirmation that no app data was cleared and no secrets were committed;
8. a small, reviewable diff.

Claims such as “implemented”, “tests pass”, or “should work” without physical evidence do not satisfy device acceptance.

## 9. Estimated schedule

| Work | Focused effort | Risk |
|---|---:|---|
| G0 + Base Sprint | 1–1.5 days | Low |
| Sprint 1 location/capture | 1 day | Low–medium |
| Sprint 2 voice | 1.5–2 days | Medium |
| Sprint 3 concurrent OCR + hybrid AI + amount | 3–4 days | Medium–high |
| Sprint 4 device regression/report | 1–1.5 days | Medium |

Expected total: roughly 7.5–10 focused development days, assuming the Realme device remains available for short validation loops.

## 10. Recommended next action

Do not send the entire implementation to Antigravity yet. First ask Codex to implement and verify the Base Sprint. Then delegate `LOC-01`, `LOC-02`, and `LOC-03` as separate tasks. After capture is accepted on the physical device, proceed to voice, then AI.

This order maximizes the chance of a stable demo: the camera experience is protected first, the higher-feasibility voice feature is completed second, and the higher-variance AI work cannot destabilize either one.
