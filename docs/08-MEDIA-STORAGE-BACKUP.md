# BillLocket V1 Media Storage, Cleanup and Backup

Status: approved by BL-016 on 2026-09-30.

## Goals

- Own every saved transaction image so records do not depend on temporary picker grants or external files.
- Keep receipts readable while bounding storage and decode cost.
- Persist stable URIs and metadata in Room, never image blobs or absolute filesystem paths.
- Preserve explicit per-image location consent while avoiding hidden GPS data in final files.
- Recover safely from process death between file and database operations.
- Provide a portable, versioned local export/import that restores financial data and image associations.

## Storage layout

All V1 media is private app storage. It is removed on uninstall and is not visible in the public gallery unless the user explicitly exports/shares it.

```text
filesDir/
  media/
    images/
      <image-id>.jpg

noBackupFilesDir/
  media-staging/
    <operation-id>/
      <image-id>.tmp
      operation.json
  media-trash/
    <operation-id>/
      <image-id>.jpg
  import-staging/
    <import-id>/
      ...validated extraction...
  recovery/
    <operation-id>/
      journal.json

cacheDir/
  thumbnails/
  share/
```

- `filesDir/media/images` owns final images.
- `noBackupFilesDir` holds restart-safe temporary/recovery state that automatic platform backup must not copy.
- `cacheDir` contains reproducible thumbnails and short-lived share files only; no authoritative data.
- Directory names are implementation details hidden behind `MediaStorePort`.

Each final image has the canonical URI:

```text
content://com.billlocket.app.media/images/<image-id>
```

Room stores that URI in `transaction_images.content_uri`. The media adapter resolves it to the app-private file. Internal code never persists `file://` URIs, absolute paths or original picker URIs. External sharing uses a short-lived, read-only `FileProvider` URI with explicit grant flags; it is not persisted.

## Capture and import

### CameraX capture

1. Generate operation/image UUIDs before capture.
2. Capture with `ImageCapture.takePicture(OutputFileOptions, ...)` directly to `noBackupFilesDir/media-staging/<operation-id>/<image-id>.tmp`.
3. Close/flush the output before inspection.
4. Read orientation/dimensions/EXIF off the main thread.
5. Present the draft preview and optional contextual current-location action.

No public-media write or legacy storage permission is needed.

### System Photo Picker

1. Use the system Photo Picker for images rather than broad library permission.
2. Open the granted URI and copy its bytes immediately into the operation staging directory.
3. Do not make long-term transaction correctness depend on persisted URI grants. Persistable grants may support an interrupted copy, but are released after the app-owned copy succeeds.
4. Enforce count, MIME, decode and size limits before accepting the draft.
5. Treat cloud-provider picker content as an input stream that may fail or disappear; no transaction is saved until the owned copy is ready.

The source file is never modified or deleted.

## Supported input and normalized output

V1 accepts image formats that AndroidX ExifInterface and the platform decoder can safely decode on the supported device, initially JPEG, PNG, WebP and HEIF/HEIC where platform decoding succeeds. Unsupported, corrupt, animated or excessively large inputs return a typed validation/storage error.

Every final V1 image is a normalized JPEG:

- orientation applied to pixels;
- sRGB-compatible output;
- long edge at most 2560 px, never upscaled;
- initial JPEG quality 88;
- if output exceeds 4 MiB, retry quality down to 75, then reduce long edge to 2048 px if still necessary;
- preserve enough resolution/contrast for receipt text; never create a thumbnail as the authoritative image;
- width, height, MIME and byte size are measured from the final file and stored in Room.

Compression limits are defaults, not permission to save an unreadable receipt. Representative receipt fixtures must remain legible in instrumentation/visual tests. If a source is already within bounds, it is still decoded/re-encoded so orientation and metadata policy are deterministic.

## EXIF and location privacy

- Use AndroidX ExifInterface to read orientation, capture time and candidate GPS metadata before normalization.
- Imported GPS is shown as an untrusted candidate. It is persisted only after explicit confirmation/correction for that image.
- Current/manual coordinates follow the same confirmation boundary.
- Final JPEG files strip GPS, device make/model, serial identifiers, comments and thumbnails. Confirmed latitude/longitude/source/place live only in the corresponding Room image row.
- Safe capture time may be persisted in Room. The final file needs no authoritative EXIF metadata.
- Removing location clears all location columns and map eligibility; no GPS remains hidden in the image file.

