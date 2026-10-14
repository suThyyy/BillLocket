# Antigravity execution prompts

Use one prompt at a time. Prefer replacing `<BASE_COMMIT>` with the accepted commit from the preceding task. If the Base Sprint is still an uncommitted working-tree checkpoint, the fallback instruction in the global preamble applies; do not run two agents against that working tree. Do not allow Antigravity to proceed to the next prompt when acceptance evidence is missing.

Current handoff status (2026-10-14): Base Sprint and `LOC-01` are accepted in checkpoint `804beb8` on `codex/demo-stabilization`. `LOC-02/03` has passed Codex review under the demo happy-path profile and is ready for its separate checkpoint. Prompt 3 must start from that resulting checkpoint hash.

## Global preamble to prepend to every prompt

```text
You are working in the BillLocket Android repository.

Read docs/17-DEMO-STABILIZATION-PLAN.md completely before changing code. This task is intentionally narrow. Do not implement later roadmap tasks, redesign unrelated UI, upgrade unrelated dependencies, mass-format files, clear device data, uninstall the app, or modify secrets/local.properties.

Preserve the offline-first transaction flow. Camera capture, manual notes, manual category selection, Continue, and Save must remain functional when location, speech, OCR, or image labeling fails. Keep Vietnamese and English string key parity. Never log receipt text, recognized speech text, exact coordinates, image contents, or microphone buffers.

Use JDK 17. Inspect existing code/tests before editing. Preserve user changes. Implement only the requested task, add focused tests, run the stated validation, and return: files changed, behavior, tests/commands/results, device evidence if requested, known limitations, and current HEAD hash. Do not claim physical-device success unless you actually executed the stated matrix on the connected device.

Terminal execution policy:
- Run ordinary read-only inspection commands and safe in-repository build/test commands directly; do not stop to ask the user conversationally for permission before each command.
- This includes rg/Get-Content/git status/git diff, Gradle build/unit-test/lint tasks, adb devices, logcat filtering, and narrowly scoped non-destructive device inspection required by this task.
- Group related validation tasks into as few terminal invocations as practical so the user is not interrupted repeatedly.
- Never bypass a platform approval dialog. If the execution environment itself requires approval, make one narrowly scoped approval request containing the exact command category and reason, then continue automatically after approval.
- Ask the user before destructive or externally consequential actions: deleting/resetting files, git reset/clean, uninstalling the app, clearing app data, revoking permissions, overwriting user data, publishing/pushing, or installing/upgrading unrelated dependencies.
- If a safe command fails because of sandbox/permission restrictions, retry through the environment's official approval mechanism once. Do not replace it with a riskier workaround and do not repeatedly ask the same question in chat.

Physical-device deployment policy:
- Never run `connectedDebugAndroidTest`, `installDebugAndroidTest`, or any Gradle task that installs APKs on the user's Realme.
- JVM tests, lint, and APK assembly run on the computer without ADB.
- Repeated UI validation must reuse the installed app via `adb shell am start`, scoped inspection, screenshots, and manual interaction.
- Do not run `adb install`, `adb uninstall`, or `pm clear` without explicit user approval. When a changed build must be tested, announce it first and install only the main app APK once with `adb install -r`; do not use `-d` unless a downgrade is necessary.
- Never install the test APK automatically. `adb shell am instrument` is allowed only when the matching test package is already installed. If it is absent, skip instrumentation unless the user explicitly approves the additional test-APK installation and its separate OEM Install prompt.
- Never describe `adb install -r` as "not reinstalling". It preserves app data but is still an APK installation and may display an OEM confirmation dialog.

Mandatory Git workflow:
- Before implementation, run `git branch --show-current`, `git status --short --branch`, and `git log -3 --oneline --decorate`.
- Stabilization work must live on `codex/demo-stabilization`, never directly on `main`. The one-time transition from the current uncommitted `main` working tree is handled by the checkpoint prompt below; switching branches must preserve the working tree.
- During an implementation/fix prompt, do not commit, amend, rebase, merge, push, reset, clean, or switch branches. Return the diff for Codex review.
- A task may be checkpointed only after Codex explicitly says it is accepted. After acceptance, run the checkpoint prompt as a separate task. The checkpoint commit hash becomes `<BASE_COMMIT>` for the next implementation prompt.
- Stage files by explicit path; never use `git add -A` or `git add .` in this mixed working tree.
- Never commit `docs/BILLOCKET-DEFENSE-HANDBOOK.md`, `local.properties`, secrets, APKs, build outputs, private photos/receipts, device logs, or unrelated user changes. If the handbook is staged, use `git restore --staged -- docs/BILLOCKET-DEFENSE-HANDBOOK.md`; this must keep the file itself intact.
- Never push unless the user separately and explicitly requests it.

Start from <BASE_COMMIT> on `codex/demo-stabilization`. If <BASE_COMMIT> has not been replaced during an implementation prompt, preserve every existing staged/unstaged/untracked change, do not reset or mass-format anything, do not commit, and report that an accepted checkpoint is still required before another implementation task begins.
```

