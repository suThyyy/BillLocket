# BillLocket V1 UI State Matrix

Last updated: 2026-10-03

This matrix records the BL-037 cross-cutting UI review. “Implemented” means production UI has an explicit state; physical-device accessibility evidence remains part of BL-039 unless otherwise recorded.

| Area | Loading | Empty | Error / degraded | Recovery / CTA |
|---|---|---|---|---|
| Home/calendar | Progress indicator | Selected-day card | Inline repository error | Create for selected day |
| Capture | Processing indicator | Camera permission/unavailable composition | Camera failure, invalid/large image and permission state | Grant permission/settings, Photo Picker or skip |
| Transaction editor | Save progress | Add-photo hero supports no-photo flow | Field-level amount/category/account/image/save errors | Correct fields, remove missing image, retry save |
| Transaction detail | Branded loading state | No-photo and not-found states | Missing image and inline error banner | Edit/back; missing image does not hide financial data |
| Search | Progress indicator | Branded no-result state | Inline error banner | Change text/date/type/account/category filters |
| Accounts | Progress indicator | Branded create-account state | Inline error banner | Create account or retry navigation |
| Categories | Existing list state | Branded create-category state | Inline error text | Create/edit category |
| Transfers | Progress indicator | Branded create-transfer state | Inline error banner | Create transfer |
| Statistics | Progress indicator | Branded no-period-data state | Inline error banner | Change period/account; financial core unaffected |
| Budgets | Existing list state | Branded create-budget state | Inline error banner; notification denial explanation | Create budget or enable notifications contextually |
| Map | Provider loading | Branded no-confirmed-location state | Provider fallback with local photo list | Retry provider/current location; core finance remains usable |
| More/settings | Synchronous local settings | N/A | Permission/storage/backup status messages | System settings, cleanup, export/import retry |
| Widget | Glance refresh | Safe no-photo fallback | Missing-media fallback to another eligible photo | Quick capture/app entry |
| Backup/import | Busy progress | N/A | Invalid/corrupt archive and rollback-safe failure message | Choose another file; old data retained |

## Motion

- Material controls provide short state transitions for chips, switches, buttons and dialogs.
- BillLocket does not add decorative or long-running animation to financial workflows.
- Image and amount hierarchy is expressed through layout and surface contrast rather than motion.
- Any later custom animation should remain approximately 150–250 ms and respect platform accessibility settings.

## Accessibility review

- Shared empty/error/loading components use readable text equivalents rather than icon-only meaning.
- Primary interactive controls preserve practical Material touch targets.
- Transaction images and map/list photos use contextual content descriptions where they convey information.
- Charts retain text rows/content descriptions; color is not the only representation of values.
- Statistics metric cards scroll horizontally instead of forcing three narrow columns at larger font scales.
- IME behavior and scrolling requirements are specified in `docs/12-UIUX-SPEC.md` and accepted under BL-034.
- Final TalkBack order, font scaling, contrast and device clipping remain explicit BL-039 physical-device checks; compile success alone is not evidence.

## Current map note

DEV-002 was resolved by app-owned representative photo markers, cluster-count badges and a stable missing-media fallback. The active renderer is now Google Maps Compose under ADR-016; blank/invalid key, disabled API/billing, offline and provider-failure states must continue to preserve local records and core finance.