## Finalization protocol

Cross-filesystem and Room writes cannot share one transaction, so operations are journaled and recoverable.

### Create/add image

1. Produce and validate the normalized staging file.
2. Write `operation.json` with image ID, expected final URI, checksum and state `PREPARED`.
3. Atomically rename the file into `filesDir/media/images/<image-id>.jpg` on the same internal filesystem; update journal to `FILE_READY`.
4. Commit the transaction/image/search metadata in one Room transaction.
5. Mark operation `COMMITTED`, remove staging/journal and request widget refresh.

If Room fails, delete the new final file or leave it for reconciliation. If the process dies after file promotion but before database commit, reconciliation sees an unreferenced file and removes it after the safety window. A database row must never be committed before its final file is ready.

### Replace/remove image

- Prepare added/replacement files first.
- Commit the desired ordered image rows atomically.
- Move no-longer-referenced owned files to `media-trash` after commit, then delete them idempotently.
- A cleanup failure does not roll back correct financial state; WorkManager retries it.

### Transaction delete and undo

- Soft delete keeps media intact during the undo window.
- Undo restores database visibility without moving files.
- Final purge hard-deletes metadata and returns media IDs. Files move to trash/delete after commit.
- If a missing file is encountered, the transaction remains valid; UI/widget use a missing-media fallback and reconciliation records the issue.

## Reconciliation and cleanup

An idempotent `MediaReconciliationWorker` runs after app upgrade/import and periodically under conservative constraints. It:

1. finishes or rolls back journaled operations;
2. removes staging operations older than 24 hours when not referenced by an active draft/recovery journal;
3. removes final files with no Room image row after a 24-hour safety window;
4. removes trash/cache/share files past their retention time;
5. marks/reports Room image rows whose file is missing, without deleting the transaction;
6. never follows symlinks or deletes outside resolved app-owned media roots.

Cleanup compares normalized canonical paths/IDs and is safe to repeat after partial failure. Work input carries IDs, not arbitrary paths. Manual “remove orphaned media” first shows count/size and never removes referenced files.

Storage reporting includes authoritative image bytes, temporary/recovery bytes and cache bytes separately. “Clear cache” never touches final media or Room.

## Automatic Android backup policy

Android Auto Backup has a small quota and can restore files/preferences independently of the intended portable data contract. To prevent partial financial restores and unreviewed cloud copies, V1 opts BillLocket financial data, final media, staging/recovery and sensitive preferences out of Android Auto Backup/device-transfer rules. The implementation must update both legacy `backup_rules.xml` and Android 12+ `data_extraction_rules.xml`; manifest behavior is verified on supported APIs.

User-controlled backup/export below is the supported V1 recovery mechanism. No cache, map tile, API key or temporary grant is backed up.

## Portable backup format

Export produces one document chosen through Storage Access Framework (`ACTION_CREATE_DOCUMENT`), suggested name:

```text
BillLocket-backup-YYYY-MM-DD-HHmm.blk.zip
```

Archive layout:

```text
manifest.json
data.json
checksums.sha256
media/
  <image-id>.jpg
```

### `manifest.json`

- format name and integer `formatVersion` (initially 1);
- BillLocket app version/database schema version;
- export timestamp and source timezone;
- configured currency;
- record/media counts;
- flags for included images and locations;
- checksum algorithm (`SHA-256`).

### `data.json`

A portable domain DTO representation of accounts, categories, active transactions, ordered images, transfers, budgets, threshold state needed for correct behavior and supported settings. It contains stable UUIDs and canonical media filenames, not Room table dumps, Android resource IDs, absolute paths, source picker URIs or provider SDK objects.

Pending-deletion transactions, caches, orphan files, logs and notification history that is not required for deduplication are excluded. Default export includes final images and confirmed locations so it is sufficient to restore V1. The UI clearly offers privacy options to omit images and/or locations; the manifest records omissions and import must not pretend the omitted content can be restored.