## Mandatory checkpoint prompt — run only after Codex acceptance

For the first checkpoint, this prompt creates `codex/demo-stabilization` from the current HEAD while preserving the uncommitted Base Sprint + accepted LOC-01 work. For later checkpoints it stays on that branch.

```text
The preceding task has been explicitly accepted by Codex. Create a clean local checkpoint; do not change source or documentation content.

First inspect `git branch --show-current`, `git status --short --branch`, and `git diff --check`.

Branch rule:
- If the current branch is `main` and `codex/demo-stabilization` does not exist, run `git switch -c codex/demo-stabilization`. This must preserve the current working tree.
- If already on `codex/demo-stabilization`, remain there.
- If the branch exists elsewhere or the current branch is anything unexpected, stop and report instead of guessing, resetting, or deleting a branch.

Safety rule:
- Run `git restore --staged -- docs/BILLOCKET-DEFENSE-HANDBOOK.md` if that path is staged. Keep the file intact and uncommitted.
- Never stage `local.properties`, secrets, APKs, build outputs, private media, device logs, or unrelated user changes.
- Stage only the explicitly accepted task files, using exact path arguments. Never use `git add .` or `git add -A`.
- Show `git diff --cached --check`, `git diff --cached --stat`, and `git diff --cached --name-status` before committing.
- If any unexpected file is staged, unstage it and stop for review if ownership is unclear.

Commit with the accepted message supplied for this checkpoint. Do not amend, squash, rebase, merge, tag, or push.

Return:
1. branch name;
2. commit hash and subject;
3. exact files in the commit (`git show --name-status --oneline --decorate --stat HEAD`);
4. `git status --short --branch` after the commit;
5. explicit confirmation that `docs/BILLOCKET-DEFENSE-HANDBOOK.md`, secrets, APKs, build outputs, and unrelated changes were not committed.
```

## Prompt 1 — LOC-01 remove location from capture critical path

```text
Implement only LOC-01 from docs/17-DEMO-STABILIZATION-PLAN.md.

The current defect is that CaptureViewModel.normalizeAndAddNow waits up to 8.5 seconds for location and can call deviceLocation.current() again after prefetch failure. Remove that behavior.

Required behavior:
- Continue warming location when the capture screen opens and permission exists.
- CameraX save and image normalization remain the only work that may delay thumbnail creation.
- Persist and show the normalized DraftImage before location handling.
- A warmed successful result may be attached. An in-flight warmed result may be awaited for at most 150 ms after normalization.
- Never call a new fresh provider acquisition from the capture completion path.
- Clear global isProcessing immediately after the image is persisted.
- Missing/failed/timed-out location keeps the image and does not emit a capture error.
- Continue remains available after image normalization regardless of location.
- Results must be matched to the correct image ID. Removed images must not be recreated.

Do not add WorkManager or an application-wide background coordinator in this task. Do not change AI or voice code.

Add tests using virtual coroutine time for success, unresolved timeout, failure, permission denial, removal, and two rapid captures. Run focused capture/location tests and :app:testDebugUnitTest. Return timing evidence from the Realme device for five cold/first captures and five subsequent captures if the device is available; otherwise explicitly mark device acceptance pending.
```

## Prompt 2 — LOC-02/03 location status UI and acceptance

```text
Implement only LOC-02 and LOC-03 from docs/17-DEMO-STABILIZATION-PLAN.md on top of the accepted LOC-01 commit.

Use the predefined CaptureLocationStatus contract. Show per-image feedback without a global spinner:
- Resolving: neutral small progress indicator and localized label.
- Attached: subtle pin/check state.
- Unavailable: muted location-off state with “can add later”; never error red.

Use the exact Vietnamese/English copy from the plan. Preserve 48 dp touch targets and existing BillLocket styling. Tapping location feedback in the editor must continue to open the existing manual location screen. Do not redesign the camera or transaction editor.

Add string parity tests and state rendering/behavior tests where practical. Execute the physical matrix in LOC-03 without uninstalling or clearing app data. Capture screenshots for first-photo unavailable/attached and editor manual-location access. Report shutter-to-thumbnail timings, not total time spent on the screen.
```

