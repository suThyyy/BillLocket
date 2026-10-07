# BillLocket V1 Requirements

## Actor

### User

The single device owner who records and reviews personal financial activity. V1 has no administrator, friend, group, or merchant actor.

## Functional requirements

### FR-CAP Capture and media

- **FR-CAP-01:** Capture a photo with the device camera.
- **FR-CAP-02:** Select one or more images from the system photo picker.
- **FR-CAP-03:** Create a transaction without an image.
- **FR-CAP-04:** Switch available cameras and control flash when supported.
- **FR-CAP-05:** Support camera zoom when supported by the device.
- **FR-CAP-06:** Preview, retake, remove, and reorder selected images before saving.
- **FR-CAP-07:** Store images in app-managed storage and persist their URIs rather than image bytes in Room.
- **FR-CAP-08:** Compress oversized images without making receipts unreadable.

### FR-TRX Transactions

- **FR-TRX-01:** Create an expense or income.
- **FR-TRX-02:** Record an amount greater than zero.
- **FR-TRX-03:** Select a category appropriate to the transaction type.
- **FR-TRX-04:** Select an account.
- **FR-TRX-05:** Add an optional note.
- **FR-TRX-06:** Select transaction date and time.
- **FR-TRX-07:** View transaction details and all attached images.
- **FR-TRX-08:** Edit a transaction and its images.
- **FR-TRX-09:** Delete a transaction with confirmation and a short undo opportunity.
- **FR-TRX-10:** Search notes and filter by date, type, category, and account.

### FR-CAL Calendar and home

- **FR-CAL-01:** Display day and month views.
- **FR-CAL-02:** Display transaction photo thumbnails on calendar days.
- **FR-CAL-03:** Show total income and expense for the selected period.
- **FR-CAL-04:** Hide or reveal financial totals.
- **FR-CAL-05:** Open a day's transaction gallery from its calendar cell.
- **FR-CAL-06:** Create a transaction from a selected day.
- **FR-CAL-07:** Navigate between months.
- **FR-CAL-08:** Filter the calendar by transaction type and account.

### FR-CAT Categories

- **FR-CAT-01:** Provide default income and expense categories.
- **FR-CAT-02:** Create, rename, recolor, reorder, and archive a custom category.
- **FR-CAT-03:** Preserve historical transactions when a category is archived.

### FR-ACC Accounts and transfers

- **FR-ACC-01:** Support cash, bank, and e-wallet account types.
- **FR-ACC-02:** Create, edit, reorder, archive, and view accounts.
- **FR-ACC-03:** Store an opening balance.
- **FR-ACC-04:** Calculate current balance from opening balance and transactions.
- **FR-ACC-05:** Transfer money between two active accounts without changing total net worth.
- **FR-ACC-06:** View transfer history.

### FR-STA Statistics

- **FR-STA-01:** Show statistics by week, month, and year.
- **FR-STA-02:** Show income, expense, and net balance.
- **FR-STA-03:** Show expense distribution by category.
- **FR-STA-04:** Show daily or monthly trend data.
- **FR-STA-05:** Compare the selected period with the preceding equivalent period.
- **FR-STA-06:** Filter statistics by account.

### FR-BUD Budgets

- **FR-BUD-01:** Create a monthly overall expense budget.
- **FR-BUD-02:** Optionally create monthly category budgets.
- **FR-BUD-03:** Show amount and percentage consumed.
- **FR-BUD-04:** Notify at configurable warning and exceeded thresholds, defaulting to 80% and 100%.

### FR-LOC Photo location and map

- **FR-LOC-01:** Ask before attaching the current location to a captured image.
- **FR-LOC-02:** Attempt to read location metadata from a selected image when accessible.
- **FR-LOC-03:** Allow selecting or correcting an image location manually on a map.
- **FR-LOC-04:** Allow removing a saved image location.
- **FR-LOC-05:** Store location independently for each transaction image.
- **FR-LOC-06:** Display geotagged images as photo markers on a full-screen map.
- **FR-LOC-07:** Cluster overlapping markers.
- **FR-LOC-08:** Open a preview containing image, amount, category, time, and place when a marker is selected.
- **FR-LOC-09:** Open the corresponding transaction detail from a marker preview.
- **FR-LOC-10:** Filter map markers by period, transaction type, category, and account.
- **FR-LOC-11:** Provide place search and a current-location control when map capabilities permit.

### FR-WID Android widget

- **FR-WID-01:** Provide a home-screen widget showing a recent transaction image.
- **FR-WID-02:** Allow the user to show the amount and category, hide them, or obscure the amount.
- **FR-WID-03:** Open transaction detail when the displayed image is tapped.
- **FR-WID-04:** Provide a quick-capture action.
- **FR-WID-05:** Refresh after a relevant create, edit, or delete operation.
- **FR-WID-06:** Show a safe empty state when no eligible image exists.

### FR-SET Settings and privacy

- **FR-SET-01:** Configure currency and number formatting.
- **FR-SET-02:** Configure light, dark, or system theme.
- **FR-SET-03:** Manage camera, photo, notification, and location permissions through contextual explanations.
- **FR-SET-04:** Export and import a local backup sufficient to restore V1 financial data and image associations.
- **FR-SET-05:** Review storage usage and remove orphaned media safely.

## Non-functional requirements

- **NFR-01 Offline:** Core capture, CRUD, calendar, statistics, budgets, and local map history must work without an account.
- **NFR-02 Performance:** Common screens should remain responsive with at least 10,000 transactions and 20,000 image references on a mid-range Android device.
- **NFR-03 Reliability:** Financial mutations must use transactions where multiple records must change together.
- **NFR-04 Privacy:** Location is opt-in per image and can be removed later.
- **NFR-05 Accessibility:** Interactive elements require content descriptions, readable contrast, and practical touch targets.
- **NFR-06 Compatibility:** Minimum and target Android SDK versions will be finalized in architecture documentation; V1 must support the course demonstration device.
- **NFR-07 Localization:** User-visible text must be stored in resources, initially supporting Vietnamese.
- **NFR-08 Testability:** Business calculations must be testable without Android UI dependencies.

## V1 navigation

1. Home / Calendar
2. Statistics
3. Camera quick action
4. Map
5. Accounts and Settings

## Explicitly excluded from V1

- OCR and AI extraction
- Voice amount entry
- Authentication and cloud synchronization
- Social features, friends, chat, or shared feeds
- Shared/group budgets
- Loans, investments, and term deposits
- Bank API connections or real payments
- Subscriptions and premium plans

