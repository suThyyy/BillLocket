# BillLocket Vision

## Product statement

BillLocket is a private visual money journal for Android. A user records an expense or income by taking a photo, entering a small amount of financial information, and saving the moment to a calendar. The photo is the primary memory; the financial fields make it useful.

## Problem

Traditional expense trackers feel like spreadsheets and require repetitive data entry. Users often stop tracking because recording a small purchase takes too long and the history is visually unmemorable.

## Target user

- University students and young adults.
- Users who want lightweight personal tracking rather than accounting software.
- Users who prefer a photo-first interaction.

## Core value

Record a transaction in seconds and later remember not only how much was spent, but what was purchased and where the moment happened.

## Product principles

1. **Camera first:** creating a record should feel as immediate as taking a Locket photo.
2. **Low friction:** only amount and transaction type are universally required.
3. **Visual history:** the calendar, widget, and map are built around photos.
4. **Private by default:** data is local-first and location is opt-in.
5. **Small, stable V1:** financial breadth is intentionally limited.

## V1 differentiators

### Photo widget

An Android home-screen widget shows a recent transaction photo. The user controls whether amount and category are visible. Tapping the widget opens the transaction or quick capture.

### Transaction photo map

Each transaction image may store an optional location. A full-screen map displays photo thumbnails as markers, allowing the user to revisit transactions by place.

## V1 success criteria

- A user can create, edit, find, and delete a photo transaction reliably.
- Calendar totals and statistics are derived correctly from stored transactions.
- The widget updates after relevant transaction changes.
- The map displays only images with location and remains usable with location permission denied.
- The app works offline for all core features.

## Not V1

All deferred ideas are maintained in `docs/FUTURE_FEATURES.md`. A feature appearing there is not authorization to implement it.