## Prompt 3 — VOICE-01/02 recognizer availability and input level

```text
Implement only VOICE-01 and VOICE-02 from docs/17-DEMO-STABILIZATION-PLAN.md.

Required changes:
- Do not require queryIntentServices(... MATCH_DEFAULT_ONLY) to return a service when SpeechRecognizer.isRecognitionAvailable is true.
- Keep recognizer creation guarded and all framework calls on the main thread.
- Convert RecognitionListener.onRmsChanged into a throttled, smoothed AudioLevel event using the exact initial mapping from the plan.
- Clamp levels to finite 0..1 and reset to zero on every terminal transition.
- Keep generation filtering and idempotent lifecycle cleanup.
- Do not read/store onBufferReceived audio and do not log speech text or RMS values.

Update fakes and focused unit tests. Do not change the editor UI in this task. Test availability behavior for: on-device available, default service available with empty query result, no service, creation exception, and stale-generation callbacks.
```

## Prompt 4 — VOICE-03/04 state machine and listening UI

```text
Implement only VOICE-03 and VOICE-04 from docs/17-DEMO-STABILIZATION-PLAN.md using the accepted voice contracts/components.

Use explicit STARTING, READY, and SPEECH_DETECTED phases. Add the 5-second ready watchdog, preserve the 8-second processing watchdog, and add the documented 30-second ceiling only if it can be covered by deterministic coroutine tests.

Replace the ambiguous check action with a stop-square action. Build the 52 dp listening capsule exactly as specified: mic + five-bar meter, phase/partial text, 48 dp stop and cancel touch targets. The meter must not trigger accessibility announcements for each level update. Handle large font without overlap. Preserve manual note editing and note-merging behavior.

Add Compose previews for dark/light themes, state-machine tests, lifecycle tests, and string parity tests. Do not add a system recognition activity fallback or change AI/location code.

After automated tests, run the ten-phrase Realme matrix online plus silence/cancel. Record expected/actual text and readiness/final-result latency without recording audio.
```

## Prompt 5 — VOICE-05 device failure states

```text
Implement only VOICE-05 from docs/17-DEMO-STABILIZATION-PLAN.md.

Use the exact actionable error meanings from the plan. Verify microphone denied, permanently denied, offline/network error, silence/no-match, recognizer busy, service unavailable, cancel, and navigation-away cleanup. Manual note input and transaction Save must remain functional in every case.

Do not auto-retry recognizer busy. Do not add ACTION_RECOGNIZE_SPEECH fallback yet. Do not broaden scope into voice amount/category entry.

Run focused tests and the physical failure matrix on Realme. Provide screenshots of at least denied permission, no speech, and service/network unavailable states. Do not clear permissions with app-data deletion; use normal permission controls or narrowly scoped adb permission commands only when explicitly approved.
```

## Prompt 6 — AI-01/02 bundled OCR and stable contracts

```text
Implement only AI-01 and AI-02 from docs/17-DEMO-STABILIZATION-PLAN.md.

Add com.google.mlkit:text-recognition:16.0.1 through the version catalog. Use only the bundled Latin recognizer. Introduce ReceiptTextExtractor, TransactionPhotoAnalysisEngine, AiEvidence, AmountSuggestion, CategoryAiSuggestion, and TransactionAiSuggestion exactly as specified. Use the current string-based CategoryId/stable semantic IDs; do not introduce Long category IDs or map translated names.

Implement bounded bitmap decoding and EXIF-correct orientation by reusing/extracting the proven image-decoding logic rather than copying divergent code. OCR raw text may exist only in bounded in-memory objects owned by the analysis request.

The extractor returns data in memory only. Do not persist or log raw OCR text. Do not integrate the new engine into TransactionEditorViewModel yet. Do not alter current image-label classification behavior in this task.

Add tests for resource cleanup, corrupt/unsupported URI, cancellation, rotation, bounds, and a small licensed/generated Latin receipt fixture. Measure APK delta and first/subsequent OCR latency on Realme if available.
```

## Prompt 7 — AI-03/04 OCR normalization and category dictionary

