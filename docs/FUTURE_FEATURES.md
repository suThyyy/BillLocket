# Optional Development After V1

This document preserves promising ideas without expanding the V1 commitment. Items may move into requirements only after V1 is stable, measured, and explicitly reprioritized.

The report-ready V1 baseline is now preserved at `baseline/report-ready-2026-10-09`. Vietnamese/English localization, bounded voice dictation for the note field, and on-device image category suggestions are approved post-baseline work in [the active delivery plan](16-VOICE-LOCALIZATION-AI-PLAN.md). Broader voice parsing and receipt OCR remain deferred.

## Selection gate

A future feature may be scheduled only when:

- V1 builds and runs reliably on the demonstration device.
- Core automated tests pass.
- No high-severity data-loss or privacy issue is open.
- The feature has a written acceptance criterion and architecture impact review.

## Priority A: Natural V1 extensions

### Receipt OCR

- Detect total amount, date, merchant, and candidate line items.
- Present suggestions for confirmation; never silently save OCR output.
- Preserve the original image and confidence metadata for debugging.

### Monthly photo recap — complete

- Generate an album/collage of the month's transactions.
- Summarize total expense, favorite category, most visited place, and memorable photos.
- Export a privacy-safe image or short video.

### Recurring transactions

- Templates for rent, subscriptions, allowance, and salary.
- WorkManager reminders or automatic draft creation.
- Clear distinction between a planned occurrence and a confirmed transaction.

### Biometric app lock

- Protect app launch and sensitive views with device biometrics.
- Provide a safe fallback when biometric hardware is unavailable.

### Rich export

- CSV export for transactions.
- PDF monthly reports with charts and selected images.
- Selectable date range and redaction of location or notes.

### Enhanced widgets

- Multiple widget sizes.
- Random memory, latest transaction, daily total, or budget progress modes.
- Category/account filters and rotation schedule.

## Priority B: Advanced insights

### Spending intent and emotion

- Optional tags such as necessary, impulse, self-reward, social, and emergency.
- Reports correlating spending with intent without presenting medical or financial advice.

### Location insights

- Place history, frequently visited merchants, category-by-location summaries, and map heatmaps.
- Travel mode and city/month filters.
- Duplicate or near-identical place merging.

### Savings goals

- Target amount and target date.
- Contributions from accounts without treating transfers as income or expense.
- Progress widget and reminders.

### Smart duplicate detection

- Warn when image, merchant, amount, and time suggest the same receipt was entered twice.
- Require user confirmation before removing anything.

### Voice-assisted entry

- Bounded note-field dictation is scheduled separately in BL-046.
- Parse utterances such as “ăn trưa năm mươi nghìn, ví tiền mặt”.
- Always show parsed fields before save.

### On-device image category suggestions — scheduled

- Label the primary transaction image locally and map only a conservative whitelist to category suggestions.
- Require explicit user confirmation and never override a manual category selection.
- Keep remote vision APIs and custom model training out of the scheduled scope.

### Barcode or QR assistance

- Read supported receipt/payment codes to prefill reference information.
- Do not execute payments.

## Priority C: Cloud and collaboration

### Account and encrypted synchronization

- Optional sign-in.
- Multi-device backup and conflict resolution.
- Documented encryption, retention, deletion, and recovery policies.

### Shared budgets

- Invite members, define roles, and track a shared household or trip budget.
- Audit trail for edits and deletions.

### Social memory sharing

- Share selected transaction memories with explicit privacy controls.
- No public-by-default feed.

### Bank data import

- Import a user-provided statement before considering direct bank connections.
- Deduplicate imported records against photo transactions.

## Long-term financial modules

- Loans and repayment schedules.
- Term deposits and maturity reminders.
- Investment tracking.
- Multi-currency accounts and exchange-rate history.
- Financial health insights and personalized recommendations.

These modules are intentionally deferred because they substantially change the domain model, security expectations, and project scope.

## Product and monetization ideas

- Optional premium tier only after the free core experience is complete.
- Additional themes, icons, recap templates, and advanced widgets.
- No advertising or monetization work is part of the course-project V1.