### Integrity and limits

- Stream export; do not load the archive or images fully into memory.
- Calculate SHA-256 for `data.json` and each included media file; `checksums.sha256` and manifest counts are verified on import.
- Reject duplicate entry names, absolute paths, `..` traversal, symlinks, unsupported methods, excessive entry count/size/compression ratio and files not declared by the manifest.
- Format version is independent of Room schema version. Readers explicitly support known versions and fail safely on newer unknown versions.
- V1 archives are not password-encrypted. Before export, explain that the selected destination may contain sensitive financial images and locations. Encrypted backup remains a future feature, not an implicit weak custom-crypto implementation.

## Import/restore protocol

V1 supports **replace restore**, not merge:

1. Select one archive with Storage Access Framework and copy it to `noBackupFilesDir/import-staging/<import-id>`.
2. Parse/validate manifest, safe paths, supported format, checksums, IDs, money, relations, image metadata and business rules without changing current data.
3. Show a summary and require explicit confirmation that current BillLocket data will be replaced.
4. Create a private recovery snapshot/journal of the current database export and media references.
5. Normalize/verify imported media into a new staging set.
6. Mark journal `PREPARED`; replace Room data in one transaction; atomically promote the imported media set; mark `COMMITTED`.
7. On process restart, complete or roll back any non-committed journal before exposing repositories/UI.
8. After success, reconcile, refresh widgets and delete recovery files. On failure, restore the previous dataset/media and report no success.

Import preserves IDs and ordering. It validates that each image belongs to exactly one transaction, coordinates are confirmed/ranged, transaction/category types match, account/category references exist, money uses one currency and media checksums match. Missing optional media produces an explicit incomplete-backup decision before commit; malformed/unsafe archives are rejected.

## Permissions and sharing

- Camera capture requests `CAMERA` contextually.
- Photo Picker import requests no broad media/storage permission.
- App-private storage needs no legacy external-storage permission; the obsolete `WRITE_EXTERNAL_STORAGE` declaration should be removed in the bounded implementation task.
- Export/import use user-selected document URI grants only for the operation; retain a grant only when a resumable operation genuinely requires it, then release it.
- Sharing is explicit, uses a read-only FileProvider URI and grants only the receiving intent.

## Port contract refinements

`MediaStorePort` implementation must support:

- `stageCapture`, `stageImport` and `inspectDraft`;
- `normalize` returning checksum/dimensions/MIME/size and EXIF candidate metadata;
- `promote`, `exists`, `openRead`, `moveToTrash` and idempotent `delete` using media IDs/references;
- `reconcile` and `measureStorage` scoped to owned roots.

`BackupPort` supports streamed `export(options, destination)` and `inspect/importReplace(source)` with progress, cancellation and typed failure. Cancellation closes streams, removes temporary output where possible and leaves current data intact.

The exact Kotlin signatures may be refined during implementation, but path validation, ownership, idempotency and recovery semantics are mandatory.

## Testing requirements

- Unit tests: URI/media-ID validation, filename/path traversal rejection, journal state transitions, checksum/manifest validation, compression decision rules and EXIF-consent mapping.
- Instrumentation tests: CameraX output file integration, picker-copy behavior, orientation normalization, readable output bounds, FileProvider sharing and app-private cleanup.
- Data/integration tests: file-first/Room failure, process-death recovery at every journal state, soft-delete undo, orphan reconciliation and missing-media fallback.
- Backup tests: round-trip representative full dataset, privacy omissions, corrupted checksum, unknown version, zip bomb/traversal, duplicate IDs, interrupted import and rollback.
- Verify backup/data-extraction rules exclude database, media and recovery roots.

## Official references reviewed

- [CameraX photo capture](https://developer.android.com/media/camera/camerax/take-photo)
- [Android Photo Picker](https://developer.android.com/training/data-storage/shared/photo-picker)
- [AndroidX ExifInterface](https://developer.android.com/reference/androidx/exifinterface/media/ExifInterface)
- [Android Auto Backup](https://developer.android.com/identity/data/autobackup)

Implementation must recheck supported formats, platform behavior and backup rules against current official Android documentation.