```text
Implement only AI-03 and AI-04 from docs/17-DEMO-STABILIZATION-PLAN.md as pure Kotlin code.

Build accented and diacritic-folded lookup forms while preserving Vietnamese text. Redact/ignore likely phone, card/account, timestamp, and long numeric sequences in semantic evidence while retaining a separate bounded numeric representation for the later amount parser. Match whole phrases. Bound input size. Add the initial auditable dictionary from the plan as data, not scattered when-statements.

Tests must cover Vietnamese diacritics, folded matching, English, phrase boundaries, false substring matches, privacy-sensitive numeric tokens, empty/huge input, merchant aliases, and every semantic group. Do not connect to the editor or tune scoring in this task.
```

## Prompt 8 — AI-05 receipt detection and amount parser

```text
Implement only AI-05 from docs/17-DEMO-STABILIZATION-PLAN.md.

Implement the pure Kotlin receipt detection and amount-candidate parser. Recognize the approved total markers and negative markers from the plan. Do not choose the first or largest number by default. Require minimum 0.90 confidence, reject ambiguous candidates, validate positive/domain-safe VND minor units, and produce an AmountSuggestion only.

Do not connect to TransactionEditorViewModel and do not autofill amount. Do not add merchant, items, tax, receipt number, payment method, or date fields.

Tests must cover Vietnamese/English total markers, line items, discounts, VAT-only values, cash received, change, phone/account strings, thousand separators, ambiguous totals, invalid/negative/overflow values, and deterministic confidence ordering.
```

## Prompt 9 — AI-06 concurrent analysis and category fusion

```text
Implement only AI-06 from docs/17-DEMO-STABILIZATION-PLAN.md.

Run OCR and existing Image Labeling concurrently under supervisorScope after the editor/photo state is already usable. Await failures independently. Fuse OCR merchant/keyword evidence and visual evidence using the documented weights, 0.72 category threshold, and 0.12 ambiguity margin. Return one TransactionAiSuggestion containing optional amount and category suggestions.

Use purpose-specific bounded image representations. Start with 3-second labeling and 5-second OCR safety ceilings, but record real latency on Realme. Never persist/log raw OCR or hold full bitmaps in ViewModel state. Failure of one source must still allow the other source to contribute.

Add tests for true concurrent start, source isolation, agreement boost, conflict/ambiguity, timeout, cancellation, stale image key, deterministic evidence order, and resource cleanup. Do not integrate editor UI yet.
```

## Prompt 10 — AI-07 editor integration and human-approved UI

```text
Implement only AI-07 from docs/17-DEMO-STABILIZATION-PLAN.md.

Integrate TransactionPhotoAnalysisEngine into the current editor lifecycle without changing canSave. Hide processing for 400 ms, then optionally show the compact inline analysis state. Show separate Apply/Dismiss actions for category and formatted VND amount. Never silently autofill either field and never write AI results directly to Room.

Track narrow amount/category user-edit protection. Manual edits invalidate the corresponding late suggestion. Preserve primary-image caching, cancellation, process recreation, apply/dismiss, manual category precedence, and image reorder behavior. Automatic engine failure is silent; no red card/snackbar/dialog. Display only sanitized dictionary or total-marker evidence.

Add tests proving save works while analyzing, explicit Apply uses normal validation, no raw OCR is persisted, late results cannot overwrite or distract after user edits, stale photo results are ignored, and source failure is nonblocking. Verify both languages, dark/light themes, large font, and TalkBack labels.
```

## Prompt 11 — AI-08 calibration and final gate

```text
Perform AI-08 and Sprint 4 from docs/17-DEMO-STABILIZATION-PLAN.md. This is validation/calibration, not feature expansion.

Build or curate the non-private set of 50–70 ordinary photos and 30–50 receipts with ground-truth category and receipt total. Keep user photos/private data out of Git. Calculate category precision/coverage/accuracy, shown-amount exact-match precision, per-class confusion, and OCR/labeling/fusion/end-to-result latency.

Thresholds may be adjusted only with before/after metrics and focused test updates. Do not add broad keywords merely to make individual examples pass. Require category precision >=80% and shown-amount exact-match precision >=85%; disable only the failing suggestion type behind a local feature flag when a gate is missed.

Run the complete JDK 17 automated gate and Realme physical regression matrix. Do not uninstall or clear data. Make a release recommendation using the stop/go criteria and never claim device completion without recorded evidence.
```

## Review prompt for Codex after each Antigravity task

```text
Review the latest Antigravity commit against the exact task and docs/17-DEMO-STABILIZATION-PLAN.md. Diagnose only unless I explicitly ask you to fix it. Check scope creep, races, lifecycle/resource cleanup, privacy, localization parity, accessibility, test quality, and whether device claims have real evidence. Run the proportional focused tests. Report findings by severity with file/line references, then state pass, conditional pass, or reject for the next task.
```
