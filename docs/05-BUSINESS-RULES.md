# BillLocket Business Rules

## Transactions

- **BR-TRX-01:** Amount must be greater than zero.
- **BR-TRX-02:** Every transaction has exactly one type: income or expense.
- **BR-TRX-03:** A transaction category must match its type.
- **BR-TRX-04:** A transaction must reference one active account when created.
- **BR-TRX-05:** A transaction may contain zero or more images.
- **BR-TRX-06:** Deleting a transaction removes its owned media only when no other record references that media.
- **BR-TRX-07:** Deleted transactions do not contribute to calendar totals, statistics, budgets, account balances, widgets, or map markers.
- **BR-TRX-08:** Editing amount, type, account, or date recalculates every affected derived value.

## Images and location

- **BR-IMG-01:** Every stored image belongs to exactly one transaction in V1.
- **BR-IMG-02:** Image order is stable and explicitly persisted.
- **BR-LOC-01:** Location consent is requested in context and is never implied by camera permission.
- **BR-LOC-02:** Denying location must not block capture or transaction creation.
- **BR-LOC-03:** Location is stored per image because images in one transaction may originate from different places.
- **BR-LOC-04:** Imported EXIF location is presented for confirmation before final save.
- **BR-LOC-05:** An image without valid latitude and longitude does not appear as a map marker.
- **BR-LOC-06:** Removing a location removes the image from the map but not from the transaction.
- **BR-LOC-07:** Latitude must be within -90..90 and longitude within -180..180.

## Accounts and transfers

- **BR-ACC-01:** Archived accounts cannot receive new transactions or transfers.
- **BR-ACC-02:** Archiving an account preserves its historical records.
- **BR-ACC-03:** A transfer requires two distinct active accounts and an amount greater than zero.
- **BR-ACC-04:** A transfer decreases one account and increases another by the same amount.
- **BR-ACC-05:** Transfers do not count as income or expense in global statistics.

## Categories

- **BR-CAT-01:** Archived categories cannot be selected for new transactions.
- **BR-CAT-02:** Archiving a category preserves its historical transactions.
- **BR-CAT-03:** Default categories may be renamed or hidden but are not hard-deleted while referenced.

## Budgets

- **BR-BUD-01:** Budgets apply only to expense transactions.
- **BR-BUD-02:** Transfers and income do not consume budget.
- **BR-BUD-03:** An overall monthly budget and category budgets may coexist.
- **BR-BUD-04:** Budget progress is derived from transaction date, not record creation date.
- **BR-BUD-05:** A threshold notification is not repeatedly emitted for the same threshold and budget period unless progress first drops below it.

## Widget

- **BR-WID-01:** Only transactions with at least one existing image are eligible for photo display.
- **BR-WID-02:** Widget privacy settings override normal in-app amount visibility.
- **BR-WID-03:** Deleted or inaccessible media must immediately fall back to another eligible image or the empty state.

## Currency and calculations

- **BR-MON-01:** Monetary values are stored using an exact integer minor-unit representation or another documented exact representation; floating-point types are forbidden for persisted money.
- **BR-MON-02:** V1 uses one configured display currency and does not perform exchange-rate conversion.

